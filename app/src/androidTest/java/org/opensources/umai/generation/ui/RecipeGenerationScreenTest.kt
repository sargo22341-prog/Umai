package org.opensources.umai.generation.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.opensources.umai.R
import org.opensources.umai.core.ui.theme.UmaiTheme

class RecipeGenerationScreenTest {
    @get:Rule
    val rule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun render(state: RecipeGenerationUiState, onOpen: (String) -> Unit = {}) {
        rule.setContent {
            UmaiTheme {
                RecipeGenerationScreen(state, {}, {}, {}, {}, onOpen, {}, {})
            }
        }
    }

    @Test
    fun emptyPromptCannotBeSent() {
        render(RecipeGenerationUiState(checkingModel = false, modelReady = true))
        rule.onNodeWithText(context.getString(R.string.generation_action)).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun completedRecipeOpensOnlyWhenRequested() {
        var opened: String? = null
        render(RecipeGenerationUiState(checkingModel = false, modelReady = true, slug = "soup")) { opened = it }
        assertEquals(null, opened)
        rule.onNodeWithText(context.getString(R.string.generation_open)).performScrollTo().performClick()
        assertEquals("soup", opened)
    }

    @Test
    fun missingModelOffersItsSettings() {
        render(RecipeGenerationUiState(checkingModel = false))
        rule.onNodeWithText(context.getString(R.string.local_ai_title)).performScrollTo().assertIsDisplayed()
    }
}
