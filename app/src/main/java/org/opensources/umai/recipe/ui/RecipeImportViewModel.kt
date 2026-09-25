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
import org.opensources.umai.llm.domain.LlmProgress
import org.opensources.umai.youtube.data.VideoImportOutcome
import org.opensources.umai.youtube.data.VideoImportProgress
import org.opensources.umai.youtube.data.VideoRecipeImporter
import org.opensources.umai.youtube.domain.BlueprintOrigin
import org.opensources.umai.youtube.domain.YouTubeFailure
import org.opensources.umai.youtube.domain.YouTubeLinks

/**
 * Where an import stands while it runs. A web page goes through [CHECKING],
 * [IMPORTING] and [FETCHING_MEDIA]; a video through [CHECKING],
 * [READING_VIDEO], [UNDERSTANDING] and [SAVING].
 */
enum class ImportPhase { CHECKING, IMPORTING, FETCHING_MEDIA, READING_VIDEO, UNDERSTANDING, SAVING }

/** What the user is told about an import that went through, but not entirely as hoped. */
enum class ImportNotice {
    /** The media of the provider could not be fetched. */
    MEDIA_FAILED,

    /** No language model is installed: the video was rebuilt with plain rules. */
    VIDEO_WITHOUT_MODEL,

    /** The language model failed, and plain rules took over. */
    VIDEO_MODEL_FAILED,

    /** The steps could not be tied to the video. */
    VIDEO_NOT_LINKED,
}

/** A recipe imported, and what did not go as hoped, if anything. */
data class ImportedRecipe(val slug: String, val notice: ImportNotice?)

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
    /** The address is a YouTube video, rebuilt into a recipe rather than scraped. */
    val isVideo: Boolean = false,
    /** Whether the local language model will rebuild the video, rather than plain rules. */
    val videoUsesModel: Boolean = false,
    /** How far the language model is, while it reads the video. */
    val modelProgress: LlmProgress? = null,
    val videoFailure: YouTubeFailure? = null,
    /** The video holds nothing a recipe could be rebuilt from. */
    val videoEmpty: Boolean = false,
    /** Set once Mealie has created the recipe; the screen then navigates to it. */
    val imported: ImportedRecipe? = null,
) {
    val importing: Boolean get() = phase != null
    val canSubmit: Boolean get() = !importing && url.isNotBlank()
}

/**
 * Hands a web address to Mealie's own scraper; Umai parses nothing itself.
 * A YouTube video is different: Mealie only keeps its title and description,
 * so the app rebuilds the recipe from the video itself ([VideoRecipeImporter]).
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
    private val videoImporter: VideoRecipeImporter,
    private val modelReady: suspend () -> Boolean,
) : ViewModel() {

    private val _state = MutableStateFlow(RecipeImportUiState())
    val state: StateFlow<RecipeImportUiState> = _state.asStateFlow()

    private var importJob: Job? = null

    init {
        initialUrl?.takeIf { it.isNotBlank() }?.let(::onUrlChange)
    }

    fun onUrlChange(value: String) {
        val provider = providers.forUrl(value.trim())
        val isVideo = YouTubeLinks.isVideo(value)
        _state.update {
            it.copy(
                url = value,
                error = null,
                duplicate = null,
                providerName = null,
                isVideo = isVideo,
                videoFailure = null,
                videoEmpty = false,
            )
        }
        if (isVideo) {
            viewModelScope.launch {
                val usesModel = modelReady()
                _state.update { if (it.isVideo) it.copy(videoUsesModel = usesModel) else it }
            }
            return
        }
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
        _state.update {
            it.copy(phase = ImportPhase.CHECKING, error = null, duplicate = null, videoFailure = null, videoEmpty = false)
        }

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

            if (current.isVideo) {
                importVideo(url)
                return@launch
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
            _state.update {
                it.copy(phase = null, imported = ImportedRecipe(slug, ImportNotice.MEDIA_FAILED.takeIf { mediaFailed }))
            }
        }
    }

    private suspend fun importVideo(url: String) {
        val outcome = videoImporter.import(url) { progress ->
            _state.update {
                when (progress) {
                    VideoImportProgress.ReadingVideo -> it.copy(phase = ImportPhase.READING_VIDEO)
                    is VideoImportProgress.Understanding ->
                        it.copy(phase = ImportPhase.UNDERSTANDING, modelProgress = progress.progress)
                    VideoImportProgress.Saving -> it.copy(phase = ImportPhase.SAVING, modelProgress = null)
                }
            }
        }
        if (outcome is VideoImportOutcome.Imported) calorieTags.sync(outcome.result.slug)
        _state.update {
            when (outcome) {
                is VideoImportOutcome.Imported -> {
                    val result = outcome.result
                    val notice = when {
                        result.modelFailure != null -> ImportNotice.VIDEO_MODEL_FAILED
                        result.origin == BlueprintOrigin.RULES -> ImportNotice.VIDEO_WITHOUT_MODEL
                        !result.videoLinked -> ImportNotice.VIDEO_NOT_LINKED
                        else -> null
                    }
                    it.copy(phase = null, modelProgress = null, imported = ImportedRecipe(result.slug, notice))
                }
                is VideoImportOutcome.VideoFailed ->
                    it.copy(phase = null, modelProgress = null, videoFailure = outcome.failure)
                VideoImportOutcome.NothingToRebuild -> it.copy(phase = null, modelProgress = null, videoEmpty = true)
                is VideoImportOutcome.SaveFailed -> it.copy(phase = null, modelProgress = null, error = outcome.error)
            }
        }
    }

    /** Stops an import on its way; a recipe already created on Mealie stays. */
    fun cancel() {
        importJob?.cancel()
        _state.update { it.copy(phase = null, modelProgress = null) }
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
                    videoImporter = container.videoRecipeImporter,
                    modelReady = container.localLanguageModel::isReady,
                )
            }
        }
    }
}
