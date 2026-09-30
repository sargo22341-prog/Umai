package org.opensources.umai.settings.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.opensources.umai.core.image.CropRegion
import org.opensources.umai.core.image.EncodedImage
import org.opensources.umai.core.image.ImageCropper
import org.opensources.umai.core.network.FakeMealieServer
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.session.AuthRepository
import org.opensources.umai.core.session.FakeSessionHolder
import org.opensources.umai.core.session.SessionState
import org.opensources.umai.profile.data.ProfileRepository
import org.opensources.umai.recipe.data.CalorieTagRepository

/** The calorie tags sync of the settings, against a Mealie answering by path. */
@OptIn(ExperimentalCoroutinesApi::class)
class MealieSettingsViewModelTest {

    private lateinit var fake: FakeMealieServer

    /** The recipes, one page of two; [recipesCode] fails their reading. */
    private var recipesCode = 200

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        fake = FakeMealieServer()
        fake.server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                val body = when {
                    path == "/api/users/self" -> """{"id":"u1","username":"marie","canManageHousehold":true}"""
                    path == "/api/households/preferences" -> """{"firstDayOfWeek":1}"""
                    path == "/api/recipes" && recipesCode != 200 -> return MockResponse.Builder().code(recipesCode).build()
                    path == "/api/recipes" ->
                        """{"page":1,"per_page":100,"total":2,"total_pages":1,"items":[{"id":"a","slug":"a"},{"id":"b","slug":"b"}]}"""
                    // Both recipes already carry the right tag: nothing to write.
                    path.startsWith("/api/recipes/") ->
                        """{"id":"r","slug":"s","name":"R","nutrition":{"calories":"500"},""" +
                            """"tags":[{"id":"t","name":"calorie-500","slug":"calorie-500"}]}"""
                    else -> return MockResponse.Builder().code(404).build()
                }
                return MockResponse.Builder().setHeader("Content-Type", "application/json").body(body).build()
            }
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        fake.shutdown()
    }

    private fun viewModel() = MealieSettingsViewModel(
        profileRepository = ProfileRepository(
            apiProvider = { fake.api() },
            imageCropper = object : ImageCropper {
                override suspend fun crop(sourceUri: String, region: CropRegion, maxSide: Int): EncodedImage? = null
            },
        ),
        authRepository = AuthRepository(FakeSessionHolder()),
        sessionState = MutableStateFlow(SessionState.NotConfigured),
        calorieTags = CalorieTagRepository(apiProvider = { fake.api() }, instanceKey = { "instance" }),
    )

    private suspend fun MealieSettingsViewModel.awaitSync(): CalorieSync =
        withTimeout(TIMEOUT_MS) { state.first { it.calorieSync.finished } }.calorieSync

    @Test
    fun `every recipe is checked, and those already right are not written`() = runBlocking {
        val vm = viewModel()

        vm.syncCalorieTags()
        val sync = vm.awaitSync()

        assertEquals(2, sync.total)
        assertEquals(2, sync.processed)
        assertEquals(0, sync.changed)
        assertEquals(0, sync.failed)
        assertTrue(sync.complete)
        assertFalse(sync.running)
    }

    @Test
    fun `recipes that cannot be listed stop the sync with the error`() = runBlocking {
        recipesCode = 503
        val vm = viewModel()

        vm.syncCalorieTags()

        assertEquals(NetworkError.Server(503), vm.awaitSync().error)
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
