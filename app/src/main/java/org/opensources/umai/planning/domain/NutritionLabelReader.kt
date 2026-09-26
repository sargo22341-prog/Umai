package org.opensources.umai.planning.domain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.opensources.umai.llm.domain.LanguageModel
import org.opensources.umai.llm.domain.LlmFailure
import org.opensources.umai.llm.domain.LlmMedia
import org.opensources.umai.llm.domain.LlmOutcome
import org.opensources.umai.llm.domain.LlmRequest
import org.opensources.umai.llm.domain.ModelAnswer

/**
 * What a nutrition label says: its values for 100 of [unit], and [portion],
 * the quantity of its other column when it prints one, such as 330 for a
 * 330 ml can.
 */
data class LabelReading(val unit: FoodUnit, val per100: NutritionFacts, val portion: Double?)

sealed interface LabelOutcome {
    data class Read(val reading: LabelReading) : LabelOutcome

    /** The model answered, but found no value: the picture shows no nutrition table it can read. */
    data object NothingFound : LabelOutcome

    /** The model could not run, or not on a picture. */
    data class Failed(val reason: LlmFailure) : LabelOutcome
}

/**
 * Reads the nutrition table on the photo of a package with the vision part of
 * the on-device model: labels are photographed at an angle, printed on
 * colours and in several languages, which the model reads where a character
 * recogniser would not. Nothing leaves the phone.
 *
 * The model only copies the table, heading and rows, as printed: asked to
 * sort the values into nutrients itself, it moved them from one row to the
 * next. Which row is which nutrient, and which column is for 100 g, is then
 * worked out here ([LabelTable]).
 */
class NutritionLabelReader(private val model: LanguageModel) {

    suspend fun isReady(): Boolean = model.isReady()

    suspend fun read(jpeg: ByteArray): LabelOutcome =
        when (val outcome = model.generate(REQUEST.copy(media = LlmMedia.Picture(jpeg)))) {
            is LlmOutcome.Failure -> LabelOutcome.Failed(outcome.reason)
            is LlmOutcome.Success -> parse(outcome.text)?.let { LabelOutcome.Read(it) } ?: LabelOutcome.NothingFound
        }

