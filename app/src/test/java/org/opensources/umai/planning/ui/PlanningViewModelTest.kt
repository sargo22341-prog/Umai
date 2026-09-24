package org.opensources.umai.planning.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.opensources.umai.core.network.FakeMealieServer
import org.opensources.umai.core.network.query
import org.opensources.umai.core.network.queryValues
import org.opensources.umai.organizer.data.OrganizerRepository
import org.opensources.umai.planning.data.MealPlanRepository
import org.opensources.umai.recipe.data.RecipeRepository
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class PlanningViewModelTest {

    private lateinit var fake: FakeMealieServer

    /** A Thursday. */
    private val today = LocalDate.of(2026, 9, 24)
    private val monday = LocalDate.of(2026, 9, 21)

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

    private fun viewModel() = PlanningViewModel(
        mealPlanRepository = MealPlanRepository { fake.api() },
        recipeRepository = RecipeRepository({ fake.api() }),
        organizerRepository = OrganizerRepository { fake.api() },
        clock = { today },
    )

    private suspend fun PlanningViewModel.awaitLoaded() =
        withTimeout(TIMEOUT_MS) { state.first { !it.loading } }

    @Test
    fun `the week runs from Monday to Sunday and opens on today`() = runBlocking {
        fake.enqueueJson(EMPTY_PLAN)

        val state = viewModel().awaitLoaded()

        assertEquals(monday, state.weekStart)
        assertEquals((0L..6L).map { monday.plusDays(it) }, state.days)
        assertEquals(today, state.focusedDay)
        val request = fake.takeRequest()
        assertEquals("2026-09-21", request.query("start_date"))
        assertEquals("2026-09-27", request.query("end_date"))
    }

    @Test
    fun `another week opens on its Monday, and today brings the current week back`() = runBlocking {
        fake.enqueueJson(EMPTY_PLAN)
        fake.enqueueJson(EMPTY_PLAN)
        fake.enqueueJson(EMPTY_PLAN)
        val vm = viewModel()
        vm.awaitLoaded()

        vm.showNextWeek()
        val next = vm.awaitLoaded()
        assertEquals(monday.plusWeeks(1), next.weekStart)
        assertEquals(monday.plusWeeks(1), next.focusedDay)

        vm.backToToday()
        val back = vm.awaitLoaded()
        assertEquals(monday, back.weekStart)
        assertEquals(today, back.focusedDay)
    }

    @Test
    fun `a recipe is drawn at random within the chosen category`() = runBlocking {
        fake.enqueueJson(EMPTY_PLAN)
        val vm = viewModel()
        vm.awaitLoaded()
        fake.takeRequest()

        fake.enqueueJson(CATEGORIES)
        vm.loadRandomCategories()
        val categories = withTimeout(TIMEOUT_MS) { vm.random.first { it.categories.isNotEmpty() } }.categories
        assertEquals(listOf("Dessert", "Plat"), categories.map { it.name })
        fake.takeRequest()

        vm.selectRandomCategory("c1")
        fake.enqueueJson(TWO_RECIPES)
        vm.drawRandomRecipe()
        val drawn = withTimeout(TIMEOUT_MS) { vm.random.first { !it.drawing && it.recipe != null } }

        assertEquals("r1", drawn.recipe?.id)
        val request = fake.takeRequest()
        assertEquals("random", request.query("orderBy"))
        assertNotNull(request.query("paginationSeed"))
        assertEquals(listOf("c1"), request.queryValues("categories"))
    }

    @Test
    fun `drawing again avoids the recipe just shown`() = runBlocking {
        fake.enqueueJson(EMPTY_PLAN)
        val vm = viewModel()
        vm.awaitLoaded()

        fake.enqueueJson(TWO_RECIPES)
        vm.drawRandomRecipe()
        withTimeout(TIMEOUT_MS) { vm.random.first { it.recipe?.id == "r1" } }

        fake.enqueueJson(TWO_RECIPES)
        vm.drawRandomRecipe()
        val again = withTimeout(TIMEOUT_MS) { vm.random.first { !it.drawing && it.recipe?.id != "r1" } }

        assertEquals("r2", again.recipe?.id)
    }

    @Test
    fun `an empty category says so rather than showing nothing`() = runBlocking {
        fake.enqueueJson(EMPTY_PLAN)
        val vm = viewModel()
        vm.awaitLoaded()

        fake.enqueueJson(EMPTY_RECIPES)
        vm.drawRandomRecipe()
        val state = withTimeout(TIMEOUT_MS) { vm.random.first { it.noMatch } }

        assertNull(state.recipe)
        assertNull(state.error)
    }

    @Test
    fun `closing the sheet forgets the drawn recipe but keeps the category`() = runBlocking {
        fake.enqueueJson(EMPTY_PLAN)
        val vm = viewModel()
        vm.awaitLoaded()
        vm.selectRandomCategory("c1")
        fake.enqueueJson(TWO_RECIPES)
        vm.drawRandomRecipe()
        withTimeout(TIMEOUT_MS) { vm.random.first { it.recipe != null } }

        vm.resetRandomRecipe()

        assertNull(vm.random.value.recipe)
        assertEquals("c1", vm.random.value.categoryId)
        assertTrue(!vm.random.value.drawing)
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L

        const val EMPTY_PLAN =
            """{"page":1,"per_page":200,"total":0,"total_pages":0,"items":[],"next":null,"previous":null}"""

        const val CATEGORIES = """
            {"page":1,"per_page":100,"total":2,"total_pages":1,
             "items":[{"id":"c2","name":"Plat","slug":"plat","recipeCount":3},
                      {"id":"c1","name":"Dessert","slug":"dessert","recipeCount":2}]}
        """

        const val TWO_RECIPES = """
            {"page":1,"per_page":2,"total":5,"total_pages":3,
             "items":[{"id":"r1","name":"Tarte","slug":"tarte","image":null},
                      {"id":"r2","name":"Crumble","slug":"crumble","image":null}]}
        """

        const val EMPTY_RECIPES = """{"page":1,"per_page":2,"total":0,"total_pages":0,"items":[]}"""
    }
}
