package org.opensources.umai.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.opensources.umai.core.di.AppContainer
import org.opensources.umai.core.session.SessionManager
import org.opensources.umai.core.session.SessionState
import org.opensources.umai.core.settings.AppLanguage
import org.opensources.umai.core.settings.AppPreferences
import org.opensources.umai.core.settings.AppPreferencesRepository
import org.opensources.umai.core.settings.LocaleController
import org.opensources.umai.core.settings.RecipeLayout
import org.opensources.umai.core.settings.RecipeSection
import org.opensources.umai.core.settings.ThemeMode

/** Preferences that belong to the app itself and never leave the device. */
class AppSettingsViewModel(
    private val preferencesRepository: AppPreferencesRepository,
    private val localeController: LocaleController,
    sessionManager: SessionManager,
) : ViewModel() {

    val preferences: StateFlow<AppPreferences> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPreferences())

    val sessionState: StateFlow<SessionState> = sessionManager.state

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

    fun setRecipeSectionVisible(section: RecipeSection, visible: Boolean) = viewModelScope.launch {
        preferencesRepository.setRecipeSectionVisible(section, visible)
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                AppSettingsViewModel(
                    preferencesRepository = container.preferencesRepository,
                    localeController = container.localeController,
                    sessionManager = container.sessionManager,
                )
            }
        }
    }
}
