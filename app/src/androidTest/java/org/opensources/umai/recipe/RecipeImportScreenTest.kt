package org.opensources.umai.recipe

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
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
import org.opensources.umai.TestData
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.ui.theme.UmaiTheme
import org.opensources.umai.llm.domain.LlmProgress
import org.opensources.umai.recipe.ui.ImportPhase
import org.opensources.umai.recipe.ui.RecipeImportScreen
import org.opensources.umai.recipe.ui.RecipeImportUiState
import org.opensources.umai.youtube.domain.YouTubeFailure

/** Handing a web address to Mealie's own scraper. */
@RunWith(AndroidJUnit4::class)
class RecipeImportScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun render(
        state: RecipeImportUiState,
        onImport: () -> Unit = {},
        onIncludeTagsChange: (Boolean) -> Unit = {},
        onImportAnyway: () -> Unit = {},
        onOpenExisting: (String) -> Unit = {},
    ) {
        rule.setContent {
            UmaiTheme {
                RecipeImportScreen(
                    state = state,
                    onBack = {},
                    onUrlChange = {},
                    onIncludeTagsChange = onIncludeTagsChange,
                    onIncludeCategoriesChange = {},
                    onImport = onImport,
                    onImportAnyway = onImportAnyway,
                    onOpenExisting = onOpenExisting,
                )
            }
        }
    }

    @Test
    fun importingIsRefusedUntilAnAddressIsTyped() {
        render(RecipeImportUiState(url = ""))

        rule.onNodeWithText(string(R.string.import_action)).assertIsNotEnabled()
    }

    @Test
    fun anAddressEnablesTheImport() {
        var imported = false
        render(RecipeImportUiState(url = "https://example.org/tarte"), onImport = { imported = true })

        rule.onNodeWithText(string(R.string.import_action)).assertIsEnabled().performClick()

        assertTrue(imported)
    }

    @Test
    fun bothOrganizerSwitchesAreOfferedAndReported() {
        var tags: Boolean? = null
        render(
            RecipeImportUiState(url = "https://example.org", includeTags = true),
            onIncludeTagsChange = { tags = it },
        )

        rule.onNodeWithText(string(R.string.import_include_categories)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.import_include_tags)).assertIsDisplayed()

        rule.onAllNodes(androidx.compose.ui.test.isToggleable())[0].assertIsOn().performClick()

        assertTrue(tags == false)
    }

    @Test
    fun aSwitchLeftOffStaysOff() {
        render(RecipeImportUiState(url = "https://example.org", includeCategories = false))

        rule.onAllNodes(androidx.compose.ui.test.isToggleable())[1].assertIsOff()
    }

    @Test
    fun whileImportingTheButtonSaysSoAndIsBlocked() {
        render(RecipeImportUiState(url = "https://example.org", phase = ImportPhase.IMPORTING))

        rule.onNodeWithText(string(R.string.import_running)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.import_running)).assertIsNotEnabled()
    }

    @Test
    fun aRecipeAlreadyImportedIsReportedInsteadOfImportedAgain() {
        var opened: String? = null
        var forced = false
        render(
            RecipeImportUiState(url = "https://jow.fr/recipes/x", duplicate = TestData.summary(slug = "tarte", name = "Tarte")),
            onImportAnyway = { forced = true },
            onOpenExisting = { opened = it },
        )

        rule.onNodeWithText(string(R.string.import_duplicate_title)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.import_action)).assertIsNotEnabled()
        rule.onNodeWithText(string(R.string.import_duplicate_open)).performClick()
        rule.onNodeWithText(string(R.string.import_duplicate_anyway)).performClick()

        assertEquals("tarte", opened)
        assertTrue(forced)
    }

    @Test
    fun aKnownProviderSaysItsMediaWillFollow() {
        render(RecipeImportUiState(url = "https://jow.fr/recipes/x", providerName = "Jow", providerOffersVideo = true))

        rule.onNodeWithText(string(R.string.import_provider_hint, "Jow")).assertIsDisplayed()
    }

    @Test
    fun aProviderOfStepPhotosSaysOnlyThoseWillFollow() {
        render(RecipeImportUiState(url = "https://www.750g.com/pavlova-r204378.htm", providerName = "750g"))

        rule.onNodeWithText(string(R.string.import_provider_hint_photos, "750g")).assertIsDisplayed()
    }

    @Test
    fun fetchingTheMediaIsShownWhileItRuns() {
        render(RecipeImportUiState(url = "https://jow.fr/recipes/x", phase = ImportPhase.FETCHING_MEDIA))

        rule.onNodeWithText(string(R.string.import_fetching_media)).assertIsDisplayed()
    }

    @Test
    fun aPageMealieCannotReadIsExplained() {
        render(RecipeImportUiState(url = "https://example.org", error = NetworkError.NotFound))

        rule.onNodeWithText(string(R.string.error_not_found_title), substring = true).assertIsDisplayed()
    }

    @Test
    fun aVideoIsAnnouncedAsRebuiltWithoutTheTagSwitches() {
        render(RecipeImportUiState(url = "https://youtu.be/0nE7dAlDshk", isVideo = true, videoUsesModel = true))

        rule.onNodeWithText(string(R.string.import_video_hint_model)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.import_include_tags)).assertDoesNotExist()
    }

    @Test
    fun withoutAModelTheVideoHintPointsToTheLocalAi() {
        render(RecipeImportUiState(url = "https://youtu.be/0nE7dAlDshk", isVideo = true, videoUsesModel = false))

        rule.onNodeWithText(string(R.string.import_video_hint_rules)).assertIsDisplayed()
    }

    @Test
    fun theModelShowsHowFarItIs() {
        render(
            RecipeImportUiState(
                url = "https://youtu.be/0nE7dAlDshk",
                isVideo = true,
                videoUsesModel = true,
                phase = ImportPhase.UNDERSTANDING,
                modelProgress = LlmProgress(promptRead = 1_000, promptTotal = 4_000, generated = 0),
            ),
        )

        rule.onNodeWithText(string(R.string.import_understanding)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.import_model_reading, 25)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.action_cancel)).assertIsDisplayed()
    }

    @Test
    fun aVideoYouTubeRefusesIsExplained() {
        render(RecipeImportUiState(url = "https://youtu.be/0nE7dAlDshk", isVideo = true, videoFailure = YouTubeFailure.BLOCKED))

        rule.onNodeWithText(string(R.string.import_video_blocked)).assertIsDisplayed()
    }
}
