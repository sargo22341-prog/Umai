package org.opensources.umai.provider.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import org.opensources.umai.core.image.EncodedImage
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.network.api.MealieApi
import org.opensources.umai.core.network.apiCall
import org.opensources.umai.core.network.dto.ScrapeRecipeTestDto
import org.opensources.umai.provider.ProviderRegistry
import org.opensources.umai.recipe.data.RecipeMediaRepository
import org.opensources.umai.recipe.data.toDomain
import org.opensources.umai.recipe.domain.RecipeMediaFiles

/** Downloads a picture published by a provider. */
fun interface PhotoDownloader {
    /** The picture at [url], `null` when it cannot be had or is not a picture. */
    suspend fun download(url: String): EncodedImage?
}

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

/**
 * Downloads provider pictures with a client of its own: the requests go to the
 * provider, never to Mealie, and carry none of its credentials.
 */
class HttpPhotoDownloader(private val client: OkHttpClient) : PhotoDownloader {

    override suspend fun download(url: String): EncodedImage? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                val type = response.body.contentType()
                if (!response.isSuccessful || type?.type != "image") return@use null
                val length = response.body.contentLength()
                if (length > MAX_BYTES) return@use null
                val bytes = response.body.bytes().takeIf { it.isNotEmpty() && it.size <= MAX_BYTES } ?: return@use null
                val extension = when (type.subtype.lowercase()) {
                    "jpeg", "jpg", "pjpeg" -> "jpg"
                    "png" -> "png"
                    "webp" -> "webp"
                    "gif" -> "gif"
                    "avif" -> "avif"
                    else -> return@use null
                }
                EncodedImage(bytes, mediaType = "${type.type}/${type.subtype}", extension = extension)
            }
        }.getOrNull()
    }

    private companion object {
        const val MAX_BYTES = 15L * 1024 * 1024
    }
}
