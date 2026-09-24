package org.opensources.umai.cooking.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opensources.umai.cooking.domain.CookingTimer
import org.opensources.umai.cooking.domain.CookingTimers
import org.opensources.umai.cooking.domain.StepDurations
import org.opensources.umai.cooking.domain.TimerAlarm
import org.opensources.umai.core.di.AppContainer
import org.opensources.umai.core.model.Recipe
import org.opensources.umai.core.model.RecipeIngredient
import org.opensources.umai.core.model.RecipeStep
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.settings.CookingTimerOptions
import org.opensources.umai.recipe.data.RecipeMediaRepository
import org.opensources.umai.recipe.data.RecipeRepository
import org.opensources.umai.recipe.domain.VideoChapter
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
    val markingCooked: Boolean = false,
    /** Set once Mealie recorded the recipe as cooked; the screen then closes. */
    val markedCooked: Boolean = false,
    val markError: NetworkError? = null,
    val timerOptions: CookingTimerOptions = CookingTimerOptions(),
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

    /** Mirrors the scaling applied on the recipe page. */
    val scale: Double
        get() {
            val base = recipe?.baseServings ?: return 1.0
            if (servings <= 0 || base <= 0) return 1.0
            return servings.toDouble() / base
        }
}

/**
 * [clock] is monotonic, in milliseconds: the timers must not jump when the
 * wall clock is changed.
 */
class CookingViewModel(
    private val slug: String,
    private val servings: Int,
    private val recipeRepository: RecipeRepository,
    private val mediaRepository: RecipeMediaRepository,
    keepScreenOn: Flow<Boolean>,
    timerOptions: Flow<CookingTimerOptions>,
    private val alarm: TimerAlarm,
    private val clock: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
) : ViewModel() {

    private val _state = MutableStateFlow(CookingUiState())
    val state: StateFlow<CookingUiState> = _state.asStateFlow()

    private var tickJob: Job? = null

    /** Finished timers the alarm already rang for: each rings once. */
    private var announced = emptySet<Int>()
    private var alarmOn = false
    private var alarmStartedAt = 0L

    init {
        load()
        viewModelScope.launch {
            val enabled = keepScreenOn.first()
            _state.update { it.copy(keepScreenOn = enabled) }
        }
        viewModelScope.launch {
            timerOptions.collect { options -> _state.update { it.copy(timerOptions = options) } }
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

    // ---- Timers -----------------------------------------------------------

    /** Starts a timer for [duration], found in the current step. */
    fun startTimer(duration: Duration) {
        val now = clock()
        _state.update { it.copy(timers = it.timers.start(it.currentStep, duration, now), now = now) }
        ensureTicking()
    }

    fun pauseTimer(id: Int) = changeTimers { timers, now -> timers.pause(id, now) }

    fun resumeTimer(id: Int) = changeTimers { timers, now -> timers.resume(id, now) }

    /** Cancels a running timer, or silences one that finished. */
    fun dismissTimer(id: Int) = changeTimers { timers, _ -> timers.remove(id) }

    private fun changeTimers(change: (CookingTimers, Long) -> CookingTimers) {
        val now = clock()
        _state.update { it.copy(timers = change(it.timers, now), now = now) }
        updateAlarm(now)
        ensureTicking()
    }

    /** Moves the clock on every second while a timer counts down or rings. */
    private fun ensureTicking() {
        if (tickJob?.isActive == true) return
        tickJob = viewModelScope.launch {
            while (true) {
                val now = clock()
                // The alarm rings before the finished timer shows, never after.
                updateAlarm(now)
                _state.update { it.copy(now = now) }
                if (!_state.value.timers.anyRunning(now) && !alarmOn) break
                delay(TICK_MILLIS - now % TICK_MILLIS)
            }
        }
    }

    private fun updateAlarm(now: Long) {
        val state = _state.value
        val finished = state.timers.finished(now).map { it.id }.toSet()
        val fresh = finished - announced
        // Dismissed timers are forgotten; the ones that just finished are remembered.
        announced = (announced intersect state.timers.timers.map { it.id }.toSet()) + fresh
        val options = state.timerOptions
        when {
            fresh.isNotEmpty() && (options.sound || options.vibrate) -> {
                alarm.start(sound = options.sound, vibrate = options.vibrate)
                alarmOn = true
                alarmStartedAt = now
            }
            alarmOn && (finished.isEmpty() || now - alarmStartedAt >= MAX_RING_MILLIS) -> {
                alarm.stop()
                alarmOn = false
            }
        }
    }

    override fun onCleared() {
        alarm.stop()
    }

    companion object {
        private const val NANOS_PER_MILLI = 1_000_000L
        private const val TICK_MILLIS = 1_000L

        /** A forgotten alarm falls silent after this long; the timer stays shown as finished. */
        private const val MAX_RING_MILLIS = 120_000L

        fun factory(container: AppContainer, slug: String, servings: Int) = viewModelFactory {
            initializer {
                CookingViewModel(
                    slug = slug,
                    servings = servings,
                    recipeRepository = container.recipeRepository,
                    mediaRepository = container.recipeMediaRepository,
                    keepScreenOn = container.preferencesRepository.preferences
                        .map { it.keepScreenOnWhileCooking },
                    timerOptions = container.preferencesRepository.preferences.map { it.cookingTimers },
                    alarm = container.timerAlarm,
                )
            }
        }
    }
}
