package org.opensources.umai.llm.domain

/**
 * One question to the language model. The answer is JSON matching
 * [jsonSchema]: the runtime only lets the model write tokens the schema
 * allows, so the answer always parses, whatever the model.
 */
data class LlmRequest(
    val system: String,
    val user: String,
    val jsonSchema: String,
    val maxTokens: Int,
    /** Low by default: the tasks extract and classify, they do not invent. */
    val temperature: Float = 0.2f,
    val seed: Int = 42,
)

/** How far a generation is: the prompt is read first, then the answer written. */
data class LlmProgress(val promptRead: Int, val promptTotal: Int, val generated: Int) {
    val readingPrompt: Boolean get() = generated == 0
    val promptFraction: Float get() = if (promptTotal == 0) 0f else promptRead.toFloat() / promptTotal
}

sealed interface LlmOutcome {
    data class Success(val text: String) : LlmOutcome

    data class Failure(val reason: LlmFailure) : LlmOutcome
}

enum class LlmFailure {
    /** No model is installed, or the local AI is turned off. */
    NOT_READY,

    /** The model file could not be loaded: not enough memory, or not a model this runtime reads. */
    LOAD_FAILED,

    /** The prompt does not fit in the context of the model. */
    TOO_LONG,

    GENERATION_FAILED,
}

/**
 * The on-device language model, as the features that use it see it. Every
 * feature also works without it, with a plain algorithm: the model only adds
 * understanding of free text where it is available.
 */
interface LanguageModel {

    /** Whether a model is installed, turned on and runnable on this device. */
    suspend fun isReady(): Boolean

    /**
     * The number of tokens [text] takes for the installed model, to fit a
     * prompt in its context; `null` when no model is ready.
     */
    suspend fun tokenCount(text: String): Int?

    /** The size of the context the model is run with, prompt and answer together. */
    val contextSize: Int

    /**
     * Runs [request]. Cancelling the calling coroutine stops the model at the
     * next token.
     */
    suspend fun generate(request: LlmRequest, onProgress: (LlmProgress) -> Unit = {}): LlmOutcome
}
