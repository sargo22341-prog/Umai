package org.opensources.umai.recipe.data

import org.opensources.umai.core.model.MAX_RATING_STARS
import org.opensources.umai.core.model.Paged
import org.opensources.umai.core.model.Recipe
import org.opensources.umai.core.model.RecipeSummary
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.network.api.MealieApi
import org.opensources.umai.core.network.call
import org.opensources.umai.core.network.flatMap
import org.opensources.umai.core.network.map
import org.opensources.umai.core.network.orInvalid
import org.opensources.umai.core.network.toPaged
import org.opensources.umai.core.network.valueOr
import org.opensources.umai.core.network.dto.RecipeLastMadeDto
import org.opensources.umai.core.network.dto.TimelineEventInDto
import org.opensources.umai.core.network.dto.UserRatingUpdateDto
import org.opensources.umai.recipe.domain.CalorieFilter
import org.opensources.umai.recipe.domain.CalorieTag
import org.opensources.umai.recipe.domain.OwnRating
import org.opensources.umai.search.domain.RecipeFilters
import org.opensources.umai.search.domain.RecipeSort
import org.opensources.umai.search.domain.SortField
import org.opensources.umai.search.domain.buildQueryFilter
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Recipe access on top of the Mealie API. Owns the translation from Umai's
 * filter model to Mealie query parameters, so ViewModels never build URLs.
 *
 * It depends on a provider rather than on the session itself: the instance can
 * change at runtime, and tests can point it at a local server.
 */
