package org.opensources.umai.recipe

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.umai.R
import org.opensources.umai.TestData
import org.opensources.umai.core.model.Recipe
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.ui.theme.UmaiTheme
import org.opensources.umai.recipe.ui.RecipeDetailScaffold
import org.opensources.umai.recipe.ui.RecipeDetailUiState

@RunWith(AndroidJUnit4::class)
class RecipeDetailScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

    @OptIn(ExperimentalMaterial3Api::class)
    private fun render(
        state: RecipeDetailUiState,
        onStartCooking: (String) -> Unit = {},
        onBack: () -> Unit = {},
        onRetry: () -> Unit = {},
        imageUrl: (Recipe) -> String? = { "https://mealie.lan/photo.webp" },
    ) {
        rule.setContent {
            UmaiTheme {
                RecipeDetailScaffold(
                    state = state,
                    scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState()),
                    snackbarHostState = SnackbarHostState(),
                    onBack = onBack,
                    onStartCooking = onStartCooking,
                    onToggleFavorite = {},
                    onOpenShoppingLists = {},
                    onOpenPlanPicker = {},
                    onRetry = onRetry,
                    imageUrl = imageUrl,
                    stepImageUrl = { _, source -> "https://mealie.lan/$source" },
                    onOpenSource = {},
                )
            }
        }
    }

    private val recipe = TestData.recipe(
        summary = TestData.summary(name = "Poulet au curry", servings = 4.0, totalTime = "PT25M"),
        ingredients = listOf(
            TestData.ingredient("ref-1", "2 citrons"),
            TestData.ingredient("ref-2", "70 grammes riz"),
        ),
        steps = listOf(
            TestData.step("s1", text = "Cuire le riz."),
            TestData.step("s2", text = "Melanger.", images = listOf("etape2.jpg")),
        ),
    )

    @Test
    fun theRecipeShowsItsTitleIngredientsAndInstructions() {
        render(RecipeDetailUiState(recipe = recipe, loading = false))

        // The name appears both in the top bar and in the page header.
        rule.onAllNodesWithText("Poulet au curry").onFirst().assertExists()
        rule.onNodeWithText(string(R.string.recipe_ingredients)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("2 citrons").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(string(R.string.recipe_instructions)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Cuire le riz.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun timesAndServingsAreShownInReadableUnits() {
        render(RecipeDetailUiState(recipe = recipe, loading = false))

        rule.onNodeWithText("25 ${string(R.string.unit_minute_short)}").assertExists()
        rule.onNodeWithText(
            context.resources.getQuantityString(R.plurals.plural_servings, 4, 4),
        ).assertExists()
    }

    @Test
    fun aRecipeWithAPictureDescribesIt() {
        render(RecipeDetailUiState(recipe = recipe, loading = false))

        rule.onNodeWithContentDescription(string(R.string.cd_recipe_image, "Poulet au curry"))
            .assertExists()
    }

    @Test
    fun aRecipeWithoutPictureFallsBackToAPlaceholder() {
        val withoutImage = TestData.recipe(
            summary = TestData.summary(name = "Sans photo", imageToken = null),
        )
        render(RecipeDetailUiState(recipe = withoutImage, loading = false), imageUrl = { null })

        rule.onNodeWithContentDescription(string(R.string.cd_recipe_no_image)).assertExists()
    }

    @Test
    fun aStepPictureIsRenderedAsAnImage() {
        render(RecipeDetailUiState(recipe = recipe, loading = false))

        rule.onNodeWithContentDescription(string(R.string.cd_step_image, 2))
            .performScrollTo()
            .assertExists()
    }

    @Test
    fun cookingModeIsOfferedForARecipeWithSteps() {
        var started: String? = null
        render(RecipeDetailUiState(recipe = recipe, loading = false), onStartCooking = { started = it })

        rule.onNodeWithText(string(R.string.recipe_cook_mode), useUnmergedTree = true)
            .performClick()

        assertEquals(recipe.slug, started)
    }

    @Test
    fun cookingModeIsHiddenWhenThereIsNothingToFollow() {
        render(
            RecipeDetailUiState(recipe = TestData.recipe(steps = emptyList()), loading = false),
        )

        rule.onNodeWithText(string(R.string.recipe_cook_mode), useUnmergedTree = true)
            .assertDoesNotExist()
        rule.onNodeWithText(string(R.string.recipe_no_instructions)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aRecipeWithoutIngredientSaysSo() {
        render(
            RecipeDetailUiState(
                recipe = TestData.recipe(ingredients = emptyList()),
                loading = false,
            ),
        )

        rule.onNodeWithText(string(R.string.recipe_no_ingredients)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aMissingRecipeIsExplained() {
        render(RecipeDetailUiState(loading = false, error = NetworkError.NotFound))

        rule.onNodeWithText(string(R.string.error_not_found_title)).assertIsDisplayed()
    }

    @Test
    fun goingBackIsAlwaysAvailable() {
        var back = false
        render(RecipeDetailUiState(recipe = recipe, loading = false), onBack = { back = true })

        rule.onNodeWithContentDescription(string(R.string.action_back)).performClick()

        assertTrue(back)
    }
}
