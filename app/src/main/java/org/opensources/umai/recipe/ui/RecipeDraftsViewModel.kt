package org.opensources.umai.recipe.ui

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
import org.opensources.umai.recipe.data.RecipeDraftStore
import org.opensources.umai.recipe.domain.RecipeDraft

data class RecipeDraftsUiState(
    val drafts: List<RecipeDraft> = emptyList(),
    val loading: Boolean = true,
) {
    /** An empty list is not an error: it simply means nothing was started. */
    val isEmpty: Boolean get() = !loading && drafts.isEmpty()
}

class RecipeDraftsViewModel(private val store: RecipeDraftStore) : ViewModel() {

    private val _state = MutableStateFlow(RecipeDraftsUiState())
    val state: StateFlow<RecipeDraftsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            store.drafts.collect { drafts ->
                _state.update { it.copy(drafts = drafts, loading = false) }
            }
        }
    }

    fun delete(id: String) = viewModelScope.launch { store.delete(id) }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { RecipeDraftsViewModel(container.recipeDraftStore) }
        }
    }
}
