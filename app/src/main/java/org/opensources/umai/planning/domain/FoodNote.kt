package org.opensources.umai.planning.domain

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

/** The names of the nutrients in the language of the app, and how it writes numbers. */
data class FoodNoteLabels(val locale: Locale, val nutrients: Map<Nutrient, String>)

/**
 * The text of the Mealie note a food added to the plan becomes. Mealie has no
 * nutrition on a plan entry, so the note carries it, readable in Mealie too:
 *
 * ```
 * 139 kcal · 330 ml
 * Fat 0 g · Carbohydrates 35 g · Sugars 35 g · …
 * ```
 *
 * The calories come first, which is where [PlanCalories.ofNote] reads them back.
 */
object FoodNote {

    /**
     * [portion] is the nutrition of the quantity eaten; its energy, rounded,
     * is the calories of the note.
     */
    fun text(quantity: Double?, unit: FoodUnit, portion: NutritionFacts, labels: FoodNoteLabels): String {
        val decimal = NumberFormat.getNumberInstance(labels.locale).apply { maximumFractionDigits = 1 }
        val summary = listOfNotNull(
            portion[Nutrient.ENERGY]?.let { "${it.roundToInt()} kcal" },
            quantity?.let { "${decimal.format(it)} ${unit.symbol}" },
        ).joinToString(SEPARATOR)
        val details = Nutrient.entries
            .filter { it != Nutrient.ENERGY }
            .mapNotNull { nutrient ->
                val value = portion[nutrient] ?: return@mapNotNull null
                val name = labels.nutrients[nutrient] ?: return@mapNotNull null
                "$name ${decimal.format(value)} g"
            }
            .joinToString(SEPARATOR)
        return listOf(summary, details).filter { it.isNotEmpty() }.joinToString("\n")
    }

    private const val SEPARATOR = " · "
}
