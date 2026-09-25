package org.opensources.umai.youtube.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import org.opensources.umai.llm.domain.LanguageModel
import org.opensources.umai.llm.domain.LlmFailure
import org.opensources.umai.llm.domain.LlmOutcome
import org.opensources.umai.llm.domain.LlmProgress
import org.opensources.umai.llm.domain.LlmRequest

/**
 * Rebuilds a recipe from a video with the language model: it reads the title,
 * the description, the chapters and the timed transcript, and writes the
 * steps as instructions, where each step starts in the video, and the
 * ingredients when neither a recipe page nor the description lists them.
 *
 * The answer is constrained to a JSON schema, then checked: starts outside
 * the video or out of order are dropped, and the ingredients are merged with
 * the lists the author published by [RecipeIngredients], which keeps only the
 * foods and quantities the video really gives. What the model leaves empty is
 * taken from [RuleRecipeBuilder]. Which ingredients a step uses is left to
 * [org.opensources.umai.recipe.domain.IngredientLinker]: on a phone-sized
 * model, positions in a list come out wrong far more often than names match.
 */
class ModelRecipeBuilder(private val model: LanguageModel) {

    sealed interface Outcome {
        data class Built(val blueprint: RecipeBlueprint) : Outcome

        data class Failed(val reason: LlmFailure) : Outcome
    }

    /**
     * [language] is the one the recipe is written in: "fr" or "en". [page] is
     * the recipe the description links to, when one was found.
     */
    suspend fun build(
        video: YouTubeVideo,
        language: String,
        page: RecipePage? = null,
        onProgress: (LlmProgress) -> Unit,
    ): Outcome {
        var transcriptBudget = transcriptChars(model.contextSize)
        repeat(ATTEMPTS) {
            val request = LlmRequest(
                system = systemPrompt(language),
                user = userPrompt(video, transcriptBudget, page),
                jsonSchema = SCHEMA,
                maxTokens = MAX_ANSWER_TOKENS,
            )
            when (val outcome = model.generate(request, onProgress)) {
                is LlmOutcome.Success -> {
                    val blueprint = parse(outcome.text, video, page)
                        ?: return Outcome.Failed(LlmFailure.GENERATION_FAILED)
                    return Outcome.Built(blueprint)
                }
                is LlmOutcome.Failure -> {
                    // A prompt too long for the context is tried again with less transcript.
                    if (outcome.reason != LlmFailure.TOO_LONG) return Outcome.Failed(outcome.reason)
                    transcriptBudget /= 2
                }
            }
        }
        return Outcome.Failed(LlmFailure.TOO_LONG)
    }

    internal fun userPrompt(video: YouTubeVideo, transcriptChars: Int, page: RecipePage? = null): String = buildString {
        appendLine("Title: ${video.title}")
        appendLine("Channel: ${video.author}")
        appendLine("Duration: ${video.durationSeconds}s")
        appendLine()
        val known = RecipeIngredients.known(video, page)
        if (known.isNotEmpty()) {
            appendLine(if (page != null) "Ingredients (from the recipe page, complete):" else "Ingredients (listed by the author):")
            known.forEach { appendLine("- $it") }
            appendLine("These ingredients are already known: answer with an empty \"ingredients\" array, and name them in the steps as they are named here.")
            appendLine()
        }
        appendLine("Description:")
        appendLine(video.description.take(MAX_DESCRIPTION_CHARS).ifBlank { "(none)" })
        appendLine()
        appendLine("Chapters:")
        if (video.chapters.isEmpty()) {
            appendLine("(none)")
        } else {
            video.chapters.forEach { appendLine("[${it.start.toInt()}s] ${it.title}") }
        }
        appendLine()
        appendLine(if (video.transcriptAutomatic) "Transcript (automatic captions, may contain errors):" else "Transcript:")
        appendLine(Transcript.timedBlocks(video.transcript, maxChars = transcriptChars).ifBlank { "(none)" })
    }

    internal fun parse(text: String, video: YouTubeVideo, page: RecipePage? = null): RecipeBlueprint? {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
        val fallback by lazy { RuleRecipeBuilder.build(video, page) }

        val ingredients = RecipeIngredients.of(
            video = video,
            page = page,
            modelLines = root.array("ingredients").mapNotNull { it.string() },
        )
        val steps = root.array("steps").mapNotNull { element ->
            val step = element as? JsonObject ?: return@mapNotNull null
            val body = step.text("text") ?: return@mapNotNull null
            BlueprintStep(
                title = step.text("title").orEmpty(),
                text = body,
                start = step.int("start")?.toDouble(),
            )
        }.withOrderedStarts(video.durationSeconds)
        if (steps.isEmpty() && ingredients.isEmpty()) return null

        return RecipeBlueprint(
            name = root.text("name") ?: fallback.name,
            summary = root.text("summary") ?: fallback.summary,
            // Said by the author, else counted by the model, which may guess.
            servings = RuleRecipeBuilder.servings(video, page) ?: root.int("servings")?.takeIf { it > 0 },
            prepMinutes = root.int("prepMinutes")?.takeIf { it > 0 },
            cookMinutes = root.int("cookMinutes")?.takeIf { it > 0 },
            ingredients = ingredients,
            steps = placed(steps, video),
            ingredientsStart = video.chapters.firstOrNull { RuleRecipeBuilder.isIngredientsChapter(it.title) }?.start,
            origin = BlueprintOrigin.LANGUAGE_MODEL,
            video = video,
        )
    }

