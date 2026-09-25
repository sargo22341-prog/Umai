package org.opensources.umai.youtube.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.umai.youtube.domain.TranscriptCue

class YouTubeMarkupTest {

    @Test
    fun `a description reads as the author typed it, links shown in full`() {
        val html = "⚖️ Quantités de la recette : <a href=\"https://tinyurl.com/3xddjkdm\">https://tinyurl.com/3xddjkdm</a><br>" +
            "📖 Livre &quot;Bien cuisiner&quot; : <a href=\"https://tinyurl.com/mucumd28\">https://tinyurl.com/muc...</a><br><br>" +
            "Facebook <a href=\"https://www.facebook.com/Chef\">Facebook: Chef</a><br>" +
            "L&apos;astuce du chef &amp; ses 200&nbsp;g de beurre"

        assertEquals(
            """
            ⚖️ Quantités de la recette : https://tinyurl.com/3xddjkdm
            📖 Livre "Bien cuisiner" : https://tinyurl.com/mucumd28

            Facebook Facebook: Chef
            L'astuce du chef & ses 200 g de beurre
            """.trimIndent(),
            YouTubeMarkup.descriptionText(html),
        )
    }

    @Test
    fun `the line breaks YouTube writes around its own ones do not double them`() {
        assertEquals("Sel\nPoivre\n\n\nFin", YouTubeMarkup.descriptionText("Sel<br>Poivre<br>\n<br>\n<br>Fin"))
    }

    @Test
    fun `a link wrapped by YouTube's redirect reads as its target`() {
        val html = "Recipe: <a href=\"https://www.youtube.com/redirect?event=video_description&amp;q=https%3A%2F%2Fexample.org%2Fpancakes&amp;v=x\">https://example.org/pan...</a>"

        assertEquals("Recipe: https://example.org/pancakes", YouTubeMarkup.descriptionText(html))
    }

    @Test
    fun `a plain description is left as it is`() {
        assertEquals("200 g de farine\n2 œufs", YouTubeMarkup.descriptionText("200 g de farine\n2 œufs"))
    }

    @Test
    fun `TTML captions become timed cues, entities decoded and line breaks joined`() {
        val ttml = """
            <?xml version="1.0" encoding="utf-8" ?>
            <tt xml:lang="fr" xmlns="http://www.w3.org/ns/ttml"><body region="r1"><div>
            <p begin="00:00:00.119" end="00:00:05.265" style="s2">Bonjour à tous, j&#39;suis<br />content</p>
            <p begin="00:01:02.500" end="00:01:04.000" style="s2">[Musique]</p>
            <p begin="01:00:00.000" end="01:00:01.000" style="s2">   </p>
            </div></body></tt>
        """.trimIndent()

        assertEquals(
            listOf(
                TranscriptCue(0.119, 5.265, "Bonjour à tous, j'suis content"),
                TranscriptCue(62.5, 64.0, "[Musique]"),
            ),
            YouTubeMarkup.ttmlCues(ttml),
        )
    }

    @Test
    fun `a document without captions gives no cue`() {
        assertTrue(YouTubeMarkup.ttmlCues("<html><body>Not found</body></html>").isEmpty())
    }

    @Test
    fun `numeric and unknown references are handled`() {
        assertEquals("é – &unknown;", YouTubeMarkup.decode("&#233; &#x2013; &unknown;"))
    }
}

class CaptionChoiceTest {

    private fun track(tag: String, automatic: Boolean) = CaptionTrack("https://yt/$tag/$automatic", tag, automatic)

    @Test
    fun `a transcript written by a person in the spoken language comes first`() {
        val tracks = listOf(track("en", automatic = true), track("en", automatic = false))

        assertEquals(track("en", automatic = false), CaptionChoice.preferred(tracks, spokenLanguage = null))
    }

    @Test
    fun `a dubbed video keeps the captions of its original language`() {
        // YouTube offers an automatic track in each language it dubs the video into.
        val tracks = listOf(track("en-US", automatic = true), track("fr", automatic = true))

        assertEquals(track("fr", automatic = true), CaptionChoice.preferred(tracks, spokenLanguage = "fr"))
    }

    @Test
    fun `a translation written by a person is not taken for the spoken language`() {
        val tracks = listOf(track("fr", automatic = true), track("en", automatic = false))

        assertEquals(track("fr", automatic = true), CaptionChoice.preferred(tracks, spokenLanguage = null))
    }

    @Test
    fun `without automatic track, any written one is taken`() {
        val tracks = listOf(track("de", automatic = false))

        assertEquals(track("de", automatic = false), CaptionChoice.preferred(tracks, spokenLanguage = "fr"))
    }

    @Test
    fun `a video without captions has no transcript`() {
        assertNull(CaptionChoice.preferred(emptyList(), spokenLanguage = "fr"))
    }
}
