package org.opensources.umai.provider.marmiton

import kotlinx.serialization.json.JsonElement
import org.opensources.umai.R
import org.opensources.umai.provider.ProviderMedia
import org.opensources.umai.provider.RecipeProvider
import org.opensources.umai.provider.schema.SchemaOrgRecipe
import java.net.URI

/**
 * Marmiton (marmiton.org). A step that has a photo gives it as the `image` of
 * the step in the schema.org data of the page.
 *
 * Marmiton serves its pictures from `assets.afcdn.com` in many sizes, named
 * after the picture: `…/67769_w40h40c1cx350cy350.webp` is a thumbnail, and
 * `…/67769_origin.jpg` the original, which may weigh several megabytes. Every
 * size is asked for as `…/67769_w1024.webp`: wide enough for a step, uncropped.
 */
object MarmitonProvider : RecipeProvider {

    override val id: String = "marmiton"
    override val name: String = "Marmiton"
    override val descriptionRes: Int = R.string.provider_marmiton_description
    override val offersVideo: Boolean = false

    private val recipePath = Regex("""^/recettes/recette_[^/]+\.aspx$""", RegexOption.IGNORE_CASE)
    private val sizedPicture = Regex("""^(/recipe/\d+/\d+)_[^/.]+\.(jpe?g|webp)$""", RegexOption.IGNORE_CASE)

    override fun handles(url: String): Boolean = runCatching {
        val uri = URI(url.trim())
        val host = uri.host?.lowercase() ?: return false
        (host == "marmiton.org" || host.endsWith(".marmiton.org")) && recipePath.matches(uri.path.orEmpty())
    }.getOrDefault(false)

    override fun media(schema: JsonElement, sourceUrl: String): ProviderMedia? {
        val recipe = SchemaOrgRecipe.find(schema) ?: return null
        return ProviderMedia(video = null, stepPhotos = SchemaOrgRecipe.stepPhotos(recipe, sourceUrl, ::stepSize))
    }

    /** The picture at [url] in the size kept for a step; any other address is left as is. */
    fun stepSize(url: String): String = runCatching {
        val uri = URI(url)
        if (uri.host?.lowercase() != PICTURE_HOST) return url
        val path = sizedPicture.matchEntire(uri.path.orEmpty()) ?: return url
        val (picture, extension) = path.destructured
        URI(uri.scheme, uri.authority, "${picture}_w$STEP_WIDTH.$extension", null, null).toString()
    }.getOrDefault(url)

    private const val PICTURE_HOST = "assets.afcdn.com"
    private const val STEP_WIDTH = 1024
}
