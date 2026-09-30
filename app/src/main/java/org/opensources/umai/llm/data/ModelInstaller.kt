package org.opensources.umai.llm.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.opensources.umai.R
import org.opensources.umai.core.download.InstallFailure
import org.opensources.umai.core.download.ModelDownloader
import org.opensources.umai.core.download.WifiDownloads
import org.opensources.umai.llm.domain.LocalModel
import org.opensources.umai.llm.domain.TensorChip
import java.io.File
import java.io.FileInputStream

/**
 * Installs the language model: a phone with a Tensor TPU gets the TPU build of
 * the model next to the file every phone runs. Each file must be a LiteRT-LM
 * model, and a catalog file must match its hash.
 */
class ModelInstaller(
    context: Context,
    private val store: LocalAiSettingsStore,
    private val chip: TensorChip?,
    private val scope: CoroutineScope,
) {

    private val downloader = ModelDownloader(
        downloads = WifiDownloads(context),
        modelsFolder = { context.getExternalFilesDir(ModelDownloader.MODELS_DIR) },
        record = store,
        scope = scope,
        filesOf = { model: LocalModel -> model.filesFor(chip) },
        titleOf = LocalModel::name,
        description = { context.getString(R.string.local_ai_download_description) },
        spaceMargin = SPACE_MARGIN,
        validate = ::liteRtLmCheck,
    )

    val state = downloader.state

    /** The installed model and the paths of its files, when the local AI is on. */
    suspend fun installedModel(): InstalledModel? {
        val settings = store.current()
        if (!settings.enabled) return null
        val model = settings.installed ?: return null
        val paths = model.filesFor(chip).mapNotNull { file -> downloader.installedFile(file)?.let { file to it.path } }.toMap()
        return InstalledModel(model, paths)
    }

    /** Follows a download started earlier, possibly before the app was last closed. */
    fun resume() {
        scope.launch(Dispatchers.IO) { deleteGgufModels() }
        downloader.resume()
    }

    /** Starts downloading [model]; [state] tells when it is installed or why it failed. */
    suspend fun install(model: LocalModel) = downloader.install(model)

    suspend fun cancel() = downloader.cancel()

    /** Deletes the files of the installed model. */
    suspend fun uninstall() = downloader.uninstall()

    fun dismissFailure() = downloader.dismissFailure()

    /**
     * The GGUF files of the llama.cpp runtime umai used before LiteRT-LM
     * (1.0.4), unreadable now: gigabytes a phone that ran it would keep.
     */
    private fun deleteGgufModels() {
        downloader.modelsDir?.listFiles { file -> file.name.endsWith(".gguf", ignoreCase = true) }?.forEach { it.delete() }
    }

    private fun liteRtLmCheck(file: File): InstallFailure? {
        val magic = ByteArray(LITERTLM_MAGIC.length)
        val read = FileInputStream(file).use { it.read(magic) }
        return InstallFailure.NOT_A_MODEL.takeIf { read != magic.size || String(magic, Charsets.US_ASCII) != LITERTLM_MAGIC }
    }

    private companion object {
        const val LITERTLM_MAGIC = "LITERTLM"
        const val SPACE_MARGIN = 512L * 1024 * 1024
    }
}
