package org.opensources.umai.search.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.umai.core.model.Organizer

class OrganizerSuggestionsTest {

    private val tags = listOf(
        Organizer("t1", "Gâteau", "gateau"),
        Organizer("t2", "Petit gâteau", "petit-gateau"),
        Organizer("t3", "Soupe", "soupe"),
        Organizer("t4", "Végétarien", "vegetarien"),
    )

    @Test
    fun `nothing is suggested before something is typed`() {
        assertTrue(tags.suggestionsFor("", emptySet()).isEmpty())
        assertTrue(tags.suggestionsFor("   ", emptySet()).isEmpty())
    }

    @Test
    fun `matching ignores case and accents`() {
        assertEquals(listOf("t4"), tags.suggestionsFor("VEGE", emptySet()).map { it.id })
    }

    @Test
    fun `names starting with the query come first`() {
        assertEquals(listOf("t1", "t2"), tags.suggestionsFor("gat", emptySet()).map { it.id })
    }

    @Test
    fun `entries already picked are not offered again`() {
        assertEquals(listOf("t2"), tags.suggestionsFor("gateau", setOf("t1")).map { it.id })
    }

    @Test
    fun `the number of suggestions is capped`() {
        val many = (1..100).map { Organizer("id$it", "Tag $it", "tag-$it") }

        assertEquals(5, many.suggestionsFor("tag", emptySet(), limit = 5).size)
    }
}
