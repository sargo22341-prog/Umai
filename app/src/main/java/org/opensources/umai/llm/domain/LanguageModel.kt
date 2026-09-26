package org.opensources.umai.llm.domain

/**
 * One question to the language model. The answer is JSON matching
 * [jsonSchema]: the runtime only lets the model write tokens the schema
 * allows, so the answer always parses, whatever the model. [media] is a
 * sound or a picture the question is about.
 */
data class LlmRequest(
    val system: String,
    val user: String,
    val jsonSchema: String,
    val maxTokens: Int,
    /** Low by default: the tasks extract and classify, they do not invent. */
    val temperature: Float = 0.2f,
    val seed: Int = 42,
    val media: LlmMedia? = null,
)

/** What the model is given to listen to or to look at, besides the text. */
sealed interface LlmMedia {
    /** The part of the model that reads it. */
    val sense: AiSense

    /** A WAV file of 16 kHz mono sound, 30 seconds at most: the longest the model hears at once. */
    class Sound(val wav: ByteArray) : LlmMedia {
        override val sense: AiSense get() = AiSense.HEARING
    }

    /** A JPEG picture. */
    class Picture(val jpeg: ByteArray) : LlmMedia {
        override val sense: AiSense get() = AiSense.SIGHT
    }
}

/**
 * How far a generation is: the prompt is read first, then the answer written.
 * The runtime does not tell how far it is in the prompt, only when the answer starts.
 */
data class LlmProgress(val generated: Int) {
    val readingPrompt: Boolean get() = generated == 0
}

sealed interface LlmOutcome {
    data class Success(val text: String) : LlmOutcome

    data class Failure(val reason: LlmFailure) : LlmOutcome
}

enum class LlmFailure {
    /** No model is installed, or the local AI is turned off. */
    NOT_READY,

    /** The model could not be loaded on any backend: not enough memory, or not a model this runtime reads. */
    LOAD_FAILED,

    /** The prompt does not fit in the context of the model. */
    TOO_LONG,

    GENERATION_FAILED,

    /** The model has no part that hears sound or sees pictures, or none of them loads on this phone. */
    MEDIA_UNSUPPORTED,
}

/**
 * The on-device language model, as the features that use it see it. Every
 * feature also works without it, with a plain algorithm: the model only adds
 * understanding of free text where it is available.
 */
interface LanguageModel {

    /** Whether a model is installed, turned on and runnable on this device. */
    suspend fun isReady(): Boolean

    /** The largest context the model can be run with, prompt and answer together, in tokens. */
    val contextSize: Int

    /**
     * Runs [request]. Cancelling the calling coroutine stops the model at the
     * next token.
     */
    suspend fun generate(request: LlmRequest, onProgress: (LlmProgress) -> Unit = {}): LlmOutcome
}
