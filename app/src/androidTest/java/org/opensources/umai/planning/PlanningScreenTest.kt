package org.opensources.umai.planning

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.umai.R
import org.opensources.umai.TestData
import org.opensources.umai.core.model.MealPlanEntry
import org.opensources.umai.core.model.MealType
import org.opensources.umai.core.model.RecipeSummary
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.ui.theme.UmaiTheme
import org.opensources.umai.planning.ui.AddMealActions
import org.opensources.umai.planning.ui.PlanningScreen
import org.opensources.umai.planning.ui.PlanningUiState
import org.opensources.umai.planning.ui.RandomRecipeState
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

/** The week from its first day (Monday unless Mealie says otherwise), opened on today, highlighted. */
@RunWith(AndroidJUnit4::class)
class PlanningScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val locale = context.resources.configuration.locales[0]

    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

    /** A Thursday, so yesterday and tomorrow are in the same week. */
    private val today: LocalDate = LocalDate.of(2026, 9, 24)
    private val monday: LocalDate = LocalDate.of(2026, 9, 21)

    private fun state(
        weekStart: LocalDate = monday,
        entries: Map<LocalDate, List<MealPlanEntry>> = emptyMap(),
        error: NetworkError? = null,
        firstDay: DayOfWeek = DayOfWeek.MONDAY,
    ) = PlanningUiState(
        today = today,
        firstDay = firstDay,
        weekStart = weekStart,
        entriesByDay = entries,
        loading = false,
        error = error,
    )

    private fun render(
        state: PlanningUiState,
        random: RandomRecipeState = RandomRecipeState(),
        onDeleteEntry: (MealPlanEntry) -> Unit = {},
        onBackToToday: () -> Unit = {},
        onRetry: () -> Unit = {},
        onSearchRecipe: (LocalDate, MealType, Float) -> Unit = { _, _, _ -> },
        onAddRecipe: (LocalDate, MealType, RecipeSummary) -> Unit = { _, _, _ -> },
        onAddNote: (LocalDate, MealType, String) -> Unit = { _, _, _ -> },
        onDrawRandom: () -> Unit = {},
    ) {
        rule.setContent {
            UmaiTheme {
                PlanningScreen(
                    state = state,
                    random = random,
                    onRecipeClick = {},
                    onPreviousWeek = {},
                    onNextWeek = {},
                    onBackToToday = onBackToToday,
                    onRetry = onRetry,
                    onRefresh = {},
                    addMealActions = AddMealActions(
                        onSearchRecipe = onSearchRecipe,
                        onAddRecipe = onAddRecipe,
                        onAddNote = onAddNote,
                        onOpen = {},
                        onClose = {},
                        onSelectRandomCategory = {},
                        onDrawRandom = onDrawRandom,
                    ),
                    onDeleteEntry = onDeleteEntry,
                    recipeImageUrl = { null },
                )
            }
        }
    }

    private fun dayName(day: DayOfWeek): String =
        day.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }

    @Test
    fun todayIsInViewWithTheDayBeforeOnItsLeft() {
        render(state())

        rule.onNodeWithText(string(R.string.planning_today)).assertIsDisplayed()
        val yesterday = rule.onNodeWithText(string(R.string.planning_yesterday)).fetchSemanticsNode()
        val todayNode = rule.onNodeWithText(string(R.string.planning_today)).fetchSemanticsNode()
        assertTrue(
            "yesterday must sit before today",
            yesterday.positionInRoot.x < todayNode.positionInRoot.x,
        )
        rule.onNodeWithText(string(R.string.planning_tomorrow)).assertExists()
    }

    @Test
    fun theWeekShownIsNamedByItsMonday() {
        render(state())

        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        rule.onNodeWithText(string(R.string.planning_week_of, monday.format(formatter))).assertIsDisplayed()
    }

    @Test
    fun anotherWeekOpensOnItsMonday() {
        render(state(weekStart = monday.plusWeeks(1)))

        rule.onNodeWithText(dayName(DayOfWeek.MONDAY)).assertIsDisplayed()
    }

    @Test
    fun aWeekStartingOnSundayIsNamedAndOpenedByItsSunday() {
        val sunday = LocalDate.of(2026, 9, 27)
        render(state(weekStart = sunday, firstDay = DayOfWeek.SUNDAY))

        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        rule.onNodeWithText(string(R.string.planning_week_of, sunday.format(formatter))).assertIsDisplayed()
        rule.onNodeWithText(dayName(DayOfWeek.SUNDAY)).assertIsDisplayed()
    }

    @Test
    fun aDayWithoutMealSaysSo() {
        render(state())

        // Every empty day of the week carries the same label.
        rule.onAllNodesWithText(string(R.string.planning_empty_day)).onFirst().assertExists()
    }

    @Test
    fun aPlannedRecipeIsShownUnderItsMealSlot() {
        render(state(entries = mapOf(today to listOf(TestData.planEntry(date = today)))))

        rule.onNodeWithText("Poulet au curry").assertIsDisplayed()
        rule.onNodeWithText(string(R.string.meal_dinner)).assertIsDisplayed()
    }

    @Test
    fun aFreeTextEntryIsShownAsTyped() {
        val note = TestData.planEntry(date = today, type = MealType.LUNCH, recipe = null, title = "Restaurant")
        render(state(entries = mapOf(today to listOf(note))))

        rule.onNodeWithText("Restaurant").assertIsDisplayed()
        rule.onNodeWithText(string(R.string.meal_lunch)).assertIsDisplayed()
    }

    @Test
    fun anEntryCanBeRemovedFromThePlan() {
        var deleted: MealPlanEntry? = null
        val entry = TestData.planEntry(date = today)
        render(state(entries = mapOf(today to listOf(entry))), onDeleteEntry = { deleted = it })

        rule.onNodeWithContentDescription(string(R.string.planning_delete_entry)).performClick()

        assertTrue(deleted?.id == entry.id)
    }

    @Test
    fun aShortcutComesBackToToday() {
        var back = false
        render(state(weekStart = monday.plusWeeks(1)), onBackToToday = { back = true })

        rule.onNodeWithContentDescription(string(R.string.planning_back_to_today)).performClick()

        assertTrue(back)
    }

    @Test
    fun aFailureIsExplainedWithARetry() {
        var retried = false
        render(state(error = NetworkError.Timeout), onRetry = { retried = true })

        rule.onNodeWithText(string(R.string.error_timeout_title)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.action_retry)).performClick()

        assertTrue(retried)
    }

    @Test
    fun theRecipeFieldOpensTheFullSearchForTheChosenMeal() {
        var searched: Pair<LocalDate, MealType>? = null
        render(state(), onSearchRecipe = { date, type, _ -> searched = date to type })

        rule.onAllNodesWithText(string(R.string.planning_add_meal)).onFirst().performClick()
        rule.onNodeWithText(string(R.string.meal_lunch)).performClick()
        rule.onNodeWithText(string(R.string.planning_search_recipe)).performClick()

        assertEquals(MealType.LUNCH, searched?.second)
        assertTrue(searched?.first in state().days)
    }

    @Test
    fun aRecipeCanBeDrawnAtRandom() {
        var drawn = false
        render(state(), onDrawRandom = { drawn = true })

        rule.onAllNodesWithText(string(R.string.planning_add_meal)).onFirst().performClick()
        rule.onNodeWithText(string(R.string.planning_random_all_categories)).assertExists()
        rule.onNodeWithText(string(R.string.planning_random_draw)).performClick()

        assertTrue(drawn)
    }

    @Test
    fun theDrawnRecipeIsAddedWithOneTap() {
        var added: RecipeSummary? = null
        render(
            state(),
            random = RandomRecipeState(recipe = TestData.summary(name = "Tarte tatin")),
            onAddRecipe = { _, _, recipe -> added = recipe },
        )

        rule.onAllNodesWithText(string(R.string.planning_add_meal)).onFirst().performClick()
        rule.onNodeWithText(string(R.string.planning_random_again)).assertExists()
        rule.onNodeWithText("Tarte tatin").performClick()

        assertEquals("Tarte tatin", added?.name)
    }

    @Test
    fun aNoteIsAddedAsTyped() {
        var note: String? = null
        render(state(), onAddNote = { _, _, text -> note = text })

        rule.onAllNodesWithText(string(R.string.planning_add_meal)).onFirst().performClick()
        rule.onNode(hasSetTextAction() and hasText(string(R.string.planning_note_placeholder)))
            .performTextInput("Restaurant ")
        rule.onAllNodesWithText(string(R.string.action_add)).onFirst().performClick()

        assertEquals("Restaurant", note)
    }
}
