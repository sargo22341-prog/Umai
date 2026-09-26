package org.opensources.umai.organizer.data

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.opensources.umai.core.model.Organizer
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.FakeMealieServer
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.network.query

class OrganizerRepositoryTest {

    private lateinit var first: FakeMealieServer
    private lateinit var second: FakeMealieServer
    private lateinit var current: FakeMealieServer
    private lateinit var repository: OrganizerRepository

    @Before
    fun setUp() {
        first = FakeMealieServer()
        second = FakeMealieServer()
        current = first
        repository = OrganizerRepository(
            apiProvider = { current.api() },
            instanceKey = { current.baseUrl.toString() },
        )
    }

    @After
    fun tearDown() {
        first.shutdown()
        second.shutdown()
    }

    private fun tags(vararg names: String, page: Int = 1, totalPages: Int = 1) =
        """{"page":$page,"total_pages":$totalPages,"items":[${
            names.joinToString(",") { """{"id":"$it","name":"$it","slug":"$it"}""" }
        }]}"""

    private fun ApiResult<List<Organizer>>.names() =
        (this as ApiResult.Success).value.map { it.name }

    @Test
    fun `the tags are read once, then kept`() = runTest {
        first.enqueueJson(tags("ete", "hiver"))

        assertEquals(listOf("ete", "hiver"), repository.tags().names())
        assertEquals(listOf("ete", "hiver"), repository.tags().names())
        assertEquals(1, first.server.requestCount)
    }

    @Test
    fun `every page of the tags is read`() = runTest {
        first.enqueueJson(tags("ete", page = 1, totalPages = 2))
        first.enqueueJson(tags("hiver", page = 2, totalPages = 2))

        assertEquals(listOf("ete", "hiver"), repository.tags().names())
        assertEquals("1", first.takeRequest().query("page"))
        assertEquals("2", first.takeRequest().query("page"))
    }

    @Test
    fun `signed in to another instance, the tags are read from it`() = runTest {
        first.enqueueJson(tags("ete"))
        second.enqueueJson(tags("printemps"))

        assertEquals(listOf("ete"), repository.tags().names())
        current = second
        assertEquals(listOf("printemps"), repository.tags().names())
        assertEquals(1, second.server.requestCount)
    }

    @Test
    fun `a refresh reads the tags again`() = runTest {
        first.enqueueJson(tags("ete"))
        first.enqueueJson(tags("ete", "automne"))

        repository.tags()
        assertEquals(listOf("ete", "automne"), repository.tags(forceRefresh = true).names())
    }

    @Test
    fun `a failure is not kept`() = runTest {
        first.enqueueError(500)
        first.enqueueJson(tags("ete"))

        assertEquals(500, ((repository.tags() as ApiResult.Failure).error as NetworkError.Server).code)
        assertEquals(listOf("ete"), repository.tags().names())
    }
}
