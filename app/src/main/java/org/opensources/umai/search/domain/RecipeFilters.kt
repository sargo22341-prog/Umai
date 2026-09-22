package org.opensources.umai.search.domain

import org.opensources.umai.core.format.ApiDates
import java.time.LocalDate

/**
 * Ordering options backed by real Mealie `orderBy` columns.
 */
enum class RecipeSort(val orderBy: String, val direction: String) {
    RECENT("createdAt", "desc"),
    OLDEST("createdAt", "asc"),
    NAME_ASC("name", "asc"),
    NAME_DESC("name", "desc"),
    RATING("rating", "desc"),
    LAST_MADE("lastMade", "desc"),

    /** Mealie also needs a `paginationSeed` for this one. */
    RANDOM("random", "desc"),
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
 * numerically server-side.
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
    val minServings: Int? = null,
    val maxServings: Int? = null,
    val addedWithin: AddedWithin = AddedWithin.ANY,
    val sort: RecipeSort = RecipeSort.RECENT,
) {
    val activeCount: Int
        get() = listOf(
            categoryIds.isNotEmpty(),
            tagIds.isNotEmpty(),
            toolIds.isNotEmpty(),
            foodIds.isNotEmpty(),
            minRating != null,
            favoritesOnly,
            minServings != null,
            maxServings != null,
            addedWithin != AddedWithin.ANY,
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
 */
fun RecipeFilters.buildQueryFilter(
    favoriteRecipeIds: List<String>,
    today: LocalDate = LocalDate.now(),
): String? {
    val clauses = mutableListOf<String>()

    minRating?.let { clauses += "rating >= $it" }
    minServings?.let { clauses += "recipeServings >= $it" }
    maxServings?.let { clauses += "recipeServings <= $it" }

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

    return clauses.takeIf { it.isNotEmpty() }?.joinToString(" AND ")
}
