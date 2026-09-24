package org.opensources.umai.cooking

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.umai.R
import org.opensources.umai.TestData
import org.opensources.umai.cooking.domain.CookingTimers
import org.opensources.umai.cooking.ui.CookingScreen
import org.opensources.umai.cooking.ui.CookingUiState
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.settings.CookingTimerOptions
import org.opensources.umai.core.ui.theme.UmaiTheme
import org.opensources.umai.recipe.domain.StepClip
import java.time.Duration

/** Cooking mode: one step at a time, with and without step pictures. */
@RunWith(AndroidJUnit4::class)
class CookingScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

    private val recipe = TestData.recipe(
        ingredients = listOf(
            TestData.ingredient("ref-1", "2 citrons"),
            TestData.ingredient("ref-2", "Huile d'olive"),
        ),
        steps = listOf(
            TestData.step("s1", text = "Presser les citrons.", ingredientRefs = listOf("ref-1")),
            TestData.step(
                id = "s2",
                title = "Avec une photo",
                text = "Ajouter l'huile.",
                images = listOf("etape2.jpg"),
                ingredientRefs = listOf("ref-2"),
            ),
            TestData.step("s3", text = "Servir aussitot."),
        ),
    )

    private fun render(
        state: CookingUiState,
        onNext: () -> Unit = {},
        onPrevious: () -> Unit = {},
        onGoToStep: (Int) -> Unit = {},
        onExit: () -> Unit = {},
        stepImageUrl: (String) -> String? = { "https://mealie.lan/api/media/$it" },
        clip: StepClip? = null,
        onMarkCooked: () -> Unit = {},
        onStartTimer: (Duration) -> Unit = {},
        onDismissTimer: (Int) -> Unit = {},
    ) {
        rule.setContent {
            UmaiTheme {
                CookingScreen(
                    state = state,
                    clip = clip,
                    onExit = onExit,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onGoToStep = onGoToStep,
                    onRetry = {},
                    onMarkCooked = onMarkCooked,
                    onDismissMarkError = {},
                    stepImageUrl = stepImageUrl,
                    stepPhotoUrl = { "https://mealie.lan/api/media/assets/$it" },
                    // The real player needs a network video; its place is what matters here.
                    videoContent = { stepClip, number -> Text("video $number from ${stepClip.start}") },
                    onStartTimer = onStartTimer,
                    onDismissTimer = onDismissTimer,
                )
            }
        }
    }

    @Test
    fun theFirstStepIsShownWithItsPosition() {
        render(CookingUiState(recipe = recipe, currentStep = 0, loading = false))

        rule.onNodeWithText(string(R.string.cooking_step_position, 1, 3)).assertIsDisplayed()
        rule.onNodeWithText("Presser les citrons.").assertIsDisplayed()
    }

    @Test
    fun onlyTheIngredientsOfTheStepAreListed() {
        render(CookingUiState(recipe = recipe, currentStep = 0, loading = false))

        rule.onNodeWithText(string(R.string.cooking_ingredients_for_step)).assertIsDisplayed()
        rule.onNodeWithText("2 citrons").assertIsDisplayed()
        rule.onNodeWithText("Huile d'olive").assertDoesNotExist()
    }

    @Test
    fun aStepWithAPictureShowsIt() {
        render(CookingUiState(recipe = recipe, currentStep = 1, loading = false))

        rule.onNodeWithContentDescription(string(R.string.cd_step_image, 2)).assertExists()
        rule.onNodeWithText("Avec une photo").assertIsDisplayed()
    }

    @Test
    fun aStepWithoutPictureShowsOnlyItsText() {
        render(CookingUiState(recipe = recipe, currentStep = 2, loading = false))

        rule.onNodeWithText("Servir aussitot.").assertIsDisplayed()
        rule.onNodeWithContentDescription(string(R.string.cd_step_image, 3)).assertDoesNotExist()
    }

    @Test
    fun theFirstStepCannotGoBack() {
        render(CookingUiState(recipe = recipe, currentStep = 0, loading = false))

        rule.onNodeWithText(string(R.string.cooking_previous)).assertIsNotEnabled()
        rule.onNodeWithText(string(R.string.cooking_next)).assertIsEnabled()
    }

    @Test
    fun theLastStepOffersToFinish() {
        render(CookingUiState(recipe = recipe, currentStep = 2, loading = false))

        rule.onNodeWithText(string(R.string.cooking_finish)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.cooking_next)).assertDoesNotExist()
    }

    @Test
    fun movingToTheNextStepIsReported() {
        var advanced = false
        render(
            CookingUiState(recipe = recipe, currentStep = 0, loading = false),
            onNext = { advanced = true },
        )

        rule.onNodeWithText(string(R.string.cooking_next)).performClick()

        assertTrue(advanced)
    }

    @Test
    fun theStepListJumpsToAChosenStep() {
        var target = -1
        render(
            CookingUiState(recipe = recipe, currentStep = 0, loading = false),
            onGoToStep = { target = it },
        )

        rule.onNodeWithContentDescription(string(R.string.cooking_steps)).performClick()
        rule.onNodeWithText("Servir aussitot.").performClick()

        assertEquals(2, target)
    }

    @Test
    fun aStepWithAVideoPlaysItsOwnChapter() {
        render(
            CookingUiState(recipe = recipe, currentStep = 0, loading = false),
            clip = StepClip("https://cdn.example/video.mp4", start = 9.1, end = 16.3),
        )

        rule.onNodeWithText("video 1 from 9.1").assertIsDisplayed()
    }

    @Test
    fun aVideoDoesNotMakeUpAPhotoForAStepThatHasNone() {
        render(
            CookingUiState(recipe = recipe, currentStep = 2, loading = false),
            clip = StepClip("https://cdn.example/video.mp4", start = 30.0, end = null),
        )

        rule.onNodeWithText("video 3 from 30.0").assertIsDisplayed()
        rule.onNodeWithContentDescription(string(R.string.cd_step_image, 3)).assertDoesNotExist()
    }

    @Test
    fun theOwnPhotoOfAStepIsShown() {
        val withPhoto = TestData.recipe(steps = listOf(TestData.step("s1", text = "Couper.", photo = "step-1.jpg")))

        render(CookingUiState(recipe = withPhoto, currentStep = 0, loading = false))

        rule.onNodeWithContentDescription(string(R.string.cd_step_image, 1)).assertIsDisplayed()
    }

    @Test
    fun finishingOffersToMarkTheRecipeAsCooked() {
        var marked = false
        render(CookingUiState(recipe = recipe, currentStep = 2, loading = false), onMarkCooked = { marked = true })

        rule.onNodeWithText(string(R.string.cooking_finish)).performClick()
        rule.onNodeWithText(string(R.string.cooking_done_title)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.cooking_mark_cooked)).performClick()

        assertTrue(marked)
    }

    @Test
    fun finishingCanLeaveWithoutMarkingAnything() {
        var exited = false
        var marked = false
        render(
            CookingUiState(recipe = recipe, currentStep = 2, loading = false),
            onExit = { exited = true },
            onMarkCooked = { marked = true },
        )

        rule.onNodeWithText(string(R.string.cooking_finish)).performClick()
        rule.onNodeWithText(string(R.string.cooking_leave)).performClick()

        assertTrue(exited)
        assertTrue(!marked)
    }

    @Test
    fun leavingCookingModeIsAlwaysOneTapAway() {
        var exited = false
        render(
            CookingUiState(recipe = recipe, currentStep = 1, loading = false),
            onExit = { exited = true },
        )

        rule.onNodeWithContentDescription(string(R.string.cooking_exit)).performClick()

        assertTrue(exited)
    }

    @Test
    fun aRecipeWithoutInstructionSaysSoInsteadOfShowingAnEmptyScreen() {
        render(CookingUiState(recipe = TestData.recipe(steps = emptyList()), loading = false))

        rule.onNodeWithText(string(R.string.cooking_no_steps)).assertIsDisplayed()
    }

    @Test
    fun aNetworkFailureIsExplainedWithARetry() {
        render(CookingUiState(loading = false, error = NetworkError.Unreachable))

        rule.onNodeWithText(string(R.string.error_unreachable_title)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.action_retry)).assertIsDisplayed()
    }

    private val timedRecipe = TestData.recipe(steps = listOf(TestData.step(text = "Cuire 15 min à feu doux.")))

    @Test
    fun aDurationWrittenInTheStepOffersATimer() {
        var started: Duration? = null
        render(CookingUiState(recipe = timedRecipe, loading = false), onStartTimer = { started = it })

        rule.onNodeWithText(string(R.string.cooking_timer_start, "15 min")).performClick()

        assertEquals(Duration.ofMinutes(15), started)
    }

    @Test
    fun noTimerIsOfferedWhenTheyAreTurnedOff() {
        render(
            CookingUiState(
                recipe = timedRecipe,
                loading = false,
                timerOptions = CookingTimerOptions(detectTimers = false),
            ),
        )

        rule.onNodeWithText(string(R.string.cooking_timer_start, "15 min")).assertDoesNotExist()
    }

    @Test
    fun runningTimersCountDownTogether() {
        val timers = CookingTimers()
            .start(stepIndex = 0, duration = Duration.ofMinutes(15), now = 0)
            .start(stepIndex = 0, duration = Duration.ofMinutes(5), now = 0)
        render(CookingUiState(recipe = timedRecipe, loading = false, timers = timers, now = 60_000))

        rule.onNodeWithText("14:00").assertIsDisplayed()
        rule.onNodeWithText("4:00").assertIsDisplayed()
    }

    @Test
    fun aFinishedTimerRingsUntilItIsStopped() {
        var stopped: Int? = null
        val timers = CookingTimers().start(stepIndex = 0, duration = Duration.ofSeconds(30), now = 0)
        render(
            CookingUiState(recipe = timedRecipe, loading = false, timers = timers, now = 31_000),
            onDismissTimer = { stopped = it },
        )

        rule.onNodeWithText(string(R.string.cooking_timer_done)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.cooking_timer_stop)).performClick()

        assertEquals(1, stopped)
    }

    @Test
    fun leavingWithTimersRunningAsksFirst() {
        var left = false
        val timers = CookingTimers().start(stepIndex = 0, duration = Duration.ofMinutes(15), now = 0)
        render(
            CookingUiState(recipe = timedRecipe, loading = false, timers = timers, now = 0),
            onExit = { left = true },
        )

        rule.onNodeWithContentDescription(string(R.string.cooking_exit)).performClick()
        assertFalse(left)
        rule.onNodeWithText(string(R.string.cooking_timer_exit_title)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.cooking_leave)).performClick()

        assertTrue(left)
    }
}
