package org.opensources.umai.youtube.data

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.opensources.umai.core.network.FakeMealieServer
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.llm.domain.LlmFailure
import org.opensources.umai.llm.domain.LlmOutcome
import org.opensources.umai.recipe.data.RecipeEditRepository
import org.opensources.umai.recipe.data.RecipeMediaRepository
import org.opensources.umai.youtube.domain.BlueprintOrigin
import org.opensources.umai.youtube.domain.ChapterMark
import org.opensources.umai.youtube.domain.ScriptedModel
import org.opensources.umai.youtube.domain.TranscriptCue
import org.opensources.umai.youtube.domain.VideoSource
import org.opensources.umai.youtube.domain.YouTubeFailure
import org.opensources.umai.youtube.domain.YouTubeResult
import org.opensources.umai.youtube.domain.YouTubeVideo
import org.opensources.umai.youtube.domain.video

class VideoRecipeImporterTest {

    private lateinit var fake: FakeMealieServer

    @Before
    fun setUp() {
        fake = FakeMealieServer()
    }

    @After
    fun tearDown() = fake.shutdown()

    private fun source(result: YouTubeResult<YouTubeVideo>) = object : VideoSource {
        override suspend fun video(id: String) = result
    }

    private fun importer(video: YouTubeResult<YouTubeVideo>, model: ScriptedModel) = VideoRecipeImporter(
        youTube = source(video),
        model = model,
        apiProvider = { fake.api() },
        edits = RecipeEditRepository({ fake.api() }),
        media = RecipeMediaRepository { fake.api() },
        language = { "fr" },
    )

    private val lasagnes = video(
        description = "Ingrédients :\n250 g de pâtes\n500 g de viande hachée",
        chapters = listOf(ChapterMark("La viande", 10.0), ChapterMark("Le montage", 100.0)),
        transcript = listOf(TranscriptCue(12.0, 15.0, "on fait revenir la viande")),
    )

    private val answer = """
        {"name":"Lasagnes","summary":"Maison.","servings":4,"prepMinutes":20,"cookMinutes":45,
         "ingredients":["250 g de pâtes","500 g de viande hachée"],
         "steps":[{"title":"La viande","text":"Faire revenir la viande hachée.","start":10},
                  {"title":"Montage","text":"Alterner pâtes et sauce.","start":100}]}
    """.trimIndent()

