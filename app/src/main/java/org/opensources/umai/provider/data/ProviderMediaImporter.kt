package org.opensources.umai.provider.data

import kotlinx.serialization.json.JsonObject
import org.opensources.umai.core.image.PhotoDownloader
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.api.MealieApi
import org.opensources.umai.core.network.call
import org.opensources.umai.core.network.dto.ScrapeRecipeTestDto
import org.opensources.umai.core.network.map
import org.opensources.umai.core.network.orInvalid
import org.opensources.umai.core.network.valueOr
import org.opensources.umai.provider.domain.ProviderRegistry
import org.opensources.umai.recipe.data.RecipeMediaRepository
import org.opensources.umai.recipe.data.toDomain
import org.opensources.umai.recipe.domain.RecipeMediaFiles

/**
 * Brings to a freshly imported recipe the media its provider publishes: the
 * chapters of its video and the photos of its steps. What the provider does
 * not give, or a photo that cannot be downloaded, is simply left out.
 */
class ProviderMediaImporter(
    private val apiProvider: () -> MealieApi?,
    private val registry: ProviderRegistry,
    private val media: RecipeMediaRepository,
    private val downloader: PhotoDownloader,
) {

    /** Fetches the media of the recipe [slug]; nothing happens when no provider handles its source. */
    suspend fun import(slug: String): ApiResult<Unit> {
        val recipe = apiProvider.call { recipe(slug) }.map { it.toDomain() }.orInvalid().valueOr { return it }
        val source = recipe.summary.sourceUrl ?: return ApiResult.Success(Unit)
        val provider = registry.forUrl(source) ?: return ApiResult.Success(Unit)

        // Mealie answers with a sentence when it finds no recipe on the page.
        val schema = apiProvider.call { testScrapeUrl(ScrapeRecipeTestDto(source)) }.valueOr { return it } as? JsonObject
        val found = schema?.let { provider.media(it, source) } ?: return ApiResult.Success(Unit)

        if (found.video != null && RecipeMediaFiles.chaptersFile(recipe.assets) == null) {
            media.saveVideoManifest(slug, found.video).valueOr { return it }
        }
        val photos = RecipeMediaFiles.stepPhotos(recipe.assets).keys
        found.stepPhotos
            .filterKeys { number -> number !in photos && number <= recipe.steps.size }
            .forEach { (number, url) -> downloader.download(url)?.let { media.saveStepPhoto(slug, number, it) } }
        return ApiResult.Success(Unit)
    }
}
