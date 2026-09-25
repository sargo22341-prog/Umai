package org.opensources.umai.llm.domain

import kotlinx.coroutines.flow.Flow

/** The processor a model runs on, from the fastest to the one every phone has. */
enum class AiBackend { TPU, GPU, CPU }

/** Speeds the runtime measured on the last answer. */
data class AiSpeed(
    val promptTokens: Int,
    /** Prompt tokens read per second. */
    val promptSpeed: Double,
    val generatedTokens: Int,
    /** Tokens written per second. */
    val generationSpeed: Double,
)

/**
 * A model file loaded on one backend. It is the only part of the app that
 * knows the runtime: the rest sees a request go in and text come out.
 */
interface AiEngine : AutoCloseable {

    /** The backend the model really runs on: an engine is only built once this is proven. */
    val backend: AiBackend

    /**
     * Streams the answer to [request] piece by piece as the model writes it.
     * Cancelling the collection stops the model.
     */
    fun generate(request: LlmRequest): Flow<String>

    /** The speeds of the last answer written to the end, when the runtime measured them. */
    val lastSpeed: AiSpeed?
}

/** Loads a model file on a backend. */
fun interface AiEngineLoader {

    /**
     * Loads the model at [path] on [backend], with room for [contextSize]
     * tokens. Throws [AiBackendUnavailable] when the model does not run there,
     * including when the runtime would quietly run it somewhere else.
     */
    fun load(path: String, backend: AiBackend, contextSize: Int): AiEngine
}

class AiBackendUnavailable(message: String, cause: Throwable? = null) : Exception(message, cause)

/** What this phone offers the model. */
data class DeviceProfile(
    /** The name of the system-on-chip, such as "Tensor G5", for the logs and the screen. */
    val socName: String,
    /** The chip a TPU build of a model targets, when this phone has one of them. */
    val tensorChip: TensorChip?,
    /**
     * Whether the app can reach the TPU: the dispatch library is installed with
     * the app and the phone's TPU driver is there. Only loading a model proves
     * it is used.
     */
    val tpuReachable: Boolean,
)

/** The Google Tensor chips whose TPU LiteRT-LM runs language models on. */
enum class TensorChip(val socModel: String) {
    G5("Tensor G5"),
    G6("Tensor G6"),
    ;

    companion object {
        /** From `Build.SOC_MANUFACTURER` and `Build.SOC_MODEL`. */
        fun of(manufacturer: String, model: String): TensorChip? =
            if (manufacturer.equals("Google", ignoreCase = true)) entries.firstOrNull { it.socModel.equals(model.trim(), ignoreCase = true) } else null
    }
}
