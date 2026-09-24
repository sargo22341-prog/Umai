package org.opensources.umai.recipe

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.umai.R
import org.opensources.umai.core.ui.theme.UmaiTheme
import org.opensources.umai.recipe.ui.MealPlanPicker
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle

/** The weeks offered from a recipe start on the household's first day, as on the meal plan. */
@RunWith(AndroidJUnit4::class)
class MealPlanPickerTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val locale = context.resources.configuration.locales[0]

    private fun dayName(day: DayOfWeek): String =
        day.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }

    @Test
    fun eachWeekOpensOnTheFirstDayChosenInMealie() {
        // Two days after today: this week then started five days ago, and next
        // week starts in two days, so both first days carry the name of the day.
        val firstDay = LocalDate.now().dayOfWeek.plus(2)
        rule.setContent {
            UmaiTheme {
                MealPlanPicker(firstDay = firstDay, onDismiss = {}, onConfirm = { _, _ -> })
            }
        }

        val firstDays = rule.onAllNodesWithText(dayName(firstDay)).fetchSemanticsNodes()
        assertEquals(2, firstDays.size)
        val today = rule.onNodeWithText(context.getString(R.string.planning_today)).fetchSemanticsNode()
        val start = firstDays.first().positionInRoot
        // The first chip of this week comes before today's: on its left, or on a line above.
        assertTrue(
            "the week must open on its first day",
            start.y < today.positionInRoot.y || (start.y == today.positionInRoot.y && start.x < today.positionInRoot.x),
        )
    }
}
