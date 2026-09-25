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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.opensources.umai.core.model.MealPlanEntry
import org.opensources.umai.core.model.MealType
import org.opensources.umai.core.network.FakeMealieServer
import org.opensources.umai.planning.data.DishPoolRepository
import org.opensources.umai.planning.data.MealPlanRepository
import org.opensources.umai.planning.data.MemoryDishCourses
import org.opensources.umai.planning.data.PlanningMealie
import org.opensources.umai.planning.domain.MealSlot
import org.opensources.umai.planning.domain.ModelCourseClassifier
import org.opensources.umai.youtube.domain.ScriptedModel
import java.time.LocalDate
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class AutoPlanViewModelTest {

    private lateinit var fake: FakeMealieServer
    private lateinit var mealie: PlanningMealie

    /** A Friday: Monday to Thursday are past and left alone. */
    private val today = LocalDate.of(2026, 9, 25)
    private val week = (0L..6L).map { LocalDate.of(2026, 9, 21).plusDays(it) }

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        fake = FakeMealieServer()
        mealie = PlanningMealie(fake).also { it.install() }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        fake.shutdown()
    }

    private fun viewModel() = AutoPlanViewModel(
        dishes = DishPoolRepository({ fake.api() }, MemoryDishCourses(), ModelCourseClassifier(ScriptedModel(emptyList(), ready = false))),
        mealPlans = MealPlanRepository { fake.api() },
        random = Random(7),
    )

    private fun entry(date: LocalDate, type: MealType) =
        MealPlanEntry(id = 99, date = date, type = type, title = "Resto", text = "", recipe = null, groupId = null, userId = null)

    @Test
    fun `the week leaves the past days and the meals already planned alone`() {
        val vm = viewModel()
        vm.open(today, week, mapOf(today to listOf(entry(today, MealType.LUNCH))), focusedDay = today)

        val slots = vm.state.value.slots
        assertEquals(5, slots.size)
        assertFalse(MealSlot(today, MealType.LUNCH) in slots)
        assertTrue(slots.all { !it.date.isBefore(today) })
        assertTrue(slots.all { it.type == MealType.LUNCH || it.type == MealType.DINNER })
    }

    @Test
    fun `one day plans a lunch and a dinner`() {
        val vm = viewModel()
        vm.open(today, week, emptyMap(), focusedDay = today)
        vm.setScope(AutoPlanScope.DAY)
        vm.setDay(today.plusDays(1))

        assertEquals(
            listOf(MealSlot(today.plusDays(1), MealType.LUNCH), MealSlot(today.plusDays(1), MealType.DINNER)),
            vm.state.value.slots,
        )
    }

    @Test
    fun `a proposal is made of dishes only, then written on Mealie once accepted`() = runBlocking {
        val vm = viewModel()
        vm.open(today, week, emptyMap(), focusedDay = today)
        vm.setScope(AutoPlanScope.DAY)

        vm.propose()
        val proposed = withTimeout(TIMEOUT_MS) { vm.state.first { it.proposal != null } }
        val meals = requireNotNull(proposed.proposal).meals
        assertEquals(2, meals.size)
        assertTrue(meals.all { it.candidate.recipe.id in setOf("lasagnes", "blanquette", "gratin", "tofu") })
        assertTrue(mealie.created.isEmpty())

        vm.accept()
        val saved = withTimeout(TIMEOUT_MS) { vm.state.first { it.saved } }
        assertFalse(saved.visible)
        assertEquals(2, mealie.created.size)
        assertTrue(mealie.created[0].contains(""""entryType":"lunch""""))
        assertTrue(mealie.created[1].contains(""""entryType":"dinner""""))
        assertTrue(mealie.created.all { it.contains(""""date":"2026-09-25"""") })
    }

    @Test
    fun `a meal can be swapped, and another plan avoids the dishes shown`() = runBlocking {
        val vm = viewModel()
        vm.open(today, week, emptyMap(), focusedDay = today)
        vm.setScope(AutoPlanScope.DAY)
        vm.propose()
        val first = requireNotNull(withTimeout(TIMEOUT_MS) { vm.state.first { it.proposal != null } }.proposal)

        vm.replace(0)
        val swapped = requireNotNull(vm.state.value.proposal)
        assertNotEquals(first.meals[0].candidate.recipe.id, swapped.meals[0].candidate.recipe.id)
        assertEquals(first.meals[1], swapped.meals[1])

        vm.regenerate()
        val again = requireNotNull(withTimeout(TIMEOUT_MS) { vm.state.first { it.proposal != null && it.phase == null } }.proposal)
        assertNotNull(again)
        val shown = swapped.meals.map { it.candidate.recipe.id }.toSet()
        assertTrue(again.meals.map { it.candidate.recipe.id }.any { it !in shown })
    }

    @Test
    fun `an unreachable instance is reported, nothing written`() = runBlocking {
        fake.shutdown()
        val vm = viewModel()
        vm.open(today, week, emptyMap(), focusedDay = today)

        vm.propose()
        val state = withTimeout(TIMEOUT_MS) { vm.state.first { it.error != null } }

        assertEquals(null, state.proposal)
        assertFalse(state.working)
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
