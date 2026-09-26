package org.opensources.umai.core.image

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Downloads a picture published by another website: a recipe provider, a food database. */
fun interface PhotoDownloader {
    /** The picture at [url], `null` when it cannot be had or is not a picture. */
    suspend fun download(url: String): EncodedImage?
}

/**
 * Downloads pictures with the client for other websites: the requests never go
 * to Mealie, and carry none of its credentials.
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
