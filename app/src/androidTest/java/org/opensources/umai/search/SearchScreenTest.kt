package org.opensources.umai.search

import androidx.compose.ui.test.assertIsDisplayed
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
import org.opensources.umai.core.model.PagedItems
import org.opensources.umai.core.model.RecipeSummary
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.ui.theme.UmaiTheme
import org.opensources.umai.search.domain.RecipeFilters
import org.opensources.umai.search.ui.FilterOptionsState
import org.opensources.umai.search.ui.SearchScreen
import org.opensources.umai.search.ui.SearchUiState

@RunWith(AndroidJUnit4::class)
class SearchScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun results(vararg recipes: RecipeSummary) =
        PagedItems(items = recipes.toList(), page = 1, totalPages = 1, total = recipes.size)

    private fun render(
        state: SearchUiState,
        onQueryChange: (String) -> Unit = {},
        onClearQuery: () -> Unit = {},
        onResetFilters: () -> Unit = {},
        onRecipeClick: (String) -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        rule.setContent {
            UmaiTheme {
                SearchScreen(
                    state = state,
                    filterOptions = FilterOptionsState(),
                    onRecipeClick = onRecipeClick,
                    onQueryChange = onQueryChange,
                    onClearQuery = onClearQuery,
                    onApplyFilters = {},
                    onResetFilters = onResetFilters,
                    onLoadFilterOptions = {},
                    onFoodQueryChange = {},
                    onFoodSelected = {},
                    onLoadMore = {},
                    onRetry = onRetry,
                    onRefresh = {},
                    recipeImageUrl = { null },
                )
            }
        }
    }

    @Test
    fun theEmptySearchInvitesTheUserToTypeOrFilter() {
        render(SearchUiState())

        rule.onNodeWithText(string(R.string.search_start_title)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.search_start_message)).assertIsDisplayed()
    }

    @Test
    fun typingIsForwardedToTheViewModel() {
        var typed = ""
        render(SearchUiState(), onQueryChange = { typed = it })

        rule.onAllNodes(hasSetTextAction())[0].performTextInput("curry")

        assertEquals("curry", typed)
    }

    @Test
    fun resultsAreListed() {
        render(
            SearchUiState(
                query = "curry",
                hasQueried = true,
                results = results(
                    TestData.summary(id = "r1", name = "Poulet au curry"),
                    TestData.summary(id = "r2", name = "Curry de legumes", slug = "curry-de-legumes"),
                ),
            ),
        )

        rule.onNodeWithText("Poulet au curry").assertIsDisplayed()
        rule.onNodeWithText("Curry de legumes").assertIsDisplayed()
    }

    @Test
    fun aRecipeWithoutPictureStillShowsInTheResults() {
        render(
            SearchUiState(
                query = "sans",
                hasQueried = true,
                results = results(TestData.summary(name = "Sans photo", imageToken = null)),
            ),
        )

        rule.onNodeWithText("Sans photo").assertIsDisplayed()
        rule.onNodeWithContentDescription(string(R.string.cd_recipe_no_image)).assertExists()
    }

    @Test
    fun aRecipeWithAPictureExposesItForAccessibility() {
        render(
            SearchUiState(
                query = "curry",
                hasQueried = true,
                results = results(TestData.summary(name = "Poulet au curry", imageToken = "73")),
            ),
        )

        rule.onNodeWithContentDescription(string(R.string.cd_recipe_image, "Poulet au curry"))
            .assertExists()
    }

    @Test
    fun noResultShowsTheEmptyStateRatherThanABlankPage() {
        render(SearchUiState(query = "zzzz", hasQueried = true, results = PagedItems()))

        rule.onNodeWithText(string(R.string.search_empty_title)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.search_empty_message)).assertIsDisplayed()
    }

    @Test
    fun clearingTheQueryIsOneTapAway() {
        var cleared = false
        render(SearchUiState(query = "curry"), onClearQuery = { cleared = true })

        rule.onNodeWithContentDescription(string(R.string.action_clear)).performClick()

        assertTrue(cleared)
    }

    @Test
    fun activeFiltersAreCountedAndCanBeCleared() {
        var reset = false
        render(
            SearchUiState(
                filters = RecipeFilters(minRating = 4, favoritesOnly = true),
                hasQueried = true,
                results = results(TestData.summary()),
            ),
            onResetFilters = { reset = true },
        )

        rule.onNodeWithText(
            context.resources.getQuantityString(R.plurals.plural_active_filters, 2, 2),
        ).assertIsDisplayed()

        rule.onNodeWithText(string(R.string.search_clear_filters)).performClick()
        assertTrue(reset)
    }

    @Test
    fun openingAResultIsReported() {
        var opened: String? = null
        render(
            SearchUiState(
                query = "curry",
                hasQueried = true,
                results = results(TestData.summary(name = "Poulet au curry", slug = "poulet-au-curry")),
            ),
            onRecipeClick = { opened = it },
        )

        rule.onNodeWithText("Poulet au curry").performClick()

        assertEquals("poulet-au-curry", opened)
    }

    @Test
    fun aFailingSearchOffersARetry() {
        var retried = false
        render(
            SearchUiState(query = "curry", hasQueried = true, error = NetworkError.Server(500)),
            onRetry = { retried = true },
        )

        rule.onNodeWithText(string(R.string.error_server_title)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.action_retry)).performClick()

        assertTrue(retried)
    }

    @Test
    fun theFilterSheetCanBeOpened() {
        render(SearchUiState())

        rule.onNodeWithContentDescription(string(R.string.cd_open_filters)).performClick()

        rule.onNodeWithText(string(R.string.filter_title)).assertIsDisplayed()
    }
}
