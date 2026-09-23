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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.umai.R
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.ui.theme.UmaiTheme
import org.opensources.umai.recipe.ui.RecipeImportScreen
import org.opensources.umai.recipe.ui.RecipeImportUiState

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
        render(RecipeImportUiState(url = "https://example.org", importing = true))

        rule.onNodeWithText(string(R.string.import_running)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.import_running)).assertIsNotEnabled()
    }

    @Test
    fun aPageMealieCannotReadIsExplained() {
        render(RecipeImportUiState(url = "https://example.org", error = NetworkError.NotFound))

        rule.onNodeWithText(string(R.string.error_not_found_title), substring = true).assertIsDisplayed()
    }
}
