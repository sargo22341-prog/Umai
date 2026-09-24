package org.opensources.umai.recipe

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
import org.opensources.umai.core.model.RecipeIngredient
import org.opensources.umai.core.model.ShoppingListSummary
import org.opensources.umai.core.ui.theme.UmaiTheme
import org.opensources.umai.recipe.ui.AddToShoppingListSheet

/**
 * Sending a recipe to a shopping list: which list, how many servings, and which
 * lines are actually needed.
 */
@RunWith(AndroidJUnit4::class)
class AddToShoppingListSheetTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

    private val recipe = TestData.recipe(
        summary = TestData.summary(servings = 4.0),
        ingredients = listOf(
            TestData.ingredient("ref-1", "2 citrons", quantity = 2.0, food = TestData.food()),
            TestData.ingredient(
                "ref-2",
                "200 grammes riz",
                quantity = 200.0,
                unit = TestData.unit(),
                food = TestData.food(name = "riz", pluralName = null),
            ),
        ),
    )

    private var confirmed: Triple<ShoppingListSummary, Int, List<RecipeIngredient>>? = null

    private fun render(
        lists: List<ShoppingListSummary> = listOf(
            TestData.shoppingListSummary("l1", "Cellier"),
            TestData.shoppingListSummary("l2", "Habituels"),
        ),
        loadingLists: Boolean = false,
        initialServings: Int = 4,
    ) {
        rule.setContent {
            UmaiTheme {
                AddToShoppingListSheet(
                    recipe = recipe,
                    lists = lists,
                    loadingLists = loadingLists,
                    initialServings = initialServings,
                    onDismiss = {},
                    onConfirm = { list, servings, ingredients ->
                        confirmed = Triple(list, servings, ingredients)
                    },
                )
            }
        }
    }

    @Test
    fun everyListOfTheHouseholdIsOffered() {
        render()

        rule.onNodeWithText("Cellier").assertIsDisplayed()
        rule.onNodeWithText("Habituels").assertIsDisplayed()
    }

    @Test
    fun anInstanceWithoutAnyListSaysSoAndBlocksTheAction() {
        render(lists = emptyList())

        rule.onNodeWithText(string(R.string.shopping_no_lists_message)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.action_add)).assertIsNotEnabled()
    }

    @Test
    fun theServingsStartAtWhatTheReaderChoseOnTheRecipe() {
        render(initialServings = 6)

        rule.onNodeWithText(
            context.resources.getQuantityString(R.plurals.plural_servings, 6, 6),
        ).assertIsDisplayed()
    }

    @Test
    fun changingTheServingsRescalesTheLinesAndIsReported() {
        render(initialServings = 4)

        rule.onNodeWithContentDescription(string(R.string.servings_increase)).performClick()
        rule.onNodeWithContentDescription(string(R.string.servings_increase)).performClick()

        // Six servings of a four-serving recipe: every quantity times 1.5.
        rule.onNodeWithText("3 citrons").assertIsDisplayed()
        rule.onNodeWithText(context.resources.getQuantityString(R.plurals.shopping_servings_scaled, 4, 4)).assertExists()

        rule.onNodeWithText("300 grammes riz").assertIsDisplayed()

        rule.onNodeWithText(string(R.string.action_add)).performClick()

        assertEquals(6, confirmed?.second)
    }

    @Test
    fun uncheckedLinesAreLeftOutOfTheSelection() {
        render()

        rule.onNodeWithText("2 citrons").performClick()
        rule.onNodeWithText(string(R.string.action_add)).performClick()

        val selection = confirmed?.third.orEmpty()
        assertEquals(1, selection.size)
        assertEquals("ref-2", selection.single().referenceId)
    }

    @Test
    fun uncheckingEverythingBlocksTheAction() {
        render()

        rule.onNodeWithText(string(R.string.action_select_none)).performClick()

        rule.onNodeWithText(string(R.string.action_add)).assertIsNotEnabled()
    }

    @Test
    fun theChosenListIsTheOneReported() {
        render()

        rule.onNodeWithText("Habituels").performClick()
        rule.onNodeWithText(string(R.string.action_add)).performClick()

        assertEquals("l2", confirmed?.first?.id)
    }
}
