package org.opensources.umai.planning.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PlanEntryTitleTest {

    @Test
    fun `a count before the multiplication sign is the servings`() {
        assertEquals(2 to "Café", PlanEntryTitle.parse("2 × Café"))
        assertEquals(12 to "Biscuits", PlanEntryTitle.parse("12×Biscuits"))
        // A recipe entry has no title of its own: only the count is written.
        assertEquals(3 to "", PlanEntryTitle.parse("3 ×"))
    }

    @Test
    fun `a title without a count is one serving, kept whole`() {
        assertEquals(1 to "Restaurant", PlanEntryTitle.parse("Restaurant"))
        assertEquals(1 to "", PlanEntryTitle.parse(""))
        // A note written by hand with a letter x is left as it is.
        assertEquals(1 to "2 x œufs", PlanEntryTitle.parse("2 x œufs"))
        assertEquals(1 to "0 × Café", PlanEntryTitle.parse("0 × Café"))
    }

    @Test
    fun `one serving writes the title alone`() {
        assertEquals("Café", PlanEntryTitle.format(1, "Café"))
        assertEquals("2 × Café", PlanEntryTitle.format(2, "Café"))
        assertEquals("2 ×", PlanEntryTitle.format(2, ""))
    }

    @Test
    fun `a title reads back as it was written`() {
        assertEquals(4 to "Thé vert", PlanEntryTitle.parse(PlanEntryTitle.format(4, "Thé vert")))
    }
}
