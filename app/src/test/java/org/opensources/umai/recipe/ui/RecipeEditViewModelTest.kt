package org.opensources.umai.recipe.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.opensources.umai.core.image.CropRegion
import org.opensources.umai.core.image.EncodedImage
import org.opensources.umai.core.network.FakeMealieServer
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.home.data.RecentRecipes
import org.opensources.umai.organizer.data.OrganizerRepository
import org.opensources.umai.recipe.data.RecipeEditRepository
import org.opensources.umai.recipe.data.RecipeImageFiles
import org.opensources.umai.recipe.domain.VideoStream

@OptIn(ExperimentalCoroutinesApi::class)
class RecipeEditViewModelTest {

    private lateinit var fake: FakeMealieServer
    private val history = FakeHistory()

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

    private val streamsAsked = mutableListOf<String>()

    private fun viewModel() = RecipeEditViewModel(
        slug = SLUG,
        editRepository = RecipeEditRepository({ fake.api() }),
        organizerRepository = OrganizerRepository { fake.api() },
        imageFiles = NoImageFiles,
        recentRecipes = history,
        streamFor = { url -> streamsAsked += url; VideoStream("https://stream/$SLUG.m3u8", isHls = true) },
    )

    private suspend fun RecipeEditViewModel.await(predicate: (RecipeEditUiState) -> Boolean): RecipeEditUiState =
        withTimeout(TIMEOUT_MS) { state.first(predicate) }

    @Test
    fun `deleting the recipe removes it from Mealie and from the history`() = runBlocking {
        fake.enqueueJson(RECIPE)
        val vm = viewModel()
        val loaded = vm.await { !it.loading }
        assertTrue(loaded.canDelete)
        assertEquals("Gratin de courgettes", loaded.savedName)

        fake.enqueueJson(RECIPE)
        vm.delete()
        val done = vm.await { it.deleted }

        assertFalse(done.deleting)
        fake.takeRequest()
        val request = fake.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/recipes/$SLUG", request.url.encodedPath)
        assertEquals(listOf(SLUG), history.forgotten)
    }

    @Test
    fun `a refused deletion keeps the recipe and says why`() = runBlocking {
        fake.enqueueJson(RECIPE)
        val vm = viewModel()
        vm.await { !it.loading }

        fake.enqueueError(403)
        vm.delete()
        val failed = vm.await { it.deleteError != null }

        assertEquals(NetworkError.Unauthorized, failed.deleteError)
        assertFalse(failed.deleted)
        assertFalse(failed.deleting)
        assertNotNull(failed.recipe)
        assertTrue(failed.canDelete)
        assertTrue(history.forgotten.isEmpty())

        vm.dismissDeleteError()
        assertEquals(null, vm.state.value.deleteError)
    }

    @Test
    fun `an unreachable server is reported as such`() = runBlocking {
        fake.enqueueJson(RECIPE)
        val vm = viewModel()
        vm.await { !it.loading }

        fake.shutdown()
        vm.delete()
        val failed = vm.await { it.deleteError != null }

        assertEquals(NetworkError.Unreachable, failed.deleteError)
        assertFalse(failed.deleted)
    }

    @Test
    fun `a recipe that could not be opened cannot be deleted`() = runBlocking {
        fake.enqueueError(404)
        val vm = viewModel()
        val failed = vm.await { !it.loading }

        assertFalse(failed.canDelete)
        vm.delete()

        assertEquals(1, fake.server.requestCount)
        assertFalse(vm.state.value.deleting)
    }

    @Test
    fun `a recipe without a video has no video section`() = runBlocking {
        fake.enqueueJson(RECIPE)

        val loaded = viewModel().await { !it.loading }

        assertFalse(RecipeFormSection.VIDEO in loaded.sections)
    }

    @Test
    fun `a recipe imported from YouTube offers its video, looked up when the section opens`() = runBlocking {
        fake.enqueueJson(FROM_YOUTUBE)
        val vm = viewModel()
        val loaded = vm.await { !it.loading }
        assertTrue(RecipeFormSection.VIDEO in loaded.sections)

        vm.showSection(RecipeFormSection.VIDEO)
        val shown = vm.await { it.videoStream != null }

        assertEquals(listOf(YOUTUBE), streamsAsked)
        assertTrue(requireNotNull(shown.videoStream).isHls)
    }

    @Test
    fun `placing a step is a change, and an end before its start keeps it from being saved`() = runBlocking {
        fake.enqueueJson(FROM_YOUTUBE)
        val vm = viewModel()
        vm.await { !it.loading }

        vm.setChapterStart(0, 42.0)
        assertTrue(vm.state.value.hasChanges)
        assertTrue(vm.state.value.canSave)

        vm.setChapterEnd(0, 30.0)
        assertFalse(vm.state.value.canSave)
        vm.setChapterEnd(0, null)
        assertTrue(vm.state.value.canSave)
    }

    @Test
    fun `saving writes the chapters of the video`() = runBlocking {
        fake.enqueueJson(FROM_YOUTUBE)
        val vm = viewModel()
        vm.await { !it.loading }
        fake.takeRequest()
        vm.setChapterStart(0, 42.0)

        // The text is read to be compared, then the chapters file is uploaded.
        fake.enqueueJson(FROM_YOUTUBE)
        fake.enqueueJson("""{"name":"gratin-de-courgettes-chapters","icon":"file-json","fileName":"gratin-de-courgettes-chapters.json"}""")
        vm.save()
        vm.await { it.savedSlug != null }

        assertEquals("GET", fake.takeRequest().method)
        val upload = fake.takeRequest()
        assertEquals("/api/recipes/$SLUG/assets", upload.url.encodedPath)
        assertTrue(upload.body?.utf8().orEmpty().contains(""""start":42.0"""))
    }

    private class FakeHistory : RecentRecipes {
        val forgotten = mutableListOf<String>()

        override suspend fun rename(oldSlug: String, newSlug: String) = Unit

        override suspend fun forget(slug: String) {
            forgotten += slug
        }
    }

    private object NoImageFiles : RecipeImageFiles {
        override suspend fun save(sourceUri: String, region: CropRegion): String? = null

        override suspend fun read(path: String): EncodedImage? = null

        override fun delete(path: String) = Unit
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val SLUG = "gratin-de-courgettes"

        const val YOUTUBE = "https://www.youtube.com/watch?v=y3L14JKSSYI"

        val FROM_YOUTUBE = """
            {"id":"r1","name":"Gratin de courgettes","slug":"gratin-de-courgettes","orgURL":"$YOUTUBE",
             "recipeIngredient":[],"recipeInstructions":[{"id":"s1","title":"","text":"Cuire."}]}
        """.trimIndent()

        val RECIPE = """
            {"id":"r1","name":"Gratin de courgettes","slug":"gratin-de-courgettes",
             "recipeIngredient":[],"recipeInstructions":[]}
        """.trimIndent()
    }
}
