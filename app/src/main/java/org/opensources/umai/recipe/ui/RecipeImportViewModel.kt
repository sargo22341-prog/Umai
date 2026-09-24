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
import org.opensources.umai.core.model.RecipeSummary
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.provider.ProviderRegistry
import org.opensources.umai.provider.data.ProviderMediaImporter
import org.opensources.umai.provider.data.ProviderSettings
import org.opensources.umai.provider.data.importsMediaNow
import org.opensources.umai.recipe.data.CalorieTagRepository
import org.opensources.umai.recipe.data.RecipeEditRepository

/** Where an import stands while it runs. */
enum class ImportPhase { CHECKING, IMPORTING, FETCHING_MEDIA }

/** A recipe imported, and whether the media of its provider could not be fetched. */
data class ImportedRecipe(val slug: String, val mediaFailed: Boolean)

data class RecipeImportUiState(
    val url: String = "",
    val includeTags: Boolean = true,
    val includeCategories: Boolean = true,
    val phase: ImportPhase? = null,
    val error: NetworkError? = null,
    /** A recipe of the instance that comes from the same page; the import waits for a decision. */
    val duplicate: RecipeSummary? = null,
    /**
     * The provider of the page, when the app knows how to fetch more from it
     * and the reader lets it; [providerOffersVideo] tells what it brings.
     */
    val providerName: String? = null,
    val providerOffersVideo: Boolean = false,
    /** Set once Mealie has created the recipe; the screen then navigates to it. */
    val imported: ImportedRecipe? = null,
) {
    val importing: Boolean get() = phase != null
    val canSubmit: Boolean get() = !importing && url.isNotBlank()
}

/**
 * Hands a web address to Mealie's own scraper; Umai parses nothing itself.
 *
 * Before that, it checks the recipe is not already on the instance. After, the
 * recipe gets its calorie tag and, for a page of a known provider, the video
 * and step photos the provider publishes.
 */
class RecipeImportViewModel(
    initialUrl: String?,
    private val repository: RecipeEditRepository,
    private val calorieTags: CalorieTagRepository,
    private val providers: ProviderRegistry,
    private val providerSettings: ProviderSettings,
    private val mediaImporter: ProviderMediaImporter,
) : ViewModel() {

    private val _state = MutableStateFlow(RecipeImportUiState())
    val state: StateFlow<RecipeImportUiState> = _state.asStateFlow()

    private var importJob: Job? = null

    init {
        initialUrl?.takeIf { it.isNotBlank() }?.let(::onUrlChange)
    }

    fun onUrlChange(value: String) {
        val provider = providers.forUrl(value.trim())
        _state.update { it.copy(url = value, error = null, duplicate = null, providerName = null) }
        if (provider == null) return
        viewModelScope.launch {
            if (!providerSettings.importsMediaNow(provider.id)) return@launch
            _state.update {
                // The address may have changed while the setting was read.
                if (providers.forUrl(it.url.trim()) != provider) {
                    it
                } else {
                    it.copy(providerName = provider.name, providerOffersVideo = provider.offersVideo)
                }
            }
        }
    }

    fun onIncludeTagsChange(value: Boolean) = _state.update { it.copy(includeTags = value) }

    fun onIncludeCategoriesChange(value: Boolean) =
        _state.update { it.copy(includeCategories = value) }

    /** Imports the page, unless the instance already holds it and [evenIfPresent] is false. */
    fun import(evenIfPresent: Boolean = false) {
        val current = _state.value
        if (!current.canSubmit) return
        val url = current.url.trim()
        _state.update { it.copy(phase = ImportPhase.CHECKING, error = null, duplicate = null) }

        importJob?.cancel()
        importJob = viewModelScope.launch {
            if (!evenIfPresent) {
                // A failed check does not stop the import: it only loses the warning.
                val existing = (repository.findBySource(url) as? ApiResult.Success)?.value
                if (existing != null) {
                    _state.update { it.copy(phase = null, duplicate = existing) }
                    return@launch
                }
            }

            _state.update { it.copy(phase = ImportPhase.IMPORTING) }
            val slug = when (
                val result = repository.importFromUrl(
                    url = url,
                    includeTags = current.includeTags,
                    includeCategories = current.includeCategories,
                )
            ) {
                is ApiResult.Failure -> {
                    _state.update { it.copy(phase = null, error = result.error) }
                    return@launch
                }
                is ApiResult.Success -> result.value
            }

            // The recipe exists by now: what follows only completes it.
            calorieTags.sync(slug)
            val provider = providers.forUrl(url)
            val mediaFailed = if (provider != null && providerSettings.importsMediaNow(provider.id)) {
                _state.update { it.copy(phase = ImportPhase.FETCHING_MEDIA) }
                mediaImporter.import(slug) is ApiResult.Failure
            } else {
                false
            }
            _state.update { it.copy(phase = null, imported = ImportedRecipe(slug, mediaFailed)) }
        }
    }

    fun dismissDuplicate() = _state.update { it.copy(duplicate = null) }

    fun consumeImported() = _state.update { it.copy(imported = null) }

    companion object {
        fun factory(container: AppContainer, initialUrl: String?) = viewModelFactory {
            initializer {
                RecipeImportViewModel(
                    initialUrl = initialUrl,
                    repository = container.recipeEditRepository,
                    calorieTags = container.calorieTagRepository,
                    providers = container.providerRegistry,
                    providerSettings = container.providerSettings,
                    mediaImporter = container.providerMediaImporter,
                )
            }
        }
    }
}
