package org.opensources.umai.recipe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.umai.core.model.RecipeAsset

class RecipeMediaFilesTest {

    private fun asset(fileName: String) = RecipeAsset(name = fileName.substringBeforeLast('.'), icon = "", fileName = fileName)

    @Test
    fun `step photos are recognised by their name`() {
        assertEquals(0, RecipeMediaFiles.stepNumberOf("step-0.jpg"))
        assertEquals(12, RecipeMediaFiles.stepNumberOf("STEP-12.WEBP"))
        assertNull(RecipeMediaFiles.stepNumberOf("step-1.json"))
        assertNull(RecipeMediaFiles.stepNumberOf("steps-1.jpg"))
        assertNull(RecipeMediaFiles.stepNumberOf("tarte-chapters.json"))
        assertNull(RecipeMediaFiles.stepNumberOf(null))
    }

    @Test
    fun `each step keeps its first photo`() {
        val photos = RecipeMediaFiles.stepPhotos(
            listOf(asset("step-1.jpg"), asset("notice.pdf"), asset("step-1.png"), asset("step-3.webp")),
        )

        assertEquals(mapOf(1 to "step-1.jpg", 3 to "step-3.webp"), photos)
    }

    @Test
    fun `the chapters file is the JSON ending in -chapters`() {
        val assets = listOf(asset("notes.json"), asset("tarte.mp4"), asset("poulet-au-curry-chapters.json"))
        assertEquals("poulet-au-curry-chapters.json", RecipeMediaFiles.chaptersFile(assets))

        assertNull(RecipeMediaFiles.chaptersFile(listOf(asset("notes.json"), asset("step-1.jpg"), asset("tarte-chapters.txt"))))
    }

    @Test
    fun `video files are told by their extension`() {
        assertTrue(RecipeMediaFiles.isVideo("clip.MOV"))
        assertFalse(RecipeMediaFiles.isVideo("step-1.jpg"))
    }
}
