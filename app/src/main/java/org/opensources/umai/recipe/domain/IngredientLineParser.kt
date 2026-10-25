package org.opensources.umai.recipe.domain

import java.text.Normalizer

/** Explicit measures take precedence over statistical unit guesses. Ranges need human review. */
object IngredientLineParser {
    fun parse(text: String): IngredientAnalysis? {
        val match = measured.find(text.trim()) ?: return null
        val quantity = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        if (!quantity.isFinite() || quantity <= 0) return null
        val range = match.groupValues[2].isNotBlank()
        val unit = canonicalUnit(match.groupValues[3]) ?: return null
        val tail = text.trim().substring(match.range.last + 1).trim()
        val notes = parentheses.findAll(tail).map { it.value }.toList()
        val food = tail.replace(parentheses, " ").trim().replace(link, "").trim()
        if (food.isBlank()) return null
        return IngredientAnalysis(
            quantity = quantity.takeUnless { range }, food = IngredientChoice(food),
            unit = IngredientChoice(unit), note = (listOfNotNull(match.value.trim().takeIf { range }) + notes).joinToString(" "),
            needsReview = true,
        )
    }

    fun canonicalUnit(value: String): String? = aliases[normalize(value)]

    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(diacritics, "").lowercase().trim()

    private val diacritics = Regex("\\p{Mn}+")
    private val parentheses = Regex("\\([^)]*\\)")
    private val link = Regex("^(?:de\\s+|d['\u2019]|of\\s+)", RegexOption.IGNORE_CASE)
    private val aliases = buildMap {
        listOf(
            "g" to listOf("g", "gr", "gramme", "grammes", "gram", "grams"),
            "kg" to listOf("kg", "kilo", "kilos", "kilogramme", "kilogrammes", "kilogram", "kilograms"),
            "mg" to listOf("mg", "milligramme", "milligrammes", "milligram", "milligrams"),
            "l" to listOf("l", "litre", "litres", "liter", "liters"),
            "ml" to listOf("ml", "millilitre", "millilitres", "milliliter", "milliliters"),
            "cl" to listOf("cl", "centilitre", "centilitres"),
            "dl" to listOf("dl", "decilitre", "decilitres"),
            "pinc\u00e9e" to listOf("pince", "pincee", "pincees", "pinch", "pinches"),
        ).forEach { (unit, names) -> names.forEach { put(it, unit) } }
    }
    private val measured = Regex(
        "^([0-9]+(?:[.,][0-9]+)?)\\s*(?:(\u00e0|a|to|[-\u2013])\\s*[0-9]+(?:[.,][0-9]+)?\\s*)?" +
            "([\\p{L}]+)(?=\\s|$)\\s*", RegexOption.IGNORE_CASE,
    )
}
