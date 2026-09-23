package org.opensources.umai.profile

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.umai.R
import org.opensources.umai.TestData
import org.opensources.umai.core.model.UserProfile
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.ui.theme.UmaiTheme
import org.opensources.umai.profile.ui.ProfileScreen
import org.opensources.umai.profile.ui.ProfileUiState

/** The account page replacing the old settings tab. */
@RunWith(AndroidJUnit4::class)
class ProfileScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun render(
        state: ProfileUiState,
        avatarUrl: (UserProfile) -> String? = { null },
        onPickAvatar: () -> Unit = {},
        onOpenAppSettings: () -> Unit = {},
        onOpenMealieSettings: () -> Unit = {},
        onImportRecipe: () -> Unit = {},
        onCreateRecipe: () -> Unit = {},
        onOpenDrafts: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        rule.setContent {
            UmaiTheme {
                ProfileScreen(
                    state = state,
                    snackbarHostState = SnackbarHostState(),
                    avatarUrl = avatarUrl,
                    onRefresh = {},
                    onRetry = onRetry,
                    onPickAvatar = onPickAvatar,
                    onOpenAppSettings = onOpenAppSettings,
                    onOpenMealieSettings = onOpenMealieSettings,
                    onOpenProviders = {},
                    onImportRecipe = onImportRecipe,
                    onCreateRecipe = onCreateRecipe,
                    onOpenDrafts = onOpenDrafts,
                )
            }
        }
    }

    private val loaded = ProfileUiState(
        user = TestData.user(),
        statistics = TestData.statistics(),
        loading = false,
    )

    @Test
    fun theAccountIsShownWithItsGroupAndHousehold() {
        render(loaded)

        rule.onNodeWithText("Hiroo").assertIsDisplayed()
        rule.onNodeWithText("hiroo@example.org").assertIsDisplayed()
        rule.onNodeWithText(string(R.string.profile_group_and_household, "Famille", "Maison"))
            .assertIsDisplayed()
    }

    @Test
    fun theHouseholdCountersAreShown() {
        render(loaded)

        rule.onNodeWithText("114").assertIsDisplayed()
        rule.onNodeWithText(string(R.string.profile_stat_recipes)).assertIsDisplayed()
        rule.onNodeWithText("499").assertIsDisplayed()
    }

    @Test
    fun anAccountWithoutPictureFallsBackToItsInitial() {
        render(loaded, avatarUrl = { null })

        // The avatar is still described, so it is never a silent empty circle.
        rule.onNodeWithContentDescription(string(R.string.profile_change_avatar)).assertIsDisplayed()
        rule.onNodeWithText("H").assertIsDisplayed()
    }

    @Test
    fun tappingTheAvatarAsksForANewPicture() {
        var picked = false
        render(loaded, onPickAvatar = { picked = true })

        rule.onNodeWithContentDescription(string(R.string.profile_change_avatar)).performClick()

        assertTrue(picked)
    }

    @Test
    fun theThreeWaysOfAddingARecipeAreOffered() {
        render(loaded)

        rule.onNodeWithText(string(R.string.profile_import_recipe)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(string(R.string.profile_create_recipe)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(string(R.string.profile_drafts)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun withoutADraftTheRowSaysSoRatherThanShowingZero() {
        render(loaded)

        rule.onNodeWithText(string(R.string.profile_drafts_empty)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theDraftCountIsShownWhenThereAreSome() {
        render(loaded.copy(draftCount = 2))

        rule.onNodeWithText(
            context.resources.getQuantityString(R.plurals.plural_drafts, 2, 2),
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun bothSetsOfSettingsAreReachable() {
        var mealie = false
        var app = false
        render(loaded, onOpenMealieSettings = { mealie = true }, onOpenAppSettings = { app = true })

        rule.onNodeWithText(string(R.string.settings_mealie_title)).performScrollTo().performClick()
        assertTrue(mealie)

        rule.onNodeWithText(string(R.string.settings_app_title)).performScrollTo().performClick()
        assertTrue(app)
    }

    @Test
    fun anUnreachableInstanceIsExplainedAndRetryable() {
        var retried = false
        render(
            ProfileUiState(loading = false, error = NetworkError.Unreachable),
            onRetry = { retried = true },
        )

        rule.onNodeWithText(string(R.string.error_unreachable_title)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.action_retry)).performClick()

        assertTrue(retried)
    }
}
