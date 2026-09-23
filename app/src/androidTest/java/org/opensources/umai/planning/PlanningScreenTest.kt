package org.opensources.umai.planning

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.umai.R
import org.opensources.umai.TestData
import org.opensources.umai.core.model.MealPlanEntry
import org.opensources.umai.core.model.MealType
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.ui.theme.UmaiTheme
import org.opensources.umai.planning.ui.PlanningScreen
import org.opensources.umai.planning.ui.PlanningUiState
import org.opensources.umai.planning.ui.RecipePickerState
import java.time.LocalDate

/** The week view, with yesterday first and today highlighted in second place. */
@RunWith(AndroidJUnit4::class)
class PlanningScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(id: Int) = context.getString(id)

    private val today: LocalDate = LocalDate.now()

    private fun render(
        state: PlanningUiState,
        onDeleteEntry: (MealPlanEntry) -> Unit = {},
        onBackToToday: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        rule.setContent {
            UmaiTheme {
                PlanningScreen(
                    state = state,
                    picker = RecipePickerState(),
                    onRecipeClick = {},
                    onPreviousWeek = {},
                    onNextWeek = {},
                    onBackToToday = onBackToToday,
                    onRetry = onRetry,
                    onRefresh = {},
                    onPickerQueryChange = {},
                    onResetPicker = {},
                    onAddRecipe = { _, _, _ -> },
                    onAddNote = { _, _, _ -> },
                    onDeleteEntry = onDeleteEntry,
                    recipeImageUrl = { null },
                )
            }
        }
    }

    @Test
    fun theWindowStartsYesterdayAndShowsTodaySecond() {
        render(PlanningUiState(anchor = today, loading = false))

        val yesterday = rule.onNodeWithText(string(R.string.planning_yesterday)).fetchSemanticsNode()
        val todayNode = rule.onNodeWithText(string(R.string.planning_today)).fetchSemanticsNode()

        assertTrue(
            "yesterday must sit before today",
            yesterday.positionInRoot.x < todayNode.positionInRoot.x,
        )
    }

    @Test
    fun todayIsVisibleWithoutScrolling() {
        render(PlanningUiState(anchor = today, loading = false))

        rule.onNodeWithText(string(R.string.planning_today)).assertIsDisplayed()
    }

    @Test
    fun tomorrowIsNamedTooSoTheOrderIsUnambiguous() {
        render(PlanningUiState(anchor = today, loading = false))

        rule.onNodeWithText(string(R.string.planning_tomorrow)).assertExists()
    }

    @Test
    fun aDayWithoutMealSaysSo() {
        render(PlanningUiState(anchor = today, loading = false))

        // Every empty day of the window carries the same label.
        rule.onAllNodesWithText(string(R.string.planning_empty_day)).onFirst().assertExists()
    }

    @Test
    fun aPlannedRecipeIsShownUnderItsMealSlot() {
        render(
            PlanningUiState(
                anchor = today,
                loading = false,
                entriesByDay = mapOf(today to listOf(TestData.planEntry(date = today))),
            ),
        )

        rule.onNodeWithText("Poulet au curry").assertIsDisplayed()
        rule.onNodeWithText(string(R.string.meal_dinner)).assertIsDisplayed()
    }

    @Test
    fun aFreeTextEntryIsShownAsTyped() {
        render(
            PlanningUiState(
                anchor = today,
                loading = false,
                entriesByDay = mapOf(
                    today to listOf(
                        TestData.planEntry(
                            date = today,
                            type = MealType.LUNCH,
                            recipe = null,
                            title = "Restaurant",
                        ),
                    ),
                ),
            ),
        )

        rule.onNodeWithText("Restaurant").assertIsDisplayed()
        rule.onNodeWithText(string(R.string.meal_lunch)).assertIsDisplayed()
    }

    @Test
    fun anEntryCanBeRemovedFromThePlan() {
        var deleted: MealPlanEntry? = null
        val entry = TestData.planEntry(date = today)
        render(
            PlanningUiState(anchor = today, loading = false, entriesByDay = mapOf(today to listOf(entry))),
            onDeleteEntry = { deleted = it },
        )

        rule.onNodeWithContentDescription(string(R.string.planning_delete_entry)).performClick()

        assertTrue(deleted?.id == entry.id)
    }

    @Test
    fun everyDayOffersToAddAMeal() {
        render(PlanningUiState(anchor = today, loading = false))

        rule.onAllNodesWithText(string(R.string.planning_add_meal)).onFirst().assertExists()
    }

    @Test
    fun aShortcutComesBackToToday() {
        var back = false
        render(
            PlanningUiState(anchor = today.plusWeeks(1), loading = false),
            onBackToToday = { back = true },
        )

        rule.onNodeWithContentDescription(string(R.string.planning_back_to_today)).performClick()

        assertTrue(back)
    }

    @Test
    fun aFailureIsExplainedWithARetry() {
        var retried = false
        render(
            PlanningUiState(anchor = today, loading = false, error = NetworkError.Timeout),
            onRetry = { retried = true },
        )

        rule.onNodeWithText(string(R.string.error_timeout_title)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.action_retry)).performClick()

        assertTrue(retried)
    }
}
