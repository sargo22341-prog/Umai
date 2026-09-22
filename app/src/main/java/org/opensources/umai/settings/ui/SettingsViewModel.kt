package org.opensources.umai.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opensources.umai.core.di.AppContainer
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.network.apiCall
import org.opensources.umai.core.session.AuthRepository
import org.opensources.umai.core.session.SessionManager
import org.opensources.umai.core.session.SessionState
import org.opensources.umai.core.settings.AppLanguage
import org.opensources.umai.core.settings.AppPreferences
import org.opensources.umai.core.settings.AppPreferencesRepository
import org.opensources.umai.core.settings.LocaleController
import org.opensources.umai.core.settings.RecipeLayout
import org.opensources.umai.core.settings.ThemeMode

/** Result of the manual "check the connection" action. */
enum class ConnectionCheck { IDLE, CHECKING, OK, FAILED }

data class HouseholdInfo(
    val firstDayOfWeek: Int,
    val showNutrition: Boolean,
)

data class SettingsUiState(
    val connectionCheck: ConnectionCheck = ConnectionCheck.IDLE,
    val checkError: NetworkError? = null,
    val household: HouseholdInfo? = null,
)

class SettingsViewModel(
    private val preferencesRepository: AppPreferencesRepository,
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
    private val localeController: LocaleController,
) : ViewModel() {

    val preferences: StateFlow<AppPreferences> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPreferences())

    val sessionState: StateFlow<SessionState> = sessionManager.state

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        loadHouseholdPreferences()
    }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch {
        preferencesRepository.setThemeMode(mode)
    }

    fun setLanguage(language: AppLanguage) = viewModelScope.launch {
        preferencesRepository.setLanguage(language)
        localeController.apply(language)
    }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch {
        preferencesRepository.setDynamicColor(enabled)
    }

    fun setLayout(layout: RecipeLayout) = viewModelScope.launch {
        preferencesRepository.setRecipeLayout(layout)
    }

    fun setKeepScreenOn(enabled: Boolean) = viewModelScope.launch {
        preferencesRepository.setKeepScreenOnWhileCooking(enabled)
    }

    fun checkConnection() {
        _state.update { it.copy(connectionCheck = ConnectionCheck.CHECKING, checkError = null) }
        viewModelScope.launch {
            val api = sessionManager.api()
            if (api == null) {
                _state.update {
                    it.copy(connectionCheck = ConnectionCheck.FAILED, checkError = NetworkError.Unauthorized)
                }
                return@launch
            }
            when (val result = apiCall { api.currentUser() }) {
                is ApiResult.Failure -> _state.update {
                    it.copy(connectionCheck = ConnectionCheck.FAILED, checkError = result.error)
                }
                is ApiResult.Success -> _state.update {
                    it.copy(connectionCheck = ConnectionCheck.OK, checkError = null)
                }
            }
        }
    }

    fun signOut() = viewModelScope.launch { authRepository.signOut() }

    /**
     * Household preferences live on the Mealie side; Umai shows them read-only
     * rather than duplicating a settings store the server already owns.
     */
    private fun loadHouseholdPreferences() {
        viewModelScope.launch {
            val api = sessionManager.api() ?: return@launch
            when (val result = apiCall { api.householdPreferences() }) {
                is ApiResult.Failure -> Unit
                is ApiResult.Success -> _state.update {
                    it.copy(
                        household = HouseholdInfo(
                            firstDayOfWeek = result.value.firstDayOfWeek,
                            showNutrition = result.value.recipeShowNutrition,
                        ),
                    )
                }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                SettingsViewModel(
                    preferencesRepository = container.preferencesRepository,
                    authRepository = container.authRepository,
                    sessionManager = container.sessionManager,
                    localeController = container.localeController,
                )
            }
        }
    }
}
