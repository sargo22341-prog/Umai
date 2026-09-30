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
import org.opensources.umai.llm.domain.AiSense
import org.opensources.umai.llm.domain.DeviceProfile
import org.opensources.umai.llm.domain.LanguageModel
import org.opensources.umai.llm.domain.LlmBenchmark
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

/**
 * The installed model, run by an [AiEngine] on the fastest backend that
 * works ([PREFERENCE]): the TPU of a Tensor chip, then the CPU, then the GPU.
 *
 * A backend is used only once loading the model there succeeded and proved it
 * runs there; one that fails is skipped until the app restarts. A prompt too
 * long for the context of the TPU build and of the CPU goes to the GPU.
 *
 * A request with a picture goes to the file that holds the vision part,
 * which a TPU build has not: on the CPU first, then the GPU. A backend where
 * that part does not load still answers text.
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
    /**
     * The routes — a backend and a model file — that failed to load: not tried
     * again for that file, but tried for another model once it is installed.
     */
    private val unavailable = mutableSetOf<Route>()

    /** The parts of a model file that did not load on a backend, which may still answer text. */
    private val senseless = mutableSetOf<Pair<Route, AiSense>>()

    private val _active = MutableStateFlow<AiBackend?>(null)

    /** The backend the model is loaded on, as proven when it loaded; null while it is not loaded. */
    val active: StateFlow<AiBackend?> = _active.asStateFlow()

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

    /** Loads the model on the backend a short text request goes to first. */
    override suspend fun prepare() {
        mutex.withLock {
            val model = currentModel() ?: return
            unloadJob?.cancel()
            try {
                withContext(Dispatchers.Default) { routes(model).firstNotNullOfOrNull { load(model, it, sense = null) } }
            } finally {
                scheduleUnload()
            }
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
                val engine = routes(model).firstNotNullOfOrNull { load(model, it, sense = null) } ?: return@withContext null
                val loadMillis = SystemClock.elapsedRealtime() - start
                try {
                    engine.generate(BENCH_REQUEST).collect {}
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // The runtime reports its failures with exceptions of its own: the screen says the test failed.
                    Log.w(TAG, "Backend: ${engine.backend} failed the benchmark", e)
                    return@withContext null
                }
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
        val sense = request.media?.sense
        val media = sense != null
        val routes = if (sense != null) senseRoutes(model, sense) else routes(model)
        if (routes.isEmpty()) return LlmOutcome.Failure(if (media) LlmFailure.MEDIA_UNSUPPORTED else LlmFailure.LOAD_FAILED)
        val needed = estimatedTokens(request)
        val fitting = routes.filter { needed <= it.contextSize }
        routes.filter { it !in fitting }.forEach {
            if (Log.isLoggable(TAG, Log.INFO)) {
                Log.i(TAG, "Backend: ${it.backend} skipped: about $needed tokens needed, context ${it.contextSize}")
            }
        }
        if (fitting.isEmpty()) return LlmOutcome.Failure(LlmFailure.TOO_LONG)
        var failure = if (media) LlmFailure.MEDIA_UNSUPPORTED else LlmFailure.LOAD_FAILED
        for (route in fitting) {
            val engine = load(model, route, sense) ?: continue
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
    private fun routes(model: InstalledModel): List<Route> = PREFERENCE
        .filter { it != AiBackend.TPU || device.tpuReachable }
        .mapNotNull { backend ->
            model.paths.entries.firstOrNull { (file, _) -> backend in file.backends }
                ?.let { (file, path) -> Route(backend, path, file.contextSizeOn(backend)) }
        }
        .filter { it !in unavailable }

    /**
     * The backends that may [sense], with the file that holds that part: the
     * CPU first, then the GPU. A TPU build has no such part.
     */
    private fun senseRoutes(model: InstalledModel, sense: AiSense): List<Route> =
        routes(model).filter { it.backend != AiBackend.TPU && (it to sense) !in senseless }

    /**
     * The engine for [route], loaded if needed, with the part that gives it
     * [sense]; null when the backend cannot run the model so.
     */
    private fun load(model: InstalledModel, route: Route, sense: AiSense?): AiEngine? {
        engine?.let { if (it.backend == route.backend && loadedPath == route.path && (sense == null || it.sense == sense)) return it }
        unload()
        return try {
            loader.load(route.path, route.backend, route.contextSize, sense).also {
                engine = it
                loadedPath = route.path
                _active.value = it.backend
                if (Log.isLoggable(TAG, Log.INFO)) {
                    Log.i(TAG, "Backend: ${it.backend} | Model: ${model.model.name} | SoC: ${device.socName}")
                }
            }
        } catch (e: AiBackendUnavailable) {
            // Without that part, the backend may still answer text.
            if (sense != null) senseless += route to sense else unavailable += route
            val what = if (sense != null) "without $sense" else "unavailable"
            Log.w(TAG, "Backend: ${route.backend} $what | Model: ${model.model.name} | SoC: ${device.socName} | ${e.message}")
            null
        }
    }

    private fun logSpeed(model: InstalledModel, engine: AiEngine) {
        val speed = engine.lastSpeed ?: return
        if (Log.isLoggable(TAG, Log.INFO)) {
            Log.i(
                TAG,
                "Backend: ${engine.backend} | Model: ${model.model.name} | SoC: ${device.socName} | " +
                    "prompt ${speed.promptTokens} tokens at %.1f/s | answer ${speed.generatedTokens} tokens at %.1f/s"
                        .format(speed.promptSpeed, speed.generationSpeed),
            )
        }
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

        /**
         * The TPU first, then the CPU for what fits its context, the GPU for the
         * rest. Measured on the Pixel 6 Pro and the Pixel 10 Pro XL (see
         * docs/local-ai.md): the CPU writes faster than the GPU, loads the model
         * in two seconds where the GPU takes a minute, and does not push the
         * phone to close other apps to make room. The GPU reads long prompts
         * several times faster: it takes those the CPU context cannot hold.
         */
        val PREFERENCE = listOf(AiBackend.TPU, AiBackend.CPU, AiBackend.GPU)

        /**
         * Gemma reads about 3.9 characters of a French import prompt per token
         * (LiteRtLmBackendTest): this errs on the long side, but not so far that
         * a prompt the TPU takes goes to the GPU, which writes three times slower.
         * One that does not fit after all fails there and is written on the GPU.
         */
        private const val CHARS_PER_TOKEN = 3.5

        /** The turn markers the chat template wraps the prompt in. */
        private const val TEMPLATE_TOKENS = 64

        /** What a picture takes in the context, with room to spare. */
        private const val MEDIA_TOKENS = 1_000

        /**
         * The chat template writes the type of each property of the schema at
         * length: measured on the planning prompt, 25 `"type": "string"` took
         * 500 tokens, where their characters count for about 115.
         */
        private const val TYPE_TOKENS = 16

        /** The schema is read too: it is the definition of the tool the model answers with. */
        fun estimatedTokens(request: LlmRequest): Int =
            ((request.system.length + request.user.length + request.jsonSchema.length) / CHARS_PER_TOKEN).toInt() +
                TYPE_TOKENS * TYPE_KEY.findAll(request.jsonSchema).count() +
                TEMPLATE_TOKENS + request.maxTokens +
                (if (request.media != null) MEDIA_TOKENS else 0)

        private val TYPE_KEY = Regex(""""type"\s*:""")

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
