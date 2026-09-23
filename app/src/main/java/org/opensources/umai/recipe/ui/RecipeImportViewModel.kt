package org.opensources.umai.recipe.ui

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
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.recipe.data.RecipeEditRepository

data class RecipeImportUiState(
    val url: String = "",
    val includeTags: Boolean = true,
    val includeCategories: Boolean = true,
    val importing: Boolean = false,
    val error: NetworkError? = null,
    /** Set once Mealie has created the recipe; the screen then navigates to it. */
    val importedSlug: String? = null,
) {
    val canSubmit: Boolean get() = !importing && url.isNotBlank()
}

/** Hands a web address to Mealie's own scraper; Umai parses nothing itself. */
class RecipeImportViewModel(
    private val repository: RecipeEditRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RecipeImportUiState())
    val state: StateFlow<RecipeImportUiState> = _state.asStateFlow()

    private var importJob: Job? = null

    fun onUrlChange(value: String) = _state.update { it.copy(url = value, error = null) }

    fun onIncludeTagsChange(value: Boolean) = _state.update { it.copy(includeTags = value) }

    fun onIncludeCategoriesChange(value: Boolean) =
        _state.update { it.copy(includeCategories = value) }

    fun import() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(importing = true, error = null) }

        importJob?.cancel()
        importJob = viewModelScope.launch {
            val result = repository.importFromUrl(
                url = current.url,
                includeTags = current.includeTags,
                includeCategories = current.includeCategories,
            )
            _state.update {
                when (result) {
                    is ApiResult.Failure -> it.copy(importing = false, error = result.error)
                    is ApiResult.Success -> it.copy(importing = false, importedSlug = result.value)
                }
            }
        }
    }

    fun consumeImportedSlug() = _state.update { it.copy(importedSlug = null) }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { RecipeImportViewModel(container.recipeEditRepository) }
        }
    }
}
