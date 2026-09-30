package org.opensources.umai.core.download

import android.app.DownloadManager
import android.content.Context
import androidx.core.net.toUri
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/** Why a model could not be installed. */
enum class InstallFailure { NO_STORAGE, NOT_ENOUGH_SPACE, DOWNLOAD_FAILED, CORRUPTED, NOT_A_MODEL }

/** Where one download stands. */
data class DownloadProgress(
    val state: State,
    val downloaded: Long,
    /** 0 until the server tells the size. */
    val total: Long,
) {
    enum class State { RUNNING, WAITING, DONE, FAILED }
}

/**
 * Model files, hundreds of megabytes to gigabytes, downloaded by the system's
 * download manager: it resumes across network changes and the app being
 * closed, shows a notification, and never uses mobile data.
 */
class WifiDownloads(context: Context) : FileDownloads {

    private val manager = context.applicationContext.getSystemService(DownloadManager::class.java)

    override fun enqueue(url: String, destination: File, title: String, description: String): Long =
        manager.enqueue(
            DownloadManager.Request(url.toUri())
                .setTitle(title)
                .setDescription(description)
                .setDestinationUri(destination.toUri())
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setAllowedOverMetered(false)
                .setAllowedOverRoaming(false),
        )

    override fun progress(id: Long): DownloadProgress? =
        manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val state = when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_SUCCESSFUL -> DownloadProgress.State.DONE
                DownloadManager.STATUS_FAILED -> DownloadProgress.State.FAILED
                DownloadManager.STATUS_PAUSED -> DownloadProgress.State.WAITING
                else -> DownloadProgress.State.RUNNING
            }
            DownloadProgress(
                state = state,
                downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
            )
        }

    override fun remove(id: Long) {
        manager.remove(id)
    }

    companion object {
        private const val BUFFER = 1 shl 20

        /** The SHA-256 of [file] in hexadecimal; [onRead] is told the bytes read so far. */
        fun sha256(file: File, onRead: (Long) -> Unit = {}): String {
            val digest = MessageDigest.getInstance("SHA-256")
            var done = 0L
            FileInputStream(file).use { input ->
                val buffer = ByteArray(BUFFER)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                    done += count
                    onRead(done)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
