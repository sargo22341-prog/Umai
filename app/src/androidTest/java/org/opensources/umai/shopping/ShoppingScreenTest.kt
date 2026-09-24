package org.opensources.umai.shopping

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
import org.opensources.umai.core.model.ShoppingItem
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.ui.theme.UmaiTheme
import org.opensources.umai.shopping.ui.ShoppingScreen
import org.opensources.umai.shopping.ui.ShoppingUiState

@RunWith(AndroidJUnit4::class)
class ShoppingScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun render(
        state: ShoppingUiState,
        onSelectList: (String) -> Unit = {},
        onAddItem: (String) -> Unit = {},
        onCheckedChange: (ShoppingItem, Boolean) -> Unit = { _, _ -> },
        onDeleteItem: (ShoppingItem) -> Unit = {},
        onRetry: () -> Unit = {},
        onStartShoppingMode: (String) -> Unit = {},
    ) {
        rule.setContent {
            UmaiTheme {
                ShoppingScreen(
                    state = state,
                    onSelectList = onSelectList,
                    onCreateList = {},
                    onDeleteList = {},
                    onAddItem = onAddItem,
                    onCheckedChange = onCheckedChange,
                    onDeleteItem = onDeleteItem,
                    onRetry = onRetry,
                    onRefresh = {},
                    onStartShoppingMode = onStartShoppingMode,
                )
            }
        }
    }

    @Test
    fun anInstanceWithoutAnyListInvitesToCreateOne() {
        render(ShoppingUiState(loadingLists = false, lists = emptyList()))

        rule.onNodeWithText(string(R.string.shopping_no_lists_title)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.shopping_no_lists_message)).assertIsDisplayed()
    }

    @Test
    fun anEmptyListExplainsHowToFillIt() {
        render(
            ShoppingUiState(
                loadingLists = false,
                lists = listOf(TestData.shoppingListSummary()),
                selectedListId = "l1",
                list = TestData.shoppingList(items = emptyList()),
            ),
        )

        rule.onNodeWithText(string(R.string.shopping_empty_title)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.shopping_empty_message)).assertIsDisplayed()
    }

    @Test
    fun itemsAreListedAndTickedOnesAreGroupedApart() {
        render(
            ShoppingUiState(
                loadingLists = false,
                lists = listOf(TestData.shoppingListSummary()),
                selectedListId = "l1",
                list = TestData.shoppingList(
                    items = listOf(
                        TestData.shoppingItem("i1", "2 citrons", checked = false, position = 0),
                        TestData.shoppingItem("i2", "Sel", checked = true, position = 1),
                    ),
                ),
            ),
        )

        rule.onNodeWithText("2 citrons").assertIsDisplayed()
        rule.onNodeWithText("Sel").assertIsDisplayed()
        rule.onNodeWithText(string(R.string.shopping_checked_section, 1)).assertIsDisplayed()
    }

    @Test
    fun itemsAreGroupedUnderTheirMealieLabel() {
        render(
            ShoppingUiState(
                loadingLists = false,
                lists = listOf(TestData.shoppingListSummary()),
                selectedListId = "l1",
                list = TestData.shoppingList(
                    items = listOf(
                        TestData.shoppingItem("i1", "2 citrons", labelName = "Fruits"),
                        TestData.shoppingItem("i2", "Pain", position = 1),
                    ),
                ),
            ),
        )

        rule.onNodeWithText("Fruits").assertIsDisplayed()
        rule.onNodeWithText(string(R.string.shopping_unlabelled)).assertIsDisplayed()
    }

    @Test
    fun tickingAnItemIsReportedWithTheNewValue() {
        var ticked: Pair<String, Boolean>? = null
        val item = TestData.shoppingItem("i1", "2 citrons", checked = false)
        render(
            ShoppingUiState(
                loadingLists = false,
                lists = listOf(TestData.shoppingListSummary()),
                selectedListId = "l1",
                list = TestData.shoppingList(items = listOf(item)),
            ),
            onCheckedChange = { changed, value -> ticked = changed.id to value },
        )

        rule.onAllNodes(androidx.compose.ui.test.isToggleable())[0].assertIsOff().performClick()

        assertEquals("i1" to true, ticked)
    }

    @Test
    fun anAlreadyTickedItemIsShownAsDone() {
        render(
            ShoppingUiState(
                loadingLists = false,
                lists = listOf(TestData.shoppingListSummary()),
                selectedListId = "l1",
                list = TestData.shoppingList(
                    items = listOf(TestData.shoppingItem("i1", "Sel", checked = true)),
                ),
            ),
        )

        rule.onAllNodes(androidx.compose.ui.test.isToggleable())[0].assertIsOn()
    }

    @Test
    fun addingAnItemSendsTheTypedText() {
        var added: String? = null
        render(
            ShoppingUiState(
                loadingLists = false,
                lists = listOf(TestData.shoppingListSummary()),
                selectedListId = "l1",
                list = TestData.shoppingList(items = emptyList()),
            ),
            onAddItem = { added = it },
        )

        rule.onAllNodes(hasSetTextAction())[0].performTextInput("Pain")
        rule.onNodeWithContentDescription(string(R.string.shopping_add_item)).performClick()

        assertEquals("Pain", added)
    }

    @Test
    fun severalListsCanBeSwitchedBetween() {
        var selected: String? = null
        render(
            ShoppingUiState(
                loadingLists = false,
                lists = listOf(
                    TestData.shoppingListSummary("l1", "Cellier"),
                    TestData.shoppingListSummary("l2", "Habituels"),
                ),
                selectedListId = "l1",
                list = TestData.shoppingList(items = emptyList()),
            ),
            onSelectList = { selected = it },
        )

        rule.onNodeWithText("Habituels").performClick()

        assertEquals("l2", selected)
    }

    @Test
    fun anUnreachableInstanceOffersARetry() {
        var retried = false
        render(
            ShoppingUiState(loadingLists = false, error = NetworkError.Unreachable),
            onRetry = { retried = true },
        )

        rule.onNodeWithText(string(R.string.error_unreachable_title)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.action_retry)).performClick()

        assertTrue(retried)
    }

    @Test
    fun aListWithItemsOffersTheShoppingMode() {
        var started: String? = null
        render(
            ShoppingUiState(
                loadingLists = false,
                lists = listOf(TestData.shoppingListSummary()),
                selectedListId = "l1",
                list = TestData.shoppingList(),
            ),
            onStartShoppingMode = { started = it },
        )

        rule.onNodeWithText(string(R.string.shopping_mode_title)).performClick()

        assertEquals("l1", started)
    }

    @Test
    fun anEmptyListOffersNoShoppingMode() {
        render(
            ShoppingUiState(
                loadingLists = false,
                lists = listOf(TestData.shoppingListSummary()),
                selectedListId = "l1",
                list = TestData.shoppingList(items = emptyList()),
            ),
        )

        rule.onNodeWithText(string(R.string.shopping_mode_title)).assertDoesNotExist()
    }
}
