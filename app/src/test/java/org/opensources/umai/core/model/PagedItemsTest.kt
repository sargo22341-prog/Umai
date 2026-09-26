package org.opensources.umai.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PagedItemsTest {

    private data class Item(val id: String, val version: Int = 1)

    private fun page(number: Int, vararg ids: String, totalPages: Int = 3) =
        Paged(items = ids.map { Item(it) }, page = number, totalPages = totalPages, total = totalPages * 2)

    private fun PagedItems<Item>.ids() = items.map { it.id }

    @Test
    fun `pages are appended after one another`() {
        val items = PagedItems<Item>().append(page(1, "a", "b"), Item::id).append(page(2, "c", "d"), Item::id)

        assertEquals(listOf("a", "b", "c", "d"), items.ids())
        assertEquals(2, items.page)
        assertEquals(true, items.canLoadMore)
    }

    @Test
    fun `the first page starts the list over`() {
        val items = PagedItems<Item>().append(page(1, "a"), Item::id).append(page(2, "b"), Item::id)

        assertEquals(listOf("c"), items.append(page(1, "c"), Item::id).ids())
    }

    @Test
    fun `an item a shifted page repeats is not shown twice`() {
        // A recipe added meanwhile pushed "b" from the end of page 1 to the start of page 2.
        val items = PagedItems<Item>().append(page(1, "a", "b"), Item::id).append(page(2, "b", "c"), Item::id)

        assertEquals(listOf("a", "b", "c"), items.ids())
    }

    @Test
    fun `the first page read again keeps the pages loaded after it`() {
        val loaded = PagedItems<Item>().append(page(1, "a", "b"), Item::id).append(page(2, "c", "d"), Item::id)

        val refreshed = loaded.withFirstPage(
            Paged(listOf(Item("new"), Item("a", version = 2)), page = 1, totalPages = 4, total = 7),
            Item::id,
        )

        assertEquals(listOf("new", "a", "b", "c", "d"), refreshed.ids())
        assertEquals(2, refreshed.items.first { it.id == "a" }.version)
        assertEquals(2, refreshed.page)
        assertEquals(4, refreshed.totalPages)
        assertEquals(7, refreshed.total)
    }

    @Test
    fun `the first page read again on a single page replaces it`() {
        val loaded = PagedItems<Item>().append(page(1, "a", "b"), Item::id)

        assertEquals(listOf("c"), loaded.withFirstPage(page(1, "c"), Item::id).ids())
    }

    @Test
    fun `removed items no longer count in the total`() {
        val items = PagedItems<Item>().append(page(1, "a", "b"), Item::id).without { it.id == "a" }

        assertEquals(listOf("b"), items.ids())
        assertEquals(5, items.total)
    }
}
