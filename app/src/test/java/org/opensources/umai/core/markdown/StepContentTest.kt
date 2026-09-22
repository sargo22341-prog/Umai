package org.opensources.umai.core.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mealie has no image field on a step, so pictures are embedded in the text.
 * These cases mirror what the Mealie editor actually writes.
 */
class StepContentTest {

    @Test
    fun `step without image keeps its text untouched`() {
        val content = StepContent.parse("Faites revenir le poulet 2 minutes.")
        assertEquals("Faites revenir le poulet 2 minutes.", content.text)
        assertTrue(content.imageSources.isEmpty())
    }

    @Test
    fun `markdown image is extracted and removed from the text`() {
        val content = StepContent.parse(
            "Melangez le tout.\n\n![Illustration](/api/media/recipes/abc/assets/etape1.jpg)",
        )
        assertEquals("Melangez le tout.", content.text)
        assertEquals(listOf("/api/media/recipes/abc/assets/etape1.jpg"), content.imageSources)
    }

    @Test
    fun `html image tag is extracted and removed from the text`() {
        val content = StepContent.parse(
            """<img src="etape2.png" height="100%" width="100%">Laissez reposer.""",
        )
        assertEquals("Laissez reposer.", content.text)
        assertEquals(listOf("etape2.png"), content.imageSources)
    }

    @Test
    fun `several images in one step are all kept in order`() {
        val content = StepContent.parse(
            "Avant ![a](one.jpg) et apres ![b](two.jpg)",
        )
        assertEquals(listOf("one.jpg", "two.jpg"), content.imageSources)
        assertEquals("Avant  et apres", content.text)
    }

    @Test
    fun `the same image referenced twice is only kept once`() {
        val content = StepContent.parse("![a](same.jpg) ![b](same.jpg)")
        assertEquals(listOf("same.jpg"), content.imageSources)
    }

    @Test
    fun `markdown image with a title attribute is handled`() {
        val content = StepContent.parse("""![alt](photo.jpg "Une photo")""")
        assertEquals(listOf("photo.jpg"), content.imageSources)
        assertEquals("", content.text)
    }

    @Test
    fun `image only step leaves an empty text`() {
        val content = StepContent.parse("![](/api/media/recipes/abc/assets/x.webp)")
        assertEquals("", content.text)
        assertEquals(1, content.imageSources.size)
    }

    @Test
    fun `blank step yields empty content`() {
        val content = StepContent.parse("   ")
        assertEquals("", content.text)
        assertTrue(content.imageSources.isEmpty())
    }

    @Test
    fun `a plain link is not mistaken for an image`() {
        val content = StepContent.parse("Voir [la source](https://exemple.fr/page).")
        assertTrue(content.imageSources.isEmpty())
        assertEquals("Voir [la source](https://exemple.fr/page).", content.text)
    }
}
