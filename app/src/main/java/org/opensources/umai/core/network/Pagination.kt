package org.opensources.umai.core.network

import org.opensources.umai.core.model.Paged
import org.opensources.umai.core.network.dto.PaginationDto

fun <D, T> PaginationDto<D>.toPaged(transform: (D) -> T?): Paged<T> = Paged(
    items = items.mapNotNull(transform),
    page = page,
    totalPages = totalPages,
    total = total,
)

/**
 * Every item of a paginated collection, read page after page. [maxPages] is a
 * hard stop, so a very large instance cannot stall the screen that asked.
 */
suspend fun <T> fetchAllPages(maxPages: Int, loadPage: suspend (page: Int) -> Paged<T>): List<T> {
    val all = mutableListOf<T>()
    var page = 1
    do {
        val result = loadPage(page)
        all += result.items
        page++
    } while (result.hasNext && page <= maxPages)
    return all
}
