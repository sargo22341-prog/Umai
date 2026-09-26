package org.opensources.umai.core.network

/**
 * Builds media URLs for the instance that is currently configured, so screens
 * never have to know the server address. [baseUrl] is read on every call: the
 * instance can change while the app runs.
 */
class ImageUrlResolver(private val baseUrl: () -> String?) {

    fun thumbnail(recipeId: String, imageToken: String?): String? =
        url(recipeId, imageToken, MealieMedia.ImageSize.SMALL)

    fun medium(recipeId: String, imageToken: String?): String? =
        url(recipeId, imageToken, MealieMedia.ImageSize.MEDIUM)

    fun original(recipeId: String, imageToken: String?): String? =
        url(recipeId, imageToken, MealieMedia.ImageSize.ORIGINAL)

    fun recipeAsset(recipeId: String, fileName: String, version: String?): String? =
        baseUrl()?.let { MealieMedia.recipeAsset(it, recipeId, fileName, version) }

    fun stepImage(recipeId: String, source: String): String? =
        baseUrl()?.let { MealieMedia.resolveStepImage(it, recipeId, source) }

    fun userAvatar(userId: String, cacheKey: String?): String? =
        baseUrl()?.let { MealieMedia.userImage(it, userId, cacheKey) }

    private fun url(recipeId: String, imageToken: String?, size: MealieMedia.ImageSize): String? {
        if (imageToken == null) return null
        val base = baseUrl() ?: return null
        return MealieMedia.recipeImage(base, recipeId, size, imageToken)
    }
}
