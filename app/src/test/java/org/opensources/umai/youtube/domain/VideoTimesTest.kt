package org.opensources.umai.youtube.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.umai.llm.domain.LlmOutcome

/** Where the steps of a recipe rebuilt from a video are placed in it, and from what. */
class VideoTimesTest {

    /** Numbers as LiteRT-LM hands tool arguments back: `0.0` for `0`. */
    private val answer = """
        {"name": "Petits pains farcis", "summary": "Sans four.", "servings": 8.0,
         "prepMinutes": 30.0, "cookMinutes": 3.0, "ingredients": [],
         "steps": [
           {"title": "La pâte", "text": "Mélanger la farine et le lait.", "start": 0.0},
           {"title": "La farce", "text": "Râper le fromage.", "start": 90.0},
           {"title": "La cuisson", "text": "Cuire à la poêle.", "start": 240.0}
         ]}
    """.trimIndent()

    private val spoken = listOf(
        TranscriptCue(0.0, 30.0, "on mélange la farine et le lait"),
        TranscriptCue(90.0, 120.0, "je râpe le fromage"),
        TranscriptCue(240.0, 270.0, "on cuit à la poêle"),
    )

    @Test
    fun `numbers written as decimals are read`() = runBlocking {
        val model = ScriptedModel(listOf(LlmOutcome.Success(answer)))

        val blueprint = (ModelRecipeBuilder(model).build(video(transcript = spoken), "fr") {} as ModelRecipeBuilder.Outcome.Built).blueprint

        assertEquals(8, blueprint.servings)
        assertEquals(30, blueprint.prepMinutes)
        assertEquals(3, blueprint.cookMinutes)
        assertEquals(listOf(0.0, 90.0, 240.0), blueprint.steps.map { it.start })
    }

    @Test
    fun `without chapters nor speech the starts the model writes are guesses, and dropped`() = runBlocking {
        val model = ScriptedModel(listOf(LlmOutcome.Success(answer)))
        val music = video(transcript = listOf(TranscriptCue(0.0, 285.0, "[Musique]")))

        val blueprint = (ModelRecipeBuilder(model).build(music, "fr") {} as ModelRecipeBuilder.Outcome.Built).blueprint

        assertFalse(music.hasTimes)
        assertEquals(listOf(null, null, null), blueprint.steps.map { it.start })
        assertNull(blueprint.manifest())
    }

    @Test
    fun `a transcript heard by the app is labelled as such`() {
        val prompt = ModelRecipeBuilder(ScriptedModel(emptyList()))
            .userPrompt(video(transcript = spoken, source = TranscriptSource.HEARD), transcriptChars = 1_000)

        assertTrue(prompt.contains("heard from the sound track"))
        assertTrue(prompt.contains("[90s] je râpe le fromage"))
    }

    @Test
    fun `a video seen in pictures is written without their times, then placed on them`() = runBlocking {
        val seen = video(
            transcript = listOf(
                TranscriptCue(0.0, 20.0, "La farine est versée dans un bol."),
                TranscriptCue(100.0, 120.0, "Une main coupe de la charcuterie."),
                TranscriptCue(240.0, 260.0, "Une galette cuit dans une poêle."),
            ),
            source = TranscriptSource.SEEN,
        )
        val model = ScriptedModel(
            listOf(
                LlmOutcome.Success(answer),
                LlmOutcome.Success("""{"step_1": 0.0, "step_2": 100.0, "step_3": 240.0}"""),
            ),
        )

        val blueprint = (ModelRecipeBuilder(model).build(seen, "fr") {} as ModelRecipeBuilder.Outcome.Built).blueprint

        val writing = model.requests[0].user
        assertTrue(writing.contains("- La farine est versée dans un bol."))
        assertFalse(writing.contains("[100s]"))
        assertTrue(model.requests[1].user.contains("[100s] Une main coupe de la charcuterie."))
        assertEquals(listOf(0.0, 100.0, 240.0), blueprint.steps.map { it.start })
        assertEquals(seen, blueprint.video)
    }

    @Test
    fun `placement asks for one named start per step`() {
        val schema = PicturePlacement.schema(2)

        assertTrue(schema.contains("\"required\": [\"step_1\", \"step_2\"]"))
        assertEquals(listOf(12.0, 40.0), PicturePlacement.starts("""{"step_1": 12, "step_2": 40.0}""", 2))
        assertNull(PicturePlacement.starts("""{"step_1": 12}""", 2))
    }

    @Test
    fun `an unusable placement falls back to the words the steps share with the pictures`() = runBlocking {
        val steps = listOf(
            BlueprintStep("La pâte", "Verser la farine dans un bol.", null),
            BlueprintStep("La cuisson", "Cuire la galette à la poêle.", null),
        )
        val seen = video(
            transcript = listOf(
                TranscriptCue(0.0, 20.0, "la farine est versée dans un bol"),
                TranscriptCue(20.0, 200.0, "une main pétrit"),
                TranscriptCue(200.0, 240.0, "une galette cuit dans une poêle"),
            ),
            source = TranscriptSource.SEEN,
        )

        val placed = PicturePlacement(ScriptedModel(listOf(LlmOutcome.Success("""{"starts": [0, 10, 20]}""")))).place(steps, seen) {}

        assertEquals(0.0, placed[0].start)
        assertTrue(requireNotNull(placed[1].start) >= 180.0)
    }

    @Test
    fun `the rules never turn what pictures show into the text of steps`() {
        val seen = video(transcript = listOf(TranscriptCue(0.0, 20.0, "Une main verse du jus d'orange.")), source = TranscriptSource.SEEN)

        assertTrue(RuleRecipeBuilder.build(seen).steps.isEmpty())
    }
}
