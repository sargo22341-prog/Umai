package org.opensources.umai.core.image

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.opensources.umai.core.network.bytesUpTo
import java.io.IOException

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
        val request = try {
            Request.Builder().url(url).build()
        } catch (_: IllegalArgumentException) {
            // Not an http(s) address.
            return@withContext null
        }
        try {
            client.newCall(request).execute().use { response ->
                val type = response.body.contentType()
                if (!response.isSuccessful || type?.type != "image") return@use null
                val extension = when (type.subtype.lowercase()) {
                    "jpeg", "jpg", "pjpeg" -> "jpg"
                    "png" -> "png"
                    "webp" -> "webp"
                    "gif" -> "gif"
                    "avif" -> "avif"
                    else -> return@use null
                }
                if (response.body.contentLength() > MAX_BYTES) return@use null
                // Read up to the limit only: a body with no announced length could be any size.
                val bytes = response.body.bytesUpTo(MAX_BYTES).takeIf { it.isNotEmpty() } ?: return@use null
                EncodedImage(bytes, mediaType = "${type.type}/${type.subtype}", extension = extension)
            }
        } catch (_: IOException) {
            // Unreachable, cut short, or longer than MAX_BYTES.
            null
        }
    }

    private companion object {
        const val MAX_BYTES = 15L * 1024 * 1024
    }
}
