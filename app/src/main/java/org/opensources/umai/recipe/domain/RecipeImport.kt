package org.opensources.umai.recipe.domain

import org.opensources.umai.core.model.RecipeSummary
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.llm.domain.LlmProgress
import org.opensources.umai.youtube.domain.WatchProgress
import org.opensources.umai.youtube.domain.YouTubeFailure

/**
 * Where an import stands while it runs. A web page goes through [CHECKING],
 * [IMPORTING] and [FETCHING_MEDIA]; a video through [CHECKING],
 * [READING_VIDEO], [WATCHING] when it has no captions, [UNDERSTANDING] and [SAVING].
 */
enum class ImportPhase { CHECKING, IMPORTING, FETCHING_MEDIA, READING_VIDEO, WATCHING, UNDERSTANDING, SAVING }

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

    /** YouTube refused the captions, and without them the steps could not be tied to the video. */
    VIDEO_CAPTIONS_REFUSED,
}

/** A recipe imported, and what did not go as hoped, if anything. */
data class ImportedRecipe(val slug: String, val notice: ImportNotice?)

/** How an import ended. */
sealed interface ImportOutcome {
    data class Imported(val recipe: ImportedRecipe) : ImportOutcome

    /** A recipe of the instance comes from the same page; the import waits for a decision. */
    data class Duplicate(val existing: RecipeSummary) : ImportOutcome

    data class Failed(val error: NetworkError) : ImportOutcome

    data class VideoFailed(val failure: YouTubeFailure) : ImportOutcome

    /** The video holds nothing a recipe could be rebuilt from. */
    data object VideoEmpty : ImportOutcome
}

/**
 * The one import of the app: running while [outcome] is null, at [phase],
 * then ended until its outcome has been seen.
 */
data class RecipeImportRun(
    val url: String,
    /** The address is a YouTube video, rebuilt into a recipe rather than scraped. */
    val isVideo: Boolean,
    val phase: ImportPhase = ImportPhase.CHECKING,
    /** How far the language model is, while it reads the video. */
    val modelProgress: LlmProgress? = null,
    /** How far the video is listened to or looked at. */
    val watchProgress: WatchProgress? = null,
    val outcome: ImportOutcome? = null,
) {
    val running: Boolean get() = outcome == null
}

/** What a notification of an import asks the app to open. */
sealed interface ImportRequest {
    /** The import screen, where the import runs or tells how it ended. */
    data object OpenImport : ImportRequest

    /** The recipe the import created. */
    data class OpenRecipe(val slug: String) : ImportRequest
}
