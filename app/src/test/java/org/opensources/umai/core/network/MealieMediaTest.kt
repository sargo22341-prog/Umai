package org.opensources.umai.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class MealieMediaTest {

    private val base = "https://mealie.ndd.custom/"
    private val recipeId = "e1583e04-1e01-4164-8ffd-035ffa755434"

    @Test
    fun `recipe pictures use the documented media path and renditions`() {
        assertEquals(
            "https://mealie.ndd.custom/api/media/recipes/$recipeId/images/original.webp?version=73",
            MealieMedia.recipeImage(base, recipeId, MealieMedia.ImageSize.ORIGINAL, "73"),
        )
        assertEquals(
            "https://mealie.ndd.custom/api/media/recipes/$recipeId/images/tiny-original.webp?version=73",
            MealieMedia.recipeImage(base, recipeId, MealieMedia.ImageSize.SMALL, "73"),
        )
        assertEquals(
            "https://mealie.ndd.custom/api/media/recipes/$recipeId/images/min-original.webp?version=73",
            MealieMedia.recipeImage(base, recipeId, MealieMedia.ImageSize.MEDIUM, "73"),
        )
    }

    @Test
    fun `a missing version token leaves the url untouched`() {
        assertEquals(
            "https://mealie.ndd.custom/api/media/recipes/$recipeId/images/original.webp",
            MealieMedia.recipeImage(base, recipeId, MealieMedia.ImageSize.ORIGINAL, null),
        )
    }

    @Test
    fun `an instance served under a sub path keeps its prefix`() {
        assertEquals(
            "https://home.lan/mealie/api/media/recipes/$recipeId/images/original.webp",
            MealieMedia.recipeImage("https://home.lan/mealie/", recipeId, MealieMedia.ImageSize.ORIGINAL, null),
        )
    }

    @Test
    fun `absolute step images are used as-is`() {
        val source = "https://cdn.example.com/photo.jpg"
        assertEquals(source, MealieMedia.resolveStepImage(base, recipeId, source))
    }

    @Test
    fun `root relative step images are resolved against the instance`() {
        assertEquals(
            "https://mealie.ndd.custom/api/media/recipes/$recipeId/assets/etape.jpg",
            MealieMedia.resolveStepImage(base, recipeId, "/api/media/recipes/$recipeId/assets/etape.jpg"),
        )
    }

    @Test
    fun `a bare file name is resolved as a recipe asset`() {
        assertEquals(
            "https://mealie.ndd.custom/api/media/recipes/$recipeId/assets/etape.jpg",
            MealieMedia.resolveStepImage(base, recipeId, "etape.jpg"),
        )
    }

    @Test
    fun `a relative path is resolved against the instance root`() {
        assertEquals(
            "https://mealie.ndd.custom/assets/etape.jpg",
            MealieMedia.resolveStepImage(base, recipeId, "assets/etape.jpg"),
        )
    }
}
