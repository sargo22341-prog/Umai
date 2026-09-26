package org.opensources.umai.planning.domain

/** What a nutrition label lists, in the order EU labels print it. Energy is in kcal, the rest in grams. */
enum class Nutrient {
    ENERGY,
    FAT,
    SATURATED_FAT,
    CARBOHYDRATES,
    SUGARS,
    FIBER,
    PROTEIN,
    SALT,
}

/** The unit a label counts the product in: its column is for 100 of them. */
enum class FoodUnit(val symbol: String) {
    GRAM("g"),
    MILLILITRE("ml"),
}

/** The values of some [Nutrient]s for a quantity of a product; a nutrient left out is not known. */
data class NutritionFacts(val values: Map<Nutrient, Double> = emptyMap()) {

    operator fun get(nutrient: Nutrient): Double? = values[nutrient]

    val isEmpty: Boolean get() = values.isEmpty()

    /** The same product in a quantity [factor] times larger. */
    fun scaled(factor: Double): NutritionFacts = NutritionFacts(values.mapValues { it.value * factor })

    companion object {
        /** A label gives its values for 100 g or 100 ml of the product. */
        const val LABEL_QUANTITY = 100.0
    }
}

/** Numbers as a label or a person writes them: "10,6", "10.6 g", "< 0.5". */
object NutritionNumbers {

    private val number = Regex("""\d+(?:[.,]\d+)?""")

    /** The first number in [text], `null` when there is none. */
    fun parse(text: String): Double? =
        number.find(text)?.value?.replace(',', '.')?.toDoubleOrNull()
}
