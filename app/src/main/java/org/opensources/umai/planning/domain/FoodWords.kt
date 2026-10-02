package org.opensources.umai.planning.domain

import java.text.Normalizer

/**
 * The words of a food name as they are compared: lower case, without accents
 * or plural, without the little words that tell nothing of the food. A typed
 * "Cafés sans sucre" and the table's "Café, … sans sucres ajoutés" then share
 * "cafe", "sans" and "sucre".
 */
object FoodWords {

    private val marks = Regex("""\p{Mn}+""")
    private val separators = Regex("""[^\p{L}\p{N}]+""")

    /**
     * Little words of French and English: "jus d'orange" and "orange juice"
     * keep only what names the food. Not "the": without its accent, it is "thé".
     */
    private val ignored = setOf(
        "a", "an", "and", "au", "aux", "avec", "d", "de", "des", "du", "en", "et",
        "l", "la", "le", "les", "of", "with",
    )

    fun of(text: String): List<String> =
        plain(text).split(separators)
            .filter { it.isNotEmpty() && it !in ignored }
            .map(::singular)

    /** [text] lower case, accents and ligatures undone. */
    fun plain(text: String): String =
        Normalizer.normalize(text.lowercase().replace("œ", "oe").replace("æ", "ae"), Normalizer.Form.NFD)
            .replace(marks, "")

    /**
     * French and English plurals end in s or x. Taken off both sides alike,
     * a word that ends so anyway ("noix", "jus") still meets itself.
     */
    private fun singular(word: String): String =
        if (word.length > MIN_PLURAL_LENGTH && (word.endsWith('s') || word.endsWith('x'))) word.dropLast(1) else word

    private const val MIN_PLURAL_LENGTH = 3
}
