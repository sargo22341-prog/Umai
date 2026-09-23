package org.opensources.umai.search.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.opensources.umai.core.network.FakeMealieServer
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.network.query
import org.opensources.umai.core.settings.RecipeLayout
import org.opensources.umai.organizer.data.OrganizerRepository
import org.opensources.umai.recipe.data.RecipeRepository
import org.opensources.umai.search.domain.AddedWithin
import org.opensources.umai.search.domain.RecipeFilters
import org.opensources.umai.search.domain.SortField

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private lateinit var fake: FakeMealieServer

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        fake = FakeMealieServer()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        fake.shutdown()
    }

    private fun viewModel() = SearchViewModel(
        recipeRepository = RecipeRepository({ fake.api() }),
        organizerRepository = OrganizerRepository { fake.api() },
        layout = flowOf(RecipeLayout.GRID),
    )

    private suspend fun SearchViewModel.awaitResults(): SearchUiState =
        withTimeout(TIMEOUT_MS) { state.first { it.hasQueried && !it.loading } }

    @Test
    fun `an empty search shows the invitation and hits no endpoint`() = runBlocking {
        val vm = viewModel()

        val state = vm.state.value
        assertTrue(state.isIdle)
        assertFalse(state.hasQueried)
        assertEquals(0, fake.server.requestCount)
    }

    @Test
    fun `typing a query returns results after the debounce`() = runBlocking {
        fake.enqueueJson(PAGE)
        val vm = viewModel()

        vm.onQueryChange("curry")
        val state = vm.awaitResults()

        assertEquals(2, state.results.items.size)
        assertEquals("Poulet au curry", state.results.items.first().name)
        assertFalse(state.isEmptyResult)
    }

    @Test
    fun `successive keystrokes only trigger one request`() = runBlocking {
        fake.enqueueJson(PAGE)
        val vm = viewModel()

        vm.onQueryChange("c")
        vm.onQueryChange("cu")
        vm.onQueryChange("curry")
        vm.awaitResults()

        assertEquals(1, fake.server.requestCount)
        assertEquals("curry", fake.takeRequest().url.queryParameter("search"))
    }

    @Test
    fun `a search with no match shows the empty state`() = runBlocking {
        fake.enqueueJson(EMPTY_PAGE)
        val vm = viewModel()

        vm.onQueryChange("zzzzzz")
        val state = vm.awaitResults()

        assertTrue(state.isEmptyResult)
        assertTrue(state.results.items.isEmpty())
    }

    @Test
    fun `clearing the query returns to the invitation`() = runBlocking {
        fake.enqueueJson(PAGE)
        val vm = viewModel()
        vm.onQueryChange("curry")
        vm.awaitResults()

        vm.clearQuery()
        val state = withTimeout(TIMEOUT_MS) { vm.state.first { it.isIdle && !it.hasQueried } }

        assertTrue(state.results.items.isEmpty())
    }

    @Test
    fun `filters alone are enough to run a search`() = runBlocking {
        fake.enqueueJson(PAGE)
        val vm = viewModel()

        vm.applyFilters(RecipeFilters(minRating = 4))
        val state = vm.awaitResults()

        assertFalse(state.isIdle)
        assertEquals("rating >= 4", fake.takeRequest().url.queryParameter("queryFilter"))
    }

    @Test
    fun `resetting the filters clears the active count`() = runBlocking {
        fake.enqueueJson(PAGE)
        val vm = viewModel()
        vm.applyFilters(RecipeFilters(minRating = 4, addedWithin = AddedWithin.WEEK))
        vm.awaitResults()

        vm.resetFilters()
        val state = withTimeout(TIMEOUT_MS) { vm.state.first { it.filters.isEmpty } }

        assertEquals(0, state.filters.activeCount)
    }

    @Test
    fun `a failing search surfaces the error instead of an empty list`() = runBlocking {
        fake.enqueueError(503)
        val vm = viewModel()

        vm.onQueryChange("curry")
        val state = vm.awaitResults()

        assertEquals(NetworkError.Server(503), state.error)
    }

    @Test
    fun `the filter sheet loads categories, tags and tools`() = runBlocking {
        fake.enqueueJson(CATEGORIES)
        fake.enqueueJson(TAGS)
        fake.enqueueJson(TOOLS)
        val vm = viewModel()

        vm.loadFilterOptions()
        val options = withTimeout(TIMEOUT_MS) { vm.filterOptions.first { !it.loading && it.categories.isNotEmpty() } }

        assertEquals(listOf("Plat"), options.categories.map { it.name })
        assertEquals(listOf("Poulet"), options.tags.map { it.name })
        assertEquals(listOf("RizCooker"), options.tools.map { it.name })
    }

    @Test
    fun `a too short ingredient query is not sent to the server`() = runBlocking {
        val vm = viewModel()
        vm.searchFoods("a")
        assertEquals(0, fake.server.requestCount)
    }

    @Test
    fun `choosing an order lists every recipe in that order, without a query`() = runBlocking {
        fake.enqueueJson(PAGE)
        val vm = viewModel()

        vm.selectSort(SortField.NAME)
        val state = vm.awaitResults()

        assertFalse(state.isIdle)
        assertEquals(2, state.results.items.size)
        val request = fake.takeRequest()
        assertEquals("name", request.query("orderBy"))
        assertEquals("asc", request.query("orderDirection"))
    }

    @Test
    fun `choosing the same order again reverses it`() = runBlocking {
        fake.enqueueJson(PAGE)
        fake.enqueueJson(PAGE)
        val vm = viewModel()

        vm.selectSort(SortField.NAME)
        vm.awaitResults()
        vm.selectSort(SortField.NAME)
        withTimeout(TIMEOUT_MS) { vm.state.first { it.sort.descending && !it.loading } }

        assertEquals("asc", fake.takeRequest().query("orderDirection"))
        assertEquals("desc", fake.takeRequest().query("orderDirection"))
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L

        val PAGE = """
        {
          "page":1,"per_page":24,"total":2,"total_pages":1,
          "items":[
            {"id":"r1","name":"Poulet au curry","slug":"poulet-au-curry","image":"73"},
            {"id":"r2","name":"Curry de legumes","slug":"curry-de-legumes","image":null}
          ]
        }
        """.trimIndent()

        const val EMPTY_PAGE =
            """{"page":1,"per_page":24,"total":0,"total_pages":0,"items":[]}"""

        const val CATEGORIES = """
            {"page":1,"per_page":100,"total":1,"total_pages":1,
             "items":[{"id":"c1","name":"Plat","slug":"plat","recipeCount":3}]}
        """

        const val TAGS = """
            {"page":1,"per_page":100,"total":1,"total_pages":1,
             "items":[{"id":"t1","name":"Poulet","slug":"poulet","recipeCount":5}]}
        """

        const val TOOLS = """
            {"page":1,"per_page":100,"total":1,"total_pages":1,
             "items":[{"id":"to1","name":"RizCooker","slug":"rizcooker","recipeCount":0}]}
        """
    }
}
