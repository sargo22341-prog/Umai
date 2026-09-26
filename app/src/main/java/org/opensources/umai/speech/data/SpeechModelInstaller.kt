package org.opensources.umai.speech.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import org.opensources.umai.R
import org.opensources.umai.core.download.ModelDownloader
import org.opensources.umai.speech.domain.SpeechModel

/**
 * Installs a Whisper model over Wi-Fi, checked against its hash; only that one
 * is kept, the previous size is deleted once the new one is checked.
 */
class SpeechModelInstaller(context: Context, private val store: SpeechSettingsStore, scope: CoroutineScope) {

    private val downloader = ModelDownloader(
        context = context,
        record = store,
        scope = scope,
        filesOf = { model: SpeechModel -> listOf(model) },
        titleOf = SpeechModel::name,
        description = R.string.speech_download_description,
        spaceMargin = SPACE_MARGIN,
    )

    val state = downloader.state

    /** The path of the installed model, when its file is there. */
    suspend fun installedPath(): String? = store.installed()?.let(downloader::installedFile)?.path

    /** Follows a download started earlier, possibly before the app was last closed. */
    fun resume() = downloader.resume()

    suspend fun install(model: SpeechModel) = downloader.install(model)

    suspend fun cancel() = downloader.cancel()

    suspend fun uninstall() = downloader.uninstall()

    fun dismissFailure() = downloader.dismissFailure()

    private companion object {
        const val SPACE_MARGIN = 128L * 1024 * 1024
    }
}
