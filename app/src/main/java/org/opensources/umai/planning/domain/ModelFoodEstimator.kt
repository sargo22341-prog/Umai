package org.opensources.umai.planning.domain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.opensources.umai.llm.domain.LanguageModel
import org.opensources.umai.llm.domain.LlmFailure
import org.opensources.umai.llm.domain.LlmOutcome
import org.opensources.umai.llm.domain.LlmRequest
import org.opensources.umai.llm.domain.ModelAnswer

/** A food the model says a meal holds: its [name], the [grams] eaten, and the [calories] it guesses for them. */
data class ModelIngredient(val name: String, val grams: Double, val calories: Double)

sealed interface ModelEstimate {
    data class Listed(val ingredients: List<ModelIngredient>) : ModelEstimate

    /** The model answered, but listed no food it could weigh. */
    data object Empty : ModelEstimate

    data class Failed(val reason: LlmFailure) : ModelEstimate
}

/**
 * Asks the on-device model what a meal the table does not know is made of,
 * "1 pizza saumon raviole": the foods in it and their weight, text only, a
 * short answer. Its calories are only a guess: the caller looks each food up
 * in the table, which is more reliable than a phone model's arithmetic, and
 * keeps the guess for a food the table does not have.
 */
class ModelFoodEstimator(private val model: LanguageModel) {

    suspend fun isReady(): Boolean = model.isReady()

    suspend fun ingredients(description: String): ModelEstimate =
        when (val outcome = model.generate(REQUEST.copy(user = description))) {
            is LlmOutcome.Failure -> ModelEstimate.Failed(outcome.reason)
            is LlmOutcome.Success -> {
                val ingredients = parse(outcome.text)
                if (ingredients.isEmpty()) ModelEstimate.Empty else ModelEstimate.Listed(ingredients)
            }
        }

    internal companion object {

        /** The runtime does not hold the answer to `maxItems`: the list is cut here. */
        private const val MAX_INGREDIENTS = 12

        /** `{"name":"saumon fumé","grams":60,"kcal":120},` is about twenty tokens. */
        private const val MAX_ANSWER_TOKENS = MAX_INGREDIENTS * 20 + 16

        val REQUEST = LlmRequest(
            system = """
                You estimate what a meal or a snack described in a few words is made of, to count its calories.
                - List every food in it, each named simply, the way a food composition table names it, in the language of the description: for example "pâte à pizza", "saumon fumé", "crème fraîche", "raviolis".
                - For each food, give the grams eaten and your estimate of the calories (kcal) of those grams.
                - A number in the description is a number of portions or pieces. Without one, count one usual adult portion.
            """.trimIndent(),
            user = "",
            jsonSchema = """
                {
                  "type": "object",
                  "properties": {
                    "foods": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "properties": {
                          "name": {"type": "string"},
                          "grams": {"type": "integer"},
                          "kcal": {"type": "integer"}
                        },
                        "required": ["name", "grams", "kcal"],
                        "additionalProperties": false
                      }
                    }
                  },
                  "required": ["foods"],
                  "additionalProperties": false
                }
            """.trimIndent(),
            maxTokens = MAX_ANSWER_TOKENS,
            temperature = 0f,
        )

        /** The foods of [answer]; one without a name, a weight or a calorie count is left out. */
        fun parse(answer: String): List<ModelIngredient> {
            val root = ModelAnswer.objectOrNull(answer) ?: return emptyList()
            return (root["foods"] as? JsonArray).orEmpty()
                .mapNotNull { element ->
                    val food = element as? JsonObject ?: return@mapNotNull null
                    val name = food.text("name")?.trim().orEmpty()
                    val grams = food.text("grams")?.toDoubleOrNull() ?: return@mapNotNull null
                    val calories = food.text("kcal")?.toDoubleOrNull() ?: return@mapNotNull null
                    if (name.isEmpty() || grams <= 0 || calories < 0) null else ModelIngredient(name, grams, calories)
                }
                .take(MAX_INGREDIENTS)
        }

        private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    }
}
