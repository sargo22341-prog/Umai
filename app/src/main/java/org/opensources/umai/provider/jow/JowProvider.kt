package org.opensources.umai.provider.jow

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.opensources.umai.R
import org.opensources.umai.provider.ProviderMedia
import org.opensources.umai.provider.RecipeProvider
import org.opensources.umai.provider.schema.SchemaOrgRecipe
import org.opensources.umai.recipe.domain.RecipeMediaFiles
import org.opensources.umai.recipe.domain.VideoChapter
import org.opensources.umai.recipe.domain.VideoManifest
import java.net.URI
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Jow (jow.fr) publishes a video for most recipes, and in the schema of each
 * step a `Clip` giving where that step starts and ends in it.
 *
 * The video file is the `contentUrl` of the recipe's `VideoObject`; `url` points
 * back at the recipe page. Older pages carried no clips but a step "image"
 * whose address ended in `#t=<seconds>`: that is a moment of the video, used
 * for the chapters, and never kept as a photo of the step.
 */
object JowProvider : RecipeProvider {

    override val id: String = "jow"
    override val name: String = "Jow"
    override val descriptionRes: Int = R.string.provider_jow_description

    private val recipePath = Regex("""^/(?:[a-z]{2}/)?recipes/[^/]+""")
    private val timeMark = Regex("""[#?&]t=([\d.]+)""", RegexOption.IGNORE_CASE)

    /** A chapter ends slightly before the next one starts, as the source cuts them. */
    private const val CHAPTER_GAP = 0.3

    override fun handles(url: String): Boolean = runCatching {
        val uri = URI(url.trim())
        val host = uri.host?.lowercase() ?: return false
        (host == "jow.fr" || host.endsWith(".jow.fr")) && recipePath.containsMatchIn(uri.path.orEmpty())
    }.getOrDefault(false)

    override fun media(schema: JsonElement, sourceUrl: String): ProviderMedia? {
        val recipe = SchemaOrgRecipe.find(schema) ?: return null
        val steps = SchemaOrgRecipe.steps(recipe)
        val videoUrl = videoUrl(recipe, sourceUrl)

        val chapters = videoUrl?.let { chapters(steps, sourceUrl) }.orEmpty()
        val video = if (videoUrl != null && chapters.isNotEmpty()) {
            VideoManifest(
                title = SchemaOrgRecipe.text(recipe, "name").orEmpty(),
                sourceUrl = sourceUrl,
                videoUrl = videoUrl,
                chapters = chapters,
            )
        } else {
            null
        }
        return ProviderMedia(video = video, stepPhotos = stepPhotos(recipe, steps, sourceUrl, videoUrl))
    }

    private fun videoUrl(recipe: JsonObject, base: String): String? {
        val videos = when (val value = recipe["video"]) {
            is JsonArray -> value.filterIsInstance<JsonObject>()
            is JsonObject -> listOf(value)
            else -> emptyList()
        }
        return (
            videos.firstNotNullOfOrNull { SchemaOrgRecipe.url(it["contentUrl"], base) }
                ?: videos.firstNotNullOfOrNull { video -> SchemaOrgRecipe.url(video, base)?.takeUnless(::handles) }
            )?.withoutTimeMark()
    }

    private fun chapters(steps: List<JsonElement>, base: String): List<VideoChapter> {
        val fromClips = steps.mapIndexedNotNull { index, step ->
            val clip = when (val value = (step as? JsonObject)?.get("video")) {
                is JsonArray -> value.filterIsInstance<JsonObject>().firstOrNull()
                is JsonObject -> value
                else -> null
            } ?: return@mapIndexedNotNull null
            val start = SchemaOrgRecipe.number(clip, "startOffset")?.takeIf { it >= 0 }
                ?: SchemaOrgRecipe.text(clip, "url")?.timeMark()
                ?: return@mapIndexedNotNull null
            val end = SchemaOrgRecipe.number(clip, "endOffset")?.takeIf { it > start }
            VideoChapter(stepIndex = index, start = start.tenth(), end = end?.tenth())
        }
        val starts = fromClips.ifEmpty {
            steps.mapIndexedNotNull { index, step ->
                val image = (step as? JsonObject)?.let { SchemaOrgRecipe.url(it["image"], base) }
                image?.timeMark()?.let { VideoChapter(stepIndex = index, start = it.tenth(), end = null) }
            }
        }.sortedBy { it.start }
        if (starts.isEmpty()) return emptyList()

        val ingredients = VideoChapter(stepIndex = -1, start = 0.0, end = max(0.0, starts.first().start - CHAPTER_GAP).tenth())
        return listOf(ingredients) + starts.mapIndexed { index, chapter ->
            val next = starts.getOrNull(index + 1)
            chapter.copy(end = chapter.end ?: next?.let { max(chapter.start, it.start - CHAPTER_GAP).tenth() })
        }
    }

    /**
     * Photos the page gives for its steps. A moment of the video, the video
     * itself, or one of the pictures of the whole recipe is not a photo of the
     * step, and is left out: such a step gets no photo at all.
     */
    private fun stepPhotos(recipe: JsonObject, steps: List<JsonElement>, base: String, videoUrl: String?): Map<Int, String> {
        val recipePictures = SchemaOrgRecipe.urls(recipe["image"], base).toSet() +
            (recipe["video"]?.let { video -> collectThumbnails(video, base) }.orEmpty())
        return steps.mapIndexedNotNull { index, step ->
            val image = (step as? JsonObject)?.let { SchemaOrgRecipe.url(it["image"], base) } ?: return@mapIndexedNotNull null
            val isVideo = image.timeMark() != null ||
                RecipeMediaFiles.isVideo(image.substringBefore('?').substringBefore('#')) ||
                image.withoutTimeMark() == videoUrl
            if (isVideo || image in recipePictures) null else (index + 1) to image
        }.toMap()
    }

    private fun collectThumbnails(video: JsonElement, base: String): List<String> = when (video) {
        is JsonArray -> video.flatMap { collectThumbnails(it, base) }
        is JsonObject -> SchemaOrgRecipe.urls(video["thumbnailUrl"], base)
        is JsonPrimitive -> emptyList()
    }

    private fun String.timeMark(): Double? = timeMark.find(this)?.groupValues?.get(1)?.toDoubleOrNull()

    private fun String.withoutTimeMark(): String = replace(Regex("""[#?]t=[\d.]+$""", RegexOption.IGNORE_CASE), "")

    private fun Double.tenth(): Double = (this * 10).roundToLong() / 10.0
}
