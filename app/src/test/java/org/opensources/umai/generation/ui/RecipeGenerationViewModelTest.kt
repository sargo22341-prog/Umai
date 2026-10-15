package org.opensources.umai.generation.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.opensources.umai.generation.data.RecipeGenerationRepository
import org.opensources.umai.planning.data.FoodTableRepository
import org.opensources.umai.provider.data.ProviderMediaImporter
import org.opensources.umai.provider.data.ProviderSettings
import org.opensources.umai.provider.domain.ProviderRegistry
import org.opensources.umai.recipe.data.CalorieTagRepository
import org.opensources.umai.recipe.data.RecipeEditRepository
import org.opensources.umai.recipe.data.RecipeImportController
import org.opensources.umai.recipe.data.RecipeMediaRepository
import org.opensources.umai.recipe.data.RecordingImportHost
import org.opensources.umai.recipe.domain.ImportOutcome
import org.opensources.umai.recipe.domain.ImportedRecipe
import org.opensources.umai.youtube.data.VideoRecipeImporter
import org.opensources.umai.youtube.domain.FakeTranscriber
import org.opensources.umai.youtube.domain.FakeVideoMedia
import org.opensources.umai.youtube.domain.ScriptedModel
import org.opensources.umai.youtube.domain.VideoSource
import org.opensources.umai.youtube.domain.VideoWatcher
import org.opensources.umai.youtube.domain.YouTubeFailure
import org.opensources.umai.youtube.domain.YouTubeResult

@OptIn(ExperimentalCoroutinesApi::class)
class RecipeGenerationViewModelTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val store = ViewModelStore()
    private val model = ScriptedModel(emptyList(), ready = false)

    @Before
    fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After
    fun close() {
        store.clear()
        scope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun anotherRecipeClearsThePreviousPromptAndResult() = runBlocking {
        val imports = controller()
        imports.generate("Chicken pasta for four") { ImportOutcome.Imported(ImportedRecipe("pasta", null)) }
        val viewModel = viewModel(imports, "pasta")
        viewModel.refreshModel()
        withTimeout(5000) { viewModel.state.first { it.slug == "pasta" && it.prompt.isNotEmpty() && it.modelReady } }
        viewModel.newRecipe()
        val state = withTimeout(5000) { viewModel.state.first { it.slug == null && it.prompt.isEmpty() } }
        assertFalse(state.canGenerate)
        assertTrue(state.modelReady)
        assertNull(imports.run.value)
    }

    @Test
    fun anotherRecipeAlsoClearsAResultRestoredFromANotification() = runBlocking {
        val viewModel = viewModel(controller(), "salad")
        viewModel.changePrompt("A low-calorie salad")
        viewModel.newRecipe()
        val state = withTimeout(5000) { viewModel.state.first { it.slug == null && it.prompt.isEmpty() } }
        assertFalse(state.canGenerate)
    }

    private fun viewModel(imports: RecipeImportController, initialSlug: String): RecipeGenerationViewModel {
        val repository = RecipeGenerationRepository(
            { null }, { null }, model, FoodTableRepository { error("No table should be read") }, { "en" }, { "AI" },
        )
        return ViewModelProvider(store, viewModelFactory {
            initializer { RecipeGenerationViewModel(imports, repository, { true }, initialSlug) }
        })[RecipeGenerationViewModel::class.java]
    }

    private fun controller(): RecipeImportController {
        val edits = RecipeEditRepository(apiProvider = { null })
        val media = RecipeMediaRepository { null }
        return RecipeImportController(
            scope, edits, CalorieTagRepository({ null }, { null }), ProviderRegistry(emptyList()),
            object : ProviderSettings {
                override fun importsMedia(providerId: String) = flowOf(false)
                override suspend fun setImportsMedia(providerId: String, enabled: Boolean) = Unit
            },
            ProviderMediaImporter({ null }, ProviderRegistry(emptyList()), media) { error("No image should be read") },
            VideoRecipeImporter(
                youTube = object : VideoSource {
                    override suspend fun video(id: String) = YouTubeResult.Failure(YouTubeFailure.UNAVAILABLE)
                },
                pages = { null }, model = model,
                watcher = VideoWatcher(model, FakeTranscriber(ready = false), FakeVideoMedia()),
                apiProvider = { null }, edits = edits, media = media, language = { "en" },
            ),
            RecordingImportHost(),
        )
    }
}
