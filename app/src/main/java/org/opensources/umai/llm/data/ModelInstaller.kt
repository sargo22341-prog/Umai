package org.opensources.umai.llm.data

import android.app.DownloadManager
import android.content.Context
import android.os.StatFs
import androidx.core.net.toUri
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
import org.opensources.umai.llm.domain.ModelFile
import org.opensources.umai.llm.domain.TensorChip
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
 * closed, then checks the files before they are used. A phone with a Tensor
 * TPU gets the TPU build of the model next to the file every phone runs.
 *
 * Only one model is kept: once a new one is checked, the files of the previous
 * one are deleted, since each takes gigabytes.
 */
class ModelInstaller(
    context: Context,
    private val store: LocalAiSettingsStore,
    private val chip: TensorChip?,
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

    private fun fileOf(file: ModelFile): File? = modelsDir?.resolve(file.fileName)

    /** The installed model and the paths of its files, when the local AI is on. */
    suspend fun installedModel(): InstalledModel? {
        val settings = store.current()
        if (!settings.enabled) return null
        val model = settings.installed ?: return null
        val paths = model.filesFor(chip).mapNotNull { file ->
            fileOf(file)?.takeIf { it.isFile }?.let { file to it.path }
        }.toMap()
        return InstalledModel(model, paths)
    }

    /** Follows a download started earlier, possibly before the app was last closed. */
    fun resume() {
        if (watchJob?.isActive == true) return
        watchJob = scope.launch {
            deleteGgufModels()
            watch()
        }
    }

    /** Starts downloading [model]; the state tells when it is installed or why it failed. */
    suspend fun install(model: LocalModel) = mutex.withLock {
        store.current().pending?.let { removeDownloads(it) }
        val dir = modelsDir
        if (dir == null) {
            _state.value = InstallState.Failed(model, InstallFailure.NO_STORAGE)
            return@withLock
        }
        dir.mkdirs()
        val files = model.filesFor(chip)
        val size = files.sumOf { it.sizeBytes }
        if (size > 0 && StatFs(dir.path).availableBytes < size + SPACE_MARGIN) {
            _state.value = InstallState.Failed(model, InstallFailure.NOT_ENOUGH_SPACE)
            return@withLock
        }
        val ids = files.map { file ->
            partFile(dir, file).delete()
            val request = DownloadManager.Request(file.url.toUri())
                .setTitle(model.name)
                .setDescription(context.getString(R.string.local_ai_download_description))
                .setDestinationUri(partFile(dir, file).toUri())
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                // Gigabytes: never over mobile data.
                .setAllowedOverMetered(false)
                .setAllowedOverRoaming(false)
            downloads.enqueue(request)
        }
        store.setPending(PendingModel(ids, model))
        _state.value = InstallState.Downloading(model, 0L, size, waiting = false)
        watchJob?.cancel()
        watchJob = scope.launch { watch() }
    }

    suspend fun cancel() = mutex.withLock {
        watchJob?.cancel()
        store.current().pending?.let { removeDownloads(it) }
        store.setPending(null)
        _state.value = InstallState.Idle
    }

    /** Deletes the files of the installed model. */
    suspend fun uninstall() = mutex.withLock {
        store.current().installed?.let { model -> model.files.forEach { fileOf(it)?.delete() } }
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
            val progress = pending.downloadIds.map(::query)
            when {
                progress.any { it == null || it.status == DownloadManager.STATUS_FAILED } -> {
                    finishWith(pending, InstallFailure.DOWNLOAD_FAILED)
                    return
                }
                progress.all { it?.status == DownloadManager.STATUS_SUCCESSFUL } -> {
                    complete(pending)
                    return
                }
                else -> {
                    val files = pending.model.filesFor(chip)
                    _state.value = InstallState.Downloading(
                        model = pending.model,
                        downloaded = progress.sumOf { it?.downloaded ?: 0L },
                        total = progress.zip(files) { p, file -> p?.total?.takeIf { it > 0 } ?: file.sizeBytes }.sum(),
                        waiting = progress.any { it?.status == DownloadManager.STATUS_PAUSED },
                    )
                }
            }
            delay(POLL_MS)
        }
    }

    private suspend fun complete(pending: PendingModel) {
        val dir = modelsDir ?: return finishWith(pending, InstallFailure.NO_STORAGE)
        val files = pending.model.filesFor(chip)
        val failure = withContext(Dispatchers.IO) { check(dir, files, pending.model) }
        if (failure != null) return finishWith(pending, failure)
        mutex.withLock {
            val previous = store.current().installed
            if (files.any { !partFile(dir, it).renameTo(dir.resolve(it.fileName)) }) {
                return@withLock finishWith(pending, InstallFailure.NO_STORAGE)
            }
            val kept = files.map { it.fileName }.toSet()
            previous?.files?.filter { it.fileName !in kept }?.forEach { fileOf(it)?.delete() }
            store.setInstalled(pending.model)
            store.setPending(null)
            _state.value = InstallState.Idle
        }
    }

    private suspend fun finishWith(pending: PendingModel, failure: InstallFailure) {
        removeDownloads(pending)
        store.setPending(null)
        _state.value = InstallState.Failed(pending.model, failure)
    }

    private fun removeDownloads(pending: PendingModel) {
        pending.downloadIds.forEach { downloads.remove(it) }
        modelsDir?.let { dir -> pending.model.files.forEach { partFile(dir, it).delete() } }
    }

    /** Every file must be a LiteRT-LM model, and a catalog file must match its hash. */
    private fun check(dir: File, files: List<ModelFile>, model: LocalModel): InstallFailure? {
        val total = files.sumOf { partFile(dir, it).length() }.coerceAtLeast(1L)
        var done = 0L
        val digest = MessageDigest.getInstance("SHA-256")
        for (file in files) {
            val part = partFile(dir, file)
            if (!part.isFile) return InstallFailure.DOWNLOAD_FAILED
            val magic = ByteArray(LITERTLM_MAGIC.length)
            val read = FileInputStream(part).use { it.read(magic) }
            if (read != magic.size || String(magic, Charsets.US_ASCII) != LITERTLM_MAGIC) return InstallFailure.NOT_A_MODEL
            val expected = file.sha256
            if (expected == null) {
                done += part.length()
                continue
            }
            digest.reset()
            FileInputStream(part).use { input ->
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
            if (!actual.equals(expected, ignoreCase = true)) return InstallFailure.CORRUPTED
        }
        return null
    }

    /** The GGUF files of the llama.cpp runtime umai used before LiteRT-LM, unreadable now. */
    private fun deleteGgufModels() {
        modelsDir?.listFiles { file -> file.name.endsWith(".gguf", ignoreCase = true) }?.forEach { it.delete() }
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

    private fun partFile(dir: File, file: ModelFile) = dir.resolve("${file.fileName}.part")

    private companion object {
        const val MODELS_DIR = "models"
        const val LITERTLM_MAGIC = "LITERTLM"
        const val POLL_MS = 1_000L
        const val BUFFER = 1 shl 20
        const val SPACE_MARGIN = 512L * 1024 * 1024
    }
}
