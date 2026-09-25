package org.opensources.umai.youtube.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.umai.llm.domain.LanguageModel
import org.opensources.umai.llm.domain.LlmFailure
import org.opensources.umai.llm.domain.LlmOutcome
import org.opensources.umai.llm.domain.LlmProgress
import org.opensources.umai.llm.domain.LlmRequest
import org.opensources.umai.recipe.domain.VideoChapter

/** A language model that answers what the test says, and remembers what it was asked. */
class ScriptedModel(private val answers: List<LlmOutcome>, private val ready: Boolean = true) : LanguageModel {
    val requests = mutableListOf<LlmRequest>()
    override suspend fun isReady() = ready
    override suspend fun tokenCount(text: String): Int = text.length / 3
    override val contextSize: Int = 16_384
    override suspend fun generate(request: LlmRequest, onProgress: (LlmProgress) -> Unit): LlmOutcome {
        requests += request
        onProgress(LlmProgress(10, 10, 1))
        return answers.getOrElse(requests.size - 1) { answers.last() }
    }
}

fun video(
    description: String = "",
    chapters: List<ChapterMark> = emptyList(),
    transcript: List<TranscriptCue> = emptyList(),
    duration: Int = 300,
) = YouTubeVideo(
    id = "0nE7dAlDshk",
    title = "Lasagnes express | Recette facile #Shorts",
    author = "750g",
    description = description,
    durationSeconds = duration,
    thumbnailUrl = "https://i.ytimg.com/vi/0nE7dAlDshk/maxresdefault.jpg",
    chapters = chapters,
    transcript = transcript,
    transcriptAutomatic = true,
)

class RuleRecipeBuilderTest {

    @Test
    fun `the title loses its tags and slogans`() {
        assertEquals("Lasagnes express", RuleRecipeBuilder.cleanTitle("Lasagnes express | Recette facile #Shorts"))
        assertEquals("Poulet curry", RuleRecipeBuilder.cleanTitle("Poulet curry 🍛"))
    }

    @Test
    fun `chapters become steps told by what is said, the greeting and the tasting left out`() {
        val blueprint = RuleRecipeBuilder.build(
            video(
                description = "Ingrédients :\n250 g de pâtes à lasagnes\n500 g de viande hachée\n",
                chapters = listOf(
                    ChapterMark("Présentation de la recette", 0.0),
                    ChapterMark("Les ingrédients", 20.0),
                    ChapterMark("La sauce bolognaise", 60.0),
                    ChapterMark("Finitions", 180.0),
                    ChapterMark("Dégustation", 250.0),
                ),
                transcript = listOf(TranscriptCue(65.0, 70.0, "on fait revenir la viande")),
            ),
        )

        assertEquals(listOf("250 g de pâtes à lasagnes", "500 g de viande hachée"), blueprint.ingredients)
        assertEquals(listOf("La sauce bolognaise", "Finitions"), blueprint.steps.map { it.title })
        assertEquals("On fait revenir la viande", blueprint.steps[0].text)
        assertEquals("Finitions", blueprint.steps[1].text)
        assertEquals(listOf(60.0, 180.0), blueprint.steps.map { it.start })
        assertEquals(20.0, blueprint.ingredientsStart)
        assertEquals(BlueprintOrigin.RULES, blueprint.origin)
    }

    @Test
    fun `written steps take the chapter starts when there is one chapter each`() {
        val blueprint = RuleRecipeBuilder.build(
            video(
                description = "Préparation :\n1. Cuire les pâtes al dente.\n2. Napper de sauce et servir chaud.",
                chapters = listOf(ChapterMark("Cuisson", 0.0), ChapterMark("Service", 120.0)),
            ),
        )

        assertEquals(listOf("Cuire les pâtes al dente.", "Napper de sauce et servir chaud."), blueprint.steps.map { it.text })
        assertEquals(listOf(0.0, 120.0), blueprint.steps.map { it.start })
    }

    @Test
    fun `without chapters nor written steps, the transcript is cut into steps`() {
        val blueprint = RuleRecipeBuilder.build(
            video(
                transcript = listOf(
                    TranscriptCue(0.0, 5.0, "on épluche les pommes de terre"),
                    TranscriptCue(70.0, 75.0, "on les fait cuire à l'eau"),
                ),
            ),
        )

        assertEquals(2, blueprint.steps.size)
        assertEquals(listOf(0.0, 70.0), blueprint.steps.map { it.start })
        assertTrue(blueprint.isUsable)
    }

    @Test
    fun `a video with nothing to read gives nothing usable`() {
        assertFalse(RuleRecipeBuilder.build(video()).isUsable)
    }

