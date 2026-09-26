package org.opensources.umai.provider.data

import kotlinx.serialization.json.JsonObject
import org.opensources.umai.core.image.PhotoDownloader
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.network.api.MealieApi
import org.opensources.umai.core.network.apiCall
import org.opensources.umai.core.network.dto.ScrapeRecipeTestDto
import org.opensources.umai.provider.ProviderRegistry
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
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        val recipe = when (val result = apiCall { api.recipe(slug) }) {
            is ApiResult.Failure -> return result
            is ApiResult.Success -> result.value.toDomain() ?: return ApiResult.Failure(NetworkError.InvalidResponse)
        }
        val source = recipe.summary.sourceUrl ?: return ApiResult.Success(Unit)
        val provider = registry.forUrl(source) ?: return ApiResult.Success(Unit)

        val schema = when (val result = apiCall { api.testScrapeUrl(ScrapeRecipeTestDto(source)) }) {
            is ApiResult.Failure -> return result
            // Mealie answers with a sentence when it finds no recipe on the page.
            is ApiResult.Success -> result.value as? JsonObject
        }
        val found = schema?.let { provider.media(it, source) } ?: return ApiResult.Success(Unit)

        if (found.video != null && RecipeMediaFiles.chaptersFile(recipe.assets) == null) {
            val saved = media.saveVideoManifest(slug, found.video)
            if (saved is ApiResult.Failure) return saved
        }
        val photos = RecipeMediaFiles.stepPhotos(recipe.assets).keys
        found.stepPhotos
            .filterKeys { number -> number !in photos && number <= recipe.steps.size }
            .forEach { (number, url) -> downloader.download(url)?.let { media.saveStepPhoto(slug, number, it) } }
        return ApiResult.Success(Unit)
    }
}
