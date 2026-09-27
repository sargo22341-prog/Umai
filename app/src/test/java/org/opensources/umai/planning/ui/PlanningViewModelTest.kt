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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.opensources.umai.core.network.FakeMealieServer
import org.opensources.umai.core.network.query
import org.opensources.umai.planning.data.FakePlanPhotos
import org.opensources.umai.planning.data.MealPlanRepository
import org.opensources.umai.planning.data.RecipeCaloriesRepository
import org.opensources.umai.planning.domain.DayCalories
import java.time.DayOfWeek
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

    private fun viewModel(photos: FakePlanPhotos = FakePlanPhotos()) = PlanningViewModel(
        mealPlanRepository = MealPlanRepository { fake.api() },
        recipeCalories = RecipeCaloriesRepository(apiProvider = { fake.api() }, instanceKey = { "instance" }),
        photos = photos,
        clock = { today },
    )

    /** Waits for the state; a test that only needs the wait leaves the value. */
    @IgnorableReturnValue
    private suspend fun PlanningViewModel.awaitLoaded() =
        withTimeout(TIMEOUT_MS) { state.first { !it.loading } }

    @Test
    fun `the week runs from Monday to Sunday and opens on today`() = runBlocking {
        fake.enqueueJson(preferences(firstDayOfWeek = 1))
        fake.enqueueJson(EMPTY_PLAN)

        val state = viewModel().awaitLoaded()

        assertEquals(monday, state.weekStart)
        assertEquals((0L..6L).map { monday.plusDays(it) }, state.days)
        assertEquals(today, state.focusedDay)
        assertEquals("/api/households/preferences", fake.takeRequest().url.encodedPath)
        val request = fake.takeRequest()
        assertEquals("2026-09-21", request.query("start_date"))
        assertEquals("2026-09-27", request.query("end_date"))
    }

    @Test
    fun `another week opens on its Monday, and today brings the current week back`() = runBlocking {
        fake.enqueueJson(preferences(firstDayOfWeek = 1))
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
    fun `every move of the week, today included, asks to bring its focused day into view`() = runBlocking {
        fake.enqueueJson(preferences(firstDayOfWeek = 1))
        repeat(4) { fake.enqueueJson(EMPTY_PLAN) }
        val vm = viewModel()
        val first = vm.awaitLoaded().focusRequests

        vm.showNextWeek()
        vm.showPreviousWeek()
        // Already on the current week: the day in view may still be another one than today.
        vm.backToToday()

        assertEquals(first + 3, vm.awaitLoaded().focusRequests)
    }

    @Test
    fun `the week starts on the first day chosen in Mealie`() = runBlocking {
        // Mealie numbers the days from Sunday: 0 is Sunday.
        fake.enqueueJson(preferences(firstDayOfWeek = 0))
        fake.enqueueJson(EMPTY_PLAN)

        val state = viewModel().awaitLoaded()

        val sunday = LocalDate.of(2026, 9, 20)
        assertEquals(DayOfWeek.SUNDAY, state.firstDay)
        assertEquals(sunday, state.weekStart)
        assertEquals((0L..6L).map { sunday.plusDays(it) }, state.days)
        assertEquals(today, state.focusedDay)
        fake.takeRequest()
        val request = fake.takeRequest()
        assertEquals("2026-09-20", request.query("start_date"))
        assertEquals("2026-09-26", request.query("end_date"))
    }

    @Test
    fun `a first day changed in Mealie is picked up when the tab comes back`() = runBlocking {
        fake.enqueueJson(preferences(firstDayOfWeek = 1))
        fake.enqueueJson(EMPTY_PLAN)
        val vm = viewModel()
        vm.awaitLoaded()
        assertEquals(monday, vm.state.value.weekStart)

        fake.enqueueJson(preferences(firstDayOfWeek = 6))
        fake.enqueueJson(EMPTY_PLAN)
        vm.onScreenShown()
        val state = withTimeout(TIMEOUT_MS) { vm.state.first { it.firstDay == DayOfWeek.SATURDAY && !it.refreshing } }

        // Thursday falls in the week that started on the Saturday before.
        assertEquals(LocalDate.of(2026, 9, 19), state.weekStart)
        assertEquals(today, state.focusedDay)
    }

    @Test
    fun `each day adds up the calories of its recipes and foods`() = runBlocking {
        fake.enqueueJson(preferences(firstDayOfWeek = 1))
        fake.enqueueJson(PLAN)
        // The recipe without a calorie tag has its nutrition asked of Mealie.
        fake.enqueueJson("""{"id":"r2","slug":"tarte","name":"Tarte","nutrition":{"calories":"380 kcal"}}""")

        val vm = viewModel()
        val state = withTimeout(TIMEOUT_MS) { vm.state.first { !it.loading && !it.loadingCalories } }

        assertEquals(DayCalories(total = 450 + 139 + 380, unknown = 1), state.calories(today))
        val cola = state.entriesByDay.getValue(today).first { it.id == 2 }
        assertEquals(139, state.caloriesOf(cola))
        assertTrue(state.calories(monday).isEmpty)
        fake.takeRequest()
        fake.takeRequest()
        assertEquals("/api/recipes/tarte", fake.takeRequest().url.encodedPath)
    }

    @Test
    fun `the photos of the week are shown, and those of removed entries go`() = runBlocking {
        val photos = FakePlanPhotos()
        photos.attached[2] = today to "cola.jpg"
        photos.attached[99] = today to "gone.jpg"
        photos.attached[98] = today.plusWeeks(2) to "later.jpg"
        fake.enqueueJson(preferences(firstDayOfWeek = 1))
        fake.enqueueJson(PLAN)
        fake.enqueueJson("""{"id":"r2","slug":"tarte","name":"Tarte","nutrition":null}""")

        val vm = viewModel(photos)
        val state = withTimeout(TIMEOUT_MS) { vm.state.first { !it.loading && !it.loadingCalories } }

        assertEquals(mapOf(2 to "cola.jpg"), state.photos)
        assertEquals(setOf(2, 98), photos.attached.keys)

        fake.enqueueJson("{}")
        fake.enqueueJson(PLAN)
        vm.deleteEntry(state.entriesByDay.getValue(today).first { it.id == 2 })
        withTimeout(TIMEOUT_MS) { vm.state.first { !it.mutating && !it.loading } }

        assertEquals(listOf(2), photos.deleted)
    }

    @Test
    fun `without the preference, the week keeps starting on Monday`() = runBlocking {
        fake.enqueueError(500)
        fake.enqueueJson(EMPTY_PLAN)

        val state = viewModel().awaitLoaded()

        assertEquals(monday, state.weekStart)
        assertNull(state.error)
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L

        fun preferences(firstDayOfWeek: Int) = """
            {"privateHousehold":false,"showAnnouncements":true,
             "lockRecipeEditsFromOtherHouseholds":true,"firstDayOfWeek":$firstDayOfWeek,"recipePublic":true,
             "recipeShowNutrition":true,"recipeShowAssets":false,"recipeLandscapeView":false,
             "recipeDisableComments":false}
        """.trimIndent()

        /** Thursday: a tagged recipe, a food with its calories, a recipe without tag, a note without. */
        const val PLAN = """
            {"page":1,"per_page":200,"total":4,"total_pages":1,"items":[
              {"id":1,"date":"2026-09-24","entryType":"lunch","title":"","text":"","recipeId":"r1",
               "groupId":"g","userId":"u","recipe":{"id":"r1","slug":"curry","name":"Curry",
               "tags":[{"id":"t1","name":"calorie-450","slug":"calorie-450"}]}},
              {"id":2,"date":"2026-09-24","entryType":"snack","title":"Cola","text":"139 kcal · 330 ml",
               "recipeId":null,"groupId":"g","userId":"u","recipe":null},
              {"id":3,"date":"2026-09-24","entryType":"dinner","title":"","text":"","recipeId":"r2",
               "groupId":"g","userId":"u","recipe":{"id":"r2","slug":"tarte","name":"Tarte","tags":[]}},
              {"id":4,"date":"2026-09-24","entryType":"dinner","title":"Restaurant","text":"",
               "recipeId":null,"groupId":"g","userId":"u","recipe":null}
            ],"next":null,"previous":null}
        """

        const val EMPTY_PLAN =
            """{"page":1,"per_page":200,"total":0,"total_pages":0,"items":[],"next":null,"previous":null}"""

    }
}
