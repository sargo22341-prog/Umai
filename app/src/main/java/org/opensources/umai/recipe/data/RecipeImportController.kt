package org.opensources.umai.recipe.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.provider.domain.ProviderRegistry
import org.opensources.umai.provider.data.ProviderMediaImporter
import org.opensources.umai.provider.data.ProviderSettings
import org.opensources.umai.provider.data.importsMediaNow
import org.opensources.umai.recipe.domain.ImportNotice
import org.opensources.umai.recipe.domain.ImportOutcome
import org.opensources.umai.recipe.domain.ImportPhase
import org.opensources.umai.recipe.domain.ImportedRecipe
import org.opensources.umai.recipe.domain.RecipeImportRun
import org.opensources.umai.youtube.data.VideoImportOutcome
import org.opensources.umai.youtube.data.VideoImportProgress
import org.opensources.umai.youtube.data.VideoRecipeImporter
import org.opensources.umai.youtube.domain.BlueprintOrigin
import org.opensources.umai.youtube.domain.YouTubeLinks

/** What the system shows of an import: the notification, and what keeps the app alive meanwhile. */
interface ImportHost {
    /** The import runs, at the phase of [run]; the first call keeps the app alive until [ended] or [clear]. */
    fun running(run: RecipeImportRun)

    /** The import ended; [announce] when no import screen was there to show it. */
    fun ended(run: RecipeImportRun, announce: Boolean)

    /** Nothing left to show: the outcome was seen, or the import cancelled. */
    fun clear()
}

/**
 * The recipe import of the whole app. It outlives the import screen: leaving
 * it, or the app, lets a video be rebuilt for minutes, shown by [host] in a
 * notification, and coming back to the screen finds the import where it is.
 *
 * Mealie's own scraper reads a web page; Umai parses nothing itself. A YouTube
 * video is different: Mealie only keeps its title and description, so the app
 * rebuilds the recipe from the video itself ([VideoRecipeImporter]). Before
 * that, the recipe is looked for on the instance; after, it gets its calorie
 * tag and, for a page of a known provider, the video and step photos the
 * provider publishes.
 *
 * One import runs at a time. Confined to the main thread, like the screens
 * that call it: [scope] runs there.
 */