    internal companion object {

        /** About twenty tokens a row, for the longest tables. */
        private const val MAX_ANSWER_TOKENS = 400

        val REQUEST = LlmRequest(
            system = """
                You copy the nutrition facts table on the photo of a food or drink package, exactly as printed.
                - columns: the heading printed above each column of values, such as "100 g", "100 ml" or "330 ml"; not the names of the rows. Leave out a column of percentages of reference intakes (%).
                - rows: one row per line of the table, in order: its name as printed, and its values, one per column, with their units, such as "10.6 g" or "180 kJ / 42 kcal". An energy line printed on two lines is one row.
                Copy only what is printed: never add a row, a value or a unit.
            """.trimIndent(),
            user = "Copy the nutrition table of this package.",
            jsonSchema = """
                {
                  "type": "object",
                  "properties": {
                    "columns": {"type": "array", "maxItems": 3, "items": {"type": "string", "maxLength": 30}},
                    "rows": {
                      "type": "array", "maxItems": 16,
                      "items": {
                        "type": "object",
                        "properties": {
                          "name": {"type": "string", "maxLength": 80},
                          "values": {"type": "array", "maxItems": 3, "items": {"type": "string", "maxLength": 30}}
                        },
                        "required": ["name", "values"],
                        "additionalProperties": false
                      }
                    }
                  },
                  "required": ["columns", "rows"],
                  "additionalProperties": false
                }
            """.trimIndent(),
            maxTokens = MAX_ANSWER_TOKENS,
            temperature = 0f,
        )

        /** The reading in [answer], `null` when it holds no value at all. */
        fun parse(answer: String): LabelReading? {
            val root = ModelAnswer.objectOrNull(answer) ?: return null
            val columns = (root["columns"] as? JsonArray).orEmpty().mapNotNull { it.text() }
            val rows = (root["rows"] as? JsonArray).orEmpty().mapNotNull { element ->
                val row = element as? JsonObject ?: return@mapNotNull null
                LabelTable.Row(
                    name = (row["name"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                    values = (row["values"] as? JsonArray).orEmpty().map { it.text().orEmpty() },
                )
            }
            return LabelTable(columns, rows).reading()
        }

        private fun JsonElement.text(): String? = (this as? JsonPrimitive)?.contentOrNull
    }
}

/**
 * A nutrition table as printed: the headings of its columns and its rows.
 * Knows how labels are laid out, in the languages of the shops around:
 * French, English, Dutch, German.
 */
internal class LabelTable(private val columns: List<String>, private val rows: List<Row>) {

    class Row(val name: String, val values: List<String>)

    /** What the table gives, `null` when no value of a known nutrient is found. */
    fun reading(): LabelReading? {
        val per100 = columns.indexOfFirst { hundred.containsMatchIn(it) }
        val column = per100.coerceAtLeast(0)
        val values = mutableMapOf<Nutrient, Double>()
        rows.forEachIndexed { index, row ->
            val nutrient = nutrientOf(row.name) ?: return@forEachIndexed
            if (nutrient in values) return@forEachIndexed
            val value = if (nutrient == Nutrient.ENERGY) energy(index, column) else grams(row, column)
            value?.let { values[nutrient] = it }
        }
        if (values.isEmpty()) return null
        // Without a heading for 100 g or 100 ml, any heading tells what the product is counted in.
        val heading = columns.getOrNull(per100) ?: columns.joinToString(" ")
        val unit = if (millilitres.containsMatchIn(heading)) FoodUnit.MILLILITRE else FoodUnit.GRAM
        val portion = columns.filterIndexed { index, _ -> index != column }.firstNotNullOfOrNull(::quantityOf)
        return LabelReading(unit = unit, per100 = NutritionFacts(values), portion = portion?.takeIf { it != 100.0 })
    }

    private fun grams(row: Row, column: Int): Double? =
        row.values.getOrNull(column)?.let(NutritionNumbers::parse)

    /**
     * The kcal of the energy row: the label may print them on the line below,
     * which the model may copy as a row of its own, without a name.
     */
    private fun energy(index: Int, column: Int): Double? {
        val cells = rows.drop(index)
            .takeWhile { it === rows[index] || it.name.isBlank() || kcalOnly.matches(it.name.trim()) }
            .mapNotNull { it.values.getOrNull(column) }
        cells.firstNotNullOfOrNull { cell -> kcal.find(cell)?.let { number(it.groupValues[1]) } }?.let { return it }
        return cells.firstNotNullOfOrNull { cell -> kilojoules.find(cell)?.let { number(it.groupValues[1]) } }
            ?.div(KJ_PER_KCAL)
    }

    private fun nutrientOf(name: String): Nutrient? {
        val plain = name.lowercase().stripAccents()
        return keywords.entries.firstOrNull { (_, words) -> words.any { it in plain } }?.key
    }

    /** "330 ml", "Per portion (45 g)", "33 cl": the quantity in g or ml. */
    private fun quantityOf(heading: String): Double? {
        val match = quantity.find(heading) ?: return null
        val value = number(match.groupValues[1]) ?: return null
        return if (match.groupValues[2].lowercase() == "cl") value * 10 else value
    }

    private fun number(text: String): Double? =
        text.filterNot { it == ' ' || it == ' ' || it == ' ' }.replace(',', '.').toDoubleOrNull()

    private fun String.stripAccents(): String =
        java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD).replace(marks, "")

    private companion object {
        /** One kilocalorie is 4.184 kilojoules. */
        const val KJ_PER_KCAL = 4.184

        val marks = Regex("""\p{Mn}+""")
        val hundred = Regex("""100\s*(g|ml)\b""", RegexOption.IGNORE_CASE)
        val millilitres = Regex("""\d\s*(ml|cl|l)\b""", RegexOption.IGNORE_CASE)
        val quantity = Regex("""(\d+(?:[.,]\d+)?)\s*(g|ml|cl)\b""", RegexOption.IGNORE_CASE)

        // A thousands separator may be a space, a no-break space or a narrow one.
        private const val NUMBER = """(\d{1,3}(?:[   ]\d{3})+|\d+(?:[.,]\d+)?)"""
        val kcal = Regex("""$NUMBER\s*kcal""", RegexOption.IGNORE_CASE)
        val kilojoules = Regex("""$NUMBER\s*kj""", RegexOption.IGNORE_CASE)
        val kcalOnly = Regex("""kcal""", RegexOption.IGNORE_CASE)

        /**
         * The words naming each nutrient, lower case and without accents. The
         * order matters: "saturated fat" is fat, and "of which sugars" are
         * carbohydrates, so the narrower ones are looked for first.
         */
        val keywords: Map<Nutrient, List<String>> = linkedMapOf(
            Nutrient.SATURATED_FAT to listOf("satur", "verzadig", "gesattig"),
            Nutrient.SUGARS to listOf("sucre", "sugar", "suiker", "zucker"),
            Nutrient.FIBER to listOf("fibre", "fiber", "vezel", "ballaststoff"),
            Nutrient.FAT to listOf("grasse", "lipide", "fat", "vet", "fett"),
            Nutrient.CARBOHYDRATES to listOf("glucide", "carbohydrate", "koolhydra", "kohlenhydra"),
            Nutrient.PROTEIN to listOf("protein", "prote", "eiwit", "eiweis"),
            Nutrient.SALT to listOf("sel", "salt", "zout", "salz"),
            Nutrient.ENERGY to listOf("energ", "kcal", "calori"),
        )
    }
}
