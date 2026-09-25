package org.opensources.umai.youtube.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptTest {

    private val cues = listOf(
        TranscriptCue(0.0, 4.0, "[Musique]"),
        TranscriptCue(4.0, 9.0, "bonjour à tous aujourd'hui on fait des lasagnes"),
        TranscriptCue(20.0, 26.0, "on commence par éplucher les carottes et les oignons"),
        TranscriptCue(40.0, 46.0, "on coupe les carottes et les oignons en petits dés"),
        TranscriptCue(80.0, 86.0, "on fait revenir la viande hachée dans l'huile d'olive"),
        TranscriptCue(120.0, 126.0, "pour la béchamel on fait fondre le beurre avec la farine"),
        TranscriptCue(160.0, 166.0, "on ajoute le lait petit à petit en fouettant la béchamel"),
        TranscriptCue(200.0, 206.0, "on monte les lasagnes couche par couche"),
    )

    @Test
    fun `sound tags are not speech`() {
        assertEquals("on y va", Transcript.clean("[Musique] on  y va (applause)"))
    }

    @Test
    fun `the words said between two times are gathered`() {
        assertEquals(
            "on fait revenir la viande hachée dans l'huile d'olive",
            Transcript.textBetween(cues, 60.0, 100.0),
        )
    }

    @Test
    fun `timed blocks are labelled in seconds and cut at the limit`() {
        val blocks = Transcript.timedBlocks(cues, blockSeconds = 15.0)
        assertTrue(blocks.startsWith("[0s] bonjour à tous aujourd'hui on fait des lasagnes\n[20s] on commence"))

        val short = Transcript.timedBlocks(cues, blockSeconds = 15.0, maxChars = 60)
        assertEquals("[0s] bonjour à tous aujourd'hui on fait des lasagnes", short)
    }

    @Test
    fun `steps are placed in order where their words are said`() {
        val starts = Transcript.alignSteps(
            listOf(
                "Éplucher les carottes et les oignons.",
                "Faire revenir la viande hachée dans l'huile d'olive.",
                "Préparer la béchamel avec le beurre, la farine et le lait.",
                "Monter les lasagnes couche par couche.",
            ),
            cues,
        )

        requireNotNull(starts)
        assertEquals(4, starts.size)
        assertTrue(starts[0] <= 40.0)
        assertTrue(starts[1] in 60.0..80.0)
        assertTrue(starts[2] in 100.0..160.0)
        assertTrue(starts[3] >= 180.0)
        assertEquals(starts.sorted(), starts)
    }

    @Test
    fun `steps sharing nothing with the transcript are not placed`() {
        assertNull(Transcript.alignSteps(listOf("Zzz qqq", "Www yyy"), cues))
    }
}
