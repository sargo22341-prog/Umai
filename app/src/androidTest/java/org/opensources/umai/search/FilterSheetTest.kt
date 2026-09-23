package org.opensources.umai.search

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.umai.R
import org.opensources.umai.core.model.Organizer
import org.opensources.umai.core.ui.theme.UmaiTheme
import org.opensources.umai.recipe.domain.CalorieFilter
import org.opensources.umai.search.domain.RecipeFilters
import org.opensources.umai.search.ui.FilterOptionsState
import org.opensources.umai.search.ui.FilterSheet

@RunWith(AndroidJUnit4::class)
class FilterSheetTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(id: Int) = context.getString(id)

    private fun plural(id: Int, count: Int) = context.resources.getQuantityString(id, count, count)

    private val options = FilterOptionsState(
        categories = listOf(
            Organizer(id = "c1", name = "Desserts", slug = "desserts"),
            Organizer(id = "c2", name = "Plats", slug = "plats"),
        ),
        tags = listOf(Organizer(id = "t1", name = "Gâteau", slug = "gateau")),
    )

    private fun render(filters: RecipeFilters = RecipeFilters.None, onApply: (RecipeFilters) -> Unit = {}) {
        rule.setContent {
            UmaiTheme {
                FilterSheet(
                    filters = filters,
                    options = options,
                    onDismiss = {},
                    onApply = onApply,
                    onReset = {},
                    onFoodQueryChange = {},
                    onFoodSelected = {},
                )
            }
        }
    }

    /** The sheet is a lazy list: a section far down is only composed once scrolled to. */
    private fun scrollTo(text: String) {
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
    }

    @Test
    fun aCalorieRangeIsPickedAndAPickAgainClearsIt() {
        var applied: RecipeFilters? = null
        render(onApply = { applied = it })

        val upTo500 = context.getString(R.string.filter_calories_up_to, 500)
        scrollTo(upTo500)
        rule.onNodeWithText(upTo500).performClick()
        rule.onNodeWithText(string(R.string.action_apply)).performClick()

        assertEquals(CalorieFilter.UP_TO_500, applied?.calories)
    }

    @Test
    fun recipesWithoutCaloriesCanBeAskedFor() {
        var applied: RecipeFilters? = null
        render(onApply = { applied = it })

        scrollTo(string(R.string.filter_calories_unknown))
        rule.onNodeWithText(string(R.string.filter_calories_unknown)).performClick()
        rule.onNodeWithText(string(R.string.action_apply)).performClick()

        assertEquals(CalorieFilter.UNKNOWN, applied?.calories)
    }

    @Test
    fun categoriesAndTagsAreOnlyListedOnceSomethingIsTyped() {
        render()

        rule.onNodeWithText("Desserts").assertDoesNotExist()
        rule.onNodeWithText("Gâteau").assertDoesNotExist()
    }

    @Test
    fun typingOffersMatchingCategoriesAndPickingOneKeepsIt() {
        var applied: RecipeFilters? = null
        render(onApply = { applied = it })

        scrollTo(string(R.string.filter_search_categories))
        rule.onNodeWithText(string(R.string.filter_search_categories)).performTextInput("des")
        rule.onNodeWithText("Desserts").performClick()
        rule.onNodeWithText(string(R.string.action_apply)).performClick()

        assertEquals(setOf("c1"), applied?.categoryIds)
    }

    @Test
    fun tagsMatchWithoutAccents() {
        render()

        scrollTo(string(R.string.filter_search_tags))
        rule.onNodeWithText(string(R.string.filter_search_tags)).performTextInput("gateau")

        rule.onNodeWithText("Gâteau").assertIsDisplayed()
    }

    @Test
    fun theThirdStarKeepsRecipesRatedThreeAndMore() {
        var applied: RecipeFilters? = null
        render(onApply = { applied = it })

        rule.onNodeWithContentDescription(plural(R.plurals.filter_rating_stars, 3)).performClick()
        rule.onNodeWithText(plural(R.plurals.filter_rating_at_least, 3)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.action_apply)).performClick()

        assertEquals(3, applied?.minRating)
    }

    @Test
    fun tappingTheSelectedStarAgainClearsTheRating() {
        var applied: RecipeFilters? = null
        render(filters = RecipeFilters(minRating = 3), onApply = { applied = it })

        rule.onNodeWithContentDescription(plural(R.plurals.filter_rating_stars, 3)).performClick()
        rule.onNodeWithText(string(R.string.action_apply)).performClick()

        assertEquals(null, applied?.minRating)
    }

    @Test
    fun servingsAreNoLongerAFilter() {
        render()

        rule.onNodeWithText(string(R.string.filter_servings)).assertDoesNotExist()
    }
}
