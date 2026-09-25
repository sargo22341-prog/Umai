package org.opensources.umai.cooking.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opensources.umai.cooking.data.CookingTimerController
import org.opensources.umai.cooking.domain.CookingTimer
import org.opensources.umai.cooking.domain.CookingTimers
import org.opensources.umai.cooking.domain.StepDurations
import org.opensources.umai.cooking.domain.TimerRecipe
import org.opensources.umai.core.di.AppContainer
import org.opensources.umai.core.model.Recipe
import org.opensources.umai.core.model.RecipeIngredient
import org.opensources.umai.core.model.RecipeStep
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.settings.CookingTimerOptions
import org.opensources.umai.recipe.data.RecipeMediaRepository
import org.opensources.umai.recipe.data.RecipeRepository
import org.opensources.umai.recipe.domain.StepClip
import org.opensources.umai.recipe.domain.VideoChapter
import org.opensources.umai.recipe.domain.VideoStream
import org.opensources.umai.recipe.domain.VideoManifest
import java.time.Duration

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
    /** Where the video is read from, once found; a YouTube video is looked up first. */
    val stream: VideoStream? = null,
    val markingCooked: Boolean = false,
    /** Set once Mealie recorded the recipe as cooked; the screen then closes. */
    val markedCooked: Boolean = false,
    val markError: NetworkError? = null,
    val timerOptions: CookingTimerOptions = CookingTimerOptions(),
    /** Every timer of the app: this recipe's, and the ones started from other recipes. */
    val timers: CookingTimers = CookingTimers(),
    /** The clock the timers are read against, moved on every second while one runs. */
    val now: Long = 0L,
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

    /** The durations written in the current step, each offered as a timer. */
    val stepDurations: List<Duration>
        get() = if (timerOptions.detectTimers) StepDurations.find(step?.text.orEmpty()) else emptyList()

    /** Timers that reached zero and ring until dismissed. */
    val ringingTimers: List<CookingTimer> get() = timers.finished(now)

    /** Where the current step starts and ends in the video, `null` when it is not in it. */
    val chapter: VideoChapter? get() = video?.chapterFor(currentStep)

    /** The part of the video to loop on for the current step. */
    val clip: StepClip?
        get() {
            val chapter = chapter ?: return null
            val stream = stream ?: return null
            return StepClip(stream.url, chapter.start, chapter.end, stream.isHls, stream.headers)
        }

    /** Mirrors the scaling applied on the recipe page. */
    val scale: Double
        get() {
            val base = recipe?.baseServings ?: return 1.0
            if (servings <= 0 || base <= 0) return 1.0
            return servings.toDouble() / base
        }
}

/**
 * The recipe, step by step. Its timers belong to [timers], the timers of the
 * whole app: leaving the cooking mode leaves them running.
 */
class CookingViewModel(
    private val slug: String,
    private val servings: Int,
    /** The step to open on, as asked for by a timer started from it. */
    private val initialStep: Int,
    private val recipeRepository: RecipeRepository,
    private val mediaRepository: RecipeMediaRepository,
    /** Where a video is read from: a YouTube page is turned into a stream the player reads. */
    private val streamFor: suspend (String) -> VideoStream?,
    keepScreenOn: Flow<Boolean>,
    timerOptions: Flow<CookingTimerOptions>,
    private val timers: CookingTimerController,
) : ViewModel() {

    private val _state = MutableStateFlow(CookingUiState())
    val state: StateFlow<CookingUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            val enabled = keepScreenOn.first()
            _state.update { it.copy(keepScreenOn = enabled) }
        }
        viewModelScope.launch {
            timerOptions.collect { options -> _state.update { it.copy(timerOptions = options) } }
        }
        viewModelScope.launch {
            combine(timers.timers, timers.ticks()) { all, now -> all to now }
                .collect { (all, now) -> _state.update { it.copy(timers = all, now = now) } }
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
                        currentStep = initialStep.coerceIn(0, (result.value.steps.size - 1).coerceAtLeast(0)),
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
        val stream = manifest.videoUrl?.let { streamFor(it) } ?: return
        _state.update { it.copy(stream = stream) }
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

    // ---- Timers -----------------------------------------------------------

    /** Starts a timer for [duration], found in the current step. */
    fun startTimer(duration: Duration) {
        val state = _state.value
        val recipe = state.recipe ?: return
        // The servings asked for on the recipe page: the cooking mode reopens scaled the same.
        timers.start(TimerRecipe(slug = slug, name = recipe.name, servings = servings), state.currentStep, duration)
    }

    fun pauseTimer(id: Int) = timers.pause(id)

    fun resumeTimer(id: Int) = timers.resume(id)

    /** Cancels a running timer, or silences one that finished. */
    fun dismissTimer(id: Int) = timers.dismiss(id)

    /** Notifications were just allowed: the timers already running can show there now. */
    fun onNotificationsAllowed() = timers.refresh()

    companion object {
        fun factory(container: AppContainer, slug: String, servings: Int, step: Int) = viewModelFactory {
            initializer {
                CookingViewModel(
                    slug = slug,
                    servings = servings,
                    initialStep = step,
                    recipeRepository = container.recipeRepository,
                    mediaRepository = container.recipeMediaRepository,
                    streamFor = container.videoStreams::streamFor,
                    keepScreenOn = container.preferencesRepository.preferences
                        .map { it.keepScreenOnWhileCooking },
                    timerOptions = container.preferencesRepository.preferences.map { it.cookingTimers },
                    timers = container.cookingTimers,
                )
            }
        }
    }
}
