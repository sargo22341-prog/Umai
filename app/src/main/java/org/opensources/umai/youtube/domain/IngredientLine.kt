package org.opensources.umai.youtube.domain

/**
 * The quantity written at the start of an ingredient line: "200 g de farine",
 * "1/2 citron", "deux œufs". [amount] is `null` when the words do not make a
 * number ("quelques", "a pinch"), [unit] is the word right after the number,
 * which is the food itself when the line has no unit ("2 œufs").
 */
data class LeadingQuantity(val text: String, val amount: Double?, val unit: String?)

/** Reads and rewrites the quantity of an ingredient line, in French or English. */
object IngredientLine {

    fun quantityOf(line: String): LeadingQuantity? {
        val trimmed = line.trim()
        val match = numberStart.find(trimmed) ?: wordStart.find(VideoDescription.fold(trimmed))?.let { folded ->
            // Number words are matched folded, and read back from the line as written.
            return LeadingQuantity(
                text = trimmed.take(folded.value.trimEnd().length),
                amount = numberWords[folded.groupValues[1]],
                unit = unitAfter(trimmed.drop(folded.value.length)),
            )
        } ?: return null
        return LeadingQuantity(
            text = match.value.trim(),
            amount = parseAmount(match.groupValues[1]),
            unit = unitAfter(trimmed.drop(match.value.length)),
        )
    }

    fun hasQuantity(line: String): Boolean = quantityOf(line) != null

    /** The line without its quantity nor its unit: "200 g de farine" becomes "Farine". */
    fun withoutQuantity(line: String): String {
        val quantity = quantityOf(line) ?: return line.trim()
        var rest = line.trim().drop(quantity.text.length).trim()
        val unit = quantity.unit
        if (unit != null && isMeasure(unit)) rest = rest.drop(unit.length).trim()
        rest = rest.replace(leadingLink, "").trim()
        return rest.replaceFirstChar { it.uppercase() }.ifBlank { line.trim() }
    }

    /**
     * Whether the amount could be meant: a phone-sized model sometimes writes
     * "100 g" for a pinch of salt, or 0. Bounds are loose on purpose: they
     * catch nonsense, not unusual recipes.
     */
    fun isPlausible(quantity: LeadingQuantity): Boolean {
        val amount = quantity.amount ?: return true
        if (amount <= 0) return false
        val max = when (quantity.unit?.let(::normalizedUnit)) {
            "g", "gr", "gramme" -> 5_000.0
            "kg", "kilo" -> 10.0
            "ml" -> 5_000.0
            "cl" -> 500.0
            "dl" -> 50.0
            "l", "litre" -> 10.0
            "oz", "ounce" -> 200.0
            "lb", "pound" -> 20.0
            "cup", "tasse" -> 20.0
            else -> MAX_COUNT
        }
        return amount <= max
    }

    /** "2 gousses" and "1 gousse" count the same thing: the unit, folded and singular. */
    fun normalizedUnit(unit: String): String {
        val folded = VideoDescription.fold(unit).trim('.', ' ')
        return if (folded.length > 3 && (folded.endsWith('s') || folded.endsWith('x'))) folded.dropLast(1) else folded
    }

    /**
     * Two lines for one food, both with an amount in the same unit, as one:
     * "1 gousse d'ail" twice is "2 gousses d'ail". `null` when they cannot be
     * added up.
     */
    fun sum(first: String, second: String): String? {
        val a = quantityOf(first) ?: return null
        val b = quantityOf(second) ?: return null
        val amountA = a.amount ?: return null
        val amountB = b.amount ?: return null
        if (a.unit == null || b.unit == null || normalizedUnit(a.unit) != normalizedUnit(b.unit)) return null
        val total = amountA + amountB
        var rest = first.trim().drop(a.text.length)
        val gap = rest.takeWhile { it == ' ' }
        rest = rest.drop(gap.length)
        if (total >= 2 && rest.startsWith(a.unit)) rest = plural(a.unit) + rest.drop(a.unit.length)
        return format(total, decimalComma = a.text.contains(',') || b.text.contains(',')) + gap + rest
    }

