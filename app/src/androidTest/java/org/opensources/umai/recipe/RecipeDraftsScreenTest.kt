package org.opensources.umai.recipe

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.umai.R
import org.opensources.umai.TestData
import org.opensources.umai.core.ui.theme.UmaiTheme
import org.opensources.umai.recipe.ui.RecipeDraftsScreen
import org.opensources.umai.recipe.ui.RecipeDraftsUiState

/** Recipes started on the device but not published to Mealie yet. */
@RunWith(AndroidJUnit4::class)
class RecipeDraftsScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun render(
        state: RecipeDraftsUiState,
        onOpenDraft: (String) -> Unit = {},
        onDeleteDraft: (String) -> Unit = {},
    ) {
        rule.setContent {
            UmaiTheme {
                RecipeDraftsScreen(
                    state = state,
                    onBack = {},
                    onOpenDraft = onOpenDraft,
                    onDeleteDraft = onDeleteDraft,
                )
            }
        }
    }

    @Test
    fun havingNoDraftIsExplainedRatherThanLeftBlank() {
        render(RecipeDraftsUiState(drafts = emptyList(), loading = false))

        rule.onNodeWithText(string(R.string.drafts_empty_title)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.drafts_empty_message)).assertIsDisplayed()
    }

    @Test
    fun aDraftIsListedByItsName() {
        render(RecipeDraftsUiState(drafts = listOf(TestData.draft()), loading = false))

        rule.onNodeWithText("Tarte aux pommes").assertIsDisplayed()
    }

    @Test
    fun aDraftWithNoNameYetStillGetsALabel() {
        render(RecipeDraftsUiState(drafts = listOf(TestData.draft(name = "")), loading = false))

        rule.onNodeWithText(string(R.string.drafts_untitled)).assertIsDisplayed()
    }

    @Test
    fun openingADraftReportsItsIdentifier() {
        var opened: String? = null
        render(
            RecipeDraftsUiState(drafts = listOf(TestData.draft(id = "d9")), loading = false),
            onOpenDraft = { opened = it },
        )

        rule.onNodeWithText("Tarte aux pommes").performClick()

        assertEquals("d9", opened)
    }

    @Test
    fun deletingADraftAsksForConfirmationFirst() {
        var deleted: String? = null
        render(
            RecipeDraftsUiState(drafts = listOf(TestData.draft(id = "d9")), loading = false),
            onDeleteDraft = { deleted = it },
        )

        rule.onNodeWithContentDescription(string(R.string.action_delete)).performClick()
        rule.onNodeWithText(string(R.string.drafts_delete_title)).assertIsDisplayed()
        assertNull("nothing must happen before confirmation", deleted)

        rule.onNodeWithText(string(R.string.action_delete)).performClick()

        assertEquals("d9", deleted)
    }
}
