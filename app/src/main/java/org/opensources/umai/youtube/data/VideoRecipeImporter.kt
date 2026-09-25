package org.opensources.umai.youtube.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.network.api.MealieApi
import org.opensources.umai.core.network.apiCall
import org.opensources.umai.core.network.dto.ScrapeRecipeDataDto
import org.opensources.umai.llm.domain.LanguageModel
import org.opensources.umai.llm.domain.LlmFailure
import org.opensources.umai.llm.domain.LlmProgress
import org.opensources.umai.recipe.data.RecipeEditRepository
import org.opensources.umai.recipe.data.RecipeMediaRepository
import org.opensources.umai.recipe.domain.DraftStep
import org.opensources.umai.recipe.domain.IngredientLinker
import org.opensources.umai.recipe.domain.RecipeDraft
import org.opensources.umai.youtube.domain.BlueprintOrigin
import org.opensources.umai.youtube.domain.ModelRecipeBuilder
import org.opensources.umai.youtube.domain.RecipeBlueprint
import org.opensources.umai.youtube.domain.RuleRecipeBuilder
import org.opensources.umai.youtube.domain.YouTubeFailure
import org.opensources.umai.youtube.domain.YouTubeLinks
import org.opensources.umai.youtube.domain.VideoSource
import org.opensources.umai.youtube.domain.YouTubeResult

/** Where an import from a video stands. */
sealed interface VideoImportProgress {
    data object ReadingVideo : VideoImportProgress

    /** The language model reads the transcript; [progress] is `null` until it starts. */
    data class Understanding(val progress: LlmProgress?) : VideoImportProgress

    data object Saving : VideoImportProgress
}

/** A recipe created from a video, and how it was rebuilt. */
data class VideoImport(
    val slug: String,
    val origin: BlueprintOrigin,
    /** Why the language model was not used, when it was installed but failed. */
    val modelFailure: LlmFailure?,
    val hadTranscript: Boolean,
    /** Whether the steps could be tied to the video, for the cooking mode. */
    val videoLinked: Boolean,
)

sealed interface VideoImportOutcome {
    data class Imported(val result: VideoImport) : VideoImportOutcome

    data class VideoFailed(val failure: YouTubeFailure) : VideoImportOutcome

    /** The video holds neither a list of ingredients, nor chapters, nor captions. */
    data object NothingToRebuild : VideoImportOutcome

    data class SaveFailed(val error: NetworkError) : VideoImportOutcome
}

/**
 * Rebuilds a recipe from a YouTube video and writes it on Mealie, where
 * Mealie's own import stops at the title and the description.
 *
 * The recipe is given to Mealie as a schema.org Recipe, which it cleans and
 * stores as it would a web page, fetching the picture itself. The steps are
 * then written again as rebuilt, with their titles and the ingredients they
 * use, and a chapters file ties each step to its part of the video, which the
 * cooking mode plays in a loop.
 */
