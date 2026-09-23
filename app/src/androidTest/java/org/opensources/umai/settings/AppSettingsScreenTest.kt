package org.opensources.umai.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.umai.BuildConfig
import org.opensources.umai.R
import org.opensources.umai.core.settings.AppLanguage
import org.opensources.umai.core.settings.AppPreferences
import org.opensources.umai.core.settings.RecipeDisplayOptions
import org.opensources.umai.core.settings.RecipeSection
import org.opensources.umai.core.settings.ThemeMode
import org.opensources.umai.core.ui.theme.UmaiTheme
import org.opensources.umai.settings.ui.AppSettingsScreen

/** The half of the settings that never leaves the device. */
@RunWith(AndroidJUnit4::class)
class AppSettingsScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(id: Int) = context.getString(id)

    private fun render(
        preferences: AppPreferences = AppPreferences(),
        serverVersion: String? = "v3.27.0",
        onLanguageChange: (AppLanguage) -> Unit = {},
        onThemeChange: (ThemeMode) -> Unit = {},
        onBack: () -> Unit = {},
        onRecipeSectionChange: (RecipeSection, Boolean) -> Unit = { _, _ -> },
    ) {
        rule.setContent {
            UmaiTheme {
                AppSettingsScreen(
                    preferences = preferences,
                    serverVersion = serverVersion,
                    onBack = onBack,
                    onLanguageChange = onLanguageChange,
                    onThemeChange = onThemeChange,
                    onLayoutChange = {},
                    onDynamicColorChange = {},
                    onKeepScreenOnChange = {},
                    onRecipeSectionChange = onRecipeSectionChange,
                )
            }
        }
    }

    @Test
    fun theThreeLanguagesAreOffered() {
        render()

        rule.onNodeWithText(string(R.string.settings_language)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(string(R.string.settings_language_system)).assertExists()
        rule.onNodeWithText(string(R.string.settings_language_french)).assertExists()
        rule.onNodeWithText(string(R.string.settings_language_english)).assertExists()
    }

    @Test
    fun theCurrentLanguageIsMarkedAsSelected() {
        render(preferences = AppPreferences(language = AppLanguage.FRENCH))

        rule.onNodeWithText(string(R.string.settings_language_french)).performScrollTo().assertIsSelected()
    }

    @Test
    fun choosingALanguageIsReported() {
        var chosen: AppLanguage? = null
        render(onLanguageChange = { chosen = it })

        rule.onNodeWithText(string(R.string.settings_language_english)).performScrollTo().performClick()

        assertEquals(AppLanguage.ENGLISH, chosen)
    }

    @Test
    fun choosingAThemeIsReported() {
        var chosen: ThemeMode? = null
        render(onThemeChange = { chosen = it })

        rule.onNodeWithText(string(R.string.settings_theme_dark)).performScrollTo().performClick()

        assertEquals(ThemeMode.DARK, chosen)
    }

    @Test
    fun bothVersionsAreListedInTheAboutSection() {
        render()

        // The about section sits at the end of a lazy list, so it has to be
        // scrolled into composition before it can be asserted on.
        rule.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText(string(R.string.settings_app_version)))

        rule.onNodeWithText(string(R.string.settings_app_version)).assertIsDisplayed()
        rule.onNodeWithText(BuildConfig.VERSION_NAME).assertExists()
        rule.onNodeWithText("v3.27.0").assertExists()
    }

    @Test
    fun anInstanceThatNeverAnnouncedItsVersionIsSimplyOmitted() {
        render(serverVersion = null)

        rule.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText(string(R.string.settings_app_version)))

        rule.onNodeWithText(string(R.string.settings_server_version)).assertDoesNotExist()
    }

    @Test
    fun goingBackIsAlwaysAvailable() {
        var back = false
        render(onBack = { back = true })

        rule.onNodeWithContentDescription(string(R.string.action_back)).performClick()

        assertTrue(back)
    }

    @Test
    fun theRecipePageSectionsCanEachBeHidden() {
        var changed: Pair<RecipeSection, Boolean>? = null
        render(onRecipeSectionChange = { section, visible -> changed = section to visible })

        // A lazy list only composes what is near the screen: each row is scrolled to.
        listOf(
            R.string.settings_recipe_times,
            R.string.settings_recipe_source,
            R.string.settings_recipe_comments,
            R.string.settings_recipe_nutrition,
        ).forEach { title ->
            rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(string(title)))
            rule.onNodeWithText(string(title)).assertExists()
        }
        rule.onNodeWithText(string(R.string.settings_recipe_nutrition)).performClick()

        // Everything is shown by default, so the first tap hides the section.
        assertEquals(RecipeSection.NUTRITION to false, changed)
    }

    @Test
    fun aHiddenSectionIsShownAsOff() {
        var changed: Pair<RecipeSection, Boolean>? = null
        render(
            preferences = AppPreferences(recipeDisplay = RecipeDisplayOptions(showComments = false)),
            onRecipeSectionChange = { section, visible -> changed = section to visible },
        )

        rule.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText(string(R.string.settings_recipe_comments)))
        rule.onNodeWithText(string(R.string.settings_recipe_comments)).performClick()

        assertEquals(RecipeSection.COMMENTS to true, changed)
    }
}
