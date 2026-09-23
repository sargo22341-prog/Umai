package org.opensources.umai.recipe

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import org.opensources.umai.core.model.RecipeComment
import org.opensources.umai.core.settings.RecipeDisplayOptions
import org.opensources.umai.core.ui.theme.UmaiTheme
import org.opensources.umai.recipe.ui.RecipeDetailScaffold
import org.opensources.umai.recipe.ui.RecipeDetailUiState

/**
 * Comments on a recipe. The bin only shows where Mealie would accept the
 * deletion: on the reader's own comment, or on any of them for an admin.
 */
@RunWith(AndroidJUnit4::class)
class RecipeCommentsTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

    @OptIn(ExperimentalMaterial3Api::class)
    private fun render(
        state: RecipeDetailUiState,
        onPostComment: (String) -> Unit = {},
        onDeleteComment: (RecipeComment) -> Unit = {},
    ) {
        rule.setContent {
            UmaiTheme {
                RecipeDetailScaffold(
                    state = state,
                    scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState()),
                    snackbarHostState = SnackbarHostState(),
                    onBack = {},
                    onStartCooking = { _, _ -> },
                    onToggleFavorite = {},
                    onRate = {},
                    onEdit = {},
                    onOpenShoppingLists = {},
                    onOpenPlanPicker = {},
                    onRetry = {},
                    onRefresh = {},
                    onServingsChange = {},
                    onPostComment = onPostComment,
                    onDeleteComment = onDeleteComment,
                    imageUrl = { null },
                    stepImageUrl = { _, _ -> null },
                    onOpenSource = {},
                )
            }
        }
    }

    private val base = RecipeDetailUiState(
        recipe = TestData.recipe(),
        loading = false,
        currentUserId = "u1",
    )

    @Test
    fun aRecipeWithoutCommentSaysSoRatherThanShowingNothing() {
        render(base)

        rule.onNodeWithText(string(R.string.recipe_comments)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(string(R.string.recipe_comments_empty)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun existingCommentsAreShownWithTheirAuthor() {
        render(base.copy(comments = listOf(TestData.comment(text = "Trop bon", authorName = "Hiroo"))))

        rule.onNodeWithText("Trop bon").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Hiroo").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun writingACommentSendsTheText() {
        var posted: String? = null
        render(base, onPostComment = { posted = it })

        rule.onNodeWithText(string(R.string.recipe_comment_placeholder))
            .performScrollTo()
            .performTextInput("Merci")
        rule.onNodeWithContentDescription(string(R.string.recipe_comment_send)).performClick()

        assertEquals("Merci", posted)
    }

    @Test
    fun theReaderCanDeleteTheirOwnComment() {
        val mine = TestData.comment(id = "c1", authorId = "u1")
        var deleted: RecipeComment? = null
        render(base.copy(comments = listOf(mine)), onDeleteComment = { deleted = it })

        rule.onNodeWithContentDescription(string(R.string.recipe_comment_delete))
            .performScrollTo()
            .performClick()

        assertEquals(mine, deleted)
    }

    @Test
    fun someoneElsesCommentOffersNoBin() {
        render(base.copy(comments = listOf(TestData.comment(authorId = "u2", authorName = "Ami"))))

        rule.onNodeWithText("Ami").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription(string(R.string.recipe_comment_delete)).assertDoesNotExist()
    }

    @Test
    fun anAdministratorCanDeleteAnyComment() {
        render(
            base.copy(
                currentUserIsAdmin = true,
                comments = listOf(TestData.comment(authorId = "u2", authorName = "Ami")),
            ),
        )

        rule.onNodeWithContentDescription(string(R.string.recipe_comment_delete))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun aRecipeWithCommentsDisabledHidesTheWholeSection() {
        render(base.copy(recipe = TestData.recipe(commentsDisabled = true)))

        rule.onNodeWithText(string(R.string.recipe_comments)).assertDoesNotExist()
        rule.onNodeWithText(string(R.string.recipe_comment_placeholder)).assertDoesNotExist()
    }

    @Test
    fun anApiTokenWithoutUserContextHidesTheSection() {
        render(base.copy(commentsSupported = false, currentUserId = null))

        rule.onNodeWithText(string(R.string.recipe_comments)).assertDoesNotExist()
    }

    @Test
    fun theFieldToAddACommentComesAfterTheExistingOnes() {
        render(base.copy(comments = listOf(TestData.comment(text = "Trop bon"))))

        // Both are measured at the same scroll position, the end of the page.
        val field = rule.onNodeWithText(string(R.string.recipe_comment_placeholder))
            .performScrollTo()
            .fetchSemanticsNode()
            .boundsInRoot
        val comment = rule.onNodeWithText("Trop bon").fetchSemanticsNode().boundsInRoot

        assertTrue("the field sits below the comments", field.top >= comment.bottom)
    }

    @Test
    fun commentsCanBeHiddenFromTheSettings() {
        render(base.copy(display = RecipeDisplayOptions(showComments = false)))

        rule.onNodeWithText(string(R.string.recipe_comments)).assertDoesNotExist()
        rule.onNodeWithText(string(R.string.recipe_comment_placeholder)).assertDoesNotExist()
    }
}