    private fun plural(word: String): String {
        val countable = word.length > 2 && word.all { it.isLetter() } && !word.endsWith('s') && !word.endsWith('x') &&
            VideoDescription.fold(word) !in abbreviations
        return if (countable) word + "s" else word
    }

    private fun format(amount: Double, decimalComma: Boolean): String {
        if (amount == Math.floor(amount)) return amount.toLong().toString()
        val text = "%.2f".format(java.util.Locale.ROOT, amount).trimEnd('0').trimEnd('.')
        return if (decimalComma) text.replace('.', ',') else text
    }

    private fun unitAfter(rest: String): String? =
        unitWord.find(rest)?.value?.trim()?.takeIf { it.isNotEmpty() }

    fun isMeasure(unit: String): Boolean = normalizedUnit(unit) in measures

    private fun parseAmount(text: String): Double? {
        val clean = text.replace(" ", "")
        vulgar[clean.lastOrNull()]?.let { fraction ->
            val whole = clean.dropLast(1).ifEmpty { "0" }.toDoubleOrNull() ?: return null
            return whole + fraction
        }
        if (clean.contains('/')) {
            val (top, bottom) = clean.split('/').map { it.toDoubleOrNull() ?: return null }
            return if (bottom == 0.0) null else top / bottom
        }
        return clean.replace(',', '.').toDoubleOrNull()
    }

    private val vulgar = mapOf('½' to 0.5, '¼' to 0.25, '¾' to 0.75, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3, '⅛' to 0.125)

    /** A number, a decimal, a fraction or a vulgar fraction, possibly glued to its unit ("200ml"). */
    private val numberStart = Regex("""^(\d+\s*/\s*\d+|\d+[.,]\d+|\d*[½¼¾⅓⅔⅛]|\d+)(?=\s|[^\d\s/.,]|$)""")
    private val wordStart = Regex(
        """^(un|une|deux|trois|quatre|cinq|six|sept|huit|neuf|dix|douze|demi|one|two|three|four|five|six|seven|eight|nine|ten|twelve|half|a|an)\s+""",
    )
    private val unitWord = Regex("""^\s*[\p{L}][\p{L}.\-’'à]*""")
    private val leadingLink = Regex("""^(?:de |d'|d’|du |des |of )""", RegexOption.IGNORE_CASE)

    private val numberWords = mapOf(
        "un" to 1.0, "une" to 1.0, "deux" to 2.0, "trois" to 3.0, "quatre" to 4.0, "cinq" to 5.0, "six" to 6.0,
        "sept" to 7.0, "huit" to 8.0, "neuf" to 9.0, "dix" to 10.0, "douze" to 12.0, "demi" to 0.5,
        "one" to 1.0, "two" to 2.0, "three" to 3.0, "four" to 4.0, "five" to 5.0, "seven" to 7.0,
        "eight" to 8.0, "nine" to 9.0, "ten" to 10.0, "twelve" to 12.0, "half" to 0.5, "a" to 1.0, "an" to 1.0,
    )

    /** Units that measure rather than name what is counted: the food comes after them. */
    private val measures = setOf(
        "g", "gr", "gramme", "kg", "kilo", "mg", "ml", "cl", "dl", "l", "litre", "cs", "cc", "cas", "cac",
        "cuillere", "cuill", "cuil", "c-a-s", "c-a-c", "tasse", "verre", "bol", "pincee", "sachet", "boite", "brin",
        "branche", "gousse", "tranche", "feuille", "botte", "bouquet", "poignee", "morceau", "pot", "filet", "trait",
        "cup", "tbsp", "tsp", "tablespoon", "teaspoon", "oz", "ounce", "lb", "pound", "pinch", "clove", "slice",
        "can", "bunch", "handful", "piece", "sprig", "stick", "package", "dash",
    )

    /** Short units written as symbols, never put in the plural. */
    private val abbreviations = setOf("g", "gr", "kg", "mg", "ml", "cl", "dl", "l", "oz", "lb", "lbs", "cs", "cc", "tbsp", "tsp")

    private const val MAX_COUNT = 60.0
}
