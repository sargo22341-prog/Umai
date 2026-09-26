package org.opensources.umai.speech.data

import android.content.Context
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
import org.opensources.umai.core.download.DownloadProgress
import org.opensources.umai.core.download.InstallFailure
import org.opensources.umai.core.download.WifiDownloads
import org.opensources.umai.speech.domain.SpeechModel
import java.io.File

/** Where the download of a speech model stands. */
sealed interface SpeechInstallState {
    data object Idle : SpeechInstallState

    data class Downloading(val model: SpeechModel, val downloaded: Long, val total: Long, val waiting: Boolean) : SpeechInstallState

    data class Verifying(val model: SpeechModel, val fraction: Float) : SpeechInstallState

    data class Failed(val model: SpeechModel, val failure: InstallFailure) : SpeechInstallState
}

/**
 * Downloads a Whisper model over Wi-Fi, checks it against its hash, and keeps
 * only that one: the previous size is deleted once the new one is checked.
 */
class SpeechModelInstaller(
    context: Context,
    private val store: SpeechSettingsStore,
    private val scope: CoroutineScope,
) {

    private val context = context.applicationContext
    private val downloads = WifiDownloads(this.context)
    private val mutex = Mutex()
    private var watchJob: Job? = null

    private val _state = MutableStateFlow<SpeechInstallState>(SpeechInstallState.Idle)
    val state: StateFlow<SpeechInstallState> = _state.asStateFlow()

    /** Next to the language models: in the app's own external files, removed with the app. */
    private val modelsDir: File? get() = context.getExternalFilesDir(MODELS_DIR)

    /** The path of the installed model, when its file is there. */
    suspend fun installedPath(): String? {
        val model = store.current().installed ?: return null
        return modelsDir?.resolve(model.fileName)?.takeIf { it.isFile }?.path
    }

    /** Follows a download started earlier, possibly before the app was last closed. */
    fun resume() {
        if (watchJob?.isActive == true) return
        watchJob = scope.launch { watch() }
    }

    suspend fun install(model: SpeechModel) = mutex.withLock {
        store.current().pending?.let(::removeDownload)
        val dir = modelsDir
        if (dir == null) {
            _state.value = SpeechInstallState.Failed(model, InstallFailure.NO_STORAGE)
            return@withLock
        }
        dir.mkdirs()
        if (StatFs(dir.path).availableBytes < model.sizeBytes + SPACE_MARGIN) {
            _state.value = SpeechInstallState.Failed(model, InstallFailure.NOT_ENOUGH_SPACE)
            return@withLock
        }
        val part = partFile(dir, model).apply { delete() }
        val id = downloads.enqueue(model.url, part, model.name, context.getString(R.string.speech_download_description))
        store.setPending(PendingSpeechModel(id, model))
        _state.value = SpeechInstallState.Downloading(model, 0L, model.sizeBytes, waiting = false)
        watchJob?.cancel()
        watchJob = scope.launch { watch() }
    }

    suspend fun cancel() = mutex.withLock {
        watchJob?.cancel()
        store.current().pending?.let(::removeDownload)
        store.setPending(null)
        _state.value = SpeechInstallState.Idle
    }

    suspend fun uninstall() = mutex.withLock {
        store.current().installed?.let { model -> modelsDir?.resolve(model.fileName)?.delete() }
        store.setInstalled(null)
    }

    fun dismissFailure() {
        if (_state.value is SpeechInstallState.Failed) _state.value = SpeechInstallState.Idle
    }

    private suspend fun watch() {
        while (scope.isActive) {
            val pending = store.current().pending ?: run {
                if (_state.value !is SpeechInstallState.Failed) _state.value = SpeechInstallState.Idle
                return
            }
            val progress = downloads.progress(pending.downloadId)
            when (progress?.state) {
                null, DownloadProgress.State.FAILED -> return finishWith(pending, InstallFailure.DOWNLOAD_FAILED)
                DownloadProgress.State.DONE -> return complete(pending)
                else -> _state.value = SpeechInstallState.Downloading(
                    model = pending.model,
                    downloaded = progress.downloaded,
                    total = progress.total.takeIf { it > 0 } ?: pending.model.sizeBytes,
                    waiting = progress.state == DownloadProgress.State.WAITING,
                )
            }
            delay(POLL_MS)
        }
    }

    private suspend fun complete(pending: PendingSpeechModel) {
        val dir = modelsDir ?: return finishWith(pending, InstallFailure.NO_STORAGE)
        val model = pending.model
        val part = partFile(dir, model)
        val failure = withContext(Dispatchers.IO) {
            when {
                !part.isFile -> InstallFailure.DOWNLOAD_FAILED
                !WifiDownloads.sha256(part) { read -> _state.value = SpeechInstallState.Verifying(model, read.toFloat() / part.length()) }
                    .equals(model.sha256, ignoreCase = true) -> InstallFailure.CORRUPTED
                else -> null
            }
        }
        if (failure != null) return finishWith(pending, failure)
        mutex.withLock {
            val previous = store.current().installed
            if (!part.renameTo(dir.resolve(model.fileName))) return@withLock finishWith(pending, InstallFailure.NO_STORAGE)
            if (previous != null && previous.fileName != model.fileName) dir.resolve(previous.fileName).delete()
            store.setInstalled(model)
            store.setPending(null)
            _state.value = SpeechInstallState.Idle
        }
    }

    private suspend fun finishWith(pending: PendingSpeechModel, failure: InstallFailure) {
        removeDownload(pending)
        store.setPending(null)
        _state.value = SpeechInstallState.Failed(pending.model, failure)
    }

    private fun removeDownload(pending: PendingSpeechModel) {
        downloads.remove(pending.downloadId)
        modelsDir?.let { partFile(it, pending.model).delete() }
    }

    private fun partFile(dir: File, model: SpeechModel) = dir.resolve("${model.fileName}.part")

    private companion object {
        const val MODELS_DIR = "models"
        const val POLL_MS = 1_000L
        const val SPACE_MARGIN = 128L * 1024 * 1024
    }
}