class VideoRecipeImporter(
    private val youTube: VideoSource,
    private val model: LanguageModel,
    private val apiProvider: () -> MealieApi?,
    private val edits: RecipeEditRepository,
    private val media: RecipeMediaRepository,
    private val language: () -> String,
) {

    private val modelBuilder = ModelRecipeBuilder(model)

    suspend fun import(url: String, onProgress: (VideoImportProgress) -> Unit): VideoImportOutcome {
        val id = YouTubeLinks.videoId(url) ?: return VideoImportOutcome.VideoFailed(YouTubeFailure.NOT_A_VIDEO)
        onProgress(VideoImportProgress.ReadingVideo)
        val video = when (val result = youTube.video(id)) {
            is YouTubeResult.Failure -> return VideoImportOutcome.VideoFailed(result.failure)
            is YouTubeResult.Success -> result.value
        }

        var modelFailure: LlmFailure? = null
        val blueprint = if (model.isReady() && (video.transcript.isNotEmpty() || video.description.isNotBlank())) {
            onProgress(VideoImportProgress.Understanding(null))
            when (val built = modelBuilder.build(video, language()) { onProgress(VideoImportProgress.Understanding(it)) }) {
                is ModelRecipeBuilder.Outcome.Built -> built.blueprint
                is ModelRecipeBuilder.Outcome.Failed -> {
                    modelFailure = built.reason
                    RuleRecipeBuilder.build(video)
                }
            }
        } else {
            RuleRecipeBuilder.build(video)
        }
        if (!blueprint.isUsable) return VideoImportOutcome.NothingToRebuild

        onProgress(VideoImportProgress.Saving)
        val slug = when (val created = create(blueprint)) {
            is ApiResult.Failure -> return VideoImportOutcome.SaveFailed(created.error)
            is ApiResult.Success -> created.value
        }
        // The recipe exists from here on: what follows completes it, and a
        // failure leaves it as Mealie read it rather than undoing it.
        val stepsWritten = writeSteps(slug, blueprint)
        val manifest = blueprint.manifest()
        val linked = stepsWritten && manifest != null && media.saveVideoManifest(slug, manifest) is ApiResult.Success

        return VideoImportOutcome.Imported(
            VideoImport(
                slug = slug,
                origin = blueprint.origin,
                modelFailure = modelFailure,
                hadTranscript = video.transcript.isNotEmpty(),
                videoLinked = linked,
            ),
        )
    }

    private suspend fun create(blueprint: RecipeBlueprint): ApiResult<String> {
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        return when (
            val result = apiCall {
                api.createRecipeFromJson(ScrapeRecipeDataDto(data = schema(blueprint).toString(), url = blueprint.video.watchUrl))
            }
        ) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> result.value.trim().trim('"').takeIf { it.isNotBlank() }
                ?.let { ApiResult.Success(it) }
                ?: ApiResult.Failure(NetworkError.InvalidResponse)
        }
    }

    /**
     * Mealie keeps the steps of a schema.org recipe as text, and their titles
     * only in its latest versions: they are written again, with their titles
     * and their links to the ingredients, keeping the ids Mealie gave them.
     */
    private suspend fun writeSteps(slug: String, blueprint: RecipeBlueprint): Boolean {
        if (blueprint.steps.isEmpty()) return true
        val original = when (val loaded = edits.loadForEdit(slug)) {
            is ApiResult.Failure -> return false
            is ApiResult.Success -> loaded.value.draft
        }
        if (original.steps.size != blueprint.steps.size) return false
        val edited = original.copy(steps = rebuiltSteps(original, blueprint))
        return edits.update(slug, original, edited) is ApiResult.Success
    }

    /** The rebuilt titles and texts, linked by name to the ingredients they use. */
    private fun rebuiltSteps(original: RecipeDraft, blueprint: RecipeBlueprint): List<DraftStep> {
        val steps = original.steps.zip(blueprint.steps) { saved, rebuilt ->
            saved.copy(title = rebuilt.title, text = rebuilt.text)
        }
        return IngredientLinker.link(original.ingredients, steps).steps
    }

    /** The recipe as a schema.org Recipe, the form Mealie reads from web pages. */
    internal fun schema(blueprint: RecipeBlueprint): JsonObject = buildJsonObject {
        val video = blueprint.video
        put("@context", "https://schema.org")
        put("@type", "Recipe")
        put("name", blueprint.name.ifBlank { video.title })
        if (blueprint.summary.isNotBlank()) put("description", blueprint.summary)
        video.thumbnailUrl?.let { put("image", it) }
        blueprint.servings?.let { put("recipeYield", it.toString()) }
        blueprint.prepMinutes?.let { put("prepTime", "PT${it}M") }
        blueprint.cookMinutes?.let { put("cookTime", "PT${it}M") }
        if (video.author.isNotBlank()) {
            putJsonObject("author") {
                put("@type", "Person")
                put("name", video.author)
            }
        }
        putJsonArray("recipeIngredient") { blueprint.ingredients.forEach { add(it) } }
        putJsonArray("recipeInstructions") {
            blueprint.steps.forEach { step ->
                add(
                    buildJsonObject {
                        put("@type", "HowToStep")
                        if (step.title.isNotBlank()) put("name", step.title)
                        put("text", step.text)
                    },
                )
            }
        }
        putJsonObject("video") {
            put("@type", "VideoObject")
            put("name", video.title)
            put("contentUrl", video.watchUrl)
            put("embedUrl", "https://www.youtube.com/embed/${video.id}")
            video.thumbnailUrl?.let { put("thumbnailUrl", it) }
        }
        put("url", video.watchUrl)
    }
}
