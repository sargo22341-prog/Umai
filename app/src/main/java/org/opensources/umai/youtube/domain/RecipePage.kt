package org.opensources.umai.youtube.domain

/**
 * The recipe a video description links to, as its page publishes it: the
 * ingredients with their quantities, and how many they serve. [url] is the
 * page, redirections followed.
 */
data class RecipePage(
    val url: String,
    val ingredients: List<String>,
    val servings: Int?,
)

/** Where the recipe pages linked from a description are read: Mealie's scraper, or a test double. */
fun interface RecipePageSource {
    /** The recipe at [url], `null` when the page holds none with ingredients or cannot be read. */
    suspend fun read(url: String): RecipePage?
}

/**
 * Finds, in a video description, the links that lead to the written recipe:
 * the words around a link say it ("Quantités de la recette :", "Full recipe:",
 * "Découvrez la recette illustrée en suivant ce lien"). The address itself
 * tells nothing: any site can host a recipe, and a shortener hides it anyway.
 */
object DescriptionLinks {

    /** The recipe links, the most explicit first, at most [MAX_LINKS]. */
    fun recipeLinks(description: String): List<String> {
        val lines = description.lines()
        return lines.flatMapIndexed { index, line ->
            url.findAll(line).mapNotNull { match ->
                val address = match.value.trimEnd(*TRAILING).let { if (it.startsWith("www.")) "https://$it" else it }
                if (YouTubeLinks.isYouTube(address)) return@mapNotNull null
                val around = line.removeRange(match.range)
                // A link alone on its line is announced by the line before.
                val context = if (around.count(Char::isLetter) < MIN_LETTERS) previousText(lines, index) + " " + around else around
                score(context).takeIf { it > 0 }?.let { Candidate(address, it, index) }
            }.toList()
        }
            .sortedWith(compareByDescending<Candidate> { it.score }.thenBy { it.line })
            .map { it.url }
            .distinct()
            .take(MAX_LINKS)
    }

    /**
     * How clearly [context] announces a recipe: 2 for its ingredients or
     * quantities, 1 for "the recipe", 0 for anything else — the equipment used
     * "for this recipe", a book of recipes, a shop.
     */
    internal fun score(context: String): Int {
        val words = Words.tokens(context)
        if (words.any { word -> excluded.any { word.startsWith(it) } }) return 0
        val text = words.joinToString(" ")
        return when {
            strong.any { it.containsMatchIn(text) } -> 2
            words.any { it in recipeWords } -> 1
            else -> 0
        }
    }

    private fun previousText(lines: List<String>, index: Int): String =
        (index - 1 downTo 0).map { lines[it] }.firstOrNull { it.isNotBlank() }.orEmpty()

    private data class Candidate(val url: String, val score: Int, val line: Int)

    private val url = Regex("""(?:https?://|www\.)[^\s<>"'`]+""", RegexOption.IGNORE_CASE)
    private val TRAILING = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '»', '"', '\'')

    /** Singular on purpose: "toutes les recettes sur …" leads to a site, not to this recipe. */
    private val recipeWords = setOf("recette", "recipe")

    private val strong = listOf(
        Regex("""\bquantit"""),
        Regex("""\bingredient"""),
        Regex("""\bproportion"""),
        Regex("""\bmeasurement"""),
        Regex("""\b(?:full|written|printable|complete|detailed) recipe\b"""),
        Regex("""\bprintable\b"""),
        Regex("""\brecette (?:complete|detaillee|ecrite|illustree|imprimable)\b"""),
        Regex("""\bfiche recette\b"""),
    )

    /** Word starts that make a link about something else than the recipe itself. */
    private val excluded = listOf(
        "materiel", "ustensil", "equipement", "equipment", "tool", "gear", "livre", "book", "boutique", "shop",
        "store", "amazon", "affili", "promo", "coupon", "sponsor", "partenaire", "partner", "abonne", "subscribe",
        "playlist", "merch", "newsletter",
    )

    private const val MIN_LETTERS = 3
    private const val MAX_LINKS = 2
}
