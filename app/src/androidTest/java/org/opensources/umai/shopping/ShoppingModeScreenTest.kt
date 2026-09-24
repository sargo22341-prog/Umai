package org.opensources.umai.shopping

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.umai.R
import org.opensources.umai.TestData
import org.opensources.umai.core.model.ShoppingItem
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.ui.theme.UmaiTheme
import org.opensources.umai.shopping.ui.ShoppingModeScreen
import org.opensources.umai.shopping.ui.ShoppingUiState

/** The in-store view: one tap per item, the basket gathered at the bottom. */
@RunWith(AndroidJUnit4::class)
class ShoppingModeScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

    private val items = listOf(
        TestData.shoppingItem(id = "i1", display = "2 citrons", labelName = "Fruits"),
        TestData.shoppingItem(id = "i2", display = "Riz basmati", labelName = "Épicerie"),
        TestData.shoppingItem(id = "i3", display = "Sel", checked = true),
    )

    private fun state(list: List<ShoppingItem> = items, error: NetworkError? = null) = ShoppingUiState(
        lists = listOf(TestData.shoppingListSummary()),
        selectedListId = "l1",
        list = TestData.shoppingList(items = list),
        loadingLists = false,
        error = error,
    )

    private fun render(
        state: ShoppingUiState,
        onExit: () -> Unit = {},
        onCheckedChange: (ShoppingItem, Boolean) -> Unit = { _, _ -> },
        onRetry: () -> Unit = {},
    ) {
        rule.setContent {
            UmaiTheme {
                ShoppingModeScreen(
                    state = state,
                    onExit = onExit,
                    onCheckedChange = onCheckedChange,
                    onRetry = onRetry,
                    onErrorShown = {},
                )
            }
        }
    }

    @Test
    fun theItemsToBuyAreListedByAisleWithTheProgress() {
        render(state())

        rule.onNodeWithText("Fruits").assertIsDisplayed()
        rule.onNodeWithText("2 citrons").assertIsDisplayed()
        rule.onNodeWithText("Riz basmati").assertIsDisplayed()
        rule.onNodeWithText(string(R.string.shopping_mode_progress, 1, 3)).assertIsDisplayed()
    }

    @Test
    fun aTapAnywhereOnTheRowTicksTheItem() {
        var ticked: Pair<String, Boolean>? = null
        render(state(), onCheckedChange = { item, checked -> ticked = item.id to checked })

        rule.onNodeWithText("Riz basmati").assertIsOff().performClick()

        assertEquals("i2" to true, ticked)
    }

    @Test
    fun theBasketStaysFoldedUntilOpened() {
        var unticked: Pair<String, Boolean>? = null
        render(state(), onCheckedChange = { item, checked -> unticked = item.id to checked })

        rule.onNodeWithText("Sel").assertDoesNotExist()
        rule.onNodeWithText(string(R.string.shopping_mode_basket, 1)).performClick()
        rule.onNodeWithText("Sel").assertIsOn().performClick()

        assertEquals("i3" to false, unticked)
    }

    @Test
    fun everythingInTheBasketIsCelebratedAndCanBeLeft() {
        var left = false
        render(state(list = items.map { it.copy(checked = true) }), onExit = { left = true })

        rule.onNodeWithText(string(R.string.shopping_mode_done)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.shopping_mode_finish)).performClick()

        assertTrue(left)
    }

    @Test
    fun theModeCanBeLeftAtAnyTime() {
        var left = false
        render(state(), onExit = { left = true })

        rule.onNodeWithContentDescription(string(R.string.shopping_mode_exit)).performClick()

        assertTrue(left)
    }

    @Test
    fun aListThatCannotBeReadOffersARetry() {
        var retried = false
        render(state().copy(list = null, error = NetworkError.Timeout), onRetry = { retried = true })

        rule.onNodeWithText(string(R.string.error_timeout_title)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.action_retry)).performClick()

        assertTrue(retried)
    }
}
