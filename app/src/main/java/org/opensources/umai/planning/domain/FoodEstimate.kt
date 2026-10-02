package org.opensources.umai.planning.domain

import kotlin.math.roundToInt

/** Where the values of a food come from. */
enum class FoodSource {
    /** The Ciqual table. */
    TABLE,

    /** The on-device model's guess, for a food the table does not have. */
    MODEL,
}

/** One food of what was eaten: [amount] of [unit], `null` when not known, and its values for 100 of [unit]. */
data class EstimatedFood(
    val name: String,
    val amount: Double?,
    val unit: FoodUnit,
    val per100: NutritionFacts,
    val source: FoodSource,
) {
    /** The values of the amount eaten; `null` while the amount is not known. */
    val nutrition: NutritionFacts? get() = amount?.let { per100.scaled(it / NutritionFacts.LABEL_QUANTITY) }

    val calories: Int? get() = nutrition?.get(Nutrient.ENERGY)?.roundToInt()
}

/**
 * What was eaten, as one food of the plan: the foods it is made of, their
 * values added up. Several foods are only added up when the amount of each
 * is known; a single one may still wait for its amount to be typed.
 */
data class FoodEstimate(val items: List<EstimatedFood>, val byModel: Boolean) {

    init {
        require(items.isNotEmpty()) { "An estimate has at least one food" }
        require(items.size == 1 || items.all { it.amount != null && it.amount > 0 }) {
            "Foods are only added up once the amount of each is known"
        }
    }

    /** Millilitres when every food is a drink, grams otherwise. */
    val unit: FoodUnit =
        if (items.all { it.unit == FoodUnit.MILLILITRE }) FoodUnit.MILLILITRE else FoodUnit.GRAM

    /** The amount of all of it together, in [unit]; `null` while the amount of a single food is not known. */
    val amount: Double? = if (items.all { it.amount != null }) items.sumOf { it.amount ?: 0.0 } else null

    /**
     * The values for 100 of [unit] of all of it. A nutrient is only given
     * when every food gives it: a sum missing a part would read as complete.
     */
    val per100: NutritionFacts = items.singleOrNull()?.per100 ?: combined()

    private fun combined(): NutritionFacts {
        val total = amount ?: return NutritionFacts()
        val values = Nutrient.entries.mapNotNull { nutrient ->
            val parts = items.map { it.nutrition?.get(nutrient) }
            if (parts.any { it == null }) null else nutrient to parts.sumOf { it ?: 0.0 }
        }.toMap()
        return NutritionFacts(values).scaled(NutritionFacts.LABEL_QUANTITY / total)
    }
}
