package org.opensources.umai.recipe.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.opensources.umai.core.image.EncodedImage
import org.opensources.umai.core.model.RecipeAsset
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.network.api.MealieApi
import org.opensources.umai.core.network.apiCall
import org.opensources.umai.recipe.domain.RecipeMediaFiles
import org.opensources.umai.recipe.domain.VideoManifest
import java.text.Normalizer

/**
 * The media a recipe keeps as assets on Mealie: the chapters of its video and
 * the photos of its steps (see [RecipeMediaFiles]).
 */
class RecipeMediaRepository(private val apiProvider: () -> MealieApi?) {

    /** The chapters file of the recipe, `null` when it has none or it cannot be read as one. */
    suspend fun videoManifest(recipeId: String, assets: List<RecipeAsset>): ApiResult<VideoManifest?> {
        val fileName = RecipeMediaFiles.chaptersFile(assets) ?: return ApiResult.Success(null)
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        return apiCall { VideoManifestJson.parse(api.recipeAsset(recipeId, fileName).string()) }
    }

    /** Writes the chapters file of a recipe that has none. */
    suspend fun saveVideoManifest(slug: String, manifest: VideoManifest): ApiResult<Unit> {
        val name = "${fileSafe(manifest.title).ifBlank { slug }}${RecipeMediaFiles.CHAPTERS_SUFFIX}"
        val bytes = VideoManifestJson.write(manifest).toByteArray()
        return upload(slug, name, icon = "file-json", extension = "json", bytes, JSON)
    }

    /** Stores [image] as the photo of step [stepNumber], replacing the one it had. */
    suspend fun saveStepPhoto(slug: String, stepNumber: Int, image: EncodedImage): ApiResult<Unit> = upload(
        slug = slug,
        name = RecipeMediaFiles.stepPhotoName(stepNumber),
        icon = "file-image",
        extension = image.extension.lowercase().ifBlank { "jpg" },
        bytes = image.bytes,
        mediaType = image.mediaType.toMediaType(),
    )

    /** The content of an asset of the recipe, to store it again under another name. */
    suspend fun assetBytes(recipeId: String, fileName: String): ApiResult<ByteArray> {
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        return apiCall { api.recipeAsset(recipeId, fileName).bytes() }
    }

    /**
     * Rewrites the list of assets of the recipe: one entry per file, and the
     * step photos limited to [keptStepPhotos] when it is given — the photo of
     * the ingredients, `step-0`, is never touched. Mealie appends an entry on
     * every upload, even when the file itself is replaced.
     */
    suspend fun tidyAssets(slug: String, keptStepPhotos: Set<String>? = null): ApiResult<Unit> {
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        val document = when (val result = apiCall { api.recipeDocument(slug) }) {
            is ApiResult.Failure -> return result
            is ApiResult.Success -> result.value
        }
        val assets = (document["assets"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
        val tidy = assets
            .distinctBy { it.fileName() ?: it.toString() }
            .filter { asset ->
                val fileName = asset.fileName()
                val number = RecipeMediaFiles.stepNumberOf(fileName)
                keptStepPhotos == null || number == null || number == 0 || fileName in keptStepPhotos
            }
        if (tidy.size == assets.size) return ApiResult.Success(Unit)
        return apiCall { api.replaceRecipe(slug, JsonObject(document + ("assets" to JsonArray(tidy)))) }
            .let { if (it is ApiResult.Failure) it else ApiResult.Success(Unit) }
    }

    private suspend fun upload(
        slug: String,
        name: String,
        icon: String,
        extension: String,
        bytes: ByteArray,
        mediaType: okhttp3.MediaType,
    ): ApiResult<Unit> {
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        val file = MultipartBody.Part.createFormData(
            name = "file",
            filename = "$name.$extension",
            body = bytes.toRequestBody(mediaType),
        )
        return apiCall {
            api.uploadRecipeAsset(
                slug = slug,
                name = name.toRequestBody(TEXT),
                icon = icon.toRequestBody(TEXT),
                extension = extension.toRequestBody(TEXT),
                file = file,
            )
        }.let { if (it is ApiResult.Failure) it else ApiResult.Success(Unit) }
    }

    private fun JsonObject.fileName(): String? = get("fileName")?.jsonPrimitive?.contentOrNull

    private companion object {
        val TEXT = "text/plain".toMediaType()
        val JSON = "application/json".toMediaType()

        /** The name Mealie itself would give: lower case, no accents, words joined by dashes. */
        fun fileSafe(title: String): String =
            Normalizer.normalize(title, Normalizer.Form.NFD)
                .replace(Regex("""\p{Mn}+"""), "")
                .lowercase()
                .replace(Regex("""[^a-z0-9]+"""), "-")
                .trim('-')
    }
}

private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()
