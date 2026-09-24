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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.opensources.umai.core.model.MealType
import org.opensources.umai.core.model.RecipeSummary
import org.opensources.umai.core.network.FakeMealieServer
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.planning.data.MealPlanRepository
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class PlanRecipePickerViewModelTest {

    private lateinit var fake: FakeMealieServer
    private val date = LocalDate.of(2026, 9, 24)

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

    private fun viewModel() = PlanRecipePickerViewModel(date, MealType.LUNCH, MealPlanRepository { fake.api() })

    @Test
    fun `the picked recipe goes on the day and the meal chosen before`() = runBlocking {
        fake.enqueueJson(CREATED)
        val vm = viewModel()

        vm.add(recipe())
        val state = withTimeout(TIMEOUT_MS) { vm.state.first { it.added } }

        assertFalse(state.adding)
        val body = fake.takeRequest().body?.utf8().orEmpty()
        assertTrue(body.contains("\"date\":\"2026-09-24\""))
        assertTrue(body.contains("\"entryType\":\"lunch\""))
        assertTrue(body.contains("\"recipeId\":\"r1\""))
    }

    @Test
    fun `a refused addition is reported and the screen stays`() = runBlocking {
        fake.enqueueError(500)
        val vm = viewModel()

        vm.add(recipe())
        val state = withTimeout(TIMEOUT_MS) { vm.state.first { it.error != null } }

        assertEquals(NetworkError.Server(500), state.error)
        assertFalse(state.added)
        vm.dismissError()
        assertEquals(null, vm.state.value.error)
    }

    private fun recipe() = RecipeSummary(
        id = "r1", slug = "tarte", name = "Tarte", description = "", imageToken = null, servings = 0.0,
        yieldText = null, totalTime = null, prepTime = null, cookTime = null, performTime = null,
        categories = emptyList(), tags = emptyList(), tools = emptyList(), rating = null, sourceUrl = null,
        dateAdded = null, lastMade = null,
    )

    private companion object {
        const val TIMEOUT_MS = 10_000L

        const val CREATED = """
            {"id":7,"date":"2026-09-24","entryType":"lunch","title":"","text":"",
             "recipeId":"r1","groupId":"g","userId":"u","householdId":"h",
             "recipe":{"id":"r1","name":"Tarte","slug":"tarte","image":null}}
        """
    }
}
