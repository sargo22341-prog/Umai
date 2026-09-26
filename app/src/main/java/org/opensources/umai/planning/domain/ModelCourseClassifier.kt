package org.opensources.umai.planning.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.opensources.umai.llm.domain.LanguageModel
import org.opensources.umai.llm.domain.LlmOutcome
import org.opensources.umai.llm.domain.LlmRequest

/** A recipe no category, tag, name or past meal places, described for the model. */
data class UnplacedRecipe(val id: String, val name: String, val ingredients: List<String>)

/**
 * Asks the language model the course of the recipes the rules could not
 * place, a batch at a time. Each recipe is given a short code, and the answer
 * is an object with one required property per code: the runtime holds the
 * model to the schema, so it answers for every recipe, and writes little more
 * than the courses. Measured on the phone (docs/local-ai.md), a list of
 * `{"id", "course"}` items took two thirds longer to write and skipped recipes.
 */
class ModelCourseClassifier(private val model: LanguageModel) {

    /** Loads the model while the recipes to classify are being read. */
    suspend fun prepare() {
        if (model.isReady()) model.prepare()
    }

    suspend fun classify(recipes: List<UnplacedRecipe>): Map<String, DishCourse> {
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
                jsonSchema = schema(codes.keys),
                maxTokens = batch.size * TOKENS_PER_ITEM + TOKENS_OVERHEAD,
                temperature = 0f,
            )
            val outcome = model.generate(request) as? LlmOutcome.Success ?: return found
            parse(outcome.text).forEach { (code, course) -> codes[code]?.let { found[it.id] = course } }
        }
        return found
    }

    internal fun parse(text: String): Map<String, DishCourse> {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return emptyMap()
        return root.mapNotNull { (code, value) ->
            val course = when ((value as? JsonPrimitive)?.contentOrNull) {
                "main" -> DishCourse.MAIN
                "dessert" -> DishCourse.DESSERT
                "drink" -> DishCourse.DRINK
                "other" -> DishCourse.OTHER
                else -> return@mapNotNull null
            }
            code to course
        }.toMap()
    }

    /**
     * One required property per code, each one of the courses. The runtime
     * only holds a value to its `enum` when its type is given: without it,
     * on a batch of a few recipes, the model copied each recipe line as the
     * value, or answered as plain text (measured on the phone).
     */
    internal fun schema(codes: Collection<String>): String {
        val properties = codes.joinToString(",") { "\"$it\":{\"type\":\"string\",\"enum\":$COURSES}" }
        val required = codes.joinToString(",") { "\"$it\"" }
        return """{"type":"object","properties":{$properties},"required":[$required],"additionalProperties":false}"""
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        const val BATCH = 25
        const val MAX_INGREDIENTS = 8

        /** `"r12":"dessert",` is 8 tokens at most: measured 201 for 25 recipes. */
        const val TOKENS_PER_ITEM = 10
        const val TOKENS_OVERHEAD = 16
        const val COURSES = """["main","dessert","drink","other"]"""

        val SYSTEM = """
            You sort recipes by course. Each line gives a code, a recipe name and some of its ingredients.
            Answer, for every code:
            - "main" for a dish eaten on its own as lunch or dinner: meat, fish, pasta, rice, gratins, curries, stews, pizzas, quiches, main salads, soups served as a meal;
            - "dessert" for sweet dishes, cakes, pastries, biscuits, ice creams, and sweet rice, semolina or fruit dishes;
            - "drink" for drinks and cocktails;
            - "other" for starters, side dishes, sauces, dips, breads, doughs, breakfasts and snacks.
        """.trimIndent()
    }
}
