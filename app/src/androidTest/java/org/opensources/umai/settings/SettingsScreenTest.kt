package org.opensources.umai.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performScrollToNode
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
import org.opensources.umai.BuildConfig
import org.opensources.umai.R
import org.opensources.umai.core.session.AuthMode
import org.opensources.umai.core.session.ServerSession
import org.opensources.umai.core.session.SessionState
import org.opensources.umai.core.settings.AppLanguage
import org.opensources.umai.core.settings.AppPreferences
import org.opensources.umai.core.settings.ThemeMode
import org.opensources.umai.core.ui.theme.UmaiTheme
import org.opensources.umai.settings.ui.SettingsScreen
import org.opensources.umai.settings.ui.SettingsUiState

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(id: Int) = context.getString(id)

    private val session = SessionState.Active(
        ServerSession(
            baseUrl = "https://mealie.ndd.custom/",
            token = "secret",
            authMode = AuthMode.API_TOKEN,
            username = "hiroo",
            userId = "u1",
            userDisplayName = "hiroo",
            serverVersion = "v3.27.0",
        ),
    )

    private fun render(
        preferences: AppPreferences = AppPreferences(),
        sessionState: SessionState = session,
        state: SettingsUiState = SettingsUiState(),
        onLanguageChange: (AppLanguage) -> Unit = {},
        onThemeChange: (ThemeMode) -> Unit = {},
        onSignOut: () -> Unit = {},
        onCheckConnection: () -> Unit = {},
    ) {
        rule.setContent {
            UmaiTheme {
                SettingsScreen(
                    preferences = preferences,
                    session = sessionState,
                    state = state,
                    onCheckConnection = onCheckConnection,
                    onSignOut = onSignOut,
                    onLanguageChange = onLanguageChange,
                    onThemeChange = onThemeChange,
                    onLayoutChange = {},
                    onDynamicColorChange = {},
                    onKeepScreenOnChange = {},
                )
            }
        }
    }

    @Test
    fun theConfiguredInstanceAndAccountAreShown() {
        render()

        rule.onNodeWithText(string(R.string.settings_instance)).assertIsDisplayed()
        rule.onNodeWithText("https://mealie.ndd.custom/").assertIsDisplayed()
        rule.onNodeWithText("hiroo").assertIsDisplayed()
        rule.onNodeWithText(string(R.string.settings_auth_token)).assertIsDisplayed()
    }

    @Test
    fun theTokenItselfIsNeverDisplayed() {
        render()

        rule.onNodeWithText("secret", substring = true).assertDoesNotExist()
    }

    @Test
    fun anActiveSessionIsReportedAsConnected() {
        render()

        rule.onNodeWithText(string(R.string.settings_status_connected)).assertIsDisplayed()
    }

    @Test
    fun anExpiredSessionIsReportedAsSuch() {
        render(sessionState = SessionState.Expired("https://mealie.ndd.custom/", "hiroo"))

        rule.onNodeWithText(string(R.string.settings_status_expired)).assertIsDisplayed()
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
    fun theConnectionCanBeChecked() {
        var checked = false
        render(onCheckConnection = { checked = true })

        rule.onNodeWithText(string(R.string.settings_check_connection)).performScrollTo().performClick()

        assertTrue(checked)
    }

    @Test
    fun signingOutAsksForConfirmationFirst() {
        var signedOut = false
        render(onSignOut = { signedOut = true })

        rule.onNodeWithText(string(R.string.settings_change_instance)).performScrollTo().performClick()
        rule.onNodeWithText(string(R.string.settings_sign_out_title)).assertIsDisplayed()
        assertTrue("nothing must happen before confirmation", !signedOut)

        rule.onNodeWithText(string(R.string.settings_sign_out)).performClick()
        assertTrue(signedOut)
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
}
