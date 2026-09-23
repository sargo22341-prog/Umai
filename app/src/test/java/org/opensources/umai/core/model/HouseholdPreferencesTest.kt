package org.opensources.umai.core.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek

/**
 * Mealie numbers the days the JavaScript way — 0 is Sunday — while `java.time`
 * numbers Monday 1 to Sunday 7. Reading the value as if it were a `java.time`
 * index shifts every day by one, which is exactly what the app used to show.
 */
class HouseholdPreferencesTest {

    private fun preferences(firstDayOfWeek: Int) = HouseholdPreferences(
        firstDayOfWeek = firstDayOfWeek,
        privateHousehold = true,
        showAnnouncements = true,
        lockRecipeEditsFromOtherHouseholds = true,
        recipePublic = true,
        recipeShowNutrition = false,
        recipeShowAssets = false,
        recipeLandscapeView = false,
        recipeDisableComments = false,
    )

    @Test
    fun `zero is Sunday, one is Monday`() {
        assertEquals(DayOfWeek.SUNDAY, preferences(0).firstDay)
        assertEquals(DayOfWeek.MONDAY, preferences(1).firstDay)
    }

    @Test
    fun `every day of the week maps back and forth`() {
        (0..6).forEach { value ->
            val day = preferences(value).firstDay
            assertEquals(value, HouseholdPreferences.mealieDayNumber(day))
        }
    }

    @Test
    fun `saturday is the last day Mealie numbers`() {
        assertEquals(DayOfWeek.SATURDAY, preferences(6).firstDay)
        assertEquals(6, HouseholdPreferences.mealieDayNumber(DayOfWeek.SATURDAY))
    }

    @Test
    fun `an out of range value is brought back into the week`() {
        assertEquals(DayOfWeek.SUNDAY, preferences(7).firstDay)
        assertEquals(DayOfWeek.SATURDAY, preferences(-1).firstDay)
    }
}
