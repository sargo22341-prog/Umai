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
import org.junit.Before
import org.junit.Test
import org.opensources.umai.core.network.FakeMealieServer
import org.opensources.umai.core.network.query
import org.opensources.umai.planning.data.MealPlanRepository
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

    private fun viewModel() = PlanningViewModel(
        mealPlanRepository = MealPlanRepository { fake.api() },
        clock = { today },
    )

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

        const val EMPTY_PLAN =
            """{"page":1,"per_page":200,"total":0,"total_pages":0,"items":[],"next":null,"previous":null}"""

    }
}
