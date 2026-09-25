package org.opensources.umai.llm.data

import android.os.Debug
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.opensources.umai.llm.domain.AiBackend
import org.opensources.umai.llm.domain.AiBackendUnavailable
import org.opensources.umai.llm.domain.AiEngine
import org.opensources.umai.llm.domain.AiEngineLoader
import org.opensources.umai.llm.domain.DeviceProfile
import org.opensources.umai.llm.domain.LanguageModel
import org.opensources.umai.llm.domain.LlmFailure
import org.opensources.umai.llm.domain.LlmOutcome
import org.opensources.umai.llm.domain.LlmProgress
import org.opensources.umai.llm.domain.LlmRequest
import org.opensources.umai.llm.domain.LocalModel
import org.opensources.umai.llm.domain.ModelFile

/** The installed model, with the path of each of its files present on this phone. */
data class InstalledModel(val model: LocalModel, val paths: Map<ModelFile, String>)

/** Keeps the app alive while the model works, and shows how far it is. */
interface ModelWork {
    fun begin()

    fun progress(progress: LlmProgress)

    fun end()
}

/** Where the model runs now, as proven when it was loaded. */
data class ActiveBackend(val backend: AiBackend, val model: String, val soc: String)

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
    val backend: AiBackend,
)

/**
 * The installed model, run by an [AiEngine] on the fastest backend that
 * works: the TPU of a Tensor chip, then the GPU, then the CPU.
 *
 * A backend is used only once loading the model there succeeded and proved it
 * runs there; one that fails is skipped until the app restarts. A prompt too
 * long for the fixed context of a TPU build goes to the GPU or the CPU.
 *
 * The model is loaded on first use and unloaded after a minute without use:
 * it takes gigabytes of memory, which the rest of the phone needs back. One
 * generation runs at a time.
 */
