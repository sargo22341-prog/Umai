package org.opensources.umai.recipe.ui

import androidx.compose.ui.test.performScrollTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.umai.R
import org.opensources.umai.core.image.CropRegion
import org.opensources.umai.core.ui.theme.UmaiTheme
import org.opensources.umai.recipe.domain.DraftChapter
import org.opensources.umai.recipe.domain.DraftStep
import org.opensources.umai.recipe.domain.DraftVideo
import org.opensources.umai.recipe.domain.EditableRecipe
import org.opensources.umai.recipe.domain.RecipeDraft
import org.opensources.umai.recipe.domain.VideoStream

/** Placing the steps of a recipe in its video, from the video tab of the editor. */
@RunWith(AndroidJUnit4::class)
class RecipeVideoSectionTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

    private val video = DraftVideo("https://www.youtube.com/watch?v=y3L14JKSSYI")

    private val draft = RecipeDraft(
        id = "pains",
        name = "Petits pains farcis",
        steps = listOf(
            DraftStep(title = "La pâte", text = "Mélanger la farine et le lait.", chapter = DraftChapter(12.0)),
            DraftStep(title = "La cuisson", text = "Cuire à la poêle.", chapter = DraftChapter(240.0, end = 200.0)),
        ),
        video = video,
    )

    private fun state(draft: RecipeDraft, stream: VideoStream? = STREAM, failed: Boolean = false) = RecipeEditUiState(
        loading = false,
        recipe = EditableRecipe(recipeId = "r1", imageToken = null, draft = draft),
        draft = draft,
        section = RecipeFormSection.VIDEO,
        videoStream = stream,
        videoStreamFailed = failed,
    )

    private val starts = mutableListOf<Pair<Int, Double?>>()
    private val ends = mutableListOf<Pair<Int, Double?>>()
    private var retries = 0

    private fun render(state: RecipeEditUiState) {
        rule.setContent {
            UmaiTheme {
                RecipeEditScreen(
                    state = state,
                    actions = RecipeFormActions(NoEditing),
                    videoActions = VideoChapterActions(
                        onStartChange = { index, seconds -> starts += index to seconds },
                        onEndChange = { index, seconds -> ends += index to seconds },
                        onRetryVideo = { retries++ },
                    ),
                    currentImageUrl = null,
                    stepPhotoUrl = { null },
                    onBack = {},
                    onSave = {},
                    onRetry = {},
                    onDismissError = {},
                    onDelete = {},
                    onDismissDeleteError = {},
                    // A still stand-in, paused at 1:23.
                    videoPlayer = { _, player ->
                        SideEffect { player.positionMillis = 83_400L }
                        Box(Modifier.size(40.dp))
                    },
                )
            }
        }
    }

    @Test
    fun theVideoTabOnlyExistsForARecipeWithAVideo() {
        render(state(draft).copy(section = RecipeFormSection.BASICS))
        // The tabs scroll: on a narrow phone the last one starts out of sight.
        rule.onNodeWithText(string(R.string.edit_section_video)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aRecipeWithoutVideoHasNoVideoTab() {
        render(state(draft.copy(video = null)).copy(section = RecipeFormSection.BASICS))
        assertEquals(0, rule.onAllNodesWithText(string(R.string.edit_section_video)).fetchSemanticsNodes().size)
    }

    @Test
    fun eachStepShowsWhereItStarts() {
        render(state(draft))

        rule.onNodeWithText("0:12").assertIsDisplayed()
        rule.onNodeWithText(string(R.string.create_step_label, 2)).assertIsDisplayed()
    }

    @Test
    fun aStepStartsWhereThePlayerIs() {
        render(state(draft))

        rule.onNodeWithContentDescription(string(R.string.edit_video_start_here, string(R.string.create_step_label, 1)))
            .performClick()

        assertEquals(listOf(0 to 83.0), starts)
    }

    @Test
    fun aTypedTimeIsRead() {
        render(state(draft))

        rule.onNodeWithText("0:12").performTextReplacement("1:05")

        assertEquals(0 to 65.0, starts.last())
    }

    @Test
    fun anEndBeforeTheStartIsExplained() {
        render(state(draft))

        rule.onNodeWithText(string(R.string.edit_video_end_before_start)).assertIsDisplayed()
    }

    @Test
    fun aStepIsTakenOutOfTheVideo() {
        render(state(draft))

        rule.onNodeWithContentDescription(string(R.string.edit_video_remove, string(R.string.create_step_label, 2)))
            .performClick()

        assertEquals(listOf(1 to null), starts)
    }

    @Test
    fun aVideoThatCannotBePlayedCanBeLookedUpAgain() {
        render(state(draft, stream = null, failed = true))

        rule.onNodeWithText(string(R.string.edit_video_unavailable)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.action_retry)).performClick()

        assertEquals(1, retries)
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

    private companion object {
        val STREAM = VideoStream("https://example.org/pains.m3u8", isHls = true)
    }
}
