package org.opensources.umai.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opensources.umai.core.di.AppContainer
import org.opensources.umai.core.model.HouseholdPreferences
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.session.AuthRepository
import org.opensources.umai.core.session.SessionManager
import org.opensources.umai.core.session.SessionState
import org.opensources.umai.profile.data.ProfileRepository
import org.opensources.umai.recipe.data.CalorieTagRepository
import org.opensources.umai.recipe.data.RecipeRepository

/** Progress of giving every recipe the tag of its calories. */
data class CalorieSync(
    val running: Boolean = false,
    val processed: Int = 0,
    val total: Int = 0,
    val changed: Int = 0,
    val failed: Int = 0,
    val error: NetworkError? = null,
    val finished: Boolean = false,
)

/** Result of the manual "check the connection" action. */
enum class ConnectionCheck { IDLE, CHECKING, OK, FAILED }

data class MealieSettingsUiState(
    val connectionCheck: ConnectionCheck = ConnectionCheck.IDLE,
    val checkError: NetworkError? = null,
    val household: HouseholdPreferences? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val saving: Boolean = false,
    val canManageHousehold: Boolean = false,
    val loadError: NetworkError? = null,
    val saveError: NetworkError? = null,
    val calorieSync: CalorieSync = CalorieSync(),
) {
    /** Nothing to edit until the preferences have been read once. */
    val householdEditable: Boolean get() = household != null && canManageHousehold && !saving
}

/**
 * The instance Umai talks to, and the preferences its household owns.
 *
 * Household preferences live on the Mealie side: they are read from the server,
 * written back to it, and never mirrored into a local store.
 */
class MealieSettingsViewModel(
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
    private val recipeRepository: RecipeRepository? = null,
    private val calorieTags: CalorieTagRepository? = null,
) : ViewModel() {

    private var calorieJob: Job? = null

    val sessionState: StateFlow<SessionState> = sessionManager.state

    private val _state = MutableStateFlow(MealieSettingsUiState())
    val state: StateFlow<MealieSettingsUiState> = _state.asStateFlow()

    init {
        load(initial = true)
    }

    fun refresh() = load(initial = false)

    fun checkConnection() {
        _state.update { it.copy(connectionCheck = ConnectionCheck.CHECKING, checkError = null) }
        viewModelScope.launch {
            when (val result = profileRepository.currentUser()) {
                is ApiResult.Failure -> _state.update {
                    it.copy(connectionCheck = ConnectionCheck.FAILED, checkError = result.error)
                }
                is ApiResult.Success -> _state.update {
                    it.copy(
                        connectionCheck = ConnectionCheck.OK,
                        checkError = null,
                        canManageHousehold = result.value.canManageHousehold,
                    )
                }
            }
        }
    }

    fun setFirstDayOfWeek(day: java.time.DayOfWeek) =
        save { it.copy(firstDayOfWeek = HouseholdPreferences.mealieDayNumber(day)) }

    fun setShowNutrition(enabled: Boolean) = save { it.copy(recipeShowNutrition = enabled) }

    fun setShowAssets(enabled: Boolean) = save { it.copy(recipeShowAssets = enabled) }

    fun setDisableComments(disabled: Boolean) = save { it.copy(recipeDisableComments = disabled) }

    fun setRecipePublic(enabled: Boolean) = save { it.copy(recipePublic = enabled) }

    fun setPrivateHousehold(enabled: Boolean) = save { it.copy(privateHousehold = enabled) }

    fun dismissSaveError() = _state.update { it.copy(saveError = null) }

    fun signOut() = viewModelScope.launch { authRepository.signOut() }

    /**
     * Gives every recipe the `calorie-<value>` tag of its nutrition, so the
     * calorie filter finds them. Recipes already right are not written. It stops
     * if the screen is left, and can be run again at no risk.
     */
    fun syncCalorieTags() {
        val recipes = recipeRepository ?: return
        val tags = calorieTags ?: return
        if (calorieJob?.isActive == true) return
        _state.update { it.copy(calorieSync = CalorieSync(running = true)) }
        calorieJob = viewModelScope.launch {
            val slugs = mutableListOf<String>()
            var page = 1
            do {
                val result = recipes.latest(page = page, perPage = SYNC_PAGE_SIZE)
                if (result is ApiResult.Failure) {
                    _state.update { it.copy(calorieSync = CalorieSync(error = result.error, finished = true)) }
                    return@launch
                }
                val paged = (result as ApiResult.Success).value
                slugs += paged.items.map { it.slug }
                page++
            } while (paged.hasNext)

            _state.update { it.copy(calorieSync = it.calorieSync.copy(total = slugs.size)) }
            slugs.forEach { slug ->
                val result = tags.sync(slug)
                _state.update {
                    val sync = it.calorieSync
                    it.copy(
                        calorieSync = sync.copy(
                            processed = sync.processed + 1,
                            changed = sync.changed + if ((result as? ApiResult.Success)?.value == true) 1 else 0,
                            failed = sync.failed + if (result is ApiResult.Failure) 1 else 0,
                        ),
                    )
                }
            }
            _state.update { it.copy(calorieSync = it.calorieSync.copy(running = false, finished = true)) }
        }
    }

    private fun load(initial: Boolean) {
        _state.update { it.copy(loading = initial, refreshing = !initial, loadError = null) }
        viewModelScope.launch {
            // The user is read first: whether the household can be edited at all
            // decides how the preferences below are presented.
            when (val user = profileRepository.currentUser()) {
                is ApiResult.Failure -> _state.update { it.copy(loadError = user.error) }
                is ApiResult.Success -> _state.update {
                    it.copy(canManageHousehold = user.value.canManageHousehold)
                }
            }
            when (val preferences = profileRepository.householdPreferences()) {
                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, refreshing = false, loadError = preferences.error)
                }
                is ApiResult.Success -> _state.update {
                    it.copy(
                        household = preferences.value,
                        loading = false,
                        refreshing = false,
                        loadError = null,
                    )
                }
            }
        }
    }

    /**
     * Applies the change locally so the control answers at once, then writes the
     * whole object back; a refusal restores what the server still holds.
     */
    private fun save(change: (HouseholdPreferences) -> HouseholdPreferences) {
        val current = _state.value.household ?: return
        if (!_state.value.canManageHousehold) return
        val updated = change(current)
        if (updated == current) return

        _state.update { it.copy(household = updated, saving = true, saveError = null) }
        viewModelScope.launch {
            when (val result = profileRepository.updateHouseholdPreferences(updated)) {
                is ApiResult.Failure -> _state.update {
                    it.copy(household = current, saving = false, saveError = result.error)
                }
                is ApiResult.Success -> _state.update {
                    it.copy(household = result.value, saving = false, saveError = null)
                }
            }
        }
    }

    companion object {
        private const val SYNC_PAGE_SIZE = 100

        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                MealieSettingsViewModel(
                    profileRepository = container.profileRepository,
                    authRepository = container.authRepository,
                    sessionManager = container.sessionManager,
                    recipeRepository = container.recipeRepository,
                    calorieTags = container.calorieTagRepository,
                )
            }
        }
    }
}
