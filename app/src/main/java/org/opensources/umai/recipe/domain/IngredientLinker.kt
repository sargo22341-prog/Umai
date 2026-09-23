package org.opensources.umai.recipe.domain

import java.text.Normalizer

/**
 * Finds which ingredients each step mentions, the way Mealie links them:
 * a step lists the `referenceId` of the ingredients it uses.
 *
 * An ingredient is known by the food Mealie linked it to, or else by the words
 * of its line once quantities and units are set aside. A step mentions it when
 * one of those names appears in its title or text as a whole word, accents,
 * case and plural marks aside. Links already on a step are always kept.
 */
object IngredientLinker {

    /** [added] links were found on top of the ones the steps had; [total] is what they now hold. */
    data class Result(val steps: List<DraftStep>, val added: Int, val total: Int)

    fun link(ingredients: List<DraftIngredient>, steps: List<DraftStep>): Result {
        val known = ingredients.map { it.referenceId }.toSet()
        val patterns = ingredients
            .filter { it.text.isNotBlank() || it.food != null }
            .map { ingredient -> ingredient.referenceId to termsFor(ingredient).map(::wordPattern) }
            .filter { (_, terms) -> terms.isNotEmpty() }

        var added = 0
        val linked = steps.map { step ->
            val text = " ${normalize(step.title)} ${normalize(step.text)} "
            val current = step.ingredientReferences.filter { it in known }.distinct()
            val found = patterns
                .filter { (reference, terms) -> reference !in current && terms.any { it.containsMatchIn(text) } }
                .map { (reference, _) -> reference }
            added += found.size
            step.copy(ingredientReferences = current + found)
        }
        return Result(linked, added, linked.sumOf { it.ingredientReferences.size })
    }

    /** The names an ingredient goes by, longest first. */
    internal fun termsFor(ingredient: DraftIngredient): List<String> {
        val food = ingredient.food
        val names = if (food != null) {
            listOfNotNull(food.name, food.pluralName).map(::normalize)
        } else {
            wordsOf(ingredient.text)
        }
        return names
            .flatMap(::singularForms)
            .filter { it.length >= MIN_TERM_LENGTH }
            .distinct()
            .sortedByDescending { it.length }
    }

    /**
     * The phrase left once quantity, unit and linking words are removed —
     * "200 g de farine de blé" gives "farine de ble" — then its first word and
     * its other meaningful words, since a step rarely repeats the whole phrase.
     */
    private fun wordsOf(line: String): List<String> {
        var core = normalize(line)
            .replace(parenthesis, " ")
            .substringBefore(',')
            .trim()
        var previous: String
        do {
            previous = core
            core = core.replace(leadingQuantity, "").replace(leadingUnit, "").replace(leadingLink, "").trim()
        } while (core != previous)

        val words = core.split(nonWord).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        // The name is the first word in French ("oignon rouge") and the last in English ("red onion").
        val heads = listOf(words.first(), words.last()).filter { it !in stopWords && it !in linkWords }
        val meaningful = words.filter { it.length >= MIN_WORD_LENGTH && it !in stopWords }
        return listOfNotNull(core.takeIf { words.size > 1 }) + heads + meaningful
    }

    /** The term and the singular it may have been written from. */
    private fun singularForms(term: String): List<String> = listOf(
        term,
        term.replace(Regex("""eufs\b"""), "euf"),
        term.replace(Regex("""aux\b"""), "al"),
        term.replace(Regex("""ies\b"""), "y"),
        term.replace(Regex("""s\b"""), ""),
        term.replace(Regex("""x\b"""), ""),
    ).map { it.trim() }.distinct()

    /** A whole-word match that also accepts the plural marks of French and English. */
    private fun wordPattern(term: String): Regex =
        Regex("""(?<![a-z0-9])${Regex.escape(term)}(?:s|x|es)?(?![a-z0-9])""")

    internal fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(diacritics, "")
            .replace("œ", "oe").replace("Œ", "oe")
            .replace("æ", "ae").replace("Æ", "ae")
            .replace('’', '\'')
            .lowercase()
            .replace(spaces, " ")
            .trim()

    private const val MIN_TERM_LENGTH = 3
    private const val MIN_WORD_LENGTH = 4

    private val diacritics = Regex("""\p{Mn}+""")
    private val spaces = Regex("""\s+""")
    private val nonWord = Regex("""[^a-z0-9]+""")
    private val parenthesis = Regex("""\([^)]*\)""")
    private val leadingQuantity = Regex("""^(?:[\d½¼¾⅓⅔⅛.,/\-–]+|x|×|une?|one|a)\s+""")
    private val leadingLink = Regex("""^(?:de |d'|du |des |of |the |la |le |les |l')""")
    private val leadingUnit = Regex(
        """^(?:kg|g|gr|mg|ml|cl|dl|l|litres?|c\. ?a ?(?:s|c)\.?|c\.?(?:s|c)\.?|cs|cc|""" +
            """cuill?(?:ere|\.)?s? (?:a |a la )?(?:soupe|cafe|the)|cuill?(?:ere|\.)?s?|""" +
            """tbsp|tsp|tablespoons?|teaspoons?|cups?|oz|lbs?|pounds?|grammes?|""" +
            """pincees?|sachets?|boites?|gousses?|tranches?|brins?|bottes?|feuilles?|morceaux?|""" +
            """poignees?|filets?|verres?|pots?|paquets?|bouquets?|cloves?|slices?|pinch(?:es)?|""" +
            """handfuls?|bunch(?:es)?|pieces?|cans?|sprigs?|dashes?)(?:\s+|$)""",
    )

    /** Words that only join others: "farine de blé" does not end on a name. */
    private val linkWords = setOf("de", "du", "des", "d", "la", "le", "les", "l", "a", "au", "aux", "en", "of", "and", "et")

    /** Words that describe an ingredient rather than name it. */
    private val stopWords = setOf(
        "avec", "pour", "dans", "sans", "frais", "fraiche", "fraiches", "gros", "grosse", "grosses",
        "petit", "petite", "petits", "petites", "moyen", "moyenne", "moyens", "moyennes", "entier",
        "entiere", "entiers", "hache", "hachee", "haches", "rape", "rapee", "rapes", "fondu", "fondue",
        "liquide", "epaisse", "sec", "seche", "secs", "seches", "moulu", "moulue", "noir", "noire",
        "blanc", "blanche", "rouge", "rouges", "vert", "verte", "jaune", "doux", "douce", "fort",
        "forte", "extra", "vierge", "bio", "nature", "finement", "environ", "facultatif", "quelques",
        "gout", "cuit", "cuite", "cuits", "cru", "crue", "chopped", "fresh", "large", "small",
        "medium", "ground", "dried", "sliced", "diced", "minced", "optional", "some", "taste",
        "red", "hot", "big", "whole", "cooked", "raw", "fine", "finely", "beaten",
    )
}
