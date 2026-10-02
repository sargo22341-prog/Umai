package org.opensources.umai.planning.domain

import org.opensources.umai.llm.domain.LlmFailure

/** A food of the table offered while the user types: the [item] it makes, and the [title] of the plan entry. */
data class FoodSuggestion(val title: String, val item: EstimatedFood)

sealed interface DescriptionOutcome {
    data class Estimated(val estimate: FoodEstimate) : DescriptionOutcome

    /** The table has no such food, and there is no model to ask. */
    data object NotFound : DescriptionOutcome

    /** The model answered without a food it could weigh. */
    data object ModelFoundNothing : DescriptionOutcome

    data class ModelFailed(val reason: LlmFailure) : DescriptionOutcome

    /** The table shipped with the app could not be read. */
    data object TableUnreadable : DescriptionOutcome
}

/**
 * Turns what the user typed they ate into a food of the plan. Each food of
 * the phrase is looked up in the table of basic foods, with the quantity
 * said; when the table does not have them all, the on-device model, if any,
 * says what the meal is made of, and each part of it is looked up in turn.
 *
 * [table] is `null` when it cannot be read. [language] is the app's, for the
 * names shown; decimals are written with [decimalSeparator].
 */
class FoodDescriptions(
    private val table: suspend () -> FoodTable?,
    private val model: ModelFoodEstimator,
    private val language: String,
    private val decimalSeparator: Char,
) {

    suspend fun canAskModel(): Boolean = model.isReady()

    /** The foods of the table [text] may be, for a single food; `null` when the table cannot be read. */
    suspend fun suggestions(text: String): List<FoodSuggestion>? {
        val foods = table() ?: return null
        val phrase = FoodPhrases.split(text).singleOrNull()?.let(FoodPhrases::parse) ?: return emptyList()
        return foods.search(phrase.food).map { choice ->
            FoodSuggestion(title = title(phrase, choice), item = item(phrase, choice))
        }
    }

    suspend fun describe(text: String): DescriptionOutcome {
        val foods = table() ?: return DescriptionOutcome.TableUnreadable
        fromTable(foods, text)?.let { return DescriptionOutcome.Estimated(it) }
        if (!model.isReady()) return DescriptionOutcome.NotFound
        return when (val answer = model.ingredients(text.trim())) {
            is ModelEstimate.Failed -> DescriptionOutcome.ModelFailed(answer.reason)
            ModelEstimate.Empty -> DescriptionOutcome.ModelFoundNothing
            is ModelEstimate.Listed -> DescriptionOutcome.Estimated(
                FoodEstimate(answer.ingredients.map { fromModel(foods, it) }, byModel = true),
            )
        }
    }

    /** Every food of [text] found in the table; `null` when one is not, or several lack their amount. */
    private fun fromTable(foods: FoodTable, text: String): FoodEstimate? {
        val parts = FoodPhrases.split(text)
        if (parts.isEmpty()) return null
        val items = parts.map { part ->
            val phrase = FoodPhrases.parse(part) ?: return null
            val choice = foods.search(phrase.food, limit = 1).firstOrNull() ?: return null
            item(phrase, choice)
        }
        if (items.size > 1 && items.any { it.amount == null }) return null
        return FoodEstimate(items, byModel = false)
    }

    /** A food the model named: the table's values when it has the food, the model's calories otherwise. */
    private fun fromModel(foods: FoodTable, ingredient: ModelIngredient): EstimatedFood {
        val choice = foods.search(ingredient.name, limit = 1).firstOrNull()
        return if (choice != null) {
            EstimatedFood(choice.name(language), ingredient.grams, choice.unit, choice.food.per100, FoodSource.TABLE)
        } else {
            val per100 = ingredient.calories * NutritionFacts.LABEL_QUANTITY / ingredient.grams
            EstimatedFood(
                name = ingredient.name.replaceFirstChar { it.titlecase() },
                amount = ingredient.grams,
                unit = FoodUnit.GRAM,
                per100 = NutritionFacts(mapOf(Nutrient.ENERGY to per100)),
                source = FoodSource.MODEL,
            )
        }
    }

    private fun item(phrase: FoodPhrase, choice: FoodChoice) = EstimatedFood(
        name = choice.name(language),
        amount = phrase.amountOf(choice),
        unit = phrase.unitOf(choice),
        per100 = choice.food.per100,
        source = FoodSource.TABLE,
    )

    /** "2 × Pomme" for two of them, the name alone for one, or for a measure the note gives. */
    private fun title(phrase: FoodPhrase, choice: FoodChoice): String {
        val name = choice.name(language)
        val quantity = phrase.quantity
        if (quantity !is PhraseQuantity.Servings || quantity.count == 1.0) return name
        val count = quantity.count
        val text = if (count % 1.0 == 0.0) count.toLong().toString() else count.toString().replace('.', decimalSeparator)
        return "$text × $name"
    }
}
