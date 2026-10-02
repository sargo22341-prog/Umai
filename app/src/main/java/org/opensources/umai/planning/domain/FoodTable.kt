package org.opensources.umai.planning.domain

/**
 * A food of the Ciqual table, with its values for 100 g. A drink's are taken
 * for 100 ml: the water it mostly is weighs a gram a millilitre.
 */
data class ReferenceFood(
    val code: Int,
    val nameFr: String,
    val nameEn: String,
    val unit: FoodUnit,
    val per100: NutritionFacts,
) {
    fun name(language: String): String = if (language == "fr") nameFr else nameEn
}

/** The words a food is usually called by, "pomme" or "apple", and how much one of it is: [serving] of [unit]. */
data class UsualFood(
    val food: ReferenceFood,
    val namesFr: List<String>,
    val namesEn: List<String>,
    val serving: Double,
    val unit: FoodUnit,
) {
    init {
        require(namesFr.isNotEmpty() && namesEn.isNotEmpty() && serving > 0) { "Usual food ${food.code} is incomplete" }
    }

    fun name(language: String): String = if (language == "fr") namesFr.first() else namesEn.first()
}

/** A food found for what was typed: by a usual name, which tells how much one of it is, or by its name in the table. */
data class FoodChoice(val food: ReferenceFood, val usual: UsualFood?) {

    val unit: FoodUnit get() = usual?.unit ?: food.unit

    /** How much one of it is, in [unit]; `null` for a food only known by its name in the table. */
    val serving: Double? get() = usual?.serving

    fun name(language: String): String =
        usual?.name(language)?.replaceFirstChar { it.titlecase() } ?: food.name(language)
}

/**
 * The basic foods umai knows without the network: the Ciqual table of ANSES
 * (`assets/ciqual.tsv`, built by `scripts/ciqual-table.py`), and the usual
 * names of the commonest ones with their portion (`assets/basic_foods.tsv`).
 * Both languages are searched, whatever the language of the app.
 */
class FoodTable(foods: List<ReferenceFood>, usualFoods: List<UsualFood>) {

    private class Entry(val choice: FoodChoice, val names: List<List<String>>, val bonus: Int)

    private val entries: List<Entry> =
        usualFoods.map { usual ->
            Entry(FoodChoice(usual.food, usual), (usual.namesFr + usual.namesEn).map(FoodWords::of), USUAL_BONUS)
        } + foods.map { food ->
            val names = listOf(food.nameFr, food.nameEn).map(FoodWords::of)
            Entry(FoodChoice(food, null), names, if (names.flatten().any { it in averages }) AVERAGE_BONUS else 0)
        }

    val size: Int get() = entries.size

    /**
     * The foods whose name holds every word of [text], the closest first:
     * a usual name before a name of the table, an exact name before a longer
     * one. The last word may be cut short, as it is while being typed, but a
     * whole word comes first: "lait" is milk before it is "laitue".
     */
    fun search(text: String, limit: Int = SUGGESTIONS): List<FoodChoice> {
        val query = FoodWords.of(text)
        if (query.isEmpty()) return emptyList()
        return entries
            .mapNotNull { entry ->
                val best = entry.names.mapNotNull { score(query, it) }.maxOrNull() ?: return@mapNotNull null
                entry to best + entry.bonus
            }
            .sortedByDescending { it.second }
            .map { it.first.choice }
            .distinct()
            .take(limit)
    }

    /** How close [name] is to [query]; `null` when it lacks one of its words. */
    private fun score(query: List<String>, name: List<String>): Int? {
        var score = BASE
        var cut = false
        query.forEachIndexed { index, word ->
            val whole = word in name
            val started = index == query.lastIndex && word.length >= MIN_PREFIX && name.any { it.startsWith(word) }
            if (!whole && !started) return null
            cut = cut || !whole
        }
        if (cut) score -= PREFIX_PENALTY
        if (name.firstOrNull() == query.first()) score += FIRST_WORD_BONUS
        if (!cut && name.size == query.size) score += EXACT_BONUS
        return score - (name.size - query.size).coerceAtLeast(0) * EXTRA_WORD_PENALTY
    }

    companion object {
        const val SUGGESTIONS = 6

        private const val BASE = 100
        private const val USUAL_BONUS = 200
        private const val EXACT_BONUS = 50
        private const val FIRST_WORD_BONUS = 10
        private const val AVERAGE_BONUS = 3
        /** More than a usual name is worth over the table: a cut word only counts when no whole one matches. */
        private const val PREFIX_PENALTY = 250
        private const val EXTRA_WORD_PENALTY = 2
        private const val MIN_PREFIX = 2

        /** Ciqual names its average of several products "aliment moyen": the one to give when nothing more is said. */
        private val averages = setOf("moyen", "average")

        private const val CIQUAL_COLUMNS = 12
        private const val USUAL_COLUMNS = 5
        private const val NAME_SEPARATOR = '|'

        /**
         * Reads the two tables, header line first. A line that does not have
         * its columns is a bug of the files shipped with the app.
         */
        fun read(ciqualLines: List<String>, usualLines: List<String>): FoodTable {
            val foods = ciqualLines.drop(1).filter { it.isNotBlank() }.map(::referenceFood)
            val byCode = foods.associateBy { it.code }
            val usual = usualLines.drop(1).filter { it.isNotBlank() }.map { usualFood(it, byCode) }
            return FoodTable(foods, usual)
        }

        /** `code, name_fr, name_en, drink, kcal, fat, saturated, carbohydrates, sugars, fiber, protein, salt`. */
        private fun referenceFood(line: String): ReferenceFood {
            val columns = line.split('\t')
            require(columns.size == CIQUAL_COLUMNS) { "Malformed Ciqual line: $line" }
            // The nutrient columns follow the order of Nutrient.
            val values = Nutrient.entries.zip(columns.drop(4))
                .mapNotNull { (nutrient, text) -> text.toDoubleOrNull()?.let { nutrient to it } }
                .toMap()
            return ReferenceFood(
                code = columns[0].toInt(),
                nameFr = columns[1],
                nameEn = columns[2],
                unit = if (columns[3] == "1") FoodUnit.MILLILITRE else FoodUnit.GRAM,
                per100 = NutritionFacts(values),
            )
        }

        /** `code, serving, unit, names_fr, names_en`, the names separated by `|`. */
        private fun usualFood(line: String, foods: Map<Int, ReferenceFood>): UsualFood {
            val columns = line.split('\t')
            require(columns.size == USUAL_COLUMNS) { "Malformed usual food line: $line" }
            val food = requireNotNull(foods[columns[0].toInt()]) { "Usual food of an unknown code: $line" }
            return UsualFood(
                food = food,
                namesFr = columns[3].split(NAME_SEPARATOR).filter { it.isNotBlank() },
                namesEn = columns[4].split(NAME_SEPARATOR).filter { it.isNotBlank() },
                serving = columns[1].toDouble(),
                unit = FoodUnit.entries.first { it.symbol == columns[2] },
            )
        }
    }
}
