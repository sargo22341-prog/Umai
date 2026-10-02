package org.opensources.umai.planning.domain

/** How much of a food a phrase says. */
sealed interface PhraseQuantity {

    /** "2 pommes", "un café", or no number at all: [count] times the usual portion. */
    data class Servings(val count: Double) : PhraseQuantity

    /**
     * "200 g de riz", "1 verre de lait": [amount] of [unit]. A household
     * measure, a glass or a spoon, has no [unit] of its own: it counts in the
     * food's, grams and millilitres weighing about the same.
     */
    data class Measured(val amount: Double, val unit: FoodUnit?) : PhraseQuantity
}

/** One food of what was typed, and how much of it: "2 pommes" is two servings of "pommes". */
data class FoodPhrase(val quantity: PhraseQuantity, val food: String) {

    /** The amount eaten of [choice], in [unitOf] it; `null` when it is a number of a food whose portion is not known. */
    fun amountOf(choice: FoodChoice): Double? = when (quantity) {
        is PhraseQuantity.Servings -> choice.serving?.let { it * quantity.count }
        is PhraseQuantity.Measured -> quantity.amount
    }

    fun unitOf(choice: FoodChoice): FoodUnit = when (quantity) {
        is PhraseQuantity.Servings -> choice.unit
        is PhraseQuantity.Measured -> quantity.unit ?: choice.unit
    }
}

/** Reads what a person types of what they ate: "2 pommes, 1 café sans sucre", "200 g de riz", "a glass of milk". */
object FoodPhrases {

    /** Commas, semicolons, "+", "et", "and" and new lines part the foods; a decimal comma does not. */
    private val separators = Regex("""(?<!\d),|,(?!\d)|[;+&\n]|\s(?:et|and)\s""", RegexOption.IGNORE_CASE)

    private val number = Regex("""^(\d+(?:[.,]\d+)?)(?:\s*/\s*(\d+))?\s*(?:[x×](?=\s|\d|$))?\s*""")
    private val wordNumber = Regex("""^(\p{L}+)\s+(?:(demie?|half)\s+)?""")
    private val linkWord = Regex("""^(?:de\s+|d['’]\s*|of\s+)""")
    private val fractions = mapOf('½' to 0.5, '¼' to 0.25, '¾' to 0.75)

    private val numberWords = mapOf(
        "un" to 1.0, "une" to 1.0, "deux" to 2.0, "trois" to 3.0, "quatre" to 4.0, "cinq" to 5.0,
        "six" to 6.0, "sept" to 7.0, "huit" to 8.0, "neuf" to 9.0, "dix" to 10.0,
        "a" to 1.0, "an" to 1.0, "one" to 1.0, "two" to 2.0, "three" to 3.0, "four" to 4.0, "five" to 5.0,
        "seven" to 7.0, "eight" to 8.0, "nine" to 9.0, "ten" to 10.0,
        "demi" to 0.5, "demie" to 0.5, "half" to 0.5,
    )

    private class Measure(val pattern: Regex, val amount: Double, val unit: FoodUnit?)

    /** A measure is a whole word: "g" is not the start of "gaufre", nor "l" of "lait". */
    private fun measure(words: String, amount: Double, unit: FoodUnit?) =
        Measure(Regex("""^(?:$words)(?![\p{L}\p{N}])\.?\s*""", RegexOption.IGNORE_CASE), amount, unit)

    private val measures = listOf(
        measure("kg|kilos?|kilogrammes?|kilograms?", 1000.0, FoodUnit.GRAM),
        measure("g|gr|grammes?|grams?", 1.0, FoodUnit.GRAM),
        measure("ml|millilitres?|milliliters?", 1.0, FoodUnit.MILLILITRE),
        measure("cl|centilitres?|centiliters?", 10.0, FoodUnit.MILLILITRE),
        measure("dl|décilitres?|decilitres?", 100.0, FoodUnit.MILLILITRE),
        measure("l|litres?|liters?", 1000.0, FoodUnit.MILLILITRE),
        measure("""cuill[eè]res?\s+à\s+soupe|c\.?\s*à\s*s|cas|tbsp|tablespoons?""", 15.0, null),
        measure("""cuill[eè]res?\s+à\s+café|c\.?\s*à\s*c|cac|tsp|teaspoons?""", 5.0, null),
        measure("verres?|glass(?:es)?", 200.0, null),
        measure("tasses?|cups?", 200.0, null),
        measure("mugs?", 300.0, null),
        measure("bols?|bowls?", 300.0, null),
        measure("canettes?|cans?", 330.0, null),
        measure("pintes?|pints?", 500.0, null),
        measure("bouteilles?|bottles?", 500.0, null),
    )

    fun split(text: String): List<String> = text.split(separators).map { it.trim() }.filter { it.isNotEmpty() }

    /** [text] as one food and its quantity; `null` when it names no food, as "2" or "200 g". */
    fun parse(text: String): FoodPhrase? {
        var rest = text.trim()
        val (count, afterCount) = count(rest)
        rest = afterCount
        val measure = measures.firstOrNull { it.pattern.containsMatchIn(rest) }
        if (measure != null) rest = rest.replaceFirst(measure.pattern, "")
        rest = rest.replaceFirst(linkWord, "").trim()
        if (rest.none { it.isLetter() }) return null
        val quantity = if (measure == null) {
            PhraseQuantity.Servings(count ?: 1.0)
        } else {
            PhraseQuantity.Measured((count ?: 1.0) * measure.amount, measure.unit)
        }
        return FoodPhrase(quantity, rest)
    }

    /** The number [text] starts with, and the text after it. */
    private fun count(text: String): Pair<Double?, String> {
        fractions[text.firstOrNull()]?.let { return it to text.drop(1).trimStart() }
        number.find(text)?.let { match ->
            val value = match.groupValues[1].replace(',', '.').toDouble()
            val divisor = match.groupValues[2].toDoubleOrNull()
            val count = if (divisor != null && divisor > 0) value / divisor else value
            return count to text.substring(match.range.last + 1)
        }
        val word = wordNumber.find(text) ?: return null to text
        val value = numberWords[word.groupValues[1].lowercase()] ?: return null to text
        // "une demi baguette": half of one.
        val count = if (word.groupValues[2].isNotEmpty()) value * HALF else value
        return count to text.substring(word.range.last + 1)
    }

    private const val HALF = 0.5
}
