package org.opensources.umai.recipe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.umai.core.model.Organizer

class CalorieTagsTest {

    @Test
    fun `calories are read from the free text Mealie stores`() {
        assertEquals(695, CalorieTags.parse("695 kcal"))
        assertEquals(695, CalorieTags.parse("695"))
        assertEquals(451, CalorieTags.parse("450,6 kcal"))
        assertEquals(1200, CalorieTags.parse("1 200 kcal"))
        assertEquals(1200, CalorieTags.parse("1 200 kcal"))
    }

    @Test
    fun `no number means no calories`() {
        assertNull(CalorieTags.parse(null))
        assertNull(CalorieTags.parse(""))
        assertNull(CalorieTags.parse("beaucoup"))
        assertNull(CalorieTags.parse("0 kcal"))
    }

    @Test
    fun `only calorie tags give a value`() {
        assertEquals("calorie-695", CalorieTags.tagName(695))
        assertEquals(695, CalorieTags.valueOf("calorie-695"))
        assertNull(CalorieTags.valueOf("calorie-"))
        assertNull(CalorieTags.valueOf("calorie-abc"))
        assertNull(CalorieTags.valueOf("dessert"))
        assertTrue(CalorieTags.isCalorieTag("calorie-12"))
        assertFalse(CalorieTags.isCalorieTag("calories"))
    }

    private val tags = listOf(
        CalorieTag("t1", "calorie-250", 250),
        CalorieTag("t2", "calorie-480", 480),
        CalorieTag("t3", "calorie-695", 695),
        CalorieTag("t4", "calorie-900", 900),
    )

    @Test
    fun `a range lists the tags up to its maximum`() {
        assertEquals("""tags.slug IN ["calorie-250"]""", CalorieFilter.UP_TO_300.queryClause(tags))
        assertEquals("""tags.slug IN ["calorie-250","calorie-480"]""", CalorieFilter.UP_TO_500.queryClause(tags))
        assertEquals(
            """tags.slug IN ["calorie-250","calorie-480","calorie-695"]""",
            CalorieFilter.UP_TO_700.queryClause(tags),
        )
    }

    @Test
    fun `a range no tag falls in matches nothing rather than everything`() {
        val clause = CalorieFilter.UP_TO_300.queryClause(listOf(CalorieTag("t4", "calorie-900", 900)))

        assertEquals("""id IN ["00000000-0000-0000-0000-000000000000"]""", clause)
    }

    @Test
    fun `recipes without calories are the ones with no calorie tag`() {
        assertEquals(
            """tags.slug NOT IN ["calorie-250","calorie-480","calorie-695","calorie-900"]""",
            CalorieFilter.UNKNOWN.queryClause(tags),
        )
        // Without any calorie tag, no recipe has calories: nothing to filter out.
        assertNull(CalorieFilter.UNKNOWN.queryClause(emptyList()))
    }

    @Test
    fun `no range means no clause`() {
        assertNull(CalorieFilter.ANY.queryClause(tags))
    }

    @Test
    fun `calorie tags are left out of the tags a recipe shows`() {
        val tags = listOf(
            Organizer("t1", "Poulet", "poulet"),
            Organizer("t2", "calorie-695", "calorie-695"),
            Organizer("t3", "calorie-light", "calorie-light"),
        )

        // Only a real calorie value makes a calorie tag.
        assertEquals(listOf("poulet", "calorie-light"), tags.withoutCalorieTags().map { it.slug })
    }
}