    /**
     * Small models sometimes give every step the same start, or none: the
     * steps are then placed from the words they share with the transcript.
     */
    private fun placed(steps: List<BlueprintStep>, video: YouTubeVideo): List<BlueprintStep> {
        val placedCount = steps.count { it.start != null }
        if (steps.size < 2 || placedCount * 2 > steps.size) return steps
        val starts = Transcript.alignSteps(steps.map { "${it.title} ${it.text}" }, video.transcript) ?: return steps
        return steps.mapIndexed { index, step -> step.copy(start = starts[index]) }.withOrderedStarts(video.durationSeconds)
    }

    private fun JsonObject.array(key: String): List<kotlinx.serialization.json.JsonElement> =
        (get(key) as? JsonArray).orEmpty()

    private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.string()?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonObject.int(key: String): Int? = (get(key) as? JsonPrimitive)?.intOrNull

    private fun kotlinx.serialization.json.JsonElement.string(): String? =
        (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        private const val ATTEMPTS = 3
        private const val MAX_ANSWER_TOKENS = 2_048
        private const val MAX_DESCRIPTION_CHARS = 4_000

        /** Room left for the instructions, the description and the chapters. */
        private const val PROMPT_OVERHEAD_TOKENS = 2_000

        /** A token is about three characters of French or English speech. */
        private const val CHARS_PER_TOKEN = 3

        fun transcriptChars(contextSize: Int): Int =
            ((contextSize - MAX_ANSWER_TOKENS - PROMPT_OVERHEAD_TOKENS) * CHARS_PER_TOKEN).coerceAtLeast(MIN_TRANSCRIPT_CHARS)

        private const val MIN_TRANSCRIPT_CHARS = 2_000

        fun systemPrompt(language: String): String {
            val writeIn = if (language == "fr") "French" else "English"
            return """
                You turn cooking videos into written recipes. You receive the title, the description the author wrote, the chapters and the transcript of one video, with times in seconds, and the ingredients when the author published them.
                Rebuild the complete recipe as a cookbook would print it:
                - name: the dish, short, without the channel name or hashtags.
                - summary: one or two sentences about the dish.
                - servings, prepMinutes, cookMinutes: as said or written; 0 when unknown.
                - ingredients: when no ingredient list is given, every ingredient the video uses, each one once, such as "200 g de farine". Write a quantity only when it is said in the transcript or written in the description, exactly as given; never estimate one: an ingredient whose quantity is not given is written without it, such as "sel". No headings.
                - steps: the actions in the order they are done. Each step is one to three short sentences in the imperative, with the useful details said in the video: temperatures, times, sizes, textures. Leave out greetings, sponsors, tasting and goodbyes.
                - title: two to five words naming the step.
                - start: the second of the video where the step begins, taken from the chapters and the transcript times.
                - In the steps, name the ingredients used as they are named in the list.
                Never invent an ingredient, a quantity or a step the video does not give. Write every text in $writeIn, translating when the video is in another language.
            """.trimIndent()
        }

        /** The shape of the answer, which the runtime enforces token by token. */
        val SCHEMA: String = """
            {
              "type": "object",
              "properties": {
                "name": {"type": "string", "maxLength": 120},
                "summary": {"type": "string", "maxLength": 400},
                "servings": {"type": "integer", "minimum": 0, "maximum": 50},
                "prepMinutes": {"type": "integer", "minimum": 0, "maximum": 1440},
                "cookMinutes": {"type": "integer", "minimum": 0, "maximum": 1440},
                "ingredients": {
                  "type": "array", "maxItems": 40,
                  "items": {"type": "string", "maxLength": 120}
                },
                "steps": {
                  "type": "array", "minItems": 1, "maxItems": 20,
                  "items": {
                    "type": "object",
                    "properties": {
                      "title": {"type": "string", "maxLength": 60},
                      "text": {"type": "string", "maxLength": 700},
                      "start": {"type": "integer", "minimum": 0}
                    },
                    "required": ["title", "text", "start"],
                    "additionalProperties": false
                  }
                }
              },
              "required": ["name", "summary", "servings", "prepMinutes", "cookMinutes", "ingredients", "steps"],
              "additionalProperties": false
            }
        """.trimIndent()
    }
}
