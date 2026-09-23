package org.opensources.umai.core.format

import org.opensources.umai.core.model.RecipeIngredient

/**
 * Renders an ingredient line for a number of servings.
 *
 * Mealie pre-renders every line for the servings the recipe was written for, so
 * that text is used verbatim as long as the recipe is not scaled: it keeps the
 * exact wording the instance produced, including whatever the user typed by
 * hand. Once the reader asks for a different number of servings the line has to
 * be rebuilt, following the same rules as Mealie's own renderer — abbreviated
 * or full unit, plural forms above one, quantity written as a fraction when the
 * unit allows it.
 */
object IngredientText {

    fun format(ingredient: RecipeIngredient, scale: Double = 1.0): String {
        if (!shouldRebuild(ingredient, scale)) {
            return ingredient.display.ifBlank { rebuild(ingredient, 1.0) }
        }
        return rebuild(ingredient, scale)
    }

    /** The scaled quantity alone, for compact displays. */
    fun quantity(ingredient: RecipeIngredient, scale: Double): String =
        QuantityText.format(ingredient.quantity?.times(scale))

    private fun shouldRebuild(ingredient: RecipeIngredient, scale: Double): Boolean =
        ingredient.isScalable && kotlin.math.abs(scale - 1.0) > 0.0001

    private fun rebuild(ingredient: RecipeIngredient, scale: Double): String {
        val quantity = ingredient.quantity?.times(scale)
        val quantityText = QuantityText.format(quantity)
        val plural = (quantity ?: 0.0) > 1.0

        val unit = ingredient.unit?.let { unit ->
            if (unit.useAbbreviation && unit.abbreviation.isNotBlank()) {
                if (plural) unit.pluralAbbreviation?.takeIf { it.isNotBlank() } ?: unit.abbreviation
                else unit.abbreviation
            } else {
                if (plural) unit.pluralName?.takeIf { it.isNotBlank() } ?: unit.name else unit.name
            }
        }

        val food = ingredient.food?.let { food ->
            if (plural) food.pluralName?.takeIf { it.isNotBlank() } ?: food.name else food.name
        }

        val parts = listOfNotNull(
            quantityText.takeIf { it.isNotBlank() },
            unit?.takeIf { it.isNotBlank() },
            food?.takeIf { it.isNotBlank() },
            ingredient.note?.takeIf { it.isNotBlank() },
        )
        // A line with no structured part at all is free text Mealie could not
        // parse; the original wording is the only thing worth showing.
        return parts.joinToString(" ").ifBlank { ingredient.originalText.orEmpty() }
    }
}
