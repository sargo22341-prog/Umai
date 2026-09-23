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
import org.opensources.umai.core.model.MealPlanEntry
import org.opensources.umai.core.model.MealType
import org.opensources.umai.core.model.RecipeSummary
import org.opensources.umai.core.network.FakeMealieServer
import org.opensources.umai.recipe.data.RecipeRepository
import org.opensources.umai.shopping.data.ShoppingRepository
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class WeekShoppingViewModelTest {

    private lateinit var fake: FakeMealieServer
    private val today = LocalDate.of(2026, 9, 23)

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

    private fun viewModel() = WeekShoppingViewModel(RecipeRepository({ fake.api() }), ShoppingRepository { fake.api() })

    private suspend fun WeekShoppingViewModel.await(predicate: (WeekShoppingUiState) -> Boolean) =
        withTimeout(TIMEOUT_MS) { state.first(predicate) }

    @Test
    fun `the meals from today on are ticked, a note is not a meal`() = runBlocking {
        fake.enqueueJson(LISTS)
        val viewModel = viewModel()

        viewModel.open(listOf(entry(1, today.minusDays(1)), entry(2, today), note(3)), today)
        val state = viewModel.await { !it.loadingLists }

        assertEquals(listOf(1, 2), state.entries.map { it.id })
        assertEquals(setOf(2), state.selected)
        assertEquals("l1", state.listId)
        assertTrue(state.canContinue)
    }

    @Test
    fun `each chosen meal is sent with its servings and the lines kept`() = runBlocking {
        fake.enqueueJson(LISTS)
        val viewModel = viewModel()
        viewModel.open(listOf(entry(1, today), entry(2, today.plusDays(1), slug = "curry")), today)
        viewModel.await { !it.loadingLists }
        fake.takeRequest()

        // The same recipe planned twice is read once.
        fake.enqueueJson(RECIPE)
        viewModel.next()
        var state = viewModel.await { !it.loadingRecipes && it.step == WeekShoppingStep.SERVINGS && it.recipes.isNotEmpty() }
        assertEquals(mapOf(1 to 2, 2 to 2), state.servings)
        assertEquals(2, fake.server.requestCount)
        fake.takeRequest()

        viewModel.setServings(state.entries[0], 4)
        viewModel.next()
        viewModel.toggleIngredient(state.entries[1], 1)

        fake.enqueueJson(LIST)
        fake.enqueueJson(LIST)
        viewModel.next()
        state = viewModel.await { it.added != null }

        assertEquals(2, state.added)
        val first = fake.takeRequest()
        assertEquals("/api/households/shopping/lists/l1/recipe/r1", first.url.encodedPath)
        val firstBody = first.body?.utf8().orEmpty()
        assertTrue(firstBody.contains(""""recipeIncrementQuantity":2.0"""))
        // Every line kept: Mealie is left to send the whole recipe.
        assertFalse(firstBody.contains("recipeIngredients"))
        val second = fake.takeRequest().body?.utf8().orEmpty()
        assertTrue(second.contains(""""recipeIncrementQuantity":1.0"""))
        assertTrue(second.contains("citrons"))
        assertFalse(second.contains("Riz"))
    }

    @Test
    fun `a failure stops there and a retry does not send a meal twice`() = runBlocking {
        fake.enqueueJson(LISTS)
        val viewModel = viewModel()
        viewModel.open(listOf(entry(1, today), entry(2, today)), today)
        viewModel.await { !it.loadingLists }
        fake.enqueueJson(RECIPE)
        viewModel.next()
        viewModel.await { !it.loadingRecipes && it.recipes.isNotEmpty() }
        viewModel.next()

        fake.enqueueJson(LIST)
        fake.enqueueError(500)
        viewModel.next()
        var state = viewModel.await { !it.adding && it.error != null }
        assertEquals(setOf(1), state.sent)

        fake.enqueueJson(LIST)
        viewModel.next()
        state = viewModel.await { it.added != null }
        assertEquals(2, state.added)
        // Lists, recipe, first meal, failed second meal, second meal again.
        assertEquals(5, fake.server.requestCount)
    }

    private fun entry(id: Int, date: LocalDate, slug: String = "curry") = MealPlanEntry(
        id = id,
        date = date,
        type = MealType.DINNER,
        title = "",
        text = "",
        recipe = RecipeSummary(
            id = "r1", slug = slug, name = "Curry", description = "", imageToken = null, servings = 2.0,
            yieldText = null, totalTime = null, prepTime = null, cookTime = null, performTime = null,
            categories = emptyList(), tags = emptyList(), tools = emptyList(), rating = null,
            sourceUrl = null, dateAdded = null, lastMade = null,
        ),
        groupId = "g",
        userId = "u",
    )

    private fun note(id: Int) = entry(id, today).copy(recipe = null, title = "Restaurant")

    private companion object {
        const val TIMEOUT_MS = 5_000L

        const val LISTS = """{"page":1,"per_page":50,"total":1,"total_pages":1,
            "items":[{"id":"l1","name":"Courses","recipeReferences":[]}]}"""

        const val LIST = """{"id":"l1","name":"Courses","listItems":[],"recipeReferences":[]}"""

        const val RECIPE = """{"id":"r1","slug":"curry","name":"Curry","recipeServings":2,
            "recipeIngredient":[
              {"quantity":2,"note":"citrons","display":"2 citrons","referenceId":"a"},
              {"quantity":0,"note":"Riz","display":"Riz","referenceId":"b"}],
            "recipeInstructions":[]}"""
    }
}
