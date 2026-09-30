package org.opensources.umai.organizer.data

import org.opensources.umai.core.model.Food
import org.opensources.umai.core.model.Organizer
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.api.MealieApi
import org.opensources.umai.core.network.call
import org.opensources.umai.core.network.fetchAllPages
import org.opensources.umai.core.network.toPaged
import org.opensources.umai.core.session.InstanceCache

/**
 * Sources for the search filters: categories, tags, tools and foods.
 *
 * Instances can hold hundreds of tags, so every collection is fetched page by
 * page and kept for as long as the app stays signed in to the same place.
 */
class OrganizerRepository(
    private val apiProvider: () -> MealieApi?,
    instanceKey: () -> String?,
) {

    private val categoryCache = InstanceCache<List<Organizer>>(instanceKey)
    private val tagCache = InstanceCache<List<Organizer>>(instanceKey)
    private val toolCache = InstanceCache<List<Organizer>>(instanceKey)

    suspend fun categories(forceRefresh: Boolean = false): ApiResult<List<Organizer>> =
        categoryCache.get(forceRefresh) {
            apiProvider.call { fetchAllPages(MAX_PAGES) { page -> categories(page = page).toPaged { it.toDomain() } }.items }
        }

    suspend fun tags(forceRefresh: Boolean = false): ApiResult<List<Organizer>> =
        tagCache.get(forceRefresh) {
            apiProvider.call { fetchAllPages(MAX_PAGES) { page -> tags(page = page).toPaged { it.toDomain() } }.items }
        }

    suspend fun tools(forceRefresh: Boolean = false): ApiResult<List<Organizer>> =
        toolCache.get(forceRefresh) {
            apiProvider.call { fetchAllPages(MAX_PAGES) { page -> tools(page = page).toPaged { it.toDomain() } }.items }
        }

    /** Foods are searched on demand: an instance can hold thousands of them. */
    suspend fun searchFoods(query: String): ApiResult<List<Food>> = apiProvider.call {
        foods(search = query.trim().takeIf { it.isNotEmpty() }, perPage = 40).toPaged { it.toDomain() }.items
    }

    private companion object {
        /**
         * Hard stop so a very large instance cannot stall the filter sheet: past
         * 2 000 organizers of a kind, the others are not offered as filters.
         */
        const val MAX_PAGES = 20
    }
}
