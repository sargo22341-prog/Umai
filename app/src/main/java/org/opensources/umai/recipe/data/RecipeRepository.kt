package org.opensources.umai.recipe.data

import org.opensources.umai.core.model.Paged
import org.opensources.umai.core.model.Recipe
import org.opensources.umai.core.model.RecipeSummary
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.network.apiCall
import org.opensources.umai.core.network.api.MealieApi
import org.opensources.umai.search.domain.RecipeFilters
import org.opensources.umai.search.domain.RecipeSort
import org.opensources.umai.search.domain.buildQueryFilter

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
) {

    suspend fun search(
        query: String?,
        filters: RecipeFilters,
        page: Int,
        perPage: Int = DEFAULT_PAGE_SIZE,
        paginationSeed: String? = null,
    ): ApiResult<Paged<RecipeSummary>> {
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)

        val favorites = if (filters.favoritesOnly) {
            when (val result = apiCall { api.favorites() }) {
                is ApiResult.Failure -> return result
                is ApiResult.Success -> result.value.ratings.map { it.recipeId }
            }
        } else {
            emptyList()
        }

        return apiCall {
            api.recipes(
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
                orderBy = filters.sort.orderBy,
                orderDirection = filters.sort.direction,
                queryFilter = filters.buildQueryFilter(favorites),
                paginationSeed = paginationSeed.takeIf { filters.sort == RecipeSort.RANDOM },
            ).toPaged { it.toDomain() }
        }
    }

    suspend fun latest(page: Int, perPage: Int = DEFAULT_PAGE_SIZE): ApiResult<Paged<RecipeSummary>> =
        search(query = null, filters = RecipeFilters.None, page = page, perPage = perPage)

    suspend fun recipe(slug: String): ApiResult<Recipe> {
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        return when (val result = apiCall { api.recipe(slug) }) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> result.value.toDomain()
                ?.let { ApiResult.Success(it) }
                ?: ApiResult.Failure(NetworkError.InvalidResponse)
        }
    }

    suspend fun favoriteIds(): ApiResult<Set<String>> {
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        return apiCall { api.favorites().ratings.map { it.recipeId }.toSet() }
    }

    suspend fun setFavorite(slug: String, favorite: Boolean): ApiResult<Unit> {
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        val userId = currentUserId() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        return apiCall {
            if (favorite) api.addFavorite(userId, slug) else api.removeFavorite(userId, slug)
        }
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 24
    }
}
