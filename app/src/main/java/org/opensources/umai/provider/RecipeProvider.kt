package org.opensources.umai.provider

import androidx.annotation.StringRes
import kotlinx.serialization.json.JsonElement
import org.opensources.umai.recipe.domain.VideoManifest

/**
 * A recipe website whose pages publish more than Mealie keeps when it imports
 * them — typically a video cut step by step, or a photo per step.
 *
 * Everything specific to one website lives in its own implementation; the rest
 * of the app only knows this interface, so a provider is removed by deleting
 * its package and its line in [ProviderRegistry].
 */
interface RecipeProvider {

    /** Stable identifier, used to store the provider's settings. */
    val id: String

    /** The name of the website, which is a brand and is not translated. */
    val name: String

    /** What the provider brings, shown on its settings page. */
    @get:StringRes
    val descriptionRes: Int

    /** Whether the provider publishes a video of its recipes, or step photos only. */
    val offersVideo: Boolean

    /** Whether the page at [url] is a recipe of this provider. */
    fun handles(url: String): Boolean

    /**
     * The media of the recipe described by [schema], the schema.org data of the
     * page at [sourceUrl]; `null` when the schema holds no recipe.
     */
    fun media(schema: JsonElement, sourceUrl: String): ProviderMedia?
}

/**
 * What a provider publishes beyond the recipe itself.
 *
 * [stepPhotos] are real photos only, keyed by step number (`0` is the
 * ingredients, `1` the first step): a frame of the video is not a photo, and a
 * step without one simply has no entry.
 */
data class ProviderMedia(
    val video: VideoManifest?,
    val stepPhotos: Map<Int, String>,
)
