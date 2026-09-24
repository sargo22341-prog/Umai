package org.opensources.umai.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
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
import org.opensources.umai.R
import org.opensources.umai.TestData
import org.opensources.umai.core.session.AuthMode
import org.opensources.umai.core.session.ServerSession
import org.opensources.umai.core.session.SessionState
import org.opensources.umai.core.ui.theme.UmaiTheme
import org.opensources.umai.settings.ui.MealieSettingsScreen
import org.opensources.umai.settings.ui.MealieSettingsUiState
import java.time.DayOfWeek

/** The Mealie half of the settings: the instance and the household preferences. */
@RunWith(AndroidJUnit4::class)
class MealieSettingsScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

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
        state: MealieSettingsUiState,
        onFirstDayChange: (DayOfWeek) -> Unit = {},
        onShowNutritionChange: (Boolean) -> Unit = {},
        onSignOut: () -> Unit = {},
        onCheckConnection: () -> Unit = {},
    ) {
        rule.setContent {
            UmaiTheme {
                MealieSettingsScreen(
                    session = session,
                    state = state,
                    onBack = {},
                    onRefresh = {},
                    onCheckConnection = onCheckConnection,
                    onSignOut = onSignOut,
                    onFirstDayChange = onFirstDayChange,
                    onShowNutritionChange = onShowNutritionChange,
                    onShowAssetsChange = {},
                    onDisableCommentsChange = {},
                    onRecipePublicChange = {},
                    onPrivateHouseholdChange = {},
                    onDismissSaveError = {},
                )
            }
        }
    }

    private val manager = MealieSettingsUiState(
        household = TestData.householdPreferences(firstDayOfWeek = 1),
        loading = false,
        canManageHousehold = true,
    )

    @Test
    fun theConfiguredInstanceAndAccountAreShown() {
        render(manager)

        rule.onNodeWithText(string(R.string.settings_instance)).assertIsDisplayed()
        rule.onNodeWithText("https://mealie.ndd.custom/").assertIsDisplayed()
        rule.onNodeWithText("hiroo").assertIsDisplayed()
        rule.onNodeWithText(string(R.string.settings_auth_token)).assertIsDisplayed()
    }

    @Test
    fun theTokenItselfIsNeverDisplayed() {
        render(manager)

        rule.onNodeWithText("secret", substring = true).assertDoesNotExist()
    }

    @Test
    fun anActiveSessionIsReportedAsConnected() {
        render(manager)

        rule.onNodeWithText(string(R.string.settings_status_connected)).assertIsDisplayed()
    }

    @Test
    fun theConnectionCanBeChecked() {
        var checked = false
        render(manager, onCheckConnection = { checked = true })

        rule.onNodeWithText(string(R.string.settings_check_connection)).performScrollTo().performClick()

        assertTrue(checked)
    }

    @Test
    fun signingOutAsksForConfirmationFirst() {
        var signedOut = false
        render(manager, onSignOut = { signedOut = true })

        rule.onNodeWithText(string(R.string.settings_change_instance)).performScrollTo().performClick()
        rule.onNodeWithText(string(R.string.settings_sign_out_title)).assertIsDisplayed()
        assertTrue("nothing must happen before confirmation", !signedOut)

        rule.onNodeWithText(string(R.string.settings_sign_out)).performClick()
        assertTrue(signedOut)
    }

    /**
     * Mealie numbers the days from Sunday, so its `1` is Monday. Reading it as a
     * `java.time` index used to shift every day by one.
     */
    @Test
    fun theFirstDayOfTheWeekMatchesWhatMealieStores() {
        render(manager)

        rule.onNodeWithText(dayName(DayOfWeek.MONDAY)).performScrollTo().assertIsSelected()
    }

    @Test
    fun choosingAnotherFirstDayIsReported() {
        var chosen: DayOfWeek? = null
        render(manager, onFirstDayChange = { chosen = it })

        rule.onNodeWithText(dayName(DayOfWeek.SUNDAY)).performScrollTo().performClick()

        assertEquals(DayOfWeek.SUNDAY, chosen)
    }

    @Test
    fun aUserWhoCannotManageTheHouseholdIsToldWhyAndCannotEdit() {
        render(manager.copy(canManageHousehold = false))

        rule.onNodeWithText(string(R.string.settings_household_read_only))
            .performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithText(dayName(DayOfWeek.SUNDAY)).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun aManagerKeepsTheControlsEnabled() {
        render(manager)

        rule.onNodeWithText(string(R.string.settings_household_read_only)).assertDoesNotExist()
    }

    @Test
    fun preferencesThatCannotBeReadAreExplained() {
        render(MealieSettingsUiState(household = null, loading = false))

        rule.onNodeWithText(string(R.string.settings_household_unavailable))
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun dayName(day: DayOfWeek): String =
        day.getDisplayName(java.time.format.TextStyle.FULL, context.resources.configuration.locales[0])
            .replaceFirstChar { it.titlecase(context.resources.configuration.locales[0]) }

    @Test
    fun preferencesThatChangeNothingInTheAppSaySo() {
        render(manager)

        rule.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText(string(R.string.settings_scope_nutrition)))
        rule.onNodeWithText(string(R.string.settings_scope_nutrition)).assertIsDisplayed()
        rule.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText(string(R.string.settings_scope_first_day)))
        rule.onNodeWithText(string(R.string.settings_scope_first_day)).assertIsDisplayed()
        rule.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText(string(R.string.settings_scope_server_only)))
        rule.onAllNodesWithText(string(R.string.settings_scope_server_only)).onFirst().assertIsDisplayed()
    }
}
