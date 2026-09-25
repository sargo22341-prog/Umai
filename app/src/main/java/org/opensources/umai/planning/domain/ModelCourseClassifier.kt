package org.opensources.umai.planning.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.opensources.umai.llm.domain.LanguageModel
import org.opensources.umai.llm.domain.LlmOutcome
import org.opensources.umai.llm.domain.LlmProgress
import org.opensources.umai.llm.domain.LlmRequest

/** A recipe no category, tag, name or past meal places, described for the model. */
data class UnplacedRecipe(val id: String, val name: String, val ingredients: List<String>)

/**
 * Asks the language model the course of the recipes the rules could not
 * place, a batch at a time. Each recipe is given a short code the model
 * echoes back with its answer, which small models get right far more often
 * than positions in a list. A recipe it does not answer for stays unplaced.
 */
class ModelCourseClassifier(private val model: LanguageModel) {

    suspend fun classify(recipes: List<UnplacedRecipe>, onProgress: (LlmProgress) -> Unit = {}): Map<String, DishCourse> {
        if (recipes.isEmpty() || !model.isReady()) return emptyMap()
        val found = mutableMapOf<String, DishCourse>()
        recipes.chunked(BATCH).forEach { batch ->
            val codes = batch.mapIndexed { index, recipe -> "r${index + 1}" to recipe }.toMap()
            val request = LlmRequest(
                system = SYSTEM,
                user = codes.entries.joinToString("\n") { (code, recipe) ->
                    val ingredients = recipe.ingredients.take(MAX_INGREDIENTS).joinToString(", ")
                    "$code: ${recipe.name}" + if (ingredients.isEmpty()) "" else " — $ingredients"
                },
                jsonSchema = SCHEMA,
                maxTokens = batch.size * TOKENS_PER_ITEM + TOKENS_OVERHEAD,
                temperature = 0f,
            )
            val outcome = model.generate(request, onProgress) as? LlmOutcome.Success ?: return found
            parse(outcome.text).forEach { (code, course) -> codes[code]?.let { found[it.id] = course } }
        }
        return found
    }

    internal fun parse(text: String): Map<String, DishCourse> {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return emptyMap()
        return (root["items"] as? JsonArray).orEmpty().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val code = (item["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val course = when ((item["course"] as? JsonPrimitive)?.contentOrNull) {
                "main" -> DishCourse.MAIN
                "dessert" -> DishCourse.DESSERT
                "drink" -> DishCourse.DRINK
                "other" -> DishCourse.OTHER
                else -> return@mapNotNull null
            }
            code to course
        }.toMap()
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        const val BATCH = 25
        const val MAX_INGREDIENTS = 8
        const val TOKENS_PER_ITEM = 16
        const val TOKENS_OVERHEAD = 32

        val SYSTEM = """
            You sort recipes by course. Each line gives a code, a recipe name and some of its ingredients.
            Answer, for every code:
            - "main" for a dish eaten on its own as lunch or dinner: meat, fish, pasta, rice, gratins, curries, stews, pizzas, quiches, main salads, soups served as a meal;
            - "dessert" for sweet dishes, cakes, pastries, biscuits, ice creams, and sweet rice, semolina or fruit dishes;
            - "drink" for drinks and cocktails;
            - "other" for starters, side dishes, sauces, dips, breads, doughs, breakfasts and snacks.
        """.trimIndent()

        val SCHEMA = """
            {
              "type": "object",
              "properties": {
                "items": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "id": {"type": "string", "maxLength": 8},
                      "course": {"type": "string", "enum": ["main", "dessert", "drink", "other"]}
                    },
                    "required": ["id", "course"],
                    "additionalProperties": false
                  }
                }
              },
              "required": ["items"],
              "additionalProperties": false
            }
        """.trimIndent()
    }
}
