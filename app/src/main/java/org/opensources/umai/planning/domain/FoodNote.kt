package org.opensources.umai.planning.domain

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The names of the nutrients in the language of the app, how it writes
 * numbers, and the line that tells values the on-device model guessed.
 */
data class FoodNoteLabels(val locale: Locale, val nutrients: Map<Nutrient, String>, val estimated: String)

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
 * Values the model guessed say so on a last line.
 */
object FoodNote {

    /**
     * [portion] is the nutrition of the quantity eaten; its energy, rounded,
     * is the calories of the note. [estimated] when the model guessed it.
     */
    fun text(
        quantity: Double?,
        unit: FoodUnit,
        portion: NutritionFacts,
        labels: FoodNoteLabels,
        estimated: Boolean = false,
    ): String {
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
        val origin = if (estimated) labels.estimated else ""
        return listOf(summary, details, origin).filter { it.isNotEmpty() }.joinToString("\n")
    }

    private const val SEPARATOR = " · "
}
