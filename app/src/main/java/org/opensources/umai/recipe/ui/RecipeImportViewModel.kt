package org.opensources.umai.recipe.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opensources.umai.core.di.AppContainer
import org.opensources.umai.core.model.RecipeSummary
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.llm.domain.LlmProgress
import org.opensources.umai.provider.ProviderRegistry
import org.opensources.umai.provider.data.ProviderSettings
import org.opensources.umai.provider.data.importsMediaNow
import org.opensources.umai.recipe.data.RecipeImportController
import org.opensources.umai.recipe.domain.ImportOutcome
import org.opensources.umai.recipe.domain.ImportPhase
import org.opensources.umai.recipe.domain.ImportedRecipe
import org.opensources.umai.recipe.domain.RecipeImportRun
import org.opensources.umai.youtube.domain.WatchProgress
import org.opensources.umai.youtube.domain.YouTubeFailure
import org.opensources.umai.youtube.domain.YouTubeLinks

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
    /** How far the language model is, while it listens to the video or looks at it. */
    val watchProgress: WatchProgress? = null,
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
 * The import form. The import itself runs in [RecipeImportController], for
 * the whole app: leaving the screen does not stop it, and the screen opened
 * again shows it where it is. How it ended is taken over by the form once the
 * screen is on display ([onDisplayed]), and otherwise told by a notification.
 */
class RecipeImportViewModel(
    initialUrl: String?,
    private val imports: RecipeImportController,
    private val providers: ProviderRegistry,
    private val providerSettings: ProviderSettings,
    private val modelReady: suspend () -> Boolean,
) : ViewModel() {

    private val form = MutableStateFlow(RecipeImportUiState())

    private var displayed = false

    val state: StateFlow<RecipeImportUiState> = combine(form, imports.run) { form, run ->
        if (run?.running == true) form.showing(run) else form
    }.stateIn(viewModelScope, SharingStarted.Eagerly, form.value)

    init {
        // A page shared to the app is the one to import; otherwise the import under way, if any.
        (initialUrl?.takeIf { it.isNotBlank() } ?: imports.run.value?.url)?.let(::onUrlChange)
        viewModelScope.launch {
            imports.run.collect { run -> if (run?.outcome != null && displayed) takeOver(run) }
        }
    }

    /** The screen is on display, or no longer is: only then is the end of an import shown on it. */
    fun onDisplayed(displayed: Boolean) {
        if (this.displayed == displayed) return
        this.displayed = displayed
        imports.watch(displayed)
        imports.run.value?.takeIf { displayed && it.outcome != null }?.let(::takeOver)
    }

    fun onUrlChange(value: String) {
        val provider = providers.forUrl(value.trim())
        val isVideo = YouTubeLinks.isVideo(value)
        form.update {
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
                form.update { if (it.isVideo) it.copy(videoUsesModel = usesModel) else it }
            }
            return
        }
        if (provider == null) return
        viewModelScope.launch {
            if (!providerSettings.importsMediaNow(provider.id)) return@launch
            form.update {
                // The address may have changed while the setting was read.
                if (providers.forUrl(it.url.trim()) != provider) {
                    it
                } else {
                    it.copy(providerName = provider.name, providerOffersVideo = provider.offersVideo)
                }
            }
        }
    }

    fun onIncludeTagsChange(value: Boolean) = form.update { it.copy(includeTags = value) }

    fun onIncludeCategoriesChange(value: Boolean) = form.update { it.copy(includeCategories = value) }

    /** Imports the page, unless the instance already holds it and [evenIfPresent] is false. */
    fun import(evenIfPresent: Boolean = false) {
        val current = state.value
        if (!current.canSubmit) return
        form.update { it.copy(error = null, duplicate = null, videoFailure = null, videoEmpty = false) }
        imports.start(current.url, current.includeTags, current.includeCategories, evenIfPresent)
    }

    /** Stops an import on its way; a recipe already created on Mealie stays. */
    fun cancel() = imports.cancel()

    fun dismissDuplicate() = form.update { it.copy(duplicate = null) }

    fun consumeImported() = form.update { it.copy(imported = null) }

    override fun onCleared() {
        if (displayed) imports.watch(false)
    }

    /** The form shows how [run] ended, which is then over for the app. */
    private fun takeOver(run: RecipeImportRun) {
        val outcome = run.outcome ?: return
        form.update { current ->
            val base = if (current.url.trim() == run.url) current else current.copy(url = run.url, isVideo = run.isVideo)
            when (outcome) {
                is ImportOutcome.Imported -> base.copy(imported = outcome.recipe)
                is ImportOutcome.Duplicate -> base.copy(duplicate = outcome.existing)
                is ImportOutcome.Failed -> base.copy(error = outcome.error)
                is ImportOutcome.VideoFailed -> base.copy(videoFailure = outcome.failure)
                ImportOutcome.VideoEmpty -> base.copy(videoEmpty = true)
            }
        }
        imports.consume()
    }

    /** The form while [run] is under way: its address, and how far it is. */
    private fun RecipeImportUiState.showing(run: RecipeImportRun) = copy(
        url = run.url,
        isVideo = run.isVideo,
        phase = run.phase,
        modelProgress = run.modelProgress,
        watchProgress = run.watchProgress,
    )

    companion object {
        fun factory(container: AppContainer, initialUrl: String?) = viewModelFactory {
            initializer {
                RecipeImportViewModel(
                    initialUrl = initialUrl,
                    imports = container.recipeImports,
                    providers = container.providerRegistry,
                    providerSettings = container.providerSettings,
                    modelReady = container.localLanguageModel::isReady,
                )
            }
        }
    }
}
