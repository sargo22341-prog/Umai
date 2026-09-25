package org.opensources.umai.youtube.domain

import org.opensources.umai.planning.domain.IngredientKeys

/**
 * One list of ingredient lines. [authored] lists were written by a person —
 * a recipe page, a description — where a food written twice is needed twice;
 * the language model repeating a line means nothing more.
 */
data class IngredientSource(val lines: List<String>, val authored: Boolean)

/**
 * Brings the ingredient lists of a recipe rebuilt from a video together, with
 * one line per food, without any language model.
 *
 * The sources come most trusted first: the recipe page the description links
 * to, the list the author wrote in the description, then what the language
 * model read in the video. A food keeps the line of the most trusted source
 * that gives its quantity; a line without quantity stays without one rather
 * than borrowing an amount nobody wrote.
 */
object IngredientMerge {

    /**
     * [sources] come most trusted first. When [firstIsComplete] and the first
     * source is not empty, it lists every ingredient, as a recipe page does:
     * the others then only lend a quantity to a food it gives none for.
     */
    fun merge(sources: List<IngredientSource>, firstIsComplete: Boolean): List<String> {
        val closed = firstIsComplete && sources.firstOrNull()?.lines?.any(::isIngredient) == true
        val kept = LinkedHashMap<String, Entry>()
        sources.forEachIndexed { index, source ->
            source.lines.map { it.trim() }.filter(::isIngredient).forEach { line ->
                val key = keyOf(line)
                val current = kept[key]
                when {
                    current == null -> if (!closed || index == 0) kept[key] = Entry(line, index)
                    current.source == index -> kept[key] = current.copy(line = sameSource(current.line, line, source.authored))
                    !IngredientLine.hasQuantity(current.line) && IngredientLine.hasQuantity(line) ->
                        kept[key] = current.copy(line = line)
                }
            }
        }
        return kept.values.map { it.line }
    }

    /** The food a line is about: "2 gousses d'ail" and "Ail" are the same one. */
    fun keyOf(line: String): String =
        IngredientKeys.nameIn(line)?.takeIf { it.isNotBlank() } ?: VideoDescription.fold(line).trim()

    /**
     * One food written twice in one source. An author who lists garlic for the
     * salad and for the sauce means both: the amounts add up, or are both
     * written when they cannot be added.
     */
    private fun sameSource(first: String, second: String, authored: Boolean): String {
        val firstHasQuantity = IngredientLine.hasQuantity(first)
        if (!authored) return if (!firstHasQuantity && IngredientLine.hasQuantity(second)) second else first
        IngredientLine.sum(first, second)?.let { return it }
        val quantity = IngredientLine.quantityOf(second) ?: return first
        return if (firstHasQuantity) "$first + ${quantityWithUnit(second, quantity)}" else second
    }

    /** "30 g" of "30 g de parmesan", "2" of "2 œufs": what is added to the first line. */
    private fun quantityWithUnit(line: String, quantity: LeadingQuantity): String {
        val unit = quantity.unit ?: return quantity.text
        val afterNumber = line.trim().drop(quantity.text.length)
        return if (IngredientLine.isMeasure(unit)) quantity.text + afterNumber.takeWhile { it == ' ' } + unit else quantity.text
    }

    /** Sub-headings of a list ("Pour la sauce :") are not ingredients. */
    private fun isIngredient(line: String): Boolean = line.isNotBlank() && !line.trim().endsWith(":")

    private data class Entry(val line: String, val source: Int)
}

/**
 * Checks the ingredients the language model wrote against what the video
 * really says. A food never named in the title, the description, the recipe
 * page or the transcript is dropped; a quantity is kept only when the same
 * number is said or written next to the food, in a plausible amount.
 * Otherwise the line stays, without its quantity.
 */
object IngredientEvidence {

    fun checked(lines: List<String>, sources: String): List<String> {
        val tokens = tokens(sources)
        val positions = HashMap<String, MutableList<Int>>()
        tokens.forEachIndexed { index, token -> positions.getOrPut(token.word) { mutableListOf() } += index }
        return lines.mapNotNull { raw ->
            val line = raw.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val food = IngredientMerge.keyOf(line).split(' ').map(::normalized).filter { it.isNotEmpty() }
            if (food.isEmpty() || food.any { it !in positions }) return@mapNotNull null
            val quantity = IngredientLine.quantityOf(line) ?: return@mapNotNull line
            val said = positions.getValue(food.first()).any { at -> supports(tokens, at, quantity) }
            if (said && IngredientLine.isPlausible(quantity)) line else IngredientLine.withoutQuantity(line)
        }
    }

    /** Whether the amount, and its unit when it has one, are said within a few words of the food at [at]. */
    private fun supports(tokens: List<Token>, at: Int, quantity: LeadingQuantity): Boolean {
        val amount = quantity.amount ?: return true
        val window = tokens.subList((at - WINDOW).coerceAtLeast(0), (at + WINDOW + 1).coerceAtMost(tokens.size))
        if (window.none { it.number != null && kotlin.math.abs(it.number - amount) < EPSILON }) return false
        val unit = quantity.unit?.takeIf { IngredientLine.isMeasure(it) } ?: return true
        val spoken = unitWords(IngredientLine.normalizedUnit(unit))
        return window.any { it.word in spoken }
    }

