package org.opensources.umai.provider.site750g

import kotlinx.serialization.json.JsonElement
import org.opensources.umai.R
import org.opensources.umai.provider.domain.ProviderMedia
import org.opensources.umai.provider.domain.RecipeProvider
import org.opensources.umai.provider.domain.isPageOf
import org.opensources.umai.provider.schema.SchemaOrgRecipe

/**
 * 750g (750g.com). Its step-by-step recipes ("pas à pas") give a photo for
 * each step as the `image` of the step in their schema.org data; the other
 * recipes give none, and are imported as Mealie scrapes them.
 */
object Site750gProvider : RecipeProvider {

    override val id: String = "750g"
    override val name: String = "750g"
    override val descriptionRes: Int = R.string.provider_750g_description
    override val offersVideo: Boolean = false

    /** Recipe pages end with their number: `/pavlova-aux-fruits-rouges-r204378.htm`. */
    private val recipePath = Regex("""-r\d+\.htm$""")

    override fun handles(url: String): Boolean = isPageOf(url, domain = "750g.com", path = recipePath)

    override fun media(schema: JsonElement, sourceUrl: String): ProviderMedia? {
        val recipe = SchemaOrgRecipe.find(schema) ?: return null
        return ProviderMedia(video = null, stepPhotos = SchemaOrgRecipe.stepPhotos(recipe, sourceUrl))
    }
}
