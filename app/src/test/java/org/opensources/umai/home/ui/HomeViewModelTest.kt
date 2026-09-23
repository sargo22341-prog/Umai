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
                request.url.encodedPath == "/api/recipes/vu-recemment" -> json(RECENT)
                request.query("orderBy") == "random" -> {
                    draws += request
                    if (drawFails) json("""{"detail":"boom"}""", code = 500) else json(DRAW)
                }
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
        recentSlugs = flowOf(listOf("vu-recemment")),
        layout = flowOf(RecipeLayout.GRID),
        newSeed = { "seed-${++seeds}" },
    )

    /** The draw is published with the latest recipes, the recently viewed ones come last. */
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
            {"page":1,"per_page":24,"total":1,"total_pages":1,
             "items":[{"id":"r1","name":"Poulet au curry","slug":"poulet-au-curry","image":"73"}]}
        """

        const val DRAW = """
            {"page":1,"per_page":5,"total":2,"total_pages":1,
             "items":[
               {"id":"r2","name":"Tarte au citron","slug":"tarte-au-citron","image":"12"},
               {"id":"r3","name":"Risotto","slug":"risotto","image":null}
             ]}
        """

        const val RECENT = """
            {"id":"r9","name":"Vu recemment","slug":"vu-recemment","image":null,"recipeServings":2.0,
             "recipeIngredient":[],"recipeInstructions":[]}
        """
    }
}
