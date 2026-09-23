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
        profile: ProfileTabInfo? = null,
    ) {
        rule.setContent {
            UmaiTheme {
                UmaiBottomBar(
                    selected = selected,
                    searchSelected = searchSelected,
                    onSelect = onSelect,
                    onSearch = onSearch,
                    profile = profile,
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
        rule.onNodeWithText(string(R.string.nav_profile)).assertExists()
    }

    @Test
    fun searchSitsBetweenPlanningAndShopping() {
        render()

        val home = rule.onNodeWithText(string(R.string.nav_home)).fetchSemanticsNode()
        val planning = rule.onNodeWithText(string(R.string.nav_planning)).fetchSemanticsNode()
        val search = rule.onNodeWithText(string(R.string.nav_search)).fetchSemanticsNode()
        val shopping = rule.onNodeWithText(string(R.string.nav_shopping)).fetchSemanticsNode()
        val profile = rule.onNodeWithText(string(R.string.nav_profile)).fetchSemanticsNode()

        val order = listOf(home, planning, search, shopping, profile).map { it.positionInRoot.x }
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

    @Test
    fun theProfileTabCarriesTheNameOfTheSignedInUser() {
        render(profile = ProfileTabInfo(displayName = "hiroo", avatarUrl = null, initials = "H"))

        rule.onNodeWithText("hiroo").assertExists()
        rule.onNodeWithText(string(R.string.nav_profile)).assertDoesNotExist()
    }

    @Test
    fun aNameTooLongForTheSlotDoesNotPushTheOtherTabsAround() {
        val long = "Jean-Baptiste de la Tour du Pin Chambly"
        render(profile = ProfileTabInfo(displayName = long, avatarUrl = null, initials = "J"))

        val label = rule.onNodeWithText(long).fetchSemanticsNode()
        val search = rule.onNodeWithText(string(R.string.nav_search)).fetchSemanticsNode()

        // The label is ellipsized inside its own slot rather than widening it,
        // so it never overlaps the control next to it.
        assertTrue(
            "the profile label must stay to the right of the search button",
            label.positionInRoot.x > search.positionInRoot.x + search.size.width,
        )
    }

    @Test
    fun withoutAKnownAccountTheTabFallsBackToItsLabel() {
        render(profile = null)

        rule.onNodeWithText(string(R.string.nav_profile)).assertExists()
    }
}
