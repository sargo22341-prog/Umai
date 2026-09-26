package org.opensources.umai.recipe.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.opensources.umai.core.image.EncodedImage
import org.opensources.umai.core.network.FakeMealieServer
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.provider.domain.ProviderRegistry
import org.opensources.umai.provider.data.ProviderMediaImporter
import org.opensources.umai.provider.data.ProviderSettings
import org.opensources.umai.provider.jow.JowProvider
import org.opensources.umai.recipe.domain.ImportOutcome
import org.opensources.umai.recipe.domain.ImportPhase
import org.opensources.umai.recipe.domain.ImportedRecipe
import org.opensources.umai.recipe.domain.RecipeImportRun
import org.opensources.umai.youtube.data.VideoRecipeImporter
import org.opensources.umai.youtube.domain.FakeTranscriber
import org.opensources.umai.youtube.domain.FakeVideoMedia
import org.opensources.umai.youtube.domain.ScriptedModel
import org.opensources.umai.youtube.domain.VideoSource
import org.opensources.umai.youtube.domain.VideoWatcher
import org.opensources.umai.youtube.domain.YouTubeFailure
import org.opensources.umai.youtube.domain.YouTubeResult

/** Records what an import would show outside the app. */
class RecordingImportHost : ImportHost {
    val phases = mutableListOf<ImportPhase>()
    var keepsAlive = false
        private set
    var announced: RecipeImportRun? = null
        private set
    var cleared = 0
        private set

    override fun running(run: RecipeImportRun) {
        keepsAlive = true
        if (phases.lastOrNull() != run.phase) phases += run.phase
    }

    override fun ended(run: RecipeImportRun, announce: Boolean) {
        keepsAlive = false
        if (announce) announced = run
    }

    override fun clear() {
        keepsAlive = false
        cleared++
    }
}

class RecipeImportControllerTest {

    private lateinit var fake: FakeMealieServer
    private val host = RecordingImportHost()

    @Before
    fun setUp() {
        fake = FakeMealieServer()
    }

    @After
    fun tearDown() = fake.shutdown()

    private val controller by lazy {
        RecipeImportController(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            repository = RecipeEditRepository(apiProvider = { fake.api() }),
            calorieTags = CalorieTagRepository(apiProvider = { fake.api() }, instanceKey = { "instance" }),
            providers = ProviderRegistry(listOf(JowProvider)),
            providerSettings = object : ProviderSettings {
                override fun importsMedia(providerId: String) = flowOf(false)
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
                    override suspend fun video(id: String) = YouTubeResult.Failure(YouTubeFailure.UNAVAILABLE)
                },
                pages = { null },
                model = ScriptedModel(emptyList(), ready = false),
                watcher = VideoWatcher(ScriptedModel(emptyList(), ready = false), FakeTranscriber(ready = false), FakeVideoMedia()),
                apiProvider = { fake.api() },
                edits = RecipeEditRepository(apiProvider = { fake.api() }),
                media = RecipeMediaRepository { fake.api() },
                language = { "fr" },
            ),
            host = host,
        )
    }

    @Test
    fun `a page imported while nobody watches is announced, and opening its recipe ends it`() = runBlocking {
        fake.enqueueJson(EMPTY_SEARCH)
        fake.enqueueJson(""""tarte"""")
        fake.enqueueJson("""{"id":"r2","slug":"tarte","name":"Tarte","nutrition":null,"tags":[]}""")

        controller.start("  https://example.org/tarte ", includeTags = true, includeCategories = true, evenIfPresent = false)
        val ended = withTimeout(TIMEOUT_MS) { controller.run.first { it?.outcome != null } }!!

        assertEquals("https://example.org/tarte", ended.url)
        assertEquals(ImportOutcome.Imported(ImportedRecipe("tarte", notice = null)), ended.outcome)
        assertEquals(listOf(ImportPhase.CHECKING, ImportPhase.IMPORTING), host.phases)
        assertFalse(host.keepsAlive)
        assertEquals(ended, host.announced)

        // Another recipe's notification does not end this import; its own does.
        controller.seen("other")
        assertEquals(ended, controller.run.value)
        controller.seen("tarte")
        assertNull(controller.run.value)
        assertEquals(1, host.cleared)
    }

    @Test
    fun `an import that ends on screen is not announced`() = runBlocking {
        fake.enqueueJson(EMPTY_SEARCH)
        fake.enqueueError(500)
        controller.watch(true)

        controller.start("https://example.org/tarte", includeTags = true, includeCategories = true, evenIfPresent = false)
        val ended = withTimeout(TIMEOUT_MS) { controller.run.first { it?.outcome != null } }!!

        assertTrue(ended.outcome is ImportOutcome.Failed)
        assertTrue((ended.outcome as ImportOutcome.Failed).error is NetworkError.Server)
        assertNull(host.announced)
    }

    @Test
    fun `an ended import makes room for the next one`() = runBlocking {
        fake.enqueueJson(EMPTY_SEARCH)
        fake.enqueueError(500)
        controller.start("https://example.org/tarte", includeTags = true, includeCategories = true, evenIfPresent = false)
        withTimeout(TIMEOUT_MS) { controller.run.first { it?.outcome != null } }

        // The first one ended: the next one runs.
        fake.enqueueJson(SEARCH_WITH_CURRY)
        controller.start("https://jow.fr/recipes/curry", includeTags = true, includeCategories = true, evenIfPresent = false)
        val duplicate = withTimeout(TIMEOUT_MS) { controller.run.first { it?.outcome is ImportOutcome.Duplicate } }!!

        assertEquals("https://jow.fr/recipes/curry", duplicate.url)
        assertEquals("curry", (duplicate.outcome as ImportOutcome.Duplicate).existing.slug)
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val EMPTY_SEARCH = """{"page":1,"per_page":10,"total":0,"total_pages":0,"items":[]}"""
        const val SEARCH_WITH_CURRY = """{"page":1,"per_page":20,"total":1,"total_pages":1,
            "items":[{"id":"r1","slug":"curry","name":"Curry","orgURL":"https://jow.fr/recipes/curry"}]}"""
    }
}
