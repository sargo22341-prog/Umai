package org.opensources.umai.llm.data

import android.content.Context
import android.os.Debug
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.opensources.umai.llm.domain.LanguageModel
import org.opensources.umai.llm.domain.LlmFailure
import org.opensources.umai.llm.domain.LlmOutcome
import org.opensources.umai.llm.domain.LlmProgress
import org.opensources.umai.llm.domain.LlmRequest
import org.opensources.umai.llm.domain.LocalModel
import java.io.File

/** What a test run of the model measured. */
data class LlmBenchmark(
    val loadMillis: Long,
    val promptTokens: Int,
    /** Prompt tokens read per second. */
    val promptSpeed: Double,
    val generatedTokens: Int,
    /** Tokens written per second. */
    val generationSpeed: Double,
    /** Memory of the app while the model was loaded, in bytes. */
    val memoryBytes: Long,
    val threads: Int,
)

/**
 * The installed model, run on the CPU by llama.cpp.
 *
 * The model is loaded on first use and unloaded after a minute without use:
 * it takes gigabytes of memory, which the rest of the phone needs back. One
 * generation runs at a time.
 */
class LocalLanguageModel(
    context: Context,
    private val store: LocalAiSettingsStore,
    private val installer: ModelInstaller,
    private val work: LocalAiWork,
    private val scope: CoroutineScope,
) : LanguageModel {

    private val nativeLibDir = context.applicationContext.applicationInfo.nativeLibraryDir
    private val mutex = Mutex()
    private var handle = 0L
    private var loadedFile: File? = null
    private var unloadJob: Job? = null

    /** Whether llama.cpp runs on this device: it is only built for 64-bit ARM. */
    val isSupported: Boolean by lazy {
        LlamaNative.load() && runCatching { LlamaNative.nativeInit(nativeLibDir) }.isSuccess
    }

    /** The big cores do the work; the two smallest would only slow the others down. */
    val threads: Int = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(MIN_THREADS, MAX_THREADS)

    override val contextSize: Int = CONTEXT_SIZE

    override suspend fun isReady(): Boolean = modelFile() != null

    override suspend fun tokenCount(text: String): Int? = mutex.withLock {
        val file = modelFile() ?: return@withLock null
        withContext(Dispatchers.Default) {
            if (!ensureLoaded(file)) null else LlamaNative.nativeTokenCount(handle, text.toByteArray())
        }.also { scheduleUnload() }
    }

    override suspend fun generate(request: LlmRequest, onProgress: (LlmProgress) -> Unit): LlmOutcome =
        mutex.withLock {
            val file = modelFile() ?: return@withLock LlmOutcome.Failure(LlmFailure.NOT_READY)
            unloadJob?.cancel()
            work.begin()
            try {
                withContext(Dispatchers.Default) { run(file, request, onProgress) }
            } finally {
                work.end()
                scheduleUnload()
            }
        }

    /** Loads the model and runs a fixed prompt, to tell the user how fast the phone is. */
    suspend fun benchmark(): LlmBenchmark? = mutex.withLock {
        val file = modelFile() ?: return@withLock null
        unloadJob?.cancel()
        work.begin()
        try {
            withContext(Dispatchers.Default) {
                if (handle != 0L) unload()
                val loadStart = SystemClock.elapsedRealtime()
                if (!ensureLoaded(file)) return@withContext null
                val loadMillis = SystemClock.elapsedRealtime() - loadStart
                val prompt = LlamaNative.nativeFormat(handle, BENCH_SYSTEM.toByteArray(), BENCH_USER.toByteArray())
                    ?: return@withContext null
                var promptTokens = 0
                var promptDoneAt = 0L
                var generated = 0
                val start = SystemClock.elapsedRealtime()
                LlamaNative.nativeGenerate(handle, prompt, null, BENCH_TOKENS, 0.7f, 1) { done, total, written ->
                    promptTokens = total
                    if (written == 0 && done == total) promptDoneAt = SystemClock.elapsedRealtime()
                    generated = written
                    true
                } ?: return@withContext null
                val end = SystemClock.elapsedRealtime()
                LlmBenchmark(
                    loadMillis = loadMillis,
                    promptTokens = promptTokens,
                    promptSpeed = promptTokens * 1000.0 / (promptDoneAt - start).coerceAtLeast(1L),
                    generatedTokens = generated,
                    generationSpeed = generated * 1000.0 / (end - promptDoneAt).coerceAtLeast(1L),
                    memoryBytes = Debug.getPss() * 1024L,
                    threads = threads,
                )
            }
        } finally {
            work.end()
            scheduleUnload()
        }
    }

    private suspend fun run(file: File, request: LlmRequest, onProgress: (LlmProgress) -> Unit): LlmOutcome {
        if (!ensureLoaded(file)) return LlmOutcome.Failure(LlmFailure.LOAD_FAILED)
        val prompt = LlamaNative.nativeFormat(handle, request.system.toByteArray(), request.user.toByteArray())
            ?: return LlmOutcome.Failure(LlmFailure.GENERATION_FAILED)
        if (LlamaNative.nativeTokenCount(handle, prompt) + request.maxTokens > LlamaNative.nativeContextSize(handle)) {
            return LlmOutcome.Failure(LlmFailure.TOO_LONG)
        }
        val grammar = LlamaNative.nativeSchemaToGrammar(request.jsonSchema.toByteArray())
            ?: return LlmOutcome.Failure(LlmFailure.GENERATION_FAILED)
        val job = currentCoroutineContext().job
        val output = LlamaNative.nativeGenerate(
            handle = handle,
            prompt = prompt,
            grammar = grammar,
            maxTokens = request.maxTokens,
            temperature = request.temperature,
            seed = request.seed,
        ) { done, total, generated ->
            val progress = LlmProgress(done, total, generated)
            work.progress(progress)
            onProgress(progress)
            job.isActive
        }
        currentCoroutineContext().ensureActive()
        return output?.let { LlmOutcome.Success(it.decodeToString()) }
            ?: LlmOutcome.Failure(LlmFailure.GENERATION_FAILED)
    }

    /** The file of the installed model, when the local AI is on and runnable here. */
    private suspend fun modelFile(): File? {
        if (!isSupported) return null
        val settings = store.current()
        if (!settings.enabled) return null
        val model: LocalModel = settings.installed ?: return null
        return installer.fileOf(model)?.takeIf { it.isFile }
    }

    private fun ensureLoaded(file: File): Boolean {
        if (handle != 0L && loadedFile == file) return true
        unload()
        handle = LlamaNative.nativeLoad(file.path, CONTEXT_SIZE, threads)
        loadedFile = file.takeIf { handle != 0L }
        return handle != 0L
    }

    private fun unload() {
        if (handle != 0L) LlamaNative.nativeFree(handle)
        handle = 0L
        loadedFile = null
    }

    private fun scheduleUnload() {
        unloadJob?.cancel()
        unloadJob = scope.launch {
            delay(IDLE_UNLOAD_MS)
            mutex.withLock { withContext(Dispatchers.Default) { unload() } }
        }
    }

    private companion object {
        /**
         * Enough for the transcript of a twenty-minute video and its answer;
         * the models offered have a hybrid or sliding-window attention, so the
         * memory this context takes stays small.
         */
        const val CONTEXT_SIZE = 16_384
        const val MIN_THREADS = 2
        const val MAX_THREADS = 6
        const val IDLE_UNLOAD_MS = 60_000L

        const val BENCH_TOKENS = 64
        const val BENCH_SYSTEM = "You are a helpful cooking assistant. Answer in French."
        val BENCH_USER = """
            Voici une recette. Résume-la en une phrase, puis donne trois conseils pour la réussir.

            Poulet au curry pour 4 personnes : 600 g de blancs de poulet, 2 oignons, 2 gousses d'ail,
            1 morceau de gingembre frais, 2 cuillères à soupe de curry en poudre, 400 ml de lait de coco,
            1 boîte de tomates concassées, 1 bouquet de coriandre, 2 cuillères à soupe d'huile, sel, poivre.
            Émincer les oignons, hacher l'ail et le gingembre. Faire revenir dans l'huile 5 minutes.
            Ajouter le poulet coupé en dés et le faire dorer. Saupoudrer de curry, mélanger 1 minute.
            Verser les tomates et le lait de coco, laisser mijoter 20 minutes à feu doux.
            Rectifier l'assaisonnement et parsemer de coriandre. Servir avec du riz basmati.
        """.trimIndent()
    }
}
