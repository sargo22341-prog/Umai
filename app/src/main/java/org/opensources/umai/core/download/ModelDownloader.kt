package org.opensources.umai.core.download

import android.os.StatFs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Where the download of a model stands. */
sealed interface DownloadState<out M> {
    data object Idle : DownloadState<Nothing>

    data class Downloading<out M>(
        val model: M,
        val downloaded: Long,
        val total: Long,
        /** The download waits for a Wi-Fi network, or for the network to come back. */
        val waiting: Boolean,
    ) : DownloadState<M>

    data class Verifying<out M>(val model: M, val fraction: Float) : DownloadState<M>

    data class Failed<out M>(val model: M, val failure: InstallFailure) : DownloadState<M>
}

/** A file of a model: where it comes from, the name it is kept under, and how to check it. */
interface DownloadableFile {
    val url: String
    val fileName: String

    /** 0 when unknown, for a file the user pointed at. */
    val sizeBytes: Long

    /** `null` when unknown: the file is then only checked by [ModelDownloader]'s `validate`. */
    val sha256: String?
}

/** A model being downloaded by the system's download manager, one download per file. */
data class PendingDownload<out M>(val downloadIds: List<Long>, val model: M)

/** The downloads the files of a model are handed to: the system's download manager ([WifiDownloads]). */
interface FileDownloads {
    fun enqueue(url: String, destination: File, title: String, description: String): Long

    /** `null` when [id] is no longer known. */
    fun progress(id: Long): DownloadProgress?

    fun remove(id: Long)
}

/** Where a downloader keeps, across app restarts, the model installed and the one on its way. */
interface DownloadRecord<M> {
    suspend fun installed(): M?

    suspend fun pending(): PendingDownload<M>?

    suspend fun setInstalled(model: M?)

    suspend fun setPending(pending: PendingDownload<M>?)
}

/**
 * Downloads the files of a model with the system's download manager, which
 * resumes a download of gigabytes across network changes and the app being
 * closed, then checks them before they are used.
 *
 * Only one model is kept: once a new one is checked, the files of the previous
 * one are deleted, since each takes hundreds of megabytes or more.
 */