    /** The words a unit is said or written with: "g" is also "grammes", "c-à-s" is a spoon. */
    private fun unitWords(unit: String): Set<String> = unitSynonyms.firstOrNull { unit in it } ?: unit.split('-').toSet()

    private data class Token(val word: String, val number: Double?)

    private fun tokens(text: String): List<Token> {
        val prepared = VideoDescription.fold(text)
            .replace(fraction) { match ->
                val bottom = match.groupValues[2].toDouble()
                if (bottom == 0.0) match.value else " ${match.groupValues[1].toDouble() / bottom} "
            }
            .replace(decimalComma, "$1.$2")
            .replace("½", " 0.5 ").replace("¼", " 0.25 ").replace("¾", " 0.75 ")
            // "200g" is said "200 g".
            .replace(glued, "$1 $2")
        return prepared.split(separators).filter { it.isNotEmpty() }.map { word ->
            Token(normalized(word), word.toDoubleOrNull() ?: numberWords[word])
        }
    }

    private fun normalized(word: String): String {
        val folded = VideoDescription.fold(word)
        return if (folded.length > 3 && (folded.endsWith('s') || folded.endsWith('x'))) folded.dropLast(1) else folded
    }

    private val fraction = Regex("""(\d+)\s*/\s*(\d+)""")
    private val decimalComma = Regex("""(\d),(\d)""")
    private val glued = Regex("""(\d)(\p{L})""")
    private val separators = Regex("""[^\p{L}0-9.]+|(?<!\d)\.|\.(?!\d)""")

    private val numberWords = mapOf(
        "un" to 1.0, "une" to 1.0, "deux" to 2.0, "trois" to 3.0, "quatre" to 4.0, "cinq" to 5.0, "six" to 6.0,
        "sept" to 7.0, "huit" to 8.0, "neuf" to 9.0, "dix" to 10.0, "douze" to 12.0, "quinze" to 15.0,
        "vingt" to 20.0, "trente" to 30.0, "cent" to 100.0, "demi" to 0.5, "moitie" to 0.5,
        "one" to 1.0, "two" to 2.0, "three" to 3.0, "four" to 4.0, "five" to 5.0, "seven" to 7.0, "eight" to 8.0,
        "nine" to 9.0, "ten" to 10.0, "twelve" to 12.0, "fifteen" to 15.0, "twenty" to 20.0, "hundred" to 100.0,
        "half" to 0.5, "a" to 1.0, "an" to 1.0,
    )

    private val unitSynonyms = listOf(
        setOf("g", "gr", "gramme"),
        setOf("kg", "kilo", "kilogramme"),
        setOf("ml", "millilitre"),
        setOf("cl", "centilitre"),
        setOf("dl", "decilitre"),
        setOf("l", "litre"),
        setOf("c-a-s", "cs", "cas", "cuillere", "cuil", "soupe", "tbsp", "tablespoon", "spoon"),
        setOf("c-a-c", "cc", "cac", "cafe", "tsp", "teaspoon"),
        setOf("cup", "tasse"),
        setOf("oz", "ounce"),
        setOf("lb", "pound"),
    )

    /** Words around the food in which its amount is looked for. */
    private const val WINDOW = 8
    private const val EPSILON = 0.001
}

/** The ingredients of a recipe rebuilt from a video, from every source that gives them. */
object RecipeIngredients {

    /**
     * The recipe page first, whose list is complete; then the list written in
     * the description; then what the language model read in the video, once
     * checked against it. [modelLines] is `null` when no model was used.
     */
    fun of(video: YouTubeVideo, page: RecipePage?, modelLines: List<String>?): List<String> {
        val model = modelLines?.let { IngredientEvidence.checked(it, sourceText(video, page)) }.orEmpty()
        return IngredientMerge.merge(
            listOf(
                IngredientSource(page?.ingredients.orEmpty(), authored = true),
                IngredientSource(VideoDescription.ingredients(video.description), authored = true),
                IngredientSource(model, authored = false),
            ),
            firstIsComplete = true,
        )
    }

    /** The list known before the model reads the video, which it then need not write again. */
    fun known(video: YouTubeVideo, page: RecipePage?): List<String> =
        page?.ingredients ?: VideoDescription.ingredients(video.description).filterNot { it.endsWith(":") }

    /** Everything the video and its page say, where the model's ingredients must be found. */
    private fun sourceText(video: YouTubeVideo, page: RecipePage?): String = buildString {
        appendLine(video.title)
        appendLine(video.description)
        page?.ingredients?.forEach(::appendLine)
        video.transcript.forEach { append(it.text).append(' ') }
    }
}
