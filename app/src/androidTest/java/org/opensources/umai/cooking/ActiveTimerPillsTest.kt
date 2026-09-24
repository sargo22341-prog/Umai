package org.opensources.umai.cooking

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.umai.R
import org.opensources.umai.cooking.domain.CookingTimer
import org.opensources.umai.cooking.domain.CookingTimers
import org.opensources.umai.cooking.domain.TimerRecipe
import org.opensources.umai.cooking.ui.ActiveTimerPills
import org.opensources.umai.cooking.ui.ActiveTimersUiState
import org.opensources.umai.core.ui.theme.UmaiTheme
import java.time.Duration

/** Once the cooking mode is left, its timers float over the other screens. */
@RunWith(AndroidJUnit4::class)
class ActiveTimerPillsTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

    private val curry = TimerRecipe(slug = "poulet-au-curry", name = "Poulet au curry", servings = 4)
    private val tart = TimerRecipe(slug = "tarte-tatin", name = "Tarte tatin", servings = 0)

    /** A curry timer 5 minutes from its end, and a tart timer that already rang. */
    private val timers = CookingTimers()
        .start(curry, stepIndex = 2, duration = Duration.ofMinutes(15), now = 0)
        .start(tart, stepIndex = 0, duration = Duration.ofMinutes(5), now = 0)

    private fun render(
        state: ActiveTimersUiState,
        onOpen: (CookingTimer) -> Unit = {},
        onStop: (Int) -> Unit = {},
    ) {
        rule.setContent {
            UmaiTheme {
                ActiveTimerPills(state = state, onOpen = onOpen, onStop = onStop)
            }
        }
    }

    @Test
    fun eachTimerShowsItsTimeLeftAndItsRecipe() {
        render(ActiveTimersUiState(timers.timers, now = Duration.ofMinutes(10).toMillis()))

        rule.onNodeWithText("5:00").assertIsDisplayed()
        rule.onNodeWithText("Poulet au curry").assertIsDisplayed()
        rule.onNodeWithText(string(R.string.cooking_timer_done)).assertIsDisplayed()
        rule.onNodeWithText("Tarte tatin").assertIsDisplayed()
    }

    @Test
    fun tappingATimerOpensItsStep() {
        var opened: CookingTimer? = null
        render(ActiveTimersUiState(timers.timers, now = Duration.ofMinutes(10).toMillis()), onOpen = { opened = it })

        rule.onNodeWithText("Poulet au curry").performClick()

        assertEquals(curry, opened?.recipe)
        assertEquals(2, opened?.stepIndex)
    }

    @Test
    fun aTimerThatRangIsStoppedFromItsPill() {
        var stopped: Int? = null
        render(ActiveTimersUiState(timers.timers, now = Duration.ofMinutes(10).toMillis()), onStop = { stopped = it })

        val label = string(R.string.cooking_timer_of_recipe, "Tarte tatin", string(R.string.cooking_timer_label, 1, "5 min"))
        rule.onNodeWithContentDescription(string(R.string.cooking_timer_stop_named, label)).performClick()

        assertEquals(2, stopped)
    }

    @Test
    fun noTimerShowsNoPill() {
        render(ActiveTimersUiState())

        rule.onNodeWithText(string(R.string.cooking_timer_done)).assertDoesNotExist()
        rule.onNodeWithText("Poulet au curry").assertDoesNotExist()
    }
}
