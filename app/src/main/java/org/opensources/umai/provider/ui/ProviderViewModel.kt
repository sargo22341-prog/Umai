package org.opensources.umai.provider.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opensources.umai.core.di.AppContainer
import org.opensources.umai.provider.RecipeProvider
import org.opensources.umai.provider.data.ProviderSettings

data class ProviderUiState(
    val provider: RecipeProvider? = null,
    val importsMedia: Boolean = true,
)

/** The page of one provider: whether imports fetch its media. */
class ProviderViewModel(
    provider: RecipeProvider?,
    private val settings: ProviderSettings,
) : ViewModel() {

    private val _state = MutableStateFlow(ProviderUiState(provider = provider))
    val state: StateFlow<ProviderUiState> = _state.asStateFlow()

    init {
        provider?.let {
            viewModelScope.launch {
                settings.importsMedia(it.id).collect { enabled -> _state.update { state -> state.copy(importsMedia = enabled) } }
            }
        }
    }

    fun setImportsMedia(enabled: Boolean) {
        val provider = _state.value.provider ?: return
        viewModelScope.launch { settings.setImportsMedia(provider.id, enabled) }
    }

    companion object {
        fun factory(container: AppContainer, providerId: String) = viewModelFactory {
            initializer {
                ProviderViewModel(
                    provider = container.providerRegistry.byId(providerId),
                    settings = container.providerSettings,
                )
            }
        }
    }
}
