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

    private fun viewModel(url: String, importsMedia: Boolean = false) = RecipeImportViewModel(
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

        assertEquals(ImportedRecipe("curry-1", mediaFailed = false), state.imported)
        assertEquals("/api/recipes/create/url", fake.takeRequest().url.encodedPath)
        // The calorie tag is checked; with the provider switched off nothing else is asked.
        assertEquals("/api/recipes/curry-1", fake.takeRequest().url.encodedPath)
        assertEquals(2, fake.server.requestCount)
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L

        const val SEARCH_WITH_CURRY = """{"page":1,"per_page":20,"total":1,"total_pages":1,
            "items":[{"id":"r1","slug":"curry","name":"Curry","orgURL":"https://jow.fr/recipes/curry"}]}"""
    }
}
