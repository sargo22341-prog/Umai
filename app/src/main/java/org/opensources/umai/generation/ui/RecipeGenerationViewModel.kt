package org.opensources.umai.generation.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opensources.umai.core.di.AppContainer
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.generation.data.RecipeGenerationRepository
import org.opensources.umai.recipe.data.RecipeImportController
import org.opensources.umai.recipe.domain.ImportOutcome

data class RecipeGenerationUiState(
    val prompt: String = "",
    val checkingModel: Boolean = true,
    val modelReady: Boolean = false,
    val running: Boolean = false,
    val busy: Boolean = false,
    val slug: String? = null,
    val ingredientsNeedReview: Boolean = false,
    val modelFailed: Boolean = false,
    val error: NetworkError? = null,
) {
    val canGenerate: Boolean get() = modelReady && !checkingModel && !busy && prompt.isNotBlank() && slug == null
}

/** The application import controller owns the work so leaving this screen does not cancel it. */
class RecipeGenerationViewModel(
    private val imports: RecipeImportController,
    private val repository: RecipeGenerationRepository,
    private val modelReady: suspend () -> Boolean,
    initialSlug: String? = null,
) : ViewModel() {
    private val form = MutableStateFlow(RecipeGenerationUiState(
        prompt = imports.run.value?.takeIf { it.isGeneration }?.url.orEmpty(), slug = initialSlug,
    ))

    val state = combine(form, imports.run) { form, run ->
        val outcome = run?.takeIf { it.isGeneration }?.outcome
        form.copy(
            running = run?.isGeneration == true && run.running,
            busy = run?.running == true,
            slug = (outcome as? ImportOutcome.Imported)?.recipe?.slug ?: form.slug,
            ingredientsNeedReview = (outcome as? ImportOutcome.Imported)?.recipe?.ingredientsNeedReview == true,
            modelFailed = outcome == ImportOutcome.GenerationFailed,
            error = (outcome as? ImportOutcome.Failed)?.error,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, form.value)

    fun refreshModel() {
        viewModelScope.launch {
            val ready = modelReady()
            form.update { it.copy(modelReady = ready, checkingModel = false) }
        }
    }

    fun changePrompt(value: String) {
        if (!state.value.busy && value.length <= 2000) form.update { it.copy(prompt = value) }
    }

    fun generate() {
        if (!state.value.canGenerate) return
        val prompt = state.value.prompt.trim()
        imports.generate(prompt) { repository.generate(prompt) }
    }

    fun cancel() = imports.cancel()

    fun newRecipe() {
        form.update { it.copy(prompt = "", slug = null) }
        imports.consume()
    }

    companion object {
        fun factory(container: AppContainer, initialSlug: String?) = viewModelFactory {
            initializer {
                RecipeGenerationViewModel(container.recipeImports, container.recipeGeneration, container.localLanguageModel::isReady, initialSlug)
            }
        }
    }
}
