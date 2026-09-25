package org.opensources.umai.recipe.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.opensources.umai.core.image.EncodedImage
import org.opensources.umai.core.network.FakeMealieServer
import org.opensources.umai.provider.ProviderRegistry
import org.opensources.umai.provider.data.ProviderMediaImporter
import org.opensources.umai.provider.data.ProviderSettings
import org.opensources.umai.provider.jow.JowProvider
import org.opensources.umai.recipe.data.CalorieTagRepository
import org.opensources.umai.recipe.data.RecipeEditRepository
import org.opensources.umai.recipe.data.RecipeMediaRepository
import org.opensources.umai.youtube.data.VideoRecipeImporter
import org.opensources.umai.youtube.domain.ChapterMark
import org.opensources.umai.youtube.domain.ScriptedModel
import org.opensources.umai.youtube.domain.VideoSource
import org.opensources.umai.youtube.domain.YouTubeFailure
import org.opensources.umai.youtube.domain.YouTubeResult
import org.opensources.umai.youtube.domain.YouTubeVideo
import org.opensources.umai.youtube.domain.video

@OptIn(ExperimentalCoroutinesApi::class)
class RecipeImportViewModelTest {

    private lateinit var fake: FakeMealieServer

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        fake = FakeMealieServer()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        fake.shutdown()
    }

    private fun viewModel(
        url: String,
        importsMedia: Boolean = false,
        video: YouTubeResult<YouTubeVideo> = YouTubeResult.Failure(YouTubeFailure.UNAVAILABLE),
        modelReady: Boolean = false,
    ) = RecipeImportViewModel(
        initialUrl = url,
        repository = RecipeEditRepository(apiProvider = { fake.api() }),
        calorieTags = CalorieTagRepository { fake.api() },
        providers = ProviderRegistry(listOf(JowProvider)),
        providerSettings = object : ProviderSettings {
            override fun importsMedia(providerId: String): Flow<Boolean> = flowOf(importsMedia)
            override suspend fun setImportsMedia(providerId: String, enabled: Boolean) = Unit
        },
        mediaImporter = ProviderMediaImporter(
            apiProvider = { fake.api() },
            registry = ProviderRegistry(listOf(JowProvider)),
            media = RecipeMediaRepository { fake.api() },
            downloader = { EncodedImage(ByteArray(1), "image/jpeg", "jpg") },
        ),
        videoImporter = VideoRecipeImporter(
            youTube = object : VideoSource {
                override suspend fun video(id: String) = video
            },
            pages = { null },
            model = ScriptedModel(emptyList(), ready = modelReady),
            apiProvider = { fake.api() },
            edits = RecipeEditRepository(apiProvider = { fake.api() }),
            media = RecipeMediaRepository { fake.api() },
            language = { "fr" },
        ),
        modelReady = { modelReady },
    )

    @Test
    fun `a shared address is filled in and its provider named`() {
        val state = viewModel("https://jow.fr/recipes/curry", importsMedia = true).state.value

        assertEquals("https://jow.fr/recipes/curry", state.url)
        assertEquals("Jow", state.providerName)
        assertTrue(state.providerOffersVideo)
    }

    @Test
    fun `a provider whose media are not fetched is not announced`() {
        val state = viewModel("https://jow.fr/recipes/curry", importsMedia = false).state.value

        assertEquals("https://jow.fr/recipes/curry", state.url)
        assertNull(state.providerName)
    }

    @Test
    fun `a page already on the instance is reported instead of imported`() = runBlocking {
        fake.enqueueJson(SEARCH_WITH_CURRY)
        val viewModel = viewModel("https://jow.fr/recipes/curry")

        viewModel.import()
        val state = withTimeout(TIMEOUT_MS) { viewModel.state.first { it.phase == null && it.duplicate != null } }

        assertEquals("curry", state.duplicate?.slug)
        assertNull(state.imported)
        assertEquals(1, fake.server.requestCount)
    }

    @Test
    fun `importing anyway creates the recipe and tags its calories`() = runBlocking {
        fake.enqueueJson(""""curry-1"""")
        fake.enqueueJson("""{"id":"r2","slug":"curry-1","name":"Curry","nutrition":null,"tags":[]}""")
        val viewModel = viewModel("https://jow.fr/recipes/curry", importsMedia = false)

        viewModel.import(evenIfPresent = true)
        val state = withTimeout(TIMEOUT_MS) { viewModel.state.first { it.imported != null } }

        assertEquals(ImportedRecipe("curry-1", notice = null), state.imported)
        assertEquals("/api/recipes/create/url", fake.takeRequest().url.encodedPath)
        // The calorie tag is checked; with the provider switched off nothing else is asked.
        assertEquals("/api/recipes/curry-1", fake.takeRequest().url.encodedPath)
        assertEquals(2, fake.server.requestCount)
    }

    @Test
    fun `a YouTube video is announced as rebuilt, with or without the model`() = runBlocking {
        val withModel = viewModel("https://youtu.be/0nE7dAlDshk", modelReady = true)
        val state = withTimeout(TIMEOUT_MS) { withModel.state.first { it.videoUsesModel } }
        assertTrue(state.isVideo)
        assertNull(state.providerName)

        assertEquals(false, viewModel("https://youtu.be/0nE7dAlDshk").state.value.videoUsesModel)
    }

    @Test
    fun `a video without the model is imported with the rules, and the user told so`() = runBlocking {
        fake.enqueueJson("""{"page":1,"per_page":10,"total":0,"total_pages":0,"items":[]}""")
        fake.enqueueJson(""""poulet-curry"""")
        fake.enqueueJson(VIDEO_RECIPE)
        fake.enqueueJson(VIDEO_RECIPE)
        fake.enqueueJson(VIDEO_RECIPE)
        fake.enqueueJson("""{"name":"c","icon":"file-json","fileName":"c.json"}""")
        fake.enqueueJson(VIDEO_RECIPE)
        val viewModel = viewModel(
            "https://youtu.be/0nE7dAlDshk",
            video = YouTubeResult.Success(
                video(
                    description = "Ingrédients :\n2 blancs de poulet\n1 oignon",
                    chapters = listOf(ChapterMark("Le poulet", 0.0), ChapterMark("La sauce", 60.0)),
                ),
            ),
        )

        viewModel.import()
        val state = withTimeout(TIMEOUT_MS) { viewModel.state.first { it.imported != null } }

        assertEquals(ImportedRecipe("poulet-curry", ImportNotice.VIDEO_WITHOUT_MODEL), state.imported)
        // The duplicate check looks the video up by its watch address, whatever the shared form.
        assertTrue(fake.takeRequest().url.queryParameter("queryFilter").orEmpty().contains("youtube.com/watch?v=0nE7dAlDshk"))
        assertEquals("/api/recipes/create/html-or-json", fake.takeRequest().url.encodedPath)
    }

    @Test
    fun `a video YouTube refuses to show is reported, nothing created`() = runBlocking {
        fake.enqueueJson("""{"page":1,"per_page":10,"total":0,"total_pages":0,"items":[]}""")
        val viewModel = viewModel("https://youtu.be/0nE7dAlDshk", video = YouTubeResult.Failure(YouTubeFailure.BLOCKED))

        viewModel.import()
        val state = withTimeout(TIMEOUT_MS) { viewModel.state.first { it.videoFailure != null } }

        assertEquals(YouTubeFailure.BLOCKED, state.videoFailure)
        assertNull(state.phase)
        assertEquals(1, fake.server.requestCount)
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L

        const val VIDEO_RECIPE = """
            {"id":"r9","slug":"poulet-curry","name":"Poulet curry","tags":[],
             "recipeIngredient":[{"display":"2 blancs de poulet","note":"2 blancs de poulet","referenceId":"a"},
                                 {"display":"1 oignon","note":"1 oignon","referenceId":"b"}],
             "recipeInstructions":[{"id":"s1","text":"Le poulet"},{"id":"s2","text":"La sauce"}],
             "assets":[]}
        """

        const val SEARCH_WITH_CURRY = """{"page":1,"per_page":20,"total":1,"total_pages":1,
            "items":[{"id":"r1","slug":"curry","name":"Curry","orgURL":"https://jow.fr/recipes/curry"}]}"""
    }
}
