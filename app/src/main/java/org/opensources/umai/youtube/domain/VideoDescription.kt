package org.opensources.umai.youtube.domain

import java.text.Normalizer

/**
 * Reads what cooks usually write under their videos: a list of ingredients,
 * sometimes the steps, the number of servings and the chapters as timestamps.
 *
 * Descriptions follow no format, so this reads them the way a person would:
 * a heading announces a list, the list runs until something else starts
 * (a blank line followed by prose, a link, a hashtag, another heading).
 * French and English headings are recognized.
 */
object VideoDescription {

    /** The ingredient lines, sub-headings such as "For the sauce:" kept as their own entries. */
    fun ingredients(description: String): List<String> {
        val lines = description.lines()
        val start = lines.indexOfFirst { isIngredientHeading(it) }
        if (start >= 0) {
            // The heading line may itself hold the servings ("Ingrédients pour 4 personnes :").
            return collectList(lines, start + 1) { !isStepsHeading(it) && (looksLikeIngredient(it) || isSubHeading(it)) }
                .dropLastWhile { isSubHeading(it) }
        }
        // No heading: the longest run of lines starting with a quantity.
        return quantityRuns(lines).maxByOrNull { it.size }?.takeIf { it.size >= MIN_UNTITLED_LIST }.orEmpty()
    }

    /** The steps written in the description, when there is a heading for them. */
    fun steps(description: String): List<String> {
        val lines = description.lines()
        val start = lines.indexOfFirst { isStepsHeading(it) }
        if (start < 0) return emptyList()
        return collectList(lines, start + 1) { line -> !isIngredientHeading(line) && line.length >= MIN_STEP_LENGTH }
            .map { stripNumbering(it) }
            .filter { it.isNotBlank() }
    }

    /** "Pour 4 personnes", "4 servings", "Serves 4": the number of servings, `null` when not said. */
    fun servings(description: String): Int? {
        val text = fold(description)
        return servingPatterns.firstNotNullOfOrNull { pattern ->
            pattern.find(text)?.groupValues?.get(1)?.toIntOrNull()
        }?.takeIf { it in 1..MAX_SERVINGS }
    }

    /**
     * Timestamps listed in the description ("0:00 Intro", "1:25 - La sauce"),
     * which are what YouTube turns into chapters. At least two are needed,
     * the first one at the very start, as YouTube itself requires.
     */
    fun timestamps(description: String): List<ChapterMark> {
        val marks = description.lines().mapNotNull { line ->
            val match = timestampLine.find(line.trim()) ?: return@mapNotNull null
            val seconds = parseTimestamp(match.groupValues[1]) ?: return@mapNotNull null
            val title = line.trim().removeRange(match.range).trim().trim('-', '–', '—', ':', '|', '•', ' ')
            title.takeIf { it.isNotBlank() }?.let { ChapterMark(it, seconds.toDouble()) }
        }.distinctBy { it.start }.sortedBy { it.start }
        return marks.takeIf { it.size >= 2 && it.first().start == 0.0 }.orEmpty()
    }

