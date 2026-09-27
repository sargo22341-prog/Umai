package org.opensources.umai.home.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.opensources.umai.core.network.FakeMealieServer
import org.opensources.umai.core.network.query
import org.opensources.umai.core.settings.RecipeLayout
import org.opensources.umai.recipe.data.RecipeRepository
import java.util.Collections

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private lateinit var fake: FakeMealieServer

    private val draws: MutableList<RecordedRequest> = Collections.synchronizedList(mutableListOf())
    private val recentReads: MutableList<RecordedRequest> = Collections.synchronizedList(mutableListOf())

    @Volatile
    private var drawFails = false
    private var seeds = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        fake = FakeMealieServer()
        // The draw and the latest recipes are fetched at the same time, so the
        // answers are picked by request rather than queued in order.
        fake.server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.query("queryFilter")?.startsWith("slug IN") == true -> {
                    recentReads += request
                    json(RECENT)
                }
                request.query("orderBy") == "random" -> {
                    draws += request
                    if (drawFails) json("""{"detail":"boom"}""", code = 500) else json(DRAW)
                }
                request.query("page") == "2" -> json(LATEST_PAGE_2)
                else -> json(LATEST)
            }
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        fake.shutdown()
    }

    private fun viewModel() = HomeViewModel(
        recipeRepository = RecipeRepository({ fake.api() }),
        recentSlugs = flowOf(listOf("vu-recemment", "supprimee", "deja-vue")),
        layout = flowOf(RecipeLayout.GRID),
        newSeed = { "seed-${++seeds}" },
    )

    /** The draw is published with the latest recipes, the recently viewed ones come last. */
    @IgnorableReturnValue
    private suspend fun HomeViewModel.awaitLoaded(): HomeUiState = withTimeout(TIMEOUT_MS) {
        state.first { !it.loading && !it.refreshing && it.recentlyViewed.isNotEmpty() }
    }

    @Test
    fun `five recipes are drawn at random with a pagination seed`() = runBlocking {
        val state = viewModel().awaitLoaded()

        assertEquals(listOf("Tarte au citron", "Risotto"), state.discovery.map { it.name })
        assertEquals(1, draws.size)
        assertEquals("5", draws.single().query("perPage"))
        assertEquals("seed-1", draws.single().query("paginationSeed"))
        assertEquals(1, state.latest.items.size)
    }

    @Test
    fun `coming back to the tab keeps the same draw`() = runBlocking {
        val vm = viewModel()
        val first = vm.awaitLoaded()

        vm.onScreenShown()
        val again = vm.awaitLoaded()

        assertEquals(1, draws.size)
        assertEquals(first.discovery, again.discovery)
    }

    @Test
    fun `refreshing draws new recipes with a new seed`() = runBlocking {
        val vm = viewModel()
        vm.awaitLoaded()

        vm.refresh()
        vm.awaitLoaded()

        assertEquals(listOf("seed-1", "seed-2"), draws.map { it.query("paginationSeed") })
    }

    @Test
    fun `the recently viewed recipes are read in one request, in the order they were seen`() = runBlocking {
        val state = viewModel().awaitLoaded()

        assertEquals(listOf("Vu recemment", "Deja vue"), state.recentlyViewed.map { it.name })
        assertEquals(1, recentReads.size)
        assertEquals("""slug IN ["vu-recemment","supprimee","deja-vue"]""", recentReads.single().query("queryFilter"))
    }

    @Test
    fun `coming back to the tab keeps the pages already scrolled through`() = runBlocking {
        val vm = viewModel()
        vm.awaitLoaded()
        vm.loadMore()
        withTimeout(TIMEOUT_MS) { vm.state.first { it.latest.page == 2 && !it.loadingMore } }

        vm.onScreenShown()
        val again = vm.awaitLoaded()

        assertEquals(listOf("r1", "r4"), again.latest.items.map { it.id })
        assertEquals(2, again.latest.page)
    }

    @Test
    fun `a failed draw leaves the carousel out without hiding the latest recipes`() = runBlocking {
        drawFails = true

        val state = viewModel().awaitLoaded()

        assertTrue(state.discovery.isEmpty())
        assertNull(state.error)
        assertEquals(1, state.latest.items.size)
    }

    private fun json(body: String, code: Int = 200): MockResponse = MockResponse.Builder()
        .code(code)
        .setHeader("Content-Type", "application/json")
        .body(body)
        .build()

    private companion object {
        const val TIMEOUT_MS = 10_000L

        const val LATEST = """
            {"page":1,"per_page":24,"total":2,"total_pages":2,
             "items":[{"id":"r1","name":"Poulet au curry","slug":"poulet-au-curry","image":"73"}]}
        """

        const val LATEST_PAGE_2 = """
            {"page":2,"per_page":24,"total":2,"total_pages":2,
             "items":[{"id":"r4","name":"Gratin","slug":"gratin","image":null}]}
        """

        const val DRAW = """
            {"page":1,"per_page":5,"total":2,"total_pages":1,
             "items":[
               {"id":"r2","name":"Tarte au citron","slug":"tarte-au-citron","image":"12"},
               {"id":"r3","name":"Risotto","slug":"risotto","image":null}
             ]}
        """

        /** Mealie lists what it found in its own order; the one deleted since is missing. */
        const val RECENT = """
            {"page":1,"per_page":3,"total":2,"total_pages":1,
             "items":[
               {"id":"r8","name":"Deja vue","slug":"deja-vue","image":null},
               {"id":"r9","name":"Vu recemment","slug":"vu-recemment","image":null}
             ]}
        """
    }
}
