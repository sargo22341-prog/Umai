package org.opensources.umai.recipe.data

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.FakeMealieServer
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.recipe.domain.DraftChapter
import org.opensources.umai.recipe.domain.DraftVideo
import org.opensources.umai.recipe.domain.VideoChapters

/** The chapters of a recipe video, as the editor loads and saves them on Mealie. */
class RecipeVideoChaptersTest {

    private lateinit var fake: FakeMealieServer
    private lateinit var repository: RecipeEditRepository

    @Before
    fun setUp() {
        fake = FakeMealieServer()
        repository = RecipeEditRepository(apiProvider = { fake.api() })
    }

    @After
    fun tearDown() = fake.shutdown()

    @Test
    fun `a recipe opens with its video and the chapters of its steps`() = runTest {
        fake.enqueueJson(WITH_VIDEO)
        fake.enqueueJson(CHAPTERS)

        val editable = (repository.loadForEdit("pains") as ApiResult.Success).value

        fake.takeRequest()
        assertEquals("/api/media/recipes/r1/assets/pains-chapters.json", fake.takeRequest().url.encodedPath)
        assertEquals("pains-chapters.json", editable.videoFile)
        assertEquals(DraftVideo(YOUTUBE), editable.draft.video)
        assertEquals(listOf(DraftChapter(10.0), DraftChapter(120.0)), editable.draft.steps.map { it.chapter })
    }

    @Test
    fun `a chapters file that cannot be read keeps the editor from opening`() = runTest {
        fake.enqueueJson(WITH_VIDEO)
        fake.enqueueError(500)

        val result = repository.loadForEdit("pains")

        assertTrue((result as ApiResult.Failure).error is NetworkError.Server)
    }

    @Test
    fun `a recipe imported from YouTube without chapters opens with a video to place`() = runTest {
        fake.enqueueJson(WITH_VIDEO.replace(ASSETS, "[]"))

        val editable = (repository.loadForEdit("pains") as ApiResult.Success).value

        assertEquals(1, fake.server.requestCount)
        assertNull(editable.videoFile)
        assertEquals(DraftVideo(YOUTUBE), editable.draft.video)
    }

    @Test
    fun `moved chapters are written over the same file, whose duplicate entry is then removed`() = runTest {
        fake.enqueueJson(WITH_VIDEO)
        fake.enqueueJson(CHAPTERS)
        val editable = (repository.loadForEdit("pains") as ApiResult.Success).value
        fake.takeRequest()
        fake.takeRequest()
        val edited = VideoChapters.update(editable.draft, 1) { DraftChapter(150.0) }

        fake.enqueueJson("""{"name":"pains-chapters","icon":"file-json","fileName":"pains-chapters.json"}""")
        fake.enqueueJson(WITH_VIDEO.replace(ASSETS, "[$ASSET, $ASSET]"))
        fake.enqueueJson(WITH_VIDEO)
        val saved = (repository.saveVideoChapters("pains", editable, edited) as ApiResult.Success).value

        val upload = fake.takeRequest()
        assertEquals("/api/recipes/pains/assets", upload.url.encodedPath)
        val body = upload.body?.utf8().orEmpty()
        assertTrue(body.contains("filename=\"pains-chapters.json\""))
        assertTrue(body.contains(""""start":150.0"""))
        assertEquals("GET", fake.takeRequest().method)
        val tidy = fake.takeRequest()
        // Only the assets are written, so a change made meanwhile to the recipe is kept.
        assertEquals("PATCH", tidy.method)
        val tidied = Json.parseToJsonElement(tidy.body?.utf8().orEmpty()).jsonObject
        assertEquals(setOf("assets"), tidied.keys)
        assertEquals(1, Regex("pains-chapters.json").findAll(tidied.toString()).count())
        assertEquals(listOf(10.0, 150.0), saved.video?.chapters?.map { it.start })
        assertEquals("pains-chapters.json", saved.videoFile)
    }

    @Test
    fun `unchanged chapters are not written`() = runTest {
        fake.enqueueJson(WITH_VIDEO)
        fake.enqueueJson(CHAPTERS)
        val editable = (repository.loadForEdit("pains") as ApiResult.Success).value

        val saved = (repository.saveVideoChapters("pains", editable, editable.draft) as ApiResult.Success).value

        assertSame(editable, saved)
        assertEquals(2, fake.server.requestCount)
    }

    @Test
    fun `the first chapters of a recipe create its file`() = runTest {
        fake.enqueueJson(WITH_VIDEO.replace(ASSETS, "[]"))
        val editable = (repository.loadForEdit("pains") as ApiResult.Success).value
        fake.takeRequest()
        val placed = VideoChapters.update(editable.draft, 0) { DraftChapter(12.0) }

        fake.enqueueJson("""{"name":"petits-pains-chapters","icon":"file-json","fileName":"petits-pains-chapters.json"}""")
        val saved = (repository.saveVideoChapters("pains", editable, placed) as ApiResult.Success).value

        val body = fake.takeRequest().body?.utf8().orEmpty()
        assertTrue(body.contains("filename=\"petits-pains-chapters.json\""))
        assertTrue(body.contains(""""originalVideoUrl":"$YOUTUBE""""))
        assertEquals("petits-pains-chapters.json", saved.videoFile)
        // A new file has no duplicate entry to remove: the load, then the upload.
        assertEquals(2, fake.server.requestCount)
    }

    private companion object {
        const val YOUTUBE = "https://www.youtube.com/watch?v=y3L14JKSSYI"
        const val ASSET = """{"name":"pains-chapters","icon":"file-json","fileName":"pains-chapters.json"}"""
        const val ASSETS = "[$ASSET]"

        val WITH_VIDEO = """
            {"id":"r1","name":"Petits pains","slug":"pains","orgURL":"$YOUTUBE",
             "recipeIngredient":[],
             "recipeInstructions":[{"id":"s1","title":"","text":"Pétrir."},{"id":"s2","title":"","text":"Cuire."}],
             "assets":$ASSETS}
        """.trimIndent()

        val CHAPTERS = """
            {"version":1,"title":"Petits pains","source":{"url_ori":"$YOUTUBE","originalVideoUrl":"$YOUTUBE"},
             "chapters":[{"stepIndex":0,"start":10.0,"end":120.0},{"stepIndex":1,"start":120.0,"end":null}]}
        """.trimIndent()
    }
}
