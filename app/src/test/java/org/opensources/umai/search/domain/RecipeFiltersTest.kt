package org.opensources.umai.search.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecipeFiltersTest {

    private val today = LocalDate.of(2026, 9, 22)

    @Test
    fun `no filter produces no query expression`() {
        assertNull(RecipeFilters.None.buildQueryFilter(emptyList(), today))
        assertTrue(RecipeFilters.None.isEmpty)
        assertEquals(0, RecipeFilters.None.activeCount)
    }

    @Test
    fun `minimum rating maps to the rating column`() {
        val filters = RecipeFilters(minRating = 4)
        assertEquals("rating >= 4", filters.buildQueryFilter(emptyList(), today))
    }

    @Test
    fun `servings bounds are combined`() {
        val filters = RecipeFilters(minServings = 2, maxServings = 6)
        assertEquals(
            "recipeServings >= 2 AND recipeServings <= 6",
            filters.buildQueryFilter(emptyList(), today),
        )
    }

    @Test
    fun `added within a week becomes a createdAt comparison`() {
        val filters = RecipeFilters(addedWithin = AddedWithin.WEEK)
        assertEquals("""createdAt > "2026-09-15"""", filters.buildQueryFilter(emptyList(), today))
    }

    @Test
    fun `favourites become an id list`() {
        val filters = RecipeFilters(favoritesOnly = true)
        val ids = listOf("aaa", "bbb")
        assertEquals("""id IN ["aaa","bbb"]""", filters.buildQueryFilter(ids, today))
    }

    @Test
    fun `favourites with no favourite matches nothing rather than everything`() {
        val filters = RecipeFilters(favoritesOnly = true)
        val expression = filters.buildQueryFilter(emptyList(), today)
        assertEquals("""id IN ["00000000-0000-0000-0000-000000000000"]""", expression)
    }

    @Test
    fun `several filters are joined with AND`() {
        val filters = RecipeFilters(
            minRating = 3,
            minServings = 4,
            addedWithin = AddedWithin.MONTH,
        )
        assertEquals(
            """rating >= 3 AND recipeServings >= 4 AND createdAt > "2026-08-23"""",
            filters.buildQueryFilter(emptyList(), today),
        )
    }

    @Test
    fun `organizer selections are not part of the query expression`() {
        // Categories, tags, tools and foods have dedicated query parameters.
        val filters = RecipeFilters(
            categoryIds = setOf("c1"),
            tagIds = setOf("t1"),
            toolIds = setOf("to1"),
            foodIds = setOf("f1"),
        )
        assertNull(filters.buildQueryFilter(emptyList(), today))
    }

    @Test
    fun `active count reflects every selected dimension`() {
        val filters = RecipeFilters(
            categoryIds = setOf("c1", "c2"),
            tagIds = setOf("t1"),
            minRating = 4,
            favoritesOnly = true,
            addedWithin = AddedWithin.YEAR,
        )
        assertEquals(5, filters.activeCount)
    }

    @Test
    fun `sort options map to real Mealie columns`() {
        assertEquals("createdAt" to "desc", RecipeSort.RECENT.orderBy to RecipeSort.RECENT.direction)
        assertEquals("name" to "asc", RecipeSort.NAME_ASC.orderBy to RecipeSort.NAME_ASC.direction)
        assertEquals("rating", RecipeSort.RATING.orderBy)
        assertEquals("lastMade", RecipeSort.LAST_MADE.orderBy)
        assertEquals("random", RecipeSort.RANDOM.orderBy)
    }
}