class RecipeImportController(
    private val scope: CoroutineScope,
    private val repository: RecipeEditRepository,
    private val calorieTags: CalorieTagRepository,
    private val providers: ProviderRegistry,
    private val providerSettings: ProviderSettings,
    private val mediaImporter: ProviderMediaImporter,
    private val videoImporter: VideoRecipeImporter,
    private val host: ImportHost,
) {
    private val _run = MutableStateFlow<RecipeImportRun?>(null)

    /** The import running, or ended and not seen yet; null otherwise. */
    val run: StateFlow<RecipeImportRun?> = _run.asStateFlow()

    private var job: Job? = null

    /** How many import screens are on display: an import that ends before one is not announced. */
    private var watchers = 0

    /** Imports [url], unless the instance already holds it and [evenIfPresent] is false. */
    fun start(url: String, includeTags: Boolean, includeCategories: Boolean, evenIfPresent: Boolean) {
        if (_run.value?.running == true) return
        val address = url.trim()
        val run = RecipeImportRun(url = address, isVideo = YouTubeLinks.isVideo(address))
        _run.value = run
        host.running(run)
        job = scope.launch {
            val duplicate = if (evenIfPresent) null else duplicateOf(address)
            end(duplicate ?: if (run.isVideo) importVideo(address) else importPage(address, includeTags, includeCategories))
        }
    }

    /** Stops the import on its way; a recipe already created on Mealie stays. */
    fun cancel() {
        job?.cancel()
        job = null
        _run.value = null
        host.clear()
    }

    /** An import screen shows the import, or no longer does. */
    fun watch(watching: Boolean) {
        watchers = (watchers + if (watching) 1 else -1).coerceAtLeast(0)
    }

    /** The outcome was shown: the import is over. */
    fun consume() {
        if (_run.value?.running != false) return
        _run.value = null
        host.clear()
    }

    /** The recipe [slug] was opened from the notification that told the import ended: the import is over. */
    fun seen(slug: String) {
        val outcome = _run.value?.outcome as? ImportOutcome.Imported ?: return
        if (outcome.recipe.slug == slug) consume()
    }

    /** The recipe of the instance that comes from [url]; a failed check only loses the warning. */
    private suspend fun duplicateOf(url: String): ImportOutcome.Duplicate? =
        (repository.findBySource(url) as? ApiResult.Success)?.value?.let { ImportOutcome.Duplicate(it) }

    private suspend fun importPage(url: String, includeTags: Boolean, includeCategories: Boolean): ImportOutcome {
        progress { it.copy(phase = ImportPhase.IMPORTING) }
        val slug = when (val result = repository.importFromUrl(url, includeTags, includeCategories)) {
            is ApiResult.Failure -> return ImportOutcome.Failed(result.error)
            is ApiResult.Success -> result.value
        }
        // The recipe exists by now: what follows only completes it. A calorie tag left
        // behind is caught up by the settings' tag sync, so it does not fail the import.
        val _ = calorieTags.sync(slug)
        val provider = providers.forUrl(url)
        val mediaFailed = if (provider != null && providerSettings.importsMediaNow(provider.id)) {
            progress { it.copy(phase = ImportPhase.FETCHING_MEDIA) }
            mediaImporter.import(slug) is ApiResult.Failure
        } else {
            false
        }
        return ImportOutcome.Imported(ImportedRecipe(slug, ImportNotice.MEDIA_FAILED.takeIf { mediaFailed }))
    }

    private suspend fun importVideo(url: String): ImportOutcome {
        val outcome = videoImporter.import(url) { step ->
            progress {
                when (step) {
                    VideoImportProgress.ReadingVideo -> it.copy(phase = ImportPhase.READING_VIDEO)
                    is VideoImportProgress.Watching -> it.copy(phase = ImportPhase.WATCHING, watchProgress = step.progress)
                    is VideoImportProgress.Understanding ->
                        it.copy(phase = ImportPhase.UNDERSTANDING, watchProgress = null, modelProgress = step.progress)
                    VideoImportProgress.Saving -> it.copy(phase = ImportPhase.SAVING, modelProgress = null, watchProgress = null)
                }
            }
        }
        return when (outcome) {
            is VideoImportOutcome.Imported -> {
                val result = outcome.result
                // Caught up by the settings' tag sync when it fails, as for a page.
                val _ = calorieTags.sync(result.slug)
                val notice = when {
                    result.modelFailure != null -> ImportNotice.VIDEO_MODEL_FAILED
                    result.captionsRefused && !result.videoLinked -> ImportNotice.VIDEO_CAPTIONS_REFUSED
                    result.origin == BlueprintOrigin.RULES -> ImportNotice.VIDEO_WITHOUT_MODEL
                    !result.videoLinked -> ImportNotice.VIDEO_NOT_LINKED
                    else -> null
                }
                ImportOutcome.Imported(ImportedRecipe(result.slug, notice))
            }
            is VideoImportOutcome.VideoFailed -> ImportOutcome.VideoFailed(outcome.failure)
            VideoImportOutcome.NothingToRebuild -> ImportOutcome.VideoEmpty
            is VideoImportOutcome.SaveFailed -> ImportOutcome.Failed(outcome.error)
        }
    }

    private fun progress(change: (RecipeImportRun) -> RecipeImportRun) {
        _run.update { run -> run?.takeIf { it.running }?.let(change) }
        _run.value?.let(host::running)
    }

    private fun end(outcome: ImportOutcome) {
        val ended = _run.value?.copy(modelProgress = null, watchProgress = null, outcome = outcome) ?: return
        _run.value = ended
        host.ended(ended, announce = watchers == 0)
    }
}
