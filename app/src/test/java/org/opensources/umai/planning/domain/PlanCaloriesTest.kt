package org.opensources.umai.planning.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.umai.core.model.MealPlanEntry
import org.opensources.umai.core.model.MealType
import org.opensources.umai.core.model.RecipeSummary
import java.time.LocalDate

class PlanCaloriesTest {

    private val day = LocalDate.of(2026, 9, 24)

    private fun entry(id: Int, recipe: RecipeSummary? = null, title: String = "", text: String = "", servings: Int = 1) =
        MealPlanEntry(id, day, MealType.SNACK, title, text, recipe, "g", "u", servings)

    @Test
    fun `the calories of a note are the number written before kcal`() {
        assertEquals(139, PlanCalories.ofNote("139 kcal · 330 ml\nGlucides 35 g"))
        assertEquals(43, PlanCalories.ofNote("Pomme, 42,6 kcal"))
        assertEquals(1200, PlanCalories.ofNote("Buffet 1 200 KCAL"))
        assertEquals(1200, PlanCalories.ofNote("1 200 kcal"))
        assertEquals(0, PlanCalories.ofNote("Eau 0 kcal"))
    }

    @Test
    fun `a note without kcal has no calories`() {
        assertNull(PlanCalories.ofNote("Restaurant"))
        assertNull(PlanCalories.ofNote("330 ml"))
        assertNull(PlanCalories.ofNote(""))
    }

    @Test
    fun `a recipe carries its calories in its calorie tag`() {
        assertEquals(450, PlanCalories.ofTags(summary("r1", tags = listOf(organizer("calorie-450", "calorie-450")))))
        assertNull(PlanCalories.ofTags(summary("r2", tags = listOf(organizer("dessert", "Dessert")))))
    }

    @Test
    fun `a recipe entry counts its recipe, a note its text, then its title`() {
        val recipe = entry(1, recipe = summary("r1"), text = "999 kcal")
        val known = mapOf("r1" to 520)

        assertEquals(520, PlanCalories.ofEntry(recipe, known))
        // A recipe without calories does not borrow the note's.
        assertNull(PlanCalories.ofEntry(recipe, mapOf("r1" to null)))
        assertEquals(139, PlanCalories.ofEntry(entry(2, title = "Cola", text = "139 kcal · 330 ml"), known))
        assertEquals(80, PlanCalories.ofEntry(entry(3, title = "Yaourt 80 kcal"), known))
    }

    @Test
    fun `every serving eaten counts`() {
        val known = mapOf("r1" to 520)

        assertEquals(1040, PlanCalories.ofEntry(entry(1, recipe = summary("r1"), servings = 2), known))
        assertEquals(6, PlanCalories.ofEntry(entry(2, title = "Café", text = "2 kcal · 1 tasse", servings = 3), known))
        assertNull(PlanCalories.ofEntry(entry(3, title = "Restaurant", servings = 2), known))
    }

    @Test
    fun `a day adds up what it knows and counts what it does not`() {
        val entries = listOf(
            entry(1, recipe = summary("r1")),
            entry(2, title = "Cola", text = "139 kcal"),
            entry(3, title = "Restaurant"),
            entry(4, recipe = summary("r2")),
        )

        val calories = PlanCalories.ofDay(entries, mapOf("r1" to 520, "r2" to null))

        assertEquals(DayCalories(total = 659, unknown = 2), calories)
    }

    @Test
    fun `an empty day has nothing to show`() {
        assertTrue(PlanCalories.ofDay(emptyList(), emptyMap()).isEmpty)
    }
}