    @Test
    fun `the video is rebuilt by the model, created from its schema, then tied to its video`() = runTest {
        fake.enqueueJson(""""lasagnes"""")
        fake.enqueueJson(CREATED)
        fake.enqueueJson(CREATED)
        fake.enqueueJson(CREATED)
        fake.enqueueJson("""{"name":"lasagnes-chapters","icon":"file-json","fileName":"lasagnes-chapters.json"}""")
        val phases = mutableListOf<VideoImportProgress>()

        val outcome = importer(YouTubeResult.Success(lasagnes), ScriptedModel(listOf(LlmOutcome.Success(answer))))
            .import("https://youtu.be/0nE7dAlDshk") { phases += it }

        val result = (outcome as VideoImportOutcome.Imported).result
        assertEquals("lasagnes", result.slug)
        assertEquals(BlueprintOrigin.LANGUAGE_MODEL, result.origin)
        assertNull(result.modelFailure)
        assertTrue(result.videoLinked)
        assertEquals(VideoImportProgress.ReadingVideo, phases.first())
        assertEquals(VideoImportProgress.Saving, phases.last())

        val create = fake.takeRequest()
        assertEquals("/api/recipes/create/html-or-json", create.url.encodedPath)
        val body = create.body?.utf8().orEmpty()
        assertTrue(body.contains("schema.org"))
        assertTrue(body.contains("500 g de viande hach"))
        assertTrue(body.contains(""""url":"https://www.youtube.com/watch?v=0nE7dAlDshk""""))

        assertEquals("GET", fake.takeRequest().method)
        assertEquals("GET", fake.takeRequest().method)
        val update = fake.takeRequest()
        assertEquals("PUT", update.method)
        val steps = update.body?.utf8().orEmpty()
        assertTrue(steps.contains(""""title":"Montage""""))
        assertTrue(steps.contains("Alterner pâtes et sauce."))
        // The first step names the minced meat: it is linked to it by name.
        assertTrue(steps.contains(""""referenceId":"ref-meat""""))

        val asset = fake.takeRequest()
        assertEquals("/api/recipes/lasagnes/assets", asset.url.encodedPath)
        assertTrue(asset.body?.utf8().orEmpty().contains("https://www.youtube.com/watch?v=0nE7dAlDshk"))
    }

    @Test
    fun `without a model the recipe is rebuilt with the rules`() = runTest {
        fake.enqueueJson(""""lasagnes"""")
        fake.enqueueJson(CREATED)
        fake.enqueueJson(CREATED)
        fake.enqueueJson(CREATED)
        fake.enqueueJson("""{"name":"lasagnes-chapters","icon":"file-json","fileName":"lasagnes-chapters.json"}""")
        val model = ScriptedModel(emptyList(), ready = false)

        val outcome = importer(YouTubeResult.Success(lasagnes), model).import("https://youtu.be/0nE7dAlDshk") {}

        val result = (outcome as VideoImportOutcome.Imported).result
        assertEquals(BlueprintOrigin.RULES, result.origin)
        assertTrue(model.requests.isEmpty())
    }

    @Test
    fun `a model that fails hands over to the rules and says so`() = runTest {
        fake.enqueueJson(""""lasagnes"""")
        fake.enqueueJson(CREATED)
        fake.enqueueJson(CREATED)
        fake.enqueueJson(CREATED)
        fake.enqueueJson("""{"name":"lasagnes-chapters","icon":"file-json","fileName":"lasagnes-chapters.json"}""")
        val model = ScriptedModel(listOf(LlmOutcome.Failure(LlmFailure.LOAD_FAILED)))

        val outcome = importer(YouTubeResult.Success(lasagnes), model).import("https://youtu.be/0nE7dAlDshk") {}

        val result = (outcome as VideoImportOutcome.Imported).result
        assertEquals(BlueprintOrigin.RULES, result.origin)
        assertEquals(LlmFailure.LOAD_FAILED, result.modelFailure)
    }

    @Test
    fun `a video that cannot be read creates nothing`() = runTest {
        val outcome = importer(YouTubeResult.Failure(YouTubeFailure.BLOCKED), ScriptedModel(emptyList()))
            .import("https://youtu.be/0nE7dAlDshk") {}

        assertEquals(VideoImportOutcome.VideoFailed(YouTubeFailure.BLOCKED), outcome)
        assertEquals(0, fake.server.requestCount)
    }

    @Test
    fun `an address that is no video is refused`() = runTest {
        val outcome = importer(YouTubeResult.Success(lasagnes), ScriptedModel(emptyList())).import("https://example.org") {}
        assertEquals(VideoImportOutcome.VideoFailed(YouTubeFailure.NOT_A_VIDEO), outcome)
    }

    @Test
    fun `a video with nothing to rebuild from creates nothing`() = runTest {
        val outcome = importer(YouTubeResult.Success(video()), ScriptedModel(emptyList(), ready = false))
            .import("https://youtu.be/0nE7dAlDshk") {}

        assertEquals(VideoImportOutcome.NothingToRebuild, outcome)
        assertEquals(0, fake.server.requestCount)
    }

    @Test
    fun `a Mealie refusing the recipe is reported`() = runTest {
        fake.enqueueError(500)

        val outcome = importer(YouTubeResult.Success(lasagnes), ScriptedModel(emptyList(), ready = false))
            .import("https://youtu.be/0nE7dAlDshk") {}

        assertTrue((outcome as VideoImportOutcome.SaveFailed).error is NetworkError.Server)
    }

    private companion object {
        /** The recipe as Mealie created it from the schema: two plain steps. */
        const val CREATED = """
            {"id":"r1","slug":"lasagnes","name":"Lasagnes",
             "recipeIngredient":[
               {"display":"250 g de pâtes","note":"250 g de pâtes","referenceId":"ref-pasta"},
               {"display":"500 g de viande hachée","note":"500 g de viande hachée","referenceId":"ref-meat"}],
             "recipeInstructions":[
               {"id":"s1","title":"","text":"Faire revenir la viande hachée.","ingredientReferences":[]},
               {"id":"s2","title":"","text":"Alterner pâtes et sauce.","ingredientReferences":[]}],
             "assets":[]}
        """
    }
}
