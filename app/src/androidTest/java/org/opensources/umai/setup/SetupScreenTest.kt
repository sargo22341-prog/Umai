package org.opensources.umai.setup

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.umai.R
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.ui.theme.UmaiTheme
import org.opensources.umai.setup.ui.SetupAuthMethod
import org.opensources.umai.setup.ui.SetupScreen
import org.opensources.umai.setup.ui.SetupUiState

/**
 * Covers the "no instance configured" entry point: what the user sees first,
 * and how each failure mode is explained.
 */
@RunWith(AndroidJUnit4::class)
class SetupScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(id: Int) = context.getString(id)

    private fun render(
        state: SetupUiState = SetupUiState(),
        onUrlChange: (String) -> Unit = {},
        onAuthMethodChange: (SetupAuthMethod) -> Unit = {},
        onConnect: () -> Unit = {},
    ) {
        rule.setContent {
            UmaiTheme {
                SetupScreen(
                    state = state,
                    onUrlChange = onUrlChange,
                    onAuthMethodChange = onAuthMethodChange,
                    onUsernameChange = {},
                    onPasswordChange = {},
                    onApiTokenChange = {},
                    onTogglePasswordVisibility = {},
                    onConnect = onConnect,
                    onUseAnotherInstance = {},
                )
            }
        }
    }

    @Test
    fun withoutAnInstanceTheSetupInvitationIsShown() {
        render()

        rule.onNodeWithText(string(R.string.setup_title)).assertExists()
        rule.onNodeWithText(string(R.string.setup_intro)).assertExists()
        rule.onNodeWithText(string(R.string.setup_url_label)).assertExists()
    }

    @Test
    fun withoutAnAddressTheConnectButtonIsDisabled() {
        render()
        rule.onNodeWithText(string(R.string.action_connect)).assertIsNotEnabled()
    }

    @Test
    fun withAnAddressTheConnectButtonBecomesAvailable() {
        render(state = SetupUiState(url = "mealie.lan"))
        rule.onNodeWithText(string(R.string.action_connect)).assertIsEnabled()
    }

    @Test
    fun typingAnAddressReachesTheViewModel() {
        var typed = ""
        render(onUrlChange = { typed = it })

        // The instance address is the first editable field of the form.
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("mealie.lan")

        assertEquals("mealie.lan", typed)
    }

    @Test
    fun aCleartextAddressShowsAWarningWithoutBlockingTheUser() {
        render(state = SetupUiState(url = "http://192.168.1.10", cleartextWarning = true))

        rule.onNodeWithText(string(R.string.setup_cleartext_warning)).assertExists()
        rule.onNodeWithText(string(R.string.action_connect)).assertIsEnabled()
    }

    @Test
    fun anUnreachableServerIsExplainedInPlainWords() {
        render(state = SetupUiState(url = "mealie.lan", networkError = NetworkError.Unreachable))

        rule.onNodeWithText(string(R.string.error_unreachable_title), substring = true).assertExists()
        rule.onNodeWithText(string(R.string.error_unreachable_message), substring = true).assertExists()
    }

    @Test
    fun invalidCredentialsAreExplained() {
        render(state = SetupUiState(url = "mealie.lan", networkError = NetworkError.Unauthorized))

        rule.onNodeWithText(string(R.string.error_unauthorized_title), substring = true).assertExists()
    }

    @Test
    fun aTlsFailureTellsTheUserAboutTheCertificateAuthority() {
        render(state = SetupUiState(url = "mealie.lan", networkError = NetworkError.Tls("bad chain")))

        rule.onNodeWithText(string(R.string.error_tls_title), substring = true).assertExists()
        rule.onNodeWithText(string(R.string.error_tls_message), substring = true).assertExists()
    }

    @Test
    fun aHostThatIsNotMealieIsNamedAsSuch() {
        render(state = SetupUiState(url = "example.com", networkError = NetworkError.NotMealie))

        rule.onNodeWithText(string(R.string.error_not_mealie_title), substring = true).assertExists()
    }

    @Test
    fun aMalformedAddressIsReportedBeforeAnyRequest() {
        render(state = SetupUiState(url = "://", formError = R.string.setup_error_url_invalid))

        rule.onNodeWithText(string(R.string.setup_error_url_invalid)).assertExists()
    }

    @Test
    fun theApiTokenMethodReplacesTheCredentialFields() {
        render(state = SetupUiState(url = "mealie.lan", authMethod = SetupAuthMethod.API_TOKEN))

        // The label appears on the method selector and on the field itself.
        rule.onAllNodesWithText(string(R.string.setup_token_label)).assertCountEquals(2)
        rule.onNodeWithText(string(R.string.setup_token_helper)).assertExists()
    }

    @Test
    fun switchingTheSignInMethodIsForwarded() {
        var method: SetupAuthMethod? = null
        render(onAuthMethodChange = { method = it })

        rule.onNodeWithText(string(R.string.setup_auth_token)).performClick()

        assertEquals(SetupAuthMethod.API_TOKEN, method)
    }

    @Test
    fun connectingWhileInFlightShowsProgressAndCannotBeTriggeredTwice() {
        render(state = SetupUiState(url = "mealie.lan", connecting = true))

        rule.onNodeWithText(string(R.string.setup_connecting)).assertExists()
        rule.onNodeWithText(string(R.string.setup_connecting)).assertIsNotEnabled()
    }

    @Test
    fun anExpiredSessionPreFillsTheInstanceAndExplainsWhy() {
        render(
            state = SetupUiState(
                url = "https://mealie.lan/",
                expiredForUrl = "https://mealie.lan/",
                username = "hiroo",
            ),
        )

        rule.onNodeWithText(string(R.string.setup_expired_title)).assertExists()
        // The address appears both in the explanation and in the pre-filled field.
        rule.onAllNodesWithText("https://mealie.lan/", substring = true).onFirst().assertExists()
        rule.onNodeWithText(string(R.string.setup_use_other_instance)).assertExists()
    }

    @Test
    fun theConnectButtonCallsBack() {
        var clicked = false
        render(state = SetupUiState(url = "mealie.lan"), onConnect = { clicked = true })

        rule.onNodeWithText(string(R.string.action_connect)).performClick()

        assertTrue(clicked)
    }
}
