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
import org.opensources.umai.recipe.data.RecipeMediaRepository
import org.opensources.umai.recipe.data.RecipeRepository
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CoroutineScope
import org.opensources.umai.cooking.data.CookingTimerController
import org.opensources.umai.cooking.domain.CookingTimers
import org.opensources.umai.cooking.domain.TimerAlarm
import org.opensources.umai.cooking.domain.TimerHost
import org.opensources.umai.cooking.domain.TimerRecipe
import org.opensources.umai.core.settings.CookingTimerOptions
import java.time.Duration

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

    private var now = 0L

    /** The timers of the app, which the cooking mode only starts and shows. */
    private val timers = CookingTimerController(
        scope = CoroutineScope(Dispatchers.Unconfined),
        alarm = object : TimerAlarm {
            override fun start(sound: Boolean, vibrate: Boolean) = Unit

            override fun stop() = Unit
        },
        host = object : TimerHost {
            override fun update(timers: CookingTimers, now: Long) = Unit
        },
        options = flowOf(CookingTimerOptions()),
        clock = { now },
    )

    private fun viewModel(
        keepScreenOn: Boolean = true,
        servings: Int = 0,
        step: Int = 0,
        timerOptions: CookingTimerOptions = CookingTimerOptions(),
    ): CookingViewModel = CookingViewModel(
        slug = "test",
        servings = servings,
        initialStep = step,
        recipeRepository = RecipeRepository({ fake.api() }),
        mediaRepository = RecipeMediaRepository { fake.api() },
        keepScreenOn = flowOf(keepScreenOn),
        timerOptions = flowOf(timerOptions),
        timers = timers,
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
    fun `the durations of the current step are offered as timers`() = runBlocking {
        fake.enqueueJson(RECIPE)
        val vm = viewModel()
        vm.awaitSettled()

        assertTrue(vm.state.value.stepDurations.isEmpty())
        vm.goToStep(2)
        assertEquals(listOf(Duration.ofMinutes(15)), vm.state.value.stepDurations)
    }

    @Test
    fun `no timer is offered when the reader turned them off`() = runBlocking {
        fake.enqueueJson(RECIPE)
        val vm = viewModel(timerOptions = CookingTimerOptions(detectTimers = false))
        vm.awaitSettled()

        vm.goToStep(2)
        assertTrue(vm.state.value.stepDurations.isEmpty())
    }

    @Test
    fun `a timer is started for the step on screen, and named after the recipe`() = runBlocking {
        fake.enqueueJson(RECIPE)
        val vm = viewModel(servings = 4)
        vm.awaitSettled()

        vm.goToStep(2)
        vm.startTimer(Duration.ofMinutes(15))

        val timer = timers.timers.value.timers.single()
        assertEquals(2, timer.stepIndex)
        assertEquals(TimerRecipe(slug = "test", name = "Test", servings = 4), timer.recipe)
        assertEquals(listOf(1), vm.state.value.timers.timers.map { it.id })
    }

    @Test
    fun `leaving the cooking mode leaves its timers running`() = runBlocking {
        fake.enqueueJson(RECIPE)
        val store = ViewModelStore()
        val vm = ViewModelProvider.create(store, viewModelFactory { initializer { viewModel() } })[
            CookingViewModel::class,
        ]
        vm.awaitSettled()
        vm.startTimer(Duration.ofMinutes(15))

        store.clear()

        assertEquals(1, timers.timers.value.timers.size)
        assertTrue(timers.timers.value.anyRunning(now))
    }

    @Test
    fun `a timer reached zero shows as ringing until it is stopped`() = runBlocking {
        fake.enqueueJson(RECIPE)
        val vm = viewModel()
        vm.awaitSettled()

        vm.startTimer(Duration.ofSeconds(30))
        now = 31_000L
        timers.refresh()
        val ringing = withTimeout(TIMEOUT_MS) { vm.state.first { it.ringingTimers.isNotEmpty() } }
        assertEquals(listOf(1), ringing.ringingTimers.map { it.id })

        vm.dismissTimer(1)
        assertTrue(vm.state.value.timers.isEmpty)
    }

    @Test
    fun `the cooking mode opens on the step a timer asks for`() = runBlocking {
        fake.enqueueJson(RECIPE)
        val state = viewModel(step = 2).awaitSettled()
        assertEquals(2, state.currentStep)
    }

    @Test
    fun `a step beyond the recipe opens its last step`() = runBlocking {
        fake.enqueueJson(RECIPE)
        val state = viewModel(step = 9).awaitSettled()
        assertEquals(2, state.currentStep)
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
            {"id":"s3","title":"","text":"Servir après 15 min de repos.","ingredientReferences":[]}
          ]
        }
        """.trimIndent()
    }
}