class RecipeRepository(
    private val apiProvider: () -> MealieApi?,
    private val currentUserId: () -> String? = { null },
    private val calorieTags: suspend () -> ApiResult<List<CalorieTag>> = { ApiResult.Success(emptyList()) },
) {

    suspend fun search(
        query: String?,
        filters: RecipeFilters,
        page: Int,
        sort: RecipeSort = RecipeSort.Default,
        perPage: Int = DEFAULT_PAGE_SIZE,
        paginationSeed: String? = null,
    ): ApiResult<Paged<RecipeSummary>> {
        val favorites = if (filters.favoritesOnly) {
            apiProvider.call { favorites().ratings.map { it.recipeId } }.valueOr { return it }
        } else {
            emptyList()
        }

        val calories = if (filters.calories != CalorieFilter.ANY) calorieTags().valueOr { return it } else emptyList()

        return apiProvider.call {
            recipes(
                page = page,
                perPage = perPage,
                search = query?.trim()?.takeIf { it.isNotEmpty() },
                categories = filters.categoryIds.toList().takeIf { it.isNotEmpty() },
                tags = filters.tagIds.toList().takeIf { it.isNotEmpty() },
                tools = filters.toolIds.toList().takeIf { it.isNotEmpty() },
                foods = filters.foodIds.toList().takeIf { it.isNotEmpty() },
                requireAllCategories = filters.requireAllCategories.takeIf { filters.categoryIds.size > 1 },
                requireAllTags = filters.requireAllTags.takeIf { filters.tagIds.size > 1 },
                requireAllTools = filters.requireAllTools.takeIf { filters.toolIds.size > 1 },
                requireAllFoods = filters.requireAllFoods.takeIf { filters.foodIds.size > 1 },
                orderBy = sort.orderBy,
                // A recipe never rated or never cooked has no value to sort on:
                // it comes after the others whichever the direction, instead
                // of filling the first pages of a descending order.
                orderByNullPosition = NULLS_LAST.takeIf { !sort.isRandom },
                orderDirection = sort.direction,
                queryFilter = filters.buildQueryFilter(favorites, calories),
                paginationSeed = paginationSeed.takeIf { sort.isRandom },
            ).toPaged { it.toDomain() }
        }
    }

    suspend fun latest(page: Int, perPage: Int = DEFAULT_PAGE_SIZE): ApiResult<Paged<RecipeSummary>> =
        search(query = null, filters = RecipeFilters.None, page = page, perPage = perPage)

    /**
     * A few recipes drawn at random by Mealie. The same [seed] gives the same
     * draw, so a list shown on screen does not reshuffle until a new seed is
     * picked.
     */
    suspend fun discover(count: Int, seed: String): ApiResult<List<RecipeSummary>> =
        search(
            query = null,
            filters = RecipeFilters.None,
            page = 1,
            sort = RecipeSort(SortField.RANDOM, descending = true),
            perPage = count,
            paginationSeed = seed,
        ).map { it.items }

    /**
     * The recipes of [slugs], in that order, read in one request; a slug the
     * instance no longer has is left out.
     */
    suspend fun bySlugs(slugs: List<String>): ApiResult<List<RecipeSummary>> {
        // Slugs are made of letters, digits and dashes: anything else could not be quoted in the filter.
        val wanted = slugs.filter { it.isNotBlank() && '"' !in it && '\\' !in it }.distinct()
        if (wanted.isEmpty()) return ApiResult.Success(emptyList())
        val filter = wanted.joinToString(separator = ",", prefix = "slug IN [", postfix = "]") { "\"$it\"" }
        return apiProvider.call { recipes(perPage = wanted.size, queryFilter = filter).toPaged { it.toDomain() }.items }
            .map { found ->
                val bySlug = found.associateBy { it.slug }
                wanted.mapNotNull(bySlug::get)
            }
    }

    suspend fun recipe(slug: String): ApiResult<Recipe> = apiProvider.call { recipe(slug) }.map { it.toDomain() }.orInvalid()

    suspend fun setFavorite(slug: String, favorite: Boolean): ApiResult<Unit> {
        val userId = currentUserId() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        return apiProvider.call { if (favorite) addFavorite(userId, slug) else removeFavorite(userId, slug) }
    }

    /**
     * The stars the signed-in user gave this recipe and whether it is one of
     * their favourites, in one request. Mealie answers 404 for a recipe the
     * user has no entry for: neither rated nor favourite.
     */
    suspend fun ownRating(recipeId: String): ApiResult<OwnRating> =
        when (val result = apiProvider.call { ownRating(recipeId) }) {
            is ApiResult.Success -> ApiResult.Success(OwnRating(result.value.rating?.toStars(), result.value.isFavorite))
            is ApiResult.Failure -> if (result.error == NetworkError.NotFound) ApiResult.Success(OwnRating.None) else result
        }

    /**
     * Rates the recipe for the signed-in user. Mealie stores the rating and the
     * favourite flag on the same row, so the current flag is sent along with it
     * rather than left for the server to guess.
     */
    suspend fun setRating(slug: String, stars: Int, isFavorite: Boolean): ApiResult<Unit> {
        val userId = currentUserId() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        val rating = stars.coerceIn(1, MAX_RATING_STARS).toDouble()
        return apiProvider.call { setRating(userId, slug, UserRatingUpdateDto(rating, isFavorite)) }
    }

    /**
     * Records that the recipe was cooked: an entry in its timeline, as Mealie's
     * own "I made this" writes, and the date it was last made.
     */
    suspend fun markCooked(recipe: Recipe, subject: String, at: Instant = Instant.now()): ApiResult<Unit> {
        val timestamp = at.toString()
        val event = TimelineEventInDto(recipeId = recipe.id, subject = subject, eventType = TIMELINE_INFO, timestamp = timestamp)
        return apiProvider.call { createTimelineEvent(event) }
            .flatMap { apiProvider.call { updateLastMade(recipe.slug, RecipeLastMadeDto(timestamp)) } }
    }

    private fun Double.toStars(): Int? = roundToInt().takeIf { it in 1..MAX_RATING_STARS }

    companion object {
        const val DEFAULT_PAGE_SIZE = 24

        /** An `OrderByNullPosition` value of the OpenAPI schema. */
        private const val NULLS_LAST = "last"

        /** One of the `TimelineEventType` values of the OpenAPI schema. */
        private const val TIMELINE_INFO = "info"
    }
}
