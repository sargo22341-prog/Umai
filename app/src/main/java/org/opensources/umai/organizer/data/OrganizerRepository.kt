package org.opensources.umai.organizer.data

import org.opensources.umai.core.model.Food
import org.opensources.umai.core.model.Organizer
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.network.apiCall
import org.opensources.umai.core.network.api.MealieApi
import org.opensources.umai.recipe.data.toDomain
import org.opensources.umai.recipe.data.toPaged

/**
 * Sources for the search filters: categories, tags, tools and foods.
 *
 * Instances can hold hundreds of tags, so every collection is fetched page by
 * page and the results are memoised for the lifetime of the process.
 */
class OrganizerRepository(private val apiProvider: () -> MealieApi?) {

    private var cachedCategories: List<Organizer>? = null
    private var cachedTags: List<Organizer>? = null
    private var cachedTools: List<Organizer>? = null

    suspend fun categories(forceRefresh: Boolean = false): ApiResult<List<Organizer>> {
        cachedCategories?.takeUnless { forceRefresh }?.let { return ApiResult.Success(it) }
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        return fetchAll { page -> api.categories(page = page).toPaged { dto -> dto.toDomain() } }
            .also { if (it is ApiResult.Success) cachedCategories = it.value }
    }

    suspend fun tags(forceRefresh: Boolean = false): ApiResult<List<Organizer>> {
        cachedTags?.takeUnless { forceRefresh }?.let { return ApiResult.Success(it) }
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        return fetchAll { page -> api.tags(page = page).toPaged { dto -> dto.toDomain() } }
            .also { if (it is ApiResult.Success) cachedTags = it.value }
    }

    suspend fun tools(forceRefresh: Boolean = false): ApiResult<List<Organizer>> {
        cachedTools?.takeUnless { forceRefresh }?.let { return ApiResult.Success(it) }
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        return fetchAll { page -> api.tools(page = page).toPaged { dto -> dto.toDomain() } }
            .also { if (it is ApiResult.Success) cachedTools = it.value }
    }

    /** Foods are searched on demand: an instance can hold thousands of them. */
    suspend fun searchFoods(query: String): ApiResult<List<Food>> {
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        return apiCall {
            api.foods(search = query.trim().takeIf { it.isNotEmpty() }, perPage = 40)
                .toPaged { it.toDomain() }
                .items
        }
    }

    fun invalidate() {
        cachedCategories = null
        cachedTags = null
        cachedTools = null
    }

    private suspend fun <T> fetchAll(
        loadPage: suspend (Int) -> org.opensources.umai.core.model.Paged<T>,
    ): ApiResult<List<T>> = apiCall {
        val all = mutableListOf<T>()
        var page = 1
        while (page <= MAX_PAGES) {
            val result = loadPage(page)
            all += result.items
            if (!result.hasNext) break
            page++
        }
        all
    }

    private companion object {
        /** Hard stop so a very large instance cannot stall the filter sheet. */
        const val MAX_PAGES = 20
    }
}
