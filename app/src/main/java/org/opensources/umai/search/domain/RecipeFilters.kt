package org.opensources.umai.search.domain

import org.opensources.umai.core.format.ApiDates
import org.opensources.umai.recipe.domain.CalorieFilter
import org.opensources.umai.recipe.domain.CalorieTag
import org.opensources.umai.recipe.domain.queryClause
import java.time.LocalDate

/**
 * The columns a search can be ordered by, each backed by a real Mealie
 * `orderBy` value. [descendingByDefault] is the direction a first tap picks:
 * newest, best rated or most recently cooked first, but names from A to Z.
 */
enum class SortField(val orderBy: String, val descendingByDefault: Boolean) {
    CREATED("createdAt", descendingByDefault = true),
    NAME("name", descendingByDefault = false),
    RATING("rating", descendingByDefault = true),
    LAST_MADE("lastMade", descendingByDefault = true),

    /** Has no direction, and Mealie also needs a `paginationSeed` for it. */
    RANDOM("random", descendingByDefault = true),
    ;

    val hasDirection: Boolean get() = this != RANDOM
}

/**
 * How the results are ordered. It is kept apart from [RecipeFilters]: it
 * changes the order of the results, never which recipes match.
 */
data class RecipeSort(val column: SortField, val descending: Boolean) {

    val orderBy: String get() = column.orderBy
    val direction: String get() = if (descending) "desc" else "asc"
    val isRandom: Boolean get() = column == SortField.RANDOM

    /**
     * Picking the current column again reverses it; picking another one starts
     * from that column's natural direction.
     */
    fun select(target: SortField): RecipeSort = when {
        target != column -> RecipeSort(target, target.descendingByDefault)
        target.hasDirection -> copy(descending = !descending)
        else -> this
    }

    companion object {
        /** Newest recipes first, as Mealie itself lists them. */
        val Default = RecipeSort(SortField.CREATED, descending = true)
    }
}

/** How recently a recipe was added to the instance. */
enum class AddedWithin(val days: Long?) {
    ANY(null),
    WEEK(7),
    MONTH(30),
    YEAR(365),
}

/**
 * The set of filters Umai exposes. Every one of them maps to something the
 * Mealie API really supports: either a documented query parameter of
 * `GET /api/recipes`, or an expression of its `queryFilter` mini-language.
 *
 * Preparation, cooking and total times are deliberately absent: Mealie stores
 * them as free text ("15 minutes", "PT1H"), so they cannot be compared
 * numerically server-side. Calories are free text too, and are filtered through
 * the `calorie-<value>` tags recipes carry instead. Servings are absent too: the reader scales them on
 * the recipe page, so the count a recipe was written for says little.
 */
data class RecipeFilters(
    val categoryIds: Set<String> = emptySet(),
    val requireAllCategories: Boolean = false,
    val tagIds: Set<String> = emptySet(),
    val requireAllTags: Boolean = false,
    val toolIds: Set<String> = emptySet(),
    val requireAllTools: Boolean = false,
    val foodIds: Set<String> = emptySet(),
    val requireAllFoods: Boolean = false,
    val minRating: Int? = null,
    val favoritesOnly: Boolean = false,
    val addedWithin: AddedWithin = AddedWithin.ANY,
    val calories: CalorieFilter = CalorieFilter.ANY,
) {
    val activeCount: Int
        get() = listOf(
            categoryIds.isNotEmpty(),
            tagIds.isNotEmpty(),
            toolIds.isNotEmpty(),
            foodIds.isNotEmpty(),
            minRating != null,
            favoritesOnly,
            addedWithin != AddedWithin.ANY,
            calories != CalorieFilter.ANY,
        ).count { it }

    val isEmpty: Boolean get() = activeCount == 0

    companion object {
        val None = RecipeFilters()
    }
}

/**
 * Builds the `queryFilter` expression for the filters that have no dedicated
 * query parameter. Returns `null` when nothing needs to be expressed.
 *
 * @param favoriteRecipeIds ids from `GET /api/users/self/favorites`; an empty
 * list combined with [RecipeFilters.favoritesOnly] yields an expression that
 * matches nothing, which is the correct result.
 * @param calorieTags the calorie tags of the instance, which the calorie range
 * is expressed with.
 */
fun RecipeFilters.buildQueryFilter(
    favoriteRecipeIds: List<String>,
    calorieTags: List<CalorieTag> = emptyList(),
    today: LocalDate = LocalDate.now(),
): String? {
    val clauses = mutableListOf<String>()

    minRating?.let { clauses += "rating >= $it" }

    addedWithin.days?.let { days ->
        clauses += """createdAt > "${ApiDates.format(today.minusDays(days))}""""
    }

    if (favoritesOnly) {
        clauses += if (favoriteRecipeIds.isEmpty()) {
            // No favourite yet: ask for an id that cannot exist rather than
            // silently dropping the filter and showing every recipe.
            """id IN ["00000000-0000-0000-0000-000000000000"]"""
        } else {
            favoriteRecipeIds.joinToString(
                separator = ",",
                prefix = "id IN [",
                postfix = "]",
            ) { "\"$it\"" }
        }
    }

    calories.queryClause(calorieTags)?.let { clauses += it }

    return clauses.takeIf { it.isNotEmpty() }?.joinToString(" AND ")
}
