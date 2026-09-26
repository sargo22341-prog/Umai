package org.opensources.umai.youtube.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import org.opensources.umai.llm.domain.LanguageModel
import org.opensources.umai.llm.domain.LlmOutcome
import org.opensources.umai.llm.domain.LlmProgress
import org.opensources.umai.llm.domain.LlmRequest

/**
 * Places the steps of a recipe in a video where nothing is said, from what
 * pictures of it show ([TranscriptSource.SEEN]).
 *
 * The steps are written without the pictures: given them, a phone-sized
 * model writes one step per picture and repeats their mistakes. Here it only
 * says which picture each step starts at, which it does well. When its
 * answer does not give one start per step, the words the steps share with
 * the descriptions place them.
 */
class PicturePlacement(private val model: LanguageModel) {

    suspend fun place(steps: List<BlueprintStep>, video: YouTubeVideo, onProgress: (LlmProgress) -> Unit): List<BlueprintStep> {
        if (steps.isEmpty() || video.transcript.isEmpty()) return steps
        val request = LlmRequest(
            system = SYSTEM,
            user = userPrompt(steps, video),
            jsonSchema = schema(steps.size),
            maxTokens = MAX_ANSWER_TOKENS,
        )
        val starts = (model.generate(request, onProgress) as? LlmOutcome.Success)?.let { starts(it.text, steps.size) }
            ?: Transcript.alignSteps(steps.map { "${it.title} ${it.text}" }, video.transcript)
            ?: return steps
        return steps.mapIndexed { index, step -> step.copy(start = starts[index]) }.withOrderedStarts(video.durationSeconds)
    }

    internal fun userPrompt(steps: List<BlueprintStep>, video: YouTubeVideo): String = buildString {
        appendLine("Steps:")
        steps.forEachIndexed { index, step -> appendLine("${key(index)}. ${step.title}: ${step.text}") }
        appendLine()
        appendLine("What the video shows:")
        video.transcript.forEach { appendLine("[${it.start.toInt()}s] ${it.text}") }
    }

    internal companion object {
        private const val MAX_ANSWER_TOKENS = 256

        private val json = Json { ignoreUnknownKeys = true }

        private val SYSTEM = """
            You place the steps of a recipe in its cooking video. You receive the steps in order, and what pictures of the video show at given seconds. For each step, give the second of the first picture that shows it being done. The steps come in order, so their starts do too.
        """.trimIndent()

        private fun key(index: Int) = "step_${index + 1}"

        /**
         * One required number per step, named after it: asked for a list, the
         * model gave one start per picture rather than per step.
         */
        fun schema(steps: Int): String {
            val keys = (0 until steps).map(::key)
            val properties = keys.joinToString(", ") { "\"$it\": {\"type\": \"integer\", \"minimum\": 0}" }
            val required = keys.joinToString(", ") { "\"$it\"" }
            return """{"type": "object", "properties": {$properties}, "required": [$required], "additionalProperties": false}"""
        }

        /** The start of each of the [steps], `null` when the answer lacks one. */
        fun starts(answer: String, steps: Int): List<Double>? {
            val root = runCatching { json.parseToJsonElement(answer) }.getOrNull() as? JsonObject ?: return null
            return (0 until steps).map { index ->
                (root[key(index)] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull ?: return null
            }
        }
    }
}