class ModelDownloader<M>(
    private val downloads: FileDownloads,
    /** The folder of the models, `null` while the storage is not available. */
    private val modelsFolder: () -> File?,
    private val record: DownloadRecord<M>,
    private val scope: CoroutineScope,
    /** The files of a model, as this phone downloads them. */
    private val filesOf: (M) -> List<DownloadableFile>,
    /** The title of the download notification. */
    private val titleOf: (M) -> String,
    /** The text of the download notification, in the language of the app when it starts. */
    private val description: () -> String,
    /** Free space left beyond the files, so a download never fills the phone. */
    private val spaceMargin: Long,
    /** A check of a downloaded file beyond its hash: the failure, or `null` when it passes. */
    private val validate: (File) -> InstallFailure? = { null },
    /** The space left in a folder. */
    private val availableBytes: (File) -> Long = { StatFs(it.path).availableBytes },
) {

    private val mutex = Mutex()
    private var watchJob: Job? = null

    private val _state = MutableStateFlow<DownloadState<M>>(DownloadState.Idle)
    val state: StateFlow<DownloadState<M>> = _state.asStateFlow()

    val modelsDir: File? get() = modelsFolder()

    /** The file of [file], when it is on the phone. */
    fun installedFile(file: DownloadableFile): File? = modelsDir?.resolve(file.fileName)?.takeIf { it.isFile }

    /** Follows a download started earlier, possibly before the app was last closed. */
    fun resume() {
        if (watchJob?.isActive == true) return
        watchJob = scope.launch { watch() }
    }

    /** Starts downloading [model]; the state tells when it is installed or why it failed. */
    suspend fun install(model: M) = mutex.withLock {
        record.pending()?.let { removeDownloads(it) }
        val dir = modelsDir
        if (dir == null) {
            _state.value = DownloadState.Failed(model, InstallFailure.NO_STORAGE)
            return@withLock
        }
        dir.mkdirs()
        val files = filesOf(model)
        val size = files.sumOf { it.sizeBytes }
        if (size > 0 && availableBytes(dir) < size + spaceMargin) {
            _state.value = DownloadState.Failed(model, InstallFailure.NOT_ENOUGH_SPACE)
            return@withLock
        }
        val ids = files.map { file ->
            val part = partFile(dir, file).apply { delete() }
            downloads.enqueue(file.url, part, titleOf(model), description())
        }
        record.setPending(PendingDownload(ids, model))
        _state.value = DownloadState.Downloading(model, 0L, size, waiting = false)
        watchJob?.cancel()
        watchJob = scope.launch { watch() }
    }

    suspend fun cancel() = mutex.withLock {
        watchJob?.cancel()
        record.pending()?.let { removeDownloads(it) }
        record.setPending(null)
        _state.value = DownloadState.Idle
    }

    /** Deletes the files of the installed model. */
    suspend fun uninstall() = mutex.withLock {
        record.installed()?.let { model -> filesOf(model).forEach { modelsDir?.resolve(it.fileName)?.delete() } }
        record.setInstalled(null)
    }

    fun dismissFailure() {
        if (_state.value is DownloadState.Failed) _state.value = DownloadState.Idle
    }

    private suspend fun watch() {
        while (true) {
            val pending = record.pending() ?: run {
                if (_state.value !is DownloadState.Failed) _state.value = DownloadState.Idle
                return
            }
            val progress = pending.downloadIds.map(downloads::progress)
            when {
                progress.any { it == null || it.state == DownloadProgress.State.FAILED } ->
                    return finishWith(pending, InstallFailure.DOWNLOAD_FAILED)
                progress.all { it?.state == DownloadProgress.State.DONE } -> return complete(pending)
                else -> _state.value = DownloadState.Downloading(
                    model = pending.model,
                    downloaded = progress.sumOf { it?.downloaded ?: 0L },
                    total = progress.zip(filesOf(pending.model)) { p, file -> p?.total?.takeIf { it > 0 } ?: file.sizeBytes }.sum(),
                    waiting = progress.any { it?.state == DownloadProgress.State.WAITING },
                )
            }
            delay(POLL_MS)
        }
    }

    private suspend fun complete(pending: PendingDownload<M>) {
        val dir = modelsDir ?: return finishWith(pending, InstallFailure.NO_STORAGE)
        val files = filesOf(pending.model)
        val failure = withContext(Dispatchers.IO) { check(dir, files, pending.model) }
        if (failure != null) return finishWith(pending, failure)
        mutex.withLock {
            val previous = record.installed()
            if (files.any { !partFile(dir, it).renameTo(dir.resolve(it.fileName)) }) {
                return@withLock finishWith(pending, InstallFailure.NO_STORAGE)
            }
            val kept = files.map { it.fileName }.toSet()
            previous?.let(filesOf)?.filter { it.fileName !in kept }?.forEach { dir.resolve(it.fileName).delete() }
            record.setInstalled(pending.model)
            record.setPending(null)
            _state.value = DownloadState.Idle
        }
    }

    private suspend fun finishWith(pending: PendingDownload<M>, failure: InstallFailure) {
        removeDownloads(pending)
        record.setPending(null)
        _state.value = DownloadState.Failed(pending.model, failure)
    }

    private fun removeDownloads(pending: PendingDownload<M>) {
        pending.downloadIds.forEach { downloads.remove(it) }
        modelsDir?.let { dir -> filesOf(pending.model).forEach { partFile(dir, it).delete() } }
    }

    /** Every file must be there and pass [validate], and match its hash when it has one. */
    private fun check(dir: File, files: List<DownloadableFile>, model: M): InstallFailure? {
        val total = files.sumOf { partFile(dir, it).length() }.coerceAtLeast(1L)
        var done = 0L
        for (file in files) {
            val part = partFile(dir, file)
            if (!part.isFile) return InstallFailure.DOWNLOAD_FAILED
            validate(part)?.let { return it }
            val expected = file.sha256
            if (expected != null) {
                val before = done
                val actual = WifiDownloads.sha256(part) { read ->
                    _state.value = DownloadState.Verifying(model, (before + read).toFloat() / total)
                }
                if (!actual.equals(expected, ignoreCase = true)) return InstallFailure.CORRUPTED
            }
            done += part.length()
        }
        return null
    }

    private fun partFile(dir: File, file: DownloadableFile) = dir.resolve("${file.fileName}.part")

    companion object {
        /**
         * The folder of the models in the app's own external files: removed with
         * the app, no permission needed. Shared by every kind of model: the
         * language models and the speech models sit side by side.
         */
        const val MODELS_DIR = "models"
        private const val POLL_MS = 1_000L
    }
}
