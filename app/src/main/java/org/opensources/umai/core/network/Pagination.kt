package org.opensources.umai.core.network

import org.opensources.umai.core.model.AllPages
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
 * hard stop, so a very large instance cannot stall the screen that asked; the
 * answer then says it is not complete.
 */
suspend fun <T> fetchAllPages(maxPages: Int, loadPage: suspend (page: Int) -> Paged<T>): AllPages<T> {
    val all = mutableListOf<T>()
    var page = 1
    var hasNext: Boolean
    do {
        val result = loadPage(page)
        all += result.items
        hasNext = result.hasNext
        page++
    } while (hasNext && page <= maxPages)
    return AllPages(all, complete = !hasNext)
}