class LocalLanguageModel(
    private val installed: suspend () -> InstalledModel?,
    val device: DeviceProfile,
    private val loader: AiEngineLoader,
    /** Whether the runtime runs on this phone at all. */
    val isSupported: Boolean,
    private val work: ModelWork,
    private val scope: CoroutineScope,
    private val memoryBytes: () -> Long = { Debug.getPss() * 1024L },
) : LanguageModel {

    private val mutex = Mutex()
    private var engine: AiEngine? = null
    private var loadedPath: String? = null
    private var unloadJob: Job? = null
    private val unavailable = mutableSetOf<AiBackend>()

    private val _active = MutableStateFlow<ActiveBackend?>(null)

    /** The backend the model is loaded on, or null while it is not loaded. */
    val active: StateFlow<ActiveBackend?> = _active.asStateFlow()

    override val contextSize: Int = LocalModel.CONTEXT_SIZE

    override suspend fun isReady(): Boolean = currentModel() != null

    override suspend fun generate(request: LlmRequest, onProgress: (LlmProgress) -> Unit): LlmOutcome =
        mutex.withLock {
            val model = currentModel() ?: return@withLock LlmOutcome.Failure(LlmFailure.NOT_READY)
            unloadJob?.cancel()
            work.begin()
            try {
                withContext(Dispatchers.Default) { run(model, request, onProgress) }
            } finally {
                work.end()
                scheduleUnload()
            }
        }

    /** Loads the model on its best backend and times a fixed prompt, to tell the user how fast the phone is. */
    suspend fun benchmark(): LlmBenchmark? = mutex.withLock {
        val model = currentModel() ?: return@withLock null
        unloadJob?.cancel()
        work.begin()
        try {
            withContext(Dispatchers.Default) {
                unload()
                val start = SystemClock.elapsedRealtime()
                val engine = routes(model).firstNotNullOfOrNull { load(model, it) } ?: return@withContext null
                val loadMillis = SystemClock.elapsedRealtime() - start
                runCatching { engine.generate(BENCH_REQUEST).collect {} }
                    .onFailure { if (it is CancellationException) throw it }
                    .getOrNull() ?: return@withContext null
                val speed = engine.lastSpeed ?: return@withContext null
                logSpeed(model, engine)
                LlmBenchmark(
                    loadMillis = loadMillis,
                    promptTokens = speed.promptTokens,
                    promptSpeed = speed.promptSpeed,
                    generatedTokens = speed.generatedTokens,
                    generationSpeed = speed.generationSpeed,
                    memoryBytes = memoryBytes(),
                    backend = engine.backend,
                )
            }
        } finally {
            work.end()
            scheduleUnload()
        }
    }

    private suspend fun run(model: InstalledModel, request: LlmRequest, onProgress: (LlmProgress) -> Unit): LlmOutcome {
        val routes = routes(model)
        if (routes.isEmpty()) return LlmOutcome.Failure(LlmFailure.LOAD_FAILED)
        val needed = estimatedTokens(request)
        val fitting = routes.filter { needed <= it.contextSize }
        if (fitting.isEmpty()) return LlmOutcome.Failure(LlmFailure.TOO_LONG)
        var failure = LlmFailure.LOAD_FAILED
        for (route in fitting) {
            val engine = load(model, route) ?: continue
            try {
                return LlmOutcome.Success(write(engine, request, onProgress)).also { logSpeed(model, engine) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Backend: ${route.backend} failed while writing, trying the next one", e)
                unload()
                failure = LlmFailure.GENERATION_FAILED
            }
        }
        // A backend with a smaller context still works: a shorter prompt can run there.
        val smallerLeft = routes(model).any { needed > it.contextSize }
        return LlmOutcome.Failure(if (smallerLeft) LlmFailure.TOO_LONG else failure)
    }

    private suspend fun write(engine: AiEngine, request: LlmRequest, onProgress: (LlmProgress) -> Unit): String {
        val answer = StringBuilder()
        var pieces = 0
        report(LlmProgress(0), onProgress)
        engine.generate(request).collect { piece ->
            answer.append(piece)
            report(LlmProgress(++pieces), onProgress)
        }
        return answer.toString()
    }

    private fun report(progress: LlmProgress, onProgress: (LlmProgress) -> Unit) {
        work.progress(progress)
        onProgress(progress)
    }

    private data class Route(val backend: AiBackend, val path: String, val contextSize: Int)

    /** The backends to try for [model], fastest first, each with the file it runs. */
    private fun routes(model: InstalledModel): List<Route> = AiBackend.entries
        .filter { it !in unavailable && (it != AiBackend.TPU || device.tpuReachable) }
        .mapNotNull { backend ->
            model.paths.entries.firstOrNull { (file, _) -> backend in file.backends }
                ?.let { (file, path) -> Route(backend, path, file.contextSizeOn(backend)) }
        }

    /** The engine for [route], loaded if needed; null when the backend cannot run the model. */
    private fun load(model: InstalledModel, route: Route): AiEngine? {
        engine?.let { if (it.backend == route.backend && loadedPath == route.path) return it }
        unload()
        return try {
            loader.load(route.path, route.backend, route.contextSize).also {
                engine = it
                loadedPath = route.path
                _active.value = ActiveBackend(it.backend, model.model.name, device.socName)
                Log.i(TAG, "Backend: ${it.backend} | Model: ${model.model.name} | SoC: ${device.socName}")
            }
        } catch (e: AiBackendUnavailable) {
            unavailable += route.backend
            Log.w(TAG, "Backend: ${route.backend} unavailable | Model: ${model.model.name} | SoC: ${device.socName} | ${e.message}")
            null
        }
    }

    private fun logSpeed(model: InstalledModel, engine: AiEngine) {
        val speed = engine.lastSpeed ?: return
        Log.i(
            TAG,
            "Backend: ${engine.backend} | Model: ${model.model.name} | SoC: ${device.socName} | " +
                "prompt ${speed.promptTokens} tokens at %.1f/s | answer ${speed.generatedTokens} tokens at %.1f/s"
                    .format(speed.promptSpeed, speed.generationSpeed),
        )
    }

    /** The installed model, when the local AI is on, runnable here and has a file for some backend. */
    private suspend fun currentModel(): InstalledModel? =
        if (isSupported) installed()?.takeIf { it.paths.isNotEmpty() } else null

    private fun unload() {
        engine?.close()
        engine = null
        loadedPath = null
        _active.value = null
    }

    private fun scheduleUnload() {
        unloadJob?.cancel()
        unloadJob = scope.launch {
            delay(IDLE_UNLOAD_MS)
            mutex.withLock { withContext(Dispatchers.Default) { unload() } }
        }
    }

    internal companion object {
        const val TAG = "UmaiAi"
        const val IDLE_UNLOAD_MS = 60_000L

        /** A token is about three characters of French or English, or more: this errs on the long side. */
        private const val CHARS_PER_TOKEN = 3

        /** The turn markers the chat template wraps the prompt in. */
        private const val TEMPLATE_TOKENS = 64

        fun estimatedTokens(request: LlmRequest): Int =
            (request.system.length + request.user.length) / CHARS_PER_TOKEN + TEMPLATE_TOKENS + request.maxTokens

        private val BENCH_REQUEST = LlmRequest(
            system = "You are a helpful cooking assistant. Answer in French.",
            user = """
                Voici une recette. Résume-la en une phrase, puis donne trois conseils pour la réussir.

                Poulet au curry pour 4 personnes : 600 g de blancs de poulet, 2 oignons, 2 gousses d'ail,
                1 morceau de gingembre frais, 2 cuillères à soupe de curry en poudre, 400 ml de lait de coco,
                1 boîte de tomates concassées, 1 bouquet de coriandre, 2 cuillères à soupe d'huile, sel, poivre.
                Émincer les oignons, hacher l'ail et le gingembre. Faire revenir dans l'huile 5 minutes.
                Ajouter le poulet coupé en dés et le faire dorer. Saupoudrer de curry, mélanger 1 minute.
                Verser les tomates et le lait de coco, laisser mijoter 20 minutes à feu doux.
                Rectifier l'assaisonnement et parsemer de coriandre. Servir avec du riz basmati.
            """.trimIndent(),
            jsonSchema = """
                {"type": "object",
                 "properties": {"summary": {"type": "string"}, "tips": {"type": "array", "items": {"type": "string"}, "maxItems": 3}},
                 "required": ["summary", "tips"]}
            """.trimIndent(),
            maxTokens = 128,
            temperature = 0.7f,
        )
    }
}
