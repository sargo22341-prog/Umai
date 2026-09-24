package org.opensources.umai.recipe.domain

import org.opensources.umai.core.model.Organizer

/**
 * Calories are free text in Mealie (`"695 kcal"`), so they cannot be compared
 * on the server. A recipe with calories therefore also carries a tag named
 * `calorie-<value>`, and filtering by calories becomes filtering by tags.
 */
object CalorieTags {

    const val PREFIX = "calorie-"

    // A thousands separator may be a space, a no-break space or a narrow one.
    private val number = Regex("""\d{1,3}(?:[   ]\d{3})+|\d+(?:[.,]\d+)?""")

    /** The whole number of calories written in [nutrition], `null` when there is none. */
    fun parse(nutrition: String?): Int? {
        val match = nutrition?.let(number::find) ?: return null
        val value = match.value
            .filterNot { it == ' ' || it == ' ' || it == ' ' }
            .replace(',', '.')
            .toDoubleOrNull()
            ?: return null
        return Math.round(value).toInt().takeIf { it > 0 }
    }

    fun tagName(calories: Int): String = "$PREFIX$calories"

    /** The calories a tag stands for, `null` when it is not a calorie tag. */
    fun valueOf(slug: String): Int? =
        slug.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.toIntOrNull()?.takeIf { it > 0 }

    fun isCalorieTag(slug: String): Boolean = valueOf(slug) != null
}

/**
 * The tags without the calorie tags: those only serve the calorie filter, and
 * are neither shown on a recipe nor offered as a tag to search on.
 */
fun List<Organizer>.withoutCalorieTags(): List<Organizer> = filterNot { CalorieTags.isCalorieTag(it.slug) }

/** A calorie tag that exists on the instance. */
data class CalorieTag(val id: String, val slug: String, val calories: Int)

/** The calorie ranges the search offers. */
enum class CalorieFilter(val maxCalories: Int?) {
    ANY(null),
    UP_TO_300(300),
    UP_TO_500(500),
    UP_TO_700(700),

    /** Recipes Mealie holds no calories for. */
    UNKNOWN(null),
}

/**
 * The `queryFilter` clause for [filter], built from the calorie tags that
 * exist: `null` when nothing needs filtering.
 *
 * A range with no matching tag asks for an id that cannot exist, so the result
 * is empty rather than silently unfiltered.
 */
fun CalorieFilter.queryClause(tags: List<CalorieTag>): String? {
    val slugs = when (this) {
        CalorieFilter.ANY -> return null
        CalorieFilter.UNKNOWN -> {
            val all = tags.map { it.slug }.distinct()
            // No calorie tag at all: every recipe lacks calories.
            return all.takeIf { it.isNotEmpty() }?.let { "tags.slug NOT IN ${it.asList()}" }
        }
        else -> tags.filter { it.calories <= checkNotNull(maxCalories) }.map { it.slug }.distinct()
    }
    return if (slugs.isEmpty()) NO_MATCH else "tags.slug IN ${slugs.asList()}"
}

// The separator matters: without it `IN [...]` is not understood by Mealie.
private fun List<String>.asList(): String = joinToString(separator = ",", prefix = "[", postfix = "]") { "\"$it\"" }

private const val NO_MATCH = """id IN ["00000000-0000-0000-0000-000000000000"]"""
