package org.opensources.umai.navigation

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
import org.opensources.umai.core.ui.theme.UmaiTheme

/** The five destinations, with search promoted to the centre. */
@RunWith(AndroidJUnit4::class)
class UmaiBottomBarTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(id: Int) = context.getString(id)

    private fun render(
        selected: TopLevelTab? = TopLevelTab.HOME,
        searchSelected: Boolean = false,
        onSelect: (TopLevelTab) -> Unit = {},
        onSearch: () -> Unit = {},
    ) {
        rule.setContent {
            UmaiTheme {
                UmaiBottomBar(
                    selected = selected,
                    searchSelected = searchSelected,
                    onSelect = onSelect,
                    onSearch = onSearch,
                )
            }
        }
    }

    @Test
    fun theFiveDestinationsAreShown() {
        render()

        rule.onNodeWithText(string(R.string.nav_home)).assertExists()
        rule.onNodeWithText(string(R.string.nav_planning)).assertExists()
        rule.onNodeWithText(string(R.string.nav_search)).assertExists()
        rule.onNodeWithText(string(R.string.nav_shopping)).assertExists()
        rule.onNodeWithText(string(R.string.nav_settings)).assertExists()
    }

    @Test
    fun searchSitsBetweenPlanningAndShopping() {
        render()

        val home = rule.onNodeWithText(string(R.string.nav_home)).fetchSemanticsNode()
        val planning = rule.onNodeWithText(string(R.string.nav_planning)).fetchSemanticsNode()
        val search = rule.onNodeWithText(string(R.string.nav_search)).fetchSemanticsNode()
        val shopping = rule.onNodeWithText(string(R.string.nav_shopping)).fetchSemanticsNode()
        val settings = rule.onNodeWithText(string(R.string.nav_settings)).fetchSemanticsNode()

        val order = listOf(home, planning, search, shopping, settings).map { it.positionInRoot.x }
        assertEquals(order.sorted(), order)
    }

    @Test
    fun selectingATabIsReported() {
        var chosen: TopLevelTab? = null
        render(onSelect = { chosen = it })

        rule.onNodeWithText(string(R.string.nav_planning)).performClick()

        assertEquals(TopLevelTab.PLANNING, chosen)
    }

    @Test
    fun theCentralSearchButtonHasItsOwnCallback() {
        var searched = false
        render(onSearch = { searched = true })

        rule.onNodeWithText(string(R.string.nav_search)).performClick()

        assertTrue(searched)
    }

    @Test
    fun theCurrentTabIsMarkedAsSelectedForAccessibility() {
        render(selected = TopLevelTab.SHOPPING)

        rule.onNodeWithText(string(R.string.nav_shopping)).assertIsSelected()
    }

    @Test
    fun theSearchDestinationCanAlsoBeSelected() {
        render(selected = null, searchSelected = true)

        rule.onNodeWithText(string(R.string.nav_search)).assertIsSelected()
    }
}
