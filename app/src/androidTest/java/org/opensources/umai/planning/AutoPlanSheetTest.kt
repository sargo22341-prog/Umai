package org.opensources.umai.planning

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.umai.R
import org.opensources.umai.TestData
import org.opensources.umai.core.model.MealType
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.ui.theme.UmaiTheme
import org.opensources.umai.planning.domain.MealPlanProposal
import org.opensources.umai.planning.domain.MealSlot
import org.opensources.umai.planning.domain.PlanCandidate
import org.opensources.umai.planning.domain.PlannedMeal
import org.opensources.umai.planning.domain.SharedIngredient
import org.opensources.umai.planning.ui.AutoPlanActions
import org.opensources.umai.planning.ui.AutoPlanPhase
import org.opensources.umai.planning.ui.AutoPlanScope
import org.opensources.umai.planning.ui.AutoPlanSheet
import org.opensources.umai.planning.ui.AutoPlanUiState
import java.time.LocalDate

/** Planning lunches and dinners automatically, before anything is written on Mealie. */
@RunWith(AndroidJUnit4::class)
class AutoPlanSheetTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

    private val today = LocalDate.of(2026, 9, 25)
    private val week = (0L..6L).map { LocalDate.of(2026, 9, 21).plusDays(it) }

    private val proposal = MealPlanProposal(
        meals = listOf(
            PlannedMeal(MealSlot(today, MealType.LUNCH), PlanCandidate(TestData.summary(id = "a", name = "Gratin de poireaux"), setOf("creme"), 0.6)),
            PlannedMeal(MealSlot(today, MealType.DINNER), PlanCandidate(TestData.summary(id = "b", name = "Quiche aux poireaux", imageToken = null), setOf("creme"), 0.6)),
        ),
        unfilled = emptyList(),
        ingredientCount = 5,
        shared = listOf(SharedIngredient("crème", 2)),
    )

    private val calls = mutableListOf<String>()

    private fun render(state: AutoPlanUiState) {
        rule.setContent {
            UmaiTheme {
                AutoPlanSheet(
                    state = state,
                    actions = AutoPlanActions(
                        onDismiss = { calls += "dismiss" },
                        onScopeChange = { calls += "scope:$it" },
                        onDayChange = { calls += "day:$it" },
                        onPropose = { calls += "propose" },
                        onRegenerate = { calls += "regenerate" },
                        onReplace = { calls += "replace:$it" },
                        onAccept = { calls += "accept" },
                        onOpenDishTypes = { calls += "types" },
                    ),
                    recipeImageUrl = { null },
                )
            }
        }
    }

    @Test
    fun theMealsToFillAreCountedAndAPlanAskedFor() {
        render(AutoPlanUiState(visible = true, today = today, days = week, day = today))

        rule.onNodeWithText(context.resources.getQuantityString(R.plurals.auto_plan_meals_to_fill, 6, 6)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.auto_plan_propose)).performClick()

        assertEquals(listOf("propose"), calls)
    }

    @Test
    fun aFullWeekHasNothingToPlan() {
        val full = week.associateWith { day ->
            listOf(TestData.planEntry().copy(date = day, type = MealType.LUNCH), TestData.planEntry().copy(date = day, type = MealType.DINNER))
        }
        render(AutoPlanUiState(visible = true, today = today, days = week, entriesByDay = full, day = today))

        rule.onNodeWithText(string(R.string.auto_plan_nothing_to_fill)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.auto_plan_propose)).assertIsNotEnabled()
    }

    @Test
    fun oneDayOffersTheDaysLeftInTheWeek() {
        render(AutoPlanUiState(visible = true, today = today, days = week, scope = AutoPlanScope.DAY, day = today))

        rule.onNodeWithText(string(R.string.planning_tomorrow)).performClick()

        assertEquals(listOf("day:${today.plusDays(1)}"), calls)
    }

    @Test
    fun theProposalShowsEachDishAndWhatTheyShare() {
        render(AutoPlanUiState(visible = true, today = today, days = week, day = today, proposal = proposal))

        rule.onNodeWithText("Gratin de poireaux").assertIsDisplayed()
        rule.onNodeWithText("Quiche aux poireaux").assertIsDisplayed()
        rule.onNodeWithText(string(R.string.auto_plan_shared_item, "crème", 2)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(context.resources.getQuantityString(R.plurals.auto_plan_ingredient_count, 5, 5)).assertIsDisplayed()
        // A dish without a picture still says so.
        rule.onNodeWithContentDescription(string(R.string.cd_recipe_no_image)).assertIsDisplayed()

        rule.onNodeWithContentDescription(string(R.string.auto_plan_replace, "Quiche aux poireaux")).performClick()
        rule.onNodeWithText(string(R.string.auto_plan_regenerate)).performScrollTo().performClick()
        rule.onNodeWithText(string(R.string.auto_plan_accept)).performScrollTo().performClick()

        assertEquals(listOf("replace:1", "regenerate", "accept"), calls)
    }

    @Test
    fun workInProgressAndFailuresAreShown() {
        render(
            AutoPlanUiState(
                visible = true,
                today = today,
                days = week,
                day = today,
                phase = AutoPlanPhase.RECOGNIZING,
                error = NetworkError.Unreachable,
            ),
        )

        rule.onNodeWithText(string(R.string.auto_plan_recognizing)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.auto_plan_title)).assertIsDisplayed()
        assertTrue(calls.isEmpty())
    }

    @Test
    fun noDishSendsToTheRecognizedTypes() {
        render(AutoPlanUiState(visible = true, today = today, days = week, day = today, noDishes = true))

        rule.onNodeWithText(string(R.string.auto_plan_no_dishes)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.dish_types_title)).performScrollTo().performClick()

        assertEquals(listOf("types"), calls)
    }
}
