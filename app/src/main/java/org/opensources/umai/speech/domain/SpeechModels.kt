package org.opensources.umai.speech.domain

import androidx.annotation.StringRes
import org.opensources.umai.R
import org.opensources.umai.core.download.DownloadableFile

/**
 * A Whisper model, as whisper.cpp publishes it on Hugging Face (MIT): it
 * writes down what is said in a video without captions. [sha256] is checked
 * once the file is downloaded.
 */
data class SpeechModel(
    val id: String,
    val name: String,
    override val url: String,
    override val fileName: String,
    override val sizeBytes: Long,
    override val sha256: String,
    @param:StringRes val descriptionRes: Int,
) : DownloadableFile {
    val license: String get() = "MIT"
}

/**
 * Three sizes, from the quickest to the most accurate: see docs/local-ai.md
 * for the figures measured on a phone. Quantized builds, a third of the size
 * of the originals for about the same text.
 */
object SpeechModelCatalog {

    private const val HF = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main"

    val base = SpeechModel(
        id = "whisper-base-q5_1",
        name = "Whisper Base",
        url = "$HF/ggml-base-q5_1.bin",
        fileName = "ggml-base-q5_1.bin",
        sizeBytes = 59_707_625L,
        sha256 = "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898",
        descriptionRes = R.string.speech_model_base,
    )

    val small = SpeechModel(
        id = "whisper-small-q5_1",
        name = "Whisper Small",
        url = "$HF/ggml-small-q5_1.bin",
        fileName = "ggml-small-q5_1.bin",
        sizeBytes = 190_085_487L,
        sha256 = "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb",
        descriptionRes = R.string.speech_model_small,
    )

    val turbo = SpeechModel(
        id = "whisper-large-v3-turbo-q5_0",
        name = "Whisper Large v3 Turbo",
        url = "$HF/ggml-large-v3-turbo-q5_0.bin",
        fileName = "ggml-large-v3-turbo-q5_0.bin",
        sizeBytes = 574_041_195L,
        sha256 = "394221709cd5ad1f40c46e6031ca61bce88931e6e088c188294c6d5a55ffa7e2",
        descriptionRes = R.string.speech_model_turbo,
    )

    val models: List<SpeechModel> = listOf(base, small, turbo)

    /**
     * Small writes French well and needs a few hundred megabytes: phones from
     * [SMALL_MEMORY_BYTES] run it next to the rest (a Pixel 6 Pro has 12 GB).
     * Below, Base is the safer choice. It is only a suggestion: any size can be picked.
     */
    fun recommendedFor(memoryBytes: Long): SpeechModel = if (memoryBytes >= SMALL_MEMORY_BYTES) small else base

    fun byId(id: String?): SpeechModel? = models.firstOrNull { it.id == id }

    private const val SMALL_MEMORY_BYTES = 6_000_000_000L
}
