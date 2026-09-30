package org.opensources.umai.core.network

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.opensources.umai.core.model.AllPages
import org.opensources.umai.core.model.Paged

class PaginationTest {

    private fun page(number: Int, of: Int) = Paged(items = listOf(number), page = number, totalPages = of, total = of)

    @Test
    fun `every page is read up to the last one`() = runTest {
        assertEquals(AllPages(listOf(1, 2, 3), complete = true), fetchAllPages(maxPages = 5) { page(it, of = 3) })
    }

    @Test
    fun `the bound stops the reading and says the collection goes on`() = runTest {
        var read = 0
        val all = fetchAllPages(maxPages = 2) { read++; page(it, of = 10) }

        assertEquals(AllPages(listOf(1, 2), complete = false), all)
        assertEquals(2, read)
    }

    @Test
    fun `an empty collection is complete`() = runTest {
        assertEquals(AllPages(emptyList<Int>(), complete = true), fetchAllPages(maxPages = 2) { Paged(emptyList<Int>(), 1, 0, 0) })
    }
}
