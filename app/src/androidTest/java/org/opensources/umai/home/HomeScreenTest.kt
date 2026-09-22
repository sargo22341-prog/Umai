package org.opensources.umai.home

import androidx.compose.ui.test.assertIsDisplayed
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
import org.opensources.umai.core.model.PagedItems
import org.opensources.umai.core.model.RecipeSummary
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.settings.RecipeLayout
import org.opensources.umai.core.ui.theme.UmaiTheme
import org.opensources.umai.home.ui.HomeScreen
import org.opensources.umai.home.ui.HomeUiState

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun page(vararg recipes: RecipeSummary) =
        PagedItems(items = recipes.toList(), page = 1, totalPages = 1, total = recipes.size)

    private fun render(
        state: HomeUiState,
        onRecipeClick: (String) -> Unit = {},
        onSearchClick: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        rule.setContent {
            UmaiTheme {
                HomeScreen(
                    state = state,
                    onRecipeClick = onRecipeClick,
                    onSearchClick = onSearchClick,
                    onRefresh = {},
                    onRetry = onRetry,
                    onLoadMore = {},
                    recipeImageUrl = { null },
                )
            }
        }
    }

    @Test
    fun theLatestRecipesAreListed() {
        render(
            HomeUiState(
                loading = false,
                latest = page(
                    TestData.summary(id = "r1", name = "Poulet au curry"),
                    TestData.summary(id = "r2", name = "Tarte au citron", slug = "tarte-au-citron"),
                ),
            ),
        )

        rule.onNodeWithText(string(R.string.home_section_latest)).assertIsDisplayed()
        rule.onNodeWithText("Poulet au curry").assertIsDisplayed()
        rule.onNodeWithText("Tarte au citron").assertIsDisplayed()
    }

    @Test
    fun anInstanceWithoutRecipeShowsTheEmptyState() {
        render(HomeUiState(loading = false, latest = PagedItems()))

        rule.onNodeWithText(string(R.string.home_empty_title)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.home_empty_message)).assertIsDisplayed()
    }

    @Test
    fun aRecipeWithoutPictureUsesThePlaceholder() {
        render(
            HomeUiState(
                loading = false,
                latest = page(TestData.summary(name = "Sans photo", imageToken = null)),
            ),
        )

        rule.onNodeWithContentDescription(string(R.string.cd_recipe_no_image)).assertExists()
    }

    @Test
    fun recentlyViewedRecipesGetTheirOwnSection() {
        render(
            HomeUiState(
                loading = false,
                latest = page(TestData.summary()),
                recentlyViewed = listOf(
                    TestData.summary(id = "r9", name = "Vue recemment", slug = "vue-recemment"),
                ),
            ),
        )

        rule.onNodeWithText(string(R.string.home_section_recent)).assertIsDisplayed()
        rule.onNodeWithText("Vue recemment").assertIsDisplayed()
    }

    @Test
    fun theSearchShortcutLeadsToTheSearchScreen() {
        var searched = false
        render(
            HomeUiState(loading = false, latest = page(TestData.summary())),
            onSearchClick = { searched = true },
        )

        rule.onNodeWithText(string(R.string.home_search_hint)).performClick()

        assertTrue(searched)
    }

    @Test
    fun openingARecipeIsReported() {
        var opened: String? = null
        render(
            HomeUiState(
                loading = false,
                latest = page(TestData.summary(name = "Poulet au curry", slug = "poulet-au-curry")),
            ),
            onRecipeClick = { opened = it },
        )

        rule.onNodeWithText("Poulet au curry").performClick()

        assertEquals("poulet-au-curry", opened)
    }

    @Test
    fun theListLayoutShowsTheSameRecipes() {
        render(
            HomeUiState(
                loading = false,
                layout = RecipeLayout.LIST,
                latest = page(TestData.summary(name = "Poulet au curry")),
            ),
        )

        rule.onNodeWithText("Poulet au curry").assertIsDisplayed()
    }

    @Test
    fun anUnreachableInstanceOffersARetry() {
        var retried = false
        render(
            HomeUiState(loading = false, error = NetworkError.Unreachable),
            onRetry = { retried = true },
        )

        rule.onNodeWithText(string(R.string.error_unreachable_title)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.error_unreachable_message)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.action_retry)).performClick()

        assertTrue(retried)
    }
}