    /** "1:02:03" or "2:03" as seconds. */
    fun parseTimestamp(text: String): Int? {
        val parts = text.split(':').map { it.toIntOrNull() ?: return null }
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> null
        }
    }

    /**
     * Takes the lines from [from] as long as they belong to the list: blank
     * lines are allowed inside it, but a list ends on a heading of another
     * kind, a link, a hashtag or a separator, and on a line [accept] refuses.
     */
    private fun collectList(lines: List<String>, from: Int, accept: (String) -> Boolean): List<String> {
        val items = mutableListOf<String>()
        var blanks = 0
        for (raw in lines.drop(from)) {
            val line = cleanLine(raw)
            if (line.isEmpty()) {
                blanks++
                if (blanks >= 2 && items.isNotEmpty()) break
                continue
            }
            if (isNoise(line)) {
                if (items.isNotEmpty()) break else continue
            }
            if (!accept(line)) {
                if (items.isNotEmpty()) break else continue
            }
            blanks = 0
            items += line
            if (items.size >= MAX_ITEMS) break
        }
        return items
    }

    private fun quantityRuns(lines: List<String>): List<List<String>> {
        val runs = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        for (raw in lines) {
            val line = cleanLine(raw)
            // "Une recette de grand-mère." starts like a quantity, but is a sentence.
            val sentence = line.endsWith('.') || line.endsWith('!')
            if (line.isNotEmpty() && !isNoise(line) && !sentence && startsWithQuantity(line) && line.length <= MAX_INGREDIENT_LENGTH) {
                current += line
            } else if (line.isNotEmpty() || current.isNotEmpty()) {
                if (current.isNotEmpty()) runs += current
                current = mutableListOf()
            }
        }
        if (current.isNotEmpty()) runs += current
        return runs
    }

    private fun looksLikeIngredient(line: String): Boolean =
        line.length <= MAX_INGREDIENT_LENGTH &&
            !isStepsHeading(line) &&
            !timestampLine.containsMatchIn(line) &&
            // A sentence of the prose around the list, not an item of it.
            !(line.endsWith(".") && line.split(' ').size > MAX_INGREDIENT_WORDS)

    private fun isSubHeading(line: String): Boolean =
        line.endsWith(":") && line.length <= MAX_SUBHEADING_LENGTH && !startsWithQuantity(line)

    private fun isIngredientHeading(line: String): Boolean {
        val text = fold(cleanLine(line))
        return text.length <= MAX_HEADING_LENGTH && ingredientHeadings.any { it.containsMatchIn(text) }
    }

    private fun isStepsHeading(line: String): Boolean {
        val text = fold(cleanLine(line))
        return text.length <= MAX_HEADING_LENGTH && stepHeadings.any { it.matches(text.trimEnd(':', ' ', '.')) }
    }

    private fun isNoise(line: String): Boolean {
        val text = line.lowercase()
        return text.contains("http://") || text.contains("https://") || text.contains("www.") ||
            text.startsWith("#") || separator.matches(text) || text.startsWith("@")
    }

    private fun startsWithQuantity(line: String): Boolean = quantityStart.containsMatchIn(fold(line))

    /** Bullets, emojis and stray spaces around a line. */
    internal fun cleanLine(line: String): String =
        line.trim()
            .replace(leadingBullet, "")
            .trim()

    private fun stripNumbering(line: String): String = line.replace(numbering, "").trim()

    internal fun fold(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(marks, "")

    private val marks = Regex("\\p{Mn}+")
    private val leadingBullet = Regex("""^[\s\-–—•·*▪▫◦●○✓✔☑➤►▶→>]+|^\p{So}+\s*""")
    private val numbering = Regex("""^(?:étape|etape|step)?\s*\d{1,2}\s*[.):/-]\s*""", RegexOption.IGNORE_CASE)
    private val separator = Regex("""^[-_=*~.·•]{3,}$""")
    private val timestampLine = Regex("""(?:^|\s|\()((?:\d{1,2}:)?\d{1,2}:\d{2})(?:\)|\s|$)""")
    private val quantityStart = Regex(
        """^(?:\d|½|¼|¾|⅓|⅔|un |une |deux |trois |quatre |cinq |six |dix |a |an |one |two |three |four |five |quelques |some |pincee|1/2|1/4)""",
    )

    private val ingredientHeadings = listOf(
        Regex("""^ingr[e]dients?\b"""),
        Regex("""\bingredients?\s*(?:pour|for|:)"""),
        Regex("""^(?:les |the )?ingredients?\b"""),
        Regex("""^il (?:vous )?faut\b"""),
        Regex("""^(?:what )?you(?:'ll| will)? need\b"""),
        Regex("""^pour \d+ (?:personnes|pers|parts|portions|gourmands|convives)\b"""),
        Regex("""^liste des (?:ingredients|courses)\b"""),
    )

    private val stepHeadings = listOf(
        Regex("""^(?:la )?preparation"""),
        Regex("""^(?:les )?etapes(?: de (?:la )?(?:recette|preparation))?"""),
        Regex("""^(?:la )?recette"""),
        Regex("""^(?:instructions|method|directions|steps|how to make it|deroulement)"""),
    )

    private val servingPatterns = listOf(
        Regex("""pour (\d{1,2}) (?:personnes|pers\b|parts|portions|gourmands|convives|assiettes)"""),
        Regex("""(\d{1,2}) (?:personnes|portions|parts)\b"""),
        Regex("""(\d{1,2}) (?:servings|portions|people)\b"""),
        Regex("""serves (\d{1,2})\b"""),
    )

    private const val MIN_UNTITLED_LIST = 3
    private const val MIN_STEP_LENGTH = 8
    private const val MAX_ITEMS = 60
    private const val MAX_INGREDIENT_LENGTH = 120
    private const val MAX_INGREDIENT_WORDS = 14
    private const val MAX_SUBHEADING_LENGTH = 50
    private const val MAX_HEADING_LENGTH = 60
    private const val MAX_SERVINGS = 50
}
