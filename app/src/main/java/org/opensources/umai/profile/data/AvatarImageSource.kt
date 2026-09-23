package org.opensources.umai.profile.data

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** A picture ready to be uploaded to Mealie. */
class AvatarImage(val bytes: ByteArray, val mediaType: String, val fileName: String)

/**
 * Where a new profile picture comes from.
 *
 * The repository depends on this rather than on a `Context`, so it stays
 * testable on the JVM and knows nothing about content resolvers.
 */
interface AvatarImageReader {
    /** `null` when the picture cannot be read. */
    suspend fun read(uri: String): AvatarImage?
}

/**
 * Reads the picture the user picked with the system photo picker.
 *
 * The ViewModel never sees a `Context` either: it hands over the URI it
 * received from the picker and the bytes come back from here.
 */
class AvatarImageSource(context: Context) : AvatarImageReader {

    private val appContext = context.applicationContext

    /** `null` when the URI cannot be opened, is empty, or is too large. */
    override suspend fun read(uri: String): AvatarImage? = withContext(Dispatchers.IO) {
        val parsed: Uri = runCatching { uri.toUri() }.getOrNull() ?: return@withContext null
        val resolver = appContext.contentResolver
        val mediaType = runCatching { resolver.getType(parsed) }.getOrNull() ?: DEFAULT_MEDIA_TYPE
        val bytes = runCatching {
            resolver.openInputStream(parsed)?.use { it.readAtMost(MAX_BYTES) }
        }.getOrNull() ?: return@withContext null

        if (bytes.isEmpty()) return@withContext null
        AvatarImage(bytes = bytes, mediaType = mediaType, fileName = fileNameFor(mediaType))
    }

    private fun fileNameFor(mediaType: String): String = when {
        mediaType.endsWith("/png") -> "profile.png"
        mediaType.endsWith("/webp") -> "profile.webp"
        else -> "profile.jpg"
    }

    /** Stops at [limit] bytes so a huge picture cannot exhaust memory. */
    private fun InputStream.readAtMost(limit: Int): ByteArray? {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(CHUNK_SIZE)
        while (true) {
            val read = read(chunk)
            if (read <= 0) break
            if (buffer.size() + read > limit) return null
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    private companion object {
        const val DEFAULT_MEDIA_TYPE = "image/jpeg"
        const val CHUNK_SIZE = 16 * 1024

        /** Mealie re-encodes the picture server-side; 8 MiB is already generous. */
        const val MAX_BYTES = 8 * 1024 * 1024
    }
}
