package org.opensources.umai.recipe

import androidx.compose.ui.test.assertIsDisplayed
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
import org.opensources.umai.core.image.CropRegion
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.ui.theme.UmaiTheme
import org.opensources.umai.recipe.domain.EditableRecipe
import org.opensources.umai.recipe.domain.RecipeDraft
import org.opensources.umai.recipe.ui.IngredientLinkResult
import org.opensources.umai.recipe.ui.RecipeDraftEditing
import org.opensources.umai.recipe.ui.RecipeEditScreen
import org.opensources.umai.recipe.ui.RecipeEditUiState
import org.opensources.umai.recipe.ui.RecipeFormActions
import org.opensources.umai.recipe.ui.RecipeFormSection

/** Deleting a recipe from its editor, behind the ⋮ menu of the top bar. */
@RunWith(AndroidJUnit4::class)
class RecipeEditScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

    private val draft = RecipeDraft(id = "gratin", name = "Gratin de courgettes")

    private val loaded = RecipeEditUiState(
        loading = false,
        recipe = EditableRecipe(recipeId = "r1", imageToken = null, draft = draft),
        draft = draft,
    )

    private fun render(state: RecipeEditUiState, onDelete: () -> Unit = {}) {
        rule.setContent {
            UmaiTheme {
                RecipeEditScreen(
                    state = state,
                    actions = RecipeFormActions(NoEditing),
                    currentImageUrl = null,
                    stepPhotoUrl = { null },
                    onBack = {},
                    onSave = {},
                    onRetry = {},
                    onDismissError = {},
                    onDelete = onDelete,
                    onDismissDeleteError = {},
                )
            }
        }
    }

    private fun openDeleteDialog() {
        rule.onNodeWithContentDescription(string(R.string.edit_more_actions)).performClick()
        rule.onNodeWithText(string(R.string.edit_delete)).performClick()
    }

    @Test
    fun deletingAsksForAConfirmationThatNamesTheRecipe() {
        var deletions = 0
        render(loaded, onDelete = { deletions++ })

        openDeleteDialog()

        rule.onNodeWithText(string(R.string.edit_delete_title, "Gratin de courgettes")).assertIsDisplayed()
        assertEquals(0, deletions)
        rule.onNodeWithText(string(R.string.edit_delete_confirm)).performClick()
        assertEquals(1, deletions)
        rule.onNodeWithText(string(R.string.edit_delete_title, "Gratin de courgettes")).assertDoesNotExist()
    }

    @Test
    fun cancellingTheConfirmationDeletesNothing() {
        var deletions = 0
        render(loaded, onDelete = { deletions++ })

        openDeleteDialog()
        rule.onNodeWithText(string(R.string.action_cancel)).performClick()

        assertEquals(0, deletions)
        rule.onNodeWithText(string(R.string.edit_delete_title, "Gratin de courgettes")).assertDoesNotExist()
    }

    @Test
    fun aRefusedDeletionIsExplained() {
        render(loaded.copy(deleteError = NetworkError.Unauthorized))

        rule.onNodeWithText(string(R.string.edit_delete_failed), substring = true).assertIsDisplayed()
    }

    @Test
    fun aRecipeThatCouldNotBeOpenedOffersNoDeletion() {
        render(RecipeEditUiState(loading = false, loadError = NetworkError.NotFound))

        rule.onNodeWithContentDescription(string(R.string.edit_more_actions)).assertDoesNotExist()
    }

    private object NoEditing : RecipeDraftEditing {
        override fun editDraft(change: (RecipeDraft) -> RecipeDraft) = Unit
        override fun setImage(sourceUri: String, region: CropRegion) = Unit
        override fun removeImage() = Unit
        override fun setStepPhoto(index: Int, sourceUri: String, region: CropRegion) = Unit
        override fun removeStepPhoto(index: Int) = Unit
        override fun showSection(section: RecipeFormSection) = Unit
        override fun onIngredientsLinked(result: IngredientLinkResult) = Unit
    }
}
