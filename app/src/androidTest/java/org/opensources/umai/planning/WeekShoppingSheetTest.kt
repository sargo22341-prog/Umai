package org.opensources.umai.planning

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.umai.R
import org.opensources.umai.TestData
import org.opensources.umai.core.model.MealPlanEntry
import org.opensources.umai.core.ui.theme.UmaiTheme
import org.opensources.umai.planning.ui.WeekShoppingActions
import org.opensources.umai.planning.ui.WeekShoppingSheet
import org.opensources.umai.planning.ui.WeekShoppingStep
import org.opensources.umai.planning.ui.WeekShoppingUiState

/** Sending the planned meals to a shopping list. */
@RunWith(AndroidJUnit4::class)
class WeekShoppingSheetTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

    private val entry = TestData.planEntry()

    private fun render(state: WeekShoppingUiState, onToggle: (MealPlanEntry) -> Unit = {}, onToggleIngredient: (MealPlanEntry, Int) -> Unit = { _, _ -> }) {
        rule.setContent {
            UmaiTheme {
                WeekShoppingSheet(
                    state = state,
                    actions = WeekShoppingActions(
                        onDismiss = {},
                        onToggle = onToggle,
                        onSelectList = {},
                        onServingsChange = { _, _ -> },
                        onToggleIngredient = onToggleIngredient,
                        onBack = {},
                        onNext = {},
                        onRetry = {},
                    ),
                )
            }
        }
    }

    @Test
    fun thePlannedRecipesAreOfferedAndTicking() {
        var toggled: MealPlanEntry? = null
        render(
            WeekShoppingUiState(visible = true, entries = listOf(entry), lists = listOf(TestData.shoppingListSummary()), listId = "l1"),
            onToggle = { toggled = it },
        )

        rule.onNodeWithText(entry.recipe!!.name).performClick()

        assertEquals(entry, toggled)
    }

    @Test
    fun nothingTickedCannotGoOn() {
        render(WeekShoppingUiState(visible = true, entries = listOf(entry), listId = "l1"))

        rule.onNodeWithText(string(R.string.create_next)).assertIsNotEnabled()
    }

    @Test
    fun theLinesOfEachRecipeCanBeLeftOut() {
        var left: Int? = null
        val recipe = TestData.recipe(ingredients = listOf(TestData.ingredient("a", "2 citrons")))
        render(
            WeekShoppingUiState(
                visible = true,
                step = WeekShoppingStep.INGREDIENTS,
                entries = listOf(entry),
                selected = setOf(entry.id),
                recipes = mapOf(entry.recipe!!.slug to recipe),
                listId = "l1",
            ),
            onToggleIngredient = { _, index -> left = index },
        )

        rule.onNodeWithText("2 citrons", substring = true).performClick()

        assertEquals(0, left)
    }

    @Test
    fun theEndSaysWhereTheMealsWent() {
        render(WeekShoppingUiState(visible = true, lists = listOf(TestData.shoppingListSummary(name = "Courses")), listId = "l1", added = 3))

        rule.onNodeWithText(context.resources.getQuantityString(R.plurals.week_shopping_done, 3, 3, "Courses")).assertIsDisplayed()
    }
}
