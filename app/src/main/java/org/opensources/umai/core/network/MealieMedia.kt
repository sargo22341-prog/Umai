package org.opensources.umai.core.network

/**
 * Builds the `/api/media/...` URLs documented in the Mealie OpenAPI schema.
 *
 * Recipe pictures come in three renditions; Umai asks for the small one in
 * lists and the full one on the detail screen, and appends the recipe's image
 * token so a picture changed on the server is not served from the disk cache.
 */
object MealieMedia {

    enum class ImageSize(val fileName: String) {
        ORIGINAL("original.webp"),
        MEDIUM("min-original.webp"),
        SMALL("tiny-original.webp"),
    }

    fun recipeImage(
        baseUrl: String,
        recipeId: String,
        size: ImageSize,
        version: String?,
    ): String {
        val root = baseUrl.trimEnd('/')
        val suffix = version?.takeIf { it.isNotBlank() }?.let { "?version=$it" }.orEmpty()
        return "$root/api/media/recipes/$recipeId/images/${size.fileName}$suffix"
    }

    fun recipeAsset(baseUrl: String, recipeId: String, fileName: String): String =
        "${baseUrl.trimEnd('/')}/api/media/recipes/$recipeId/assets/$fileName"

    /**
     * Profile pictures are always stored as `profile.webp`. Mealie hands out a
     * cache key that changes on every upload, so the new picture is not served
     * from the disk cache.
     */
    fun userImage(baseUrl: String, userId: String, cacheKey: String?): String {
        val suffix = cacheKey?.takeIf { it.isNotBlank() }?.let { "?cacheKey=$it" }.orEmpty()
        return "${baseUrl.trimEnd('/')}/api/media/users/$userId/profile.webp$suffix"
    }

    /**
     * Step images are authored inside the step text and may be absolute,
     * root-relative (`/api/media/...`) or a bare asset file name.
     */
    fun resolveStepImage(baseUrl: String, recipeId: String, source: String): String = when {
        source.startsWith("http://", ignoreCase = true) ||
            source.startsWith("https://", ignoreCase = true) -> source
        source.startsWith("/") -> "${baseUrl.trimEnd('/')}$source"
        source.contains('/') -> "${baseUrl.trimEnd('/')}/${source.trimStart('/')}"
        else -> recipeAsset(baseUrl, recipeId, source)
    }
}
