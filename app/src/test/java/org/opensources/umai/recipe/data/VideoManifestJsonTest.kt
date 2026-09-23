package org.opensources.umai.recipe.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.umai.recipe.domain.VideoChapter
import org.opensources.umai.recipe.domain.VideoManifest

class VideoManifestJsonTest {

    @Test
    fun `a chapters file already on Mealie is read as it is`() {
        val manifest = VideoManifestJson.parse(EXISTING)!!

        assertEquals("Burger au poulet à la mexicaine", manifest.title)
        assertEquals("https://static.jow.fr/recipes/7dlGl4kYMlk0kw_transcoded.mp4", manifest.videoUrl)
        assertEquals("https://jow.fr/recipes/burger-au-poulet", manifest.sourceUrl)
        assertEquals(
            listOf(VideoChapter(-1, 0.0, 8.8), VideoChapter(0, 9.1, 19.0), VideoChapter(1, 19.3, null)),
            manifest.chapters,
        )
    }

    @Test
    fun `a file without the address of the video is still read`() {
        val manifest = VideoManifestJson.parse(
            """{"version":1,"title":"Tarte","videoAsset":"tarte.mp4","chapters":[{"stepId":"0","start":"2.5","end":""}]}""",
        )!!

        assertNull(manifest.videoUrl)
        assertEquals(listOf(VideoChapter(0, 2.5, null)), manifest.chapters)
    }

    @Test
    fun `a file without chapters is not a chapters file`() {
        assertNull(VideoManifestJson.parse("""{"version":1,"chapters":[]}"""))
        assertNull(VideoManifestJson.parse("not json"))
        assertNull(VideoManifestJson.parse("""["a"]"""))
    }

    @Test
    fun `what is written reads back the same`() {
        val manifest = VideoManifest(
            title = "Pâtes carbonara",
            sourceUrl = "https://jow.fr/recipes/pates-carbonara",
            videoUrl = "https://static.jow.fr/recipes/U72k_transcoded.mp4",
            chapters = listOf(VideoChapter(-1, 0.0, 8.8), VideoChapter(0, 9.1, 16.3), VideoChapter(1, 16.3, null)),
        )

        val text = VideoManifestJson.write(manifest)

        assertEquals(manifest, VideoManifestJson.parse(text))
        // The same keys as the files already on Mealie, so any reader of them reads this one.
        listOf("\"url_ori\"", "\"originalVideoUrl\"", "\"key\":\"ingredients\"", "\"key\":\"step-1\"")
            .forEach { assertTrue("$it missing from $text", it in text) }
    }

    private companion object {
        val EXISTING = """
            {
              "version": 1,
              "title": "Burger au poulet à la mexicaine",
              "source": {
                "type": "schema.org/Recipe",
                "name": "Burger au poulet à la mexicaine",
                "url_ori": "https://jow.fr/recipes/burger-au-poulet",
                "originalVideoUrl": "https://static.jow.fr/recipes/7dlGl4kYMlk0kw_transcoded.mp4"
              },
              "videoAsset": "burger-au-poulet-a-la-mexicaine.mp4",
              "chapters": [
                {"key": "step-2", "stepIndex": 1, "start": 19.3, "end": null},
                {"key": "ingredients", "label": "Ingredients", "stepIndex": -1, "start": 0, "end": 8.8},
                {"key": "step-1", "stepIndex": 0, "start": 9.1, "end": 19}
              ]
            }
        """.trimIndent()
    }
}
