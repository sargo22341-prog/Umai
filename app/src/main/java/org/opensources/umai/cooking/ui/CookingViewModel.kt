package org.opensources.umai.cooking.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opensources.umai.core.di.AppContainer
import org.opensources.umai.core.model.Recipe
import org.opensources.umai.core.model.RecipeIngredient
import org.opensources.umai.core.model.RecipeStep
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.recipe.data.RecipeMediaRepository
import org.opensources.umai.recipe.data.RecipeRepository
import org.opensources.umai.recipe.domain.VideoChapter
import org.opensources.umai.recipe.domain.VideoManifest

data class CookingUiState(
    val recipe: Recipe? = null,
    val currentStep: Int = 0,
    val loading: Boolean = true,
    val error: NetworkError? = null,
    val keepScreenOn: Boolean = true,
    /** Servings chosen on the recipe page; `0` keeps the recipe's own count. */
    val servings: Int = 0,
    /** The chapters of the recipe video, when it has one. */
    val video: VideoManifest? = null,
    val markingCooked: Boolean = false,
    /** Set once Mealie recorded the recipe as cooked; the screen then closes. */
    val markedCooked: Boolean = false,
    val markError: NetworkError? = null,
) {
    val steps: List<RecipeStep> get() = recipe?.steps.orEmpty()
    val stepCount: Int get() = steps.size
    val step: RecipeStep? get() = steps.getOrNull(currentStep)
    val hasPrevious: Boolean get() = currentStep > 0
    val hasNext: Boolean get() = currentStep < stepCount - 1
    val isLastStep: Boolean get() = stepCount > 0 && currentStep == stepCount - 1

    /**
     * Mealie links steps to ingredients through `ingredientReferences`; when a
     * step declares none, the whole ingredient list stays available instead.
     */
    val ingredientsForStep: List<RecipeIngredient>
        get() {
            val references = step?.ingredientReferenceIds.orEmpty()
            val all = recipe?.ingredients.orEmpty()
            if (references.isEmpty()) return emptyList()
            return all.filter { it.referenceId != null && it.referenceId in references }
        }

    /** Where the current step starts and ends in the video, `null` when it is not in it. */
    val chapter: VideoChapter? get() = video?.chapterFor(currentStep)

    /** Mirrors the scaling applied on the recipe page. */
    val scale: Double
        get() {
            val base = recipe?.baseServings ?: return 1.0
            if (servings <= 0 || base <= 0) return 1.0
            return servings.toDouble() / base
        }
}

class CookingViewModel(
    private val slug: String,
    private val servings: Int,
    private val recipeRepository: RecipeRepository,
    private val mediaRepository: RecipeMediaRepository,
    keepScreenOn: Flow<Boolean>,
) : ViewModel() {

    private val _state = MutableStateFlow(CookingUiState())
    val state: StateFlow<CookingUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            val enabled = keepScreenOn.first()
            _state.update { it.copy(keepScreenOn = enabled) }
        }
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = recipeRepository.recipe(slug)) {
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = result.error) }
                is ApiResult.Success -> _state.update {
                    it.copy(
                        recipe = result.value,
                        loading = false,
                        error = null,
                        currentStep = 0,
                        servings = servings.takeIf { value -> value > 0 }
                            ?: result.value.baseServings ?: 0,
                    )
                }
            }
            _state.value.recipe?.let { loadVideo(it) }
        }
    }

    /** The video is an extra: without it, the steps are followed as before. */
    private suspend fun loadVideo(recipe: Recipe) {
        val manifest = (mediaRepository.videoManifest(recipe.id, recipe.assets) as? ApiResult.Success)?.value
            ?: return
        _state.update { it.copy(video = manifest) }
    }

    /** Records in Mealie that the recipe was cooked; [subject] titles the timeline entry. */
    fun markCooked(subject: String) {
        val recipe = _state.value.recipe ?: return
        if (_state.value.markingCooked) return
        _state.update { it.copy(markingCooked = true, markError = null) }
        viewModelScope.launch {
            val result = recipeRepository.markCooked(recipe, subject)
            _state.update {
                when (result) {
                    is ApiResult.Failure -> it.copy(markingCooked = false, markError = result.error)
                    is ApiResult.Success -> it.copy(markingCooked = false, markedCooked = true)
                }
            }
        }
    }

    fun dismissMarkError() = _state.update { it.copy(markError = null) }

    fun next() = _state.update {
        if (it.hasNext) it.copy(currentStep = it.currentStep + 1) else it
    }

    fun previous() = _state.update {
        if (it.hasPrevious) it.copy(currentStep = it.currentStep - 1) else it
    }

    fun goToStep(index: Int) = _state.update {
        if (index in it.steps.indices) it.copy(currentStep = index) else it
    }

    companion object {
        fun factory(container: AppContainer, slug: String, servings: Int) = viewModelFactory {
            initializer {
                CookingViewModel(
                    slug = slug,
                    servings = servings,
                    recipeRepository = container.recipeRepository,
                    mediaRepository = container.recipeMediaRepository,
                    keepScreenOn = container.preferencesRepository.preferences
                        .map { it.keepScreenOnWhileCooking },
                )
            }
        }
    }
}
