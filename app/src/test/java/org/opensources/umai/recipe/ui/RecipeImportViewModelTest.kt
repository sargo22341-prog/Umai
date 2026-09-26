package org.opensources.umai.recipe.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
import org.opensources.umai.provider.domain.ProviderRegistry
import org.opensources.umai.provider.data.ProviderMediaImporter
import org.opensources.umai.provider.data.ProviderSettings
import org.opensources.umai.provider.jow.JowProvider
import org.opensources.umai.recipe.data.CalorieTagRepository
import org.opensources.umai.recipe.data.RecipeEditRepository
import org.opensources.umai.recipe.data.RecipeImportController
import org.opensources.umai.recipe.data.RecipeMediaRepository
import org.opensources.umai.recipe.data.RecordingImportHost
import org.opensources.umai.recipe.domain.ImportNotice
import org.opensources.umai.recipe.domain.ImportPhase
import org.opensources.umai.recipe.domain.ImportedRecipe
import org.opensources.umai.youtube.data.VideoRecipeImporter
import org.opensources.umai.youtube.domain.ChapterMark
import org.opensources.umai.youtube.domain.FakeTranscriber
import org.opensources.umai.youtube.domain.FakeVideoMedia
import org.opensources.umai.youtube.domain.ScriptedModel
import org.opensources.umai.youtube.domain.VideoSource
import org.opensources.umai.youtube.domain.VideoWatcher
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

    private val host = RecordingImportHost()

    private fun viewModel(
        url: String?,
        importsMedia: Boolean = false,
        video: YouTubeResult<YouTubeVideo> = YouTubeResult.Failure(YouTubeFailure.UNAVAILABLE),
        modelReady: Boolean = false,
        imports: RecipeImportController = controller(importsMedia, { video }, modelReady),
    ) = RecipeImportViewModel(
        initialUrl = url,
        imports = imports,
        providers = ProviderRegistry(listOf(JowProvider)),
        providerSettings = providerSettings(importsMedia),
        modelReady = { modelReady },
    ).also { it.onDisplayed(true) }

    private fun controller(
        importsMedia: Boolean,
        video: suspend () -> YouTubeResult<YouTubeVideo>,
        modelReady: Boolean = false,
    ) = RecipeImportController(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        repository = RecipeEditRepository(apiProvider = { fake.api() }),
        calorieTags = CalorieTagRepository(apiProvider = { fake.api() }, instanceKey = { "instance" }),
        providers = ProviderRegistry(listOf(JowProvider)),
        providerSettings = providerSettings(importsMedia),
        mediaImporter = ProviderMediaImporter(
            apiProvider = { fake.api() },
            registry = ProviderRegistry(listOf(JowProvider)),
            media = RecipeMediaRepository { fake.api() },
            downloader = { EncodedImage(ByteArray(1), "image/jpeg", "jpg") },
        ),
        videoImporter = VideoRecipeImporter(
            youTube = object : VideoSource {
                override suspend fun video(id: String) = video()
            },
            pages = { null },
            model = ScriptedModel(emptyList(), ready = modelReady),
            watcher = VideoWatcher(ScriptedModel(emptyList(), ready = modelReady), FakeTranscriber(ready = false), FakeVideoMedia()),
            apiProvider = { fake.api() },
            edits = RecipeEditRepository(apiProvider = { fake.api() }),
            media = RecipeMediaRepository { fake.api() },
            language = { "fr" },
        ),
        host = host,
    )

    private fun providerSettings(importsMedia: Boolean) = object : ProviderSettings {
        override fun importsMedia(providerId: String): Flow<Boolean> = flowOf(importsMedia)
        override suspend fun setImportsMedia(providerId: String, enabled: Boolean) = Unit
    }

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
    fun `captions YouTube refused are named as why the steps are not in the video`() = runBlocking {
        fake.enqueueJson("""{"page":1,"per_page":10,"total":0,"total_pages":0,"items":[]}""")
        fake.enqueueJson(""""poulet-curry"""")
        repeat(4) { fake.enqueueJson(VIDEO_RECIPE) }
        val viewModel = viewModel(
            "https://youtu.be/0nE7dAlDshk",
            video = YouTubeResult.Success(
                video(description = "Ingrédients :\n2 blancs de poulet\n1 oignon\n\nPréparation :\nCouper le poulet en dés.\nCuire la sauce au curry.")
                    .copy(transcriptRefused = true),
            ),
        )

        viewModel.import()
        val state = withTimeout(TIMEOUT_MS) { viewModel.state.first { it.imported != null } }

        assertEquals(ImportedRecipe("poulet-curry", ImportNotice.VIDEO_CAPTIONS_REFUSED), state.imported)
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

    @Test
    fun `an import goes on once its screen is gone, and the screen opened again finds it`() = runBlocking {
        fake.enqueueJson("""{"page":1,"per_page":10,"total":0,"total_pages":0,"items":[]}""")
        val answer = CompletableDeferred<YouTubeResult<YouTubeVideo>>()
        val imports = controller(importsMedia = false, video = { answer.await() })
        val store = ViewModelStore()
        val first = ViewModelProvider.create(
            store,
            viewModelFactory { initializer { viewModel("https://youtu.be/0nE7dAlDshk", imports = imports) } },
        )[RecipeImportViewModel::class]

        first.import()
        withTimeout(TIMEOUT_MS) { first.state.first { it.phase == ImportPhase.READING_VIDEO } }
        first.onDisplayed(false)
        store.clear()

        val again = viewModel(url = null, imports = imports)
        val running = again.state.value
        assertEquals("https://youtu.be/0nE7dAlDshk", running.url)
        assertEquals(ImportPhase.READING_VIDEO, running.phase)
        assertTrue(running.isVideo)
        assertTrue(host.keepsAlive)

        answer.complete(YouTubeResult.Failure(YouTubeFailure.BLOCKED))
        val ended = withTimeout(TIMEOUT_MS) { again.state.first { it.videoFailure != null } }

        assertNull(ended.phase)
        assertEquals("https://youtu.be/0nE7dAlDshk", ended.url)
        // Seen ending on screen: nothing to announce, and nothing left to show.
        assertNull(host.announced)
        assertNull(imports.run.value)
        assertEquals(false, host.keepsAlive)
    }

    @Test
    fun `an import that ended with no screen on display is announced, then shown by the next screen`() = runBlocking {
        fake.enqueueJson("""{"page":1,"per_page":10,"total":0,"total_pages":0,"items":[]}""")
        val answer = CompletableDeferred<YouTubeResult<YouTubeVideo>>()
        val imports = controller(importsMedia = false, video = { answer.await() })
        val first = viewModel("https://youtu.be/0nE7dAlDshk", imports = imports)
        first.import()
        withTimeout(TIMEOUT_MS) { first.state.first { it.phase == ImportPhase.READING_VIDEO } }
        first.onDisplayed(false)

        answer.complete(YouTubeResult.Failure(YouTubeFailure.BLOCKED))
        withTimeout(TIMEOUT_MS) { imports.run.first { it?.outcome != null } }

        assertEquals("https://youtu.be/0nE7dAlDshk", host.announced?.url)
        assertEquals(0, host.cleared)
        // A screen that is not seen does not take the outcome over.
        assertNull(first.state.value.videoFailure)

        val again = viewModel(url = null, imports = imports)

        assertEquals(YouTubeFailure.BLOCKED, again.state.value.videoFailure)
        assertNull(imports.run.value)
        assertEquals(1, host.cleared)
    }

    @Test
    fun `a cancelled import leaves nothing running nor shown`() = runBlocking {
        fake.enqueueJson("""{"page":1,"per_page":10,"total":0,"total_pages":0,"items":[]}""")
        val imports = controller(importsMedia = false, video = { CompletableDeferred<YouTubeResult<YouTubeVideo>>().await() })
        val viewModel = viewModel("https://youtu.be/0nE7dAlDshk", imports = imports)
        viewModel.import()
        withTimeout(TIMEOUT_MS) { viewModel.state.first { it.phase == ImportPhase.READING_VIDEO } }
        // One import at a time: another one is not started over it.
        imports.start("https://example.org/tarte", includeTags = true, includeCategories = true, evenIfPresent = false)
        assertEquals("https://youtu.be/0nE7dAlDshk", imports.run.value?.url)

        viewModel.cancel()

        assertNull(viewModel.state.value.phase)
        assertNull(imports.run.value)
        assertEquals(false, host.keepsAlive)
        assertNull(host.announced)
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