    @Test
    fun `the manifest plays each step until the next one, the ingredients first`() {
        val blueprint = RuleRecipeBuilder.build(
            video(
                chapters = listOf(ChapterMark("Ingrédients", 5.0), ChapterMark("Sauce", 30.0), ChapterMark("Cuisson", 90.0)),
                duration = 200,
            ),
        )

        val manifest = requireNotNull(blueprint.manifest())
        assertEquals("https://www.youtube.com/watch?v=0nE7dAlDshk", manifest.videoUrl)
        assertEquals(
            listOf(VideoChapter(-1, 5.0, 30.0), VideoChapter(0, 30.0, 90.0), VideoChapter(1, 90.0, 200.0)),
            manifest.chapters,
        )
    }
}

class ModelRecipeBuilderTest {

    private val answer = """
        {"name": "Lasagnes à la bolognaise", "summary": "Des lasagnes maison.", "servings": 4,
         "prepMinutes": 30, "cookMinutes": 50,
         "ingredients": ["250 g de feuilles de lasagne", "500 g de bœuf haché"],
         "steps": [
           {"title": "Sauce", "text": "Faire revenir le bœuf.", "start": 60},
           {"title": "Montage", "text": "Monter les lasagnes.", "start": 40},
           {"title": "Cuisson", "text": "Cuire 50 minutes.", "start": 250}
         ]}
    """.trimIndent()

    @Test
    fun `the answer of the model becomes the recipe, out-of-order starts dropped`() = runBlocking {
        val model = ScriptedModel(listOf(LlmOutcome.Success(answer)))

        val outcome = ModelRecipeBuilder(model).build(video(), "fr") {}

        val blueprint = (outcome as ModelRecipeBuilder.Outcome.Built).blueprint
        assertEquals("Lasagnes à la bolognaise", blueprint.name)
        assertEquals(4, blueprint.servings)
        assertEquals(30, blueprint.prepMinutes)
        assertEquals(listOf("Sauce", "Montage", "Cuisson"), blueprint.steps.map { it.title })
        assertEquals(listOf(60.0, null, 250.0), blueprint.steps.map { it.start })
        assertEquals(BlueprintOrigin.LANGUAGE_MODEL, blueprint.origin)
        assertTrue(model.requests.single().system.contains("French"))
    }

    @Test
    fun `the prompt holds the description, the chapters and the timed transcript`() {
        val prompt = ModelRecipeBuilder(ScriptedModel(emptyList())).userPrompt(
            video(
                description = "Pour 4 personnes",
                chapters = listOf(ChapterMark("La sauce", 85.0)),
                transcript = listOf(TranscriptCue(12.0, 15.0, "bonjour")),
            ),
            transcriptChars = 1_000,
        )

        assertTrue(prompt.contains("Pour 4 personnes"))
        assertTrue(prompt.contains("[85s] La sauce"))
        assertTrue(prompt.contains("[12s] bonjour"))
        assertTrue(prompt.contains("automatic captions"))
    }

    @Test
    fun `a prompt too long is tried again with less transcript`() = runBlocking {
        val model = ScriptedModel(listOf(LlmOutcome.Failure(LlmFailure.TOO_LONG), LlmOutcome.Success(answer)))
        val long = (0 until 4_000).map { TranscriptCue(it * 20.0, it * 20.0 + 5, "mot ".repeat(30)) }

        val outcome = ModelRecipeBuilder(model).build(video(transcript = long, duration = 90_000), "en") {}

        assertTrue(outcome is ModelRecipeBuilder.Outcome.Built)
        assertEquals(2, model.requests.size)
        assertTrue(model.requests[1].user.length < model.requests[0].user.length)
    }

    @Test
    fun `a failure of the model is reported`() = runBlocking {
        val model = ScriptedModel(listOf(LlmOutcome.Failure(LlmFailure.LOAD_FAILED)))

        val outcome = ModelRecipeBuilder(model).build(video(), "fr") {}

        assertEquals(ModelRecipeBuilder.Outcome.Failed(LlmFailure.LOAD_FAILED), outcome)
    }

    @Test
    fun `an answer that is not a recipe is rejected, missing parts come from the rules`() {
        val builder = ModelRecipeBuilder(ScriptedModel(emptyList()))
        assertNull(builder.parse("not json", video()))
        assertNull(builder.parse("""{"ingredients": [], "steps": []}""", video()))

        val partial = builder.parse(
            """{"name": "", "ingredients": [], "steps": [{"title": "", "text": "Cuire.", "start": 0}]}""",
            video(description = "Ingrédients :\n2 œufs\n100 g de farine"),
        )
        assertEquals("Lasagnes express", partial?.name)
        assertEquals(listOf("2 œufs", "100 g de farine"), partial?.ingredients)
    }
}
