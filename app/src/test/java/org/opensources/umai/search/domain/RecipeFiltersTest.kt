package org.opensources.umai.search.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.umai.recipe.domain.CalorieFilter
import org.opensources.umai.recipe.domain.CalorieTag
import java.time.LocalDate

class RecipeFiltersTest {

    private val today = LocalDate.of(2026, 9, 22)

    @Test
    fun `a calorie range joins the other clauses`() {
        val filters = RecipeFilters(minRating = 4, calories = CalorieFilter.UP_TO_500)
        val tags = listOf(CalorieTag("t1", "calorie-480", 480), CalorieTag("t2", "calorie-695", 695))

        assertEquals(
            """rating >= 4 AND tags.slug IN ["calorie-480"]""",
            filters.buildQueryFilter(emptyList(), tags, today),
        )
        assertEquals(2, filters.activeCount)
    }

    @Test
    fun `no filter produces no query expression`() {
        assertNull(RecipeFilters.None.buildQueryFilter(emptyList(), today = today))
        assertTrue(RecipeFilters.None.isEmpty)
        assertEquals(0, RecipeFilters.None.activeCount)
    }

    @Test
    fun `minimum rating maps to the rating column`() {
        val filters = RecipeFilters(minRating = 4)
        assertEquals("rating >= 4", filters.buildQueryFilter(emptyList(), today = today))
    }

    @Test
    fun `added within a week becomes a createdAt comparison`() {
        val filters = RecipeFilters(addedWithin = AddedWithin.WEEK)
        assertEquals("""createdAt > "2026-09-15"""", filters.buildQueryFilter(emptyList(), today = today))
    }

    @Test
    fun `favourites become an id list`() {
        val filters = RecipeFilters(favoritesOnly = true)
        val ids = listOf("aaa", "bbb")
        assertEquals("""id IN ["aaa","bbb"]""", filters.buildQueryFilter(ids, today = today))
    }

    @Test
    fun `favourites with no favourite matches nothing rather than everything`() {
        val filters = RecipeFilters(favoritesOnly = true)
        val expression = filters.buildQueryFilter(emptyList(), today = today)
        assertEquals("""id IN ["00000000-0000-0000-0000-000000000000"]""", expression)
    }

    @Test
    fun `several filters are joined with AND`() {
        val filters = RecipeFilters(
            minRating = 3,
            favoritesOnly = true,
            addedWithin = AddedWithin.MONTH,
        )
        assertEquals(
            """rating >= 3 AND createdAt > "2026-08-23" AND id IN ["00000000-0000-0000-0000-000000000000"]""",
            filters.buildQueryFilter(emptyList(), today = today),
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
        assertNull(filters.buildQueryFilter(emptyList(), today = today))
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
    fun `sort columns map to real Mealie columns`() {
        assertEquals("createdAt", SortField.CREATED.orderBy)
        assertEquals("name", SortField.NAME.orderBy)
        assertEquals("rating", SortField.RATING.orderBy)
        assertEquals("lastMade", SortField.LAST_MADE.orderBy)
        assertEquals("random", SortField.RANDOM.orderBy)
    }

    @Test
    fun `the default order lists the newest recipes first`() {
        assertEquals("createdAt" to "desc", RecipeSort.Default.orderBy to RecipeSort.Default.direction)
    }

    @Test
    fun `picking the current column again reverses its direction`() {
        val ascending = RecipeSort.Default.select(SortField.CREATED)
        assertEquals("asc", ascending.direction)
        assertEquals("desc", ascending.select(SortField.CREATED).direction)
    }

    @Test
    fun `picking another column starts from its natural direction`() {
        assertEquals("asc", RecipeSort.Default.select(SortField.NAME).direction)
        val byName = RecipeSort(SortField.NAME, descending = true)
        assertEquals("desc", byName.select(SortField.RATING).direction)
    }

    @Test
    fun `the random order has no direction to reverse`() {
        val random = RecipeSort.Default.select(SortField.RANDOM)
        assertTrue(random.isRandom)
        assertEquals(random, random.select(SortField.RANDOM))
    }
}
