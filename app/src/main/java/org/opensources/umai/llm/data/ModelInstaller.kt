package org.opensources.umai.llm.data

import android.app.DownloadManager
import android.content.Context
import androidx.core.net.toUri
import android.os.StatFs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.opensources.umai.R
import org.opensources.umai.llm.domain.LocalModel
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/** Why a model could not be installed. */
enum class InstallFailure { NO_STORAGE, NOT_ENOUGH_SPACE, DOWNLOAD_FAILED, CORRUPTED, NOT_A_MODEL }

/** Where the download of a model stands. */
sealed interface InstallState {
    data object Idle : InstallState

    data class Downloading(
        val model: LocalModel,
        val downloaded: Long,
        val total: Long,
        /** The download waits for a Wi-Fi network, or for the network to come back. */
        val waiting: Boolean,
    ) : InstallState

    data class Verifying(val model: LocalModel, val fraction: Float) : InstallState

    data class Failed(val model: LocalModel, val failure: InstallFailure) : InstallState
}

/**
 * Downloads model files with the system's download manager, which resumes a
 * download of several gigabytes across network changes and the app being
 * closed, then checks the file before it is used.
 *
 * Only one model is kept: once a new one is checked, the previous file is
 * deleted, since each takes gigabytes.
 */
class ModelInstaller(
    context: Context,
    private val store: LocalAiSettingsStore,
    private val scope: CoroutineScope,
) {

    private val context = context.applicationContext
    private val downloads = this.context.getSystemService(DownloadManager::class.java)
    private val mutex = Mutex()
    private var watchJob: Job? = null

    private val _state = MutableStateFlow<InstallState>(InstallState.Idle)
    val state: StateFlow<InstallState> = _state.asStateFlow()

    /** The models live in the app's own external files: removed with the app, no permission needed. */
    private val modelsDir: File? get() = context.getExternalFilesDir(MODELS_DIR)

    fun fileOf(model: LocalModel): File? = modelsDir?.resolve(model.fileName)

    /** Follows a download started earlier, possibly before the app was last closed. */
    fun resume() {
        if (watchJob?.isActive == true) return
        watchJob = scope.launch { watch() }
    }

    /** Starts downloading [model]; the state tells when it is installed or why it failed. */
    suspend fun install(model: LocalModel) = mutex.withLock {
        store.current().pending?.let { downloads.remove(it.downloadId) }
        val dir = modelsDir
        if (dir == null) {
            _state.value = InstallState.Failed(model, InstallFailure.NO_STORAGE)
            return@withLock
        }
        dir.mkdirs()
        if (model.sizeBytes > 0 && StatFs(dir.path).availableBytes < model.sizeBytes + SPACE_MARGIN) {
            _state.value = InstallState.Failed(model, InstallFailure.NOT_ENOUGH_SPACE)
            return@withLock
        }
        partFile(dir, model).delete()
        val request = DownloadManager.Request(model.url.toUri())
            .setTitle(model.name)
            .setDescription(context.getString(R.string.local_ai_download_description))
            .setDestinationUri(partFile(dir, model).toUri())
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            // Several gigabytes: never over mobile data.
            .setAllowedOverMetered(false)
            .setAllowedOverRoaming(false)
        val id = downloads.enqueue(request)
        store.setPending(PendingModel(id, model))
        _state.value = InstallState.Downloading(model, 0L, model.sizeBytes, waiting = false)
        watchJob?.cancel()
        watchJob = scope.launch { watch() }
    }

    suspend fun cancel() = mutex.withLock {
        watchJob?.cancel()
        store.current().pending?.let { pending ->
            downloads.remove(pending.downloadId)
            modelsDir?.let { partFile(it, pending.model).delete() }
        }
        store.setPending(null)
        _state.value = InstallState.Idle
    }

    /** Deletes the installed model file. */
    suspend fun uninstall() = mutex.withLock {
        store.current().installed?.let { model -> fileOf(model)?.delete() }
        store.setInstalled(null)
    }

    fun dismissFailure() {
        if (_state.value is InstallState.Failed) _state.value = InstallState.Idle
    }

    private suspend fun watch() {
        while (scope.isActive) {
            val pending = store.current().pending ?: run {
                if (_state.value !is InstallState.Failed) _state.value = InstallState.Idle
                return
            }
            val progress = query(pending.downloadId)
            when (progress?.status) {
                null, DownloadManager.STATUS_FAILED -> {
                    finishWith(pending, InstallFailure.DOWNLOAD_FAILED)
                    return
                }
                DownloadManager.STATUS_SUCCESSFUL -> {
                    complete(pending)
                    return
                }
                else -> _state.value = InstallState.Downloading(
                    model = pending.model,
                    downloaded = progress.downloaded,
                    total = progress.total.takeIf { it > 0 } ?: pending.model.sizeBytes,
                    waiting = progress.status == DownloadManager.STATUS_PAUSED,
                )
            }
            delay(POLL_MS)
        }
    }

    private suspend fun complete(pending: PendingModel) {
        val dir = modelsDir ?: return finishWith(pending, InstallFailure.NO_STORAGE)
        val part = partFile(dir, pending.model)
        val failure = withContext(Dispatchers.IO) { check(part, pending.model) }
        if (failure != null) {
            part.delete()
            return finishWith(pending, failure)
        }
        mutex.withLock {
            val previous = store.current().installed
            val target = dir.resolve(pending.model.fileName)
            if (!part.renameTo(target)) return@withLock finishWith(pending, InstallFailure.NO_STORAGE)
            if (previous != null && previous.fileName != pending.model.fileName) fileOf(previous)?.delete()
            store.setInstalled(pending.model)
            store.setPending(null)
            _state.value = InstallState.Idle
        }
    }

    private suspend fun finishWith(pending: PendingModel, failure: InstallFailure) {
        downloads.remove(pending.downloadId)
        store.setPending(null)
        _state.value = InstallState.Failed(pending.model, failure)
    }

    /** A catalog model must match its hash; any model must at least be a GGUF file. */
    private fun check(file: File, model: LocalModel): InstallFailure? {
        if (!file.isFile) return InstallFailure.DOWNLOAD_FAILED
        val magic = ByteArray(4)
        val read = FileInputStream(file).use { it.read(magic) }
        if (read != 4 || String(magic, Charsets.US_ASCII) != GGUF_MAGIC) return InstallFailure.NOT_A_MODEL
        val expected = model.sha256 ?: return null
        val digest = MessageDigest.getInstance("SHA-256")
        val total = file.length().coerceAtLeast(1L)
        var done = 0L
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
                done += count
                _state.value = InstallState.Verifying(model, done.toFloat() / total)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return if (actual.equals(expected, ignoreCase = true)) null else InstallFailure.CORRUPTED
    }

    private data class Progress(val status: Int, val downloaded: Long, val total: Long)

    private fun query(id: Long): Progress? =
        downloads.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            Progress(
                status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
            )
        }

    private fun partFile(dir: File, model: LocalModel) = dir.resolve("${model.fileName}.part")

    private companion object {
        const val MODELS_DIR = "models"
        const val GGUF_MAGIC = "GGUF"
        const val POLL_MS = 1_000L
        const val BUFFER = 1 shl 20
        const val SPACE_MARGIN = 512L * 1024 * 1024
    }
}
