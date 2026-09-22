package org.opensources.umai.cooking.ui

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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.opensources.umai.core.model.Recipe
import org.opensources.umai.core.model.RecipeIngredient
import org.opensources.umai.core.model.RecipeStep
import org.opensources.umai.core.model.RecipeSummary
import org.opensources.umai.core.network.FakeMealieServer
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.recipe.data.RecipeRepository

/**
 * Step navigation and the step/ingredient association are pure state logic, so
 * they are exercised on the state object directly.
 */
class CookingStateTest {

    private val ingredients = listOf(
        ingredient("ref-1", "2 citrons"),
        ingredient("ref-2", "Huile d'olive"),
        ingredient(null, "Sel"),
    )

    private val recipe = recipe(
        steps = listOf(
            step("s1", "Presser les citrons.", listOf("ref-1")),
            step("s2", "Ajouter l'huile.", listOf("ref-2"), images = listOf("etape2.jpg")),
            step("s3", "Servir.", emptyList()),
        ),
    )

    private fun state(index: Int) = CookingUiState(recipe = recipe, currentStep = index, loading = false)

    @Test
    fun `the first step has no previous step`() {
        val state = state(0)
        assertFalse(state.hasPrevious)
        assertTrue(state.hasNext)
        assertFalse(state.isLastStep)
        assertEquals(3, state.stepCount)
    }

    @Test
    fun `the last step offers finishing instead of a next step`() {
        val state = state(2)
        assertTrue(state.hasPrevious)
        assertFalse(state.hasNext)
        assertTrue(state.isLastStep)
    }

    @Test
    fun `each step exposes only the ingredients Mealie linked to it`() {
        assertEquals(listOf("2 citrons"), state(0).ingredientsForStep.map { it.display })
        assertEquals(listOf("Huile d'olive"), state(1).ingredientsForStep.map { it.display })
    }

    @Test
    fun `a step with no ingredient reference shows none rather than all of them`() {
        assertTrue(state(2).ingredientsForStep.isEmpty())
    }

    @Test
    fun `step images extracted from the text are carried to the screen`() {
        assertEquals(listOf("etape2.jpg"), state(1).step?.images)
        assertTrue(state(0).step!!.images.isEmpty())
    }

    @Test
    fun `a recipe without instruction has no current step`() {
        val empty = CookingUiState(recipe = recipe(steps = emptyList()), loading = false)
        assertEquals(0, empty.stepCount)
        assertNull(empty.step)
        assertFalse(empty.hasNext)
        assertFalse(empty.isLastStep)
    }

    private fun ingredient(referenceId: String?, display: String) = RecipeIngredient(
        referenceId = referenceId,
        display = display,
        quantity = null,
        unit = null,
        food = null,
        note = null,
        sectionTitle = null,
        foodId = null,
        unitId = null,
    )

    private fun step(id: String, text: String, refs: List<String>, images: List<String> = emptyList()) =
        RecipeStep(id = id, title = null, text = text, images = images, ingredientReferenceIds = refs)

    private fun recipe(steps: List<RecipeStep>) = Recipe(
        summary = RecipeSummary(
            id = "r1",
            slug = "test",
            name = "Test",
            description = "",
            imageToken = null,
            servings = 2.0,
            yieldText = null,
            totalTime = null,
            prepTime = null,
            cookTime = null,
            performTime = null,
            categories = emptyList(),
            tags = emptyList(),
            tools = emptyList(),
            rating = null,
            sourceUrl = null,
            dateAdded = null,
            lastMade = null,
        ),
        ingredients = ingredients,
        steps = steps,
        nutrition = null,
        notes = emptyList(),
        showNutrition = false,
        showAssets = false,
        assets = emptyList(),
    )
}

/** Navigation goes through the ViewModel, driven by a local Mealie stub. */
@OptIn(ExperimentalCoroutinesApi::class)
class CookingViewModelTest {

    private lateinit var fake: FakeMealieServer

    @Before
    fun setUp() {
        // The repository performs a real HTTP call against the local stub, so
        // the tests wait on the state rather than on virtual time.
        Dispatchers.setMain(Dispatchers.Unconfined)
        fake = FakeMealieServer()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        fake.shutdown()
    }

    private fun viewModel(keepScreenOn: Boolean = true): CookingViewModel = CookingViewModel(
        slug = "test",
        recipeRepository = RecipeRepository({ fake.api() }),
        keepScreenOn = flowOf(keepScreenOn),
    )

    /** Suspends until the screen has something to show, or fails the test. */
    private suspend fun CookingViewModel.awaitSettled(): CookingUiState =
        withTimeout(TIMEOUT_MS) { state.first { !it.loading } }

    @Test
    fun `the recipe is loaded and starts on the first step`() = runBlocking {
        fake.enqueueJson(RECIPE)
        val state = viewModel().awaitSettled()
        assertFalse(state.loading)
        assertEquals(3, state.stepCount)
        assertEquals(0, state.currentStep)
        assertTrue(state.keepScreenOn)
    }

    @Test
    fun `next and previous walk through the steps`() = runBlocking {
        fake.enqueueJson(RECIPE)
        val vm = viewModel()
        vm.awaitSettled()

        vm.next()
        assertEquals(1, vm.state.value.currentStep)
        vm.next()
        assertEquals(2, vm.state.value.currentStep)
        vm.previous()
        assertEquals(1, vm.state.value.currentStep)
    }

    @Test
    fun `navigation never runs past either end`() = runBlocking {
        fake.enqueueJson(RECIPE)
        val vm = viewModel()
        vm.awaitSettled()

        vm.previous()
        assertEquals(0, vm.state.value.currentStep)

        repeat(10) { vm.next() }
        assertEquals(2, vm.state.value.currentStep)
        assertTrue(vm.state.value.isLastStep)
    }

    @Test
    fun `the step list can jump straight to a step`() = runBlocking {
        fake.enqueueJson(RECIPE)
        val vm = viewModel()
        vm.awaitSettled()

        vm.goToStep(2)
        assertEquals(2, vm.state.value.currentStep)

        vm.goToStep(99)
        assertEquals(2, vm.state.value.currentStep)
    }

    @Test
    fun `a step image embedded in the text reaches the state`() = runBlocking {
        fake.enqueueJson(RECIPE)
        val vm = viewModel()
        vm.awaitSettled()

        vm.goToStep(1)
        assertEquals(listOf("etape2.jpg"), vm.state.value.step?.images)
        assertEquals("Ajouter l'huile.", vm.state.value.step?.text)
    }

    @Test
    fun `a failure is surfaced instead of an empty screen`() = runBlocking {
        fake.enqueueError(500)
        val state = viewModel(keepScreenOn = false).awaitSettled()

        assertEquals(NetworkError.Server(500), state.error)
        assertEquals(0, state.stepCount)
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L

        val RECIPE = """
        {
          "id":"r1","name":"Test","slug":"test",
          "recipeIngredient":[
            {"quantity":2,"display":"2 citrons","referenceId":"11111111-1111-4111-8111-111111111111"}
          ],
          "recipeInstructions":[
            {"id":"s1","title":"","text":"Presser les citrons.",
             "ingredientReferences":[{"referenceId":"11111111-1111-4111-8111-111111111111"}]},
            {"id":"s2","title":"","text":"Ajouter l'huile.

![i](etape2.jpg)","ingredientReferences":[]},
            {"id":"s3","title":"","text":"Servir.","ingredientReferences":[]}
          ]
        }
        """.trimIndent()
    }
}
