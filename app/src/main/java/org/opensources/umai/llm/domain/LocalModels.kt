package org.opensources.umai.llm.domain

import androidx.annotation.StringRes
import org.opensources.umai.R

/**
 * A GGUF model file the app can download and run with llama.cpp. The models
 * are published on Hugging Face, not bundled: the app installs without them,
 * and a model is replaced without updating the app.
 *
 * [sha256] is checked once the file is downloaded; a model added by address
 * has none, and is only checked to be a GGUF file.
 */
data class LocalModel(
    val id: String,
    val name: String,
    val url: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String?,
    val license: String,
    @param:StringRes val descriptionRes: Int?,
) {
    val isCustom: Boolean get() = id == CUSTOM_ID

    companion object {
        const val CUSTOM_ID = "custom"

        /**
         * A model the user points at by its address. [url] must end with the
         * file name, as Hugging Face download links do.
         */
        fun custom(url: String): LocalModel? {
            val trimmed = url.trim()
            val fileName = trimmed.substringBefore('?').substringAfterLast('/')
            if (!trimmed.startsWith("https://") || !fileName.endsWith(".gguf", ignoreCase = true)) return null
            return LocalModel(
                id = CUSTOM_ID,
                name = fileName.removeSuffix(".gguf"),
                url = trimmed,
                fileName = fileName,
                sizeBytes = 0L,
                sha256 = null,
                license = "",
                descriptionRes = null,
            )
        }
    }
}

/**
 * The models offered, measured on a Pixel 10 Pro XL (Tensor G5, 16 GB) with
 * the CPU build of llama.cpp: see docs/local-ai.md for the figures and why
 * these ones. All are instruction models under Apache 2.0, good in French and
 * English, quantized to 4 bits.
 */
object LocalModelCatalog {

    private const val HF = "https://huggingface.co"

    val models: List<LocalModel> = listOf(
        LocalModel(
            id = "qwen3.5-4b-q4_k_m",
            name = "Qwen3.5 4B",
            url = "$HF/unsloth/Qwen3.5-4B-GGUF/resolve/main/Qwen3.5-4B-Q4_K_M.gguf",
            fileName = "Qwen3.5-4B-Q4_K_M.gguf",
            sizeBytes = 2_740_937_888L,
            sha256 = "00fe7986ff5f6b463e62455821146049db6f9313603938a70800d1fb69ef11a4",
            license = "Apache 2.0",
            descriptionRes = R.string.local_ai_model_qwen4b,
        ),
        LocalModel(
            id = "gemma-4-e4b-q4_k_m",
            name = "Gemma 4 E4B",
            url = "$HF/unsloth/gemma-4-E4B-it-GGUF/resolve/main/gemma-4-E4B-it-Q4_K_M.gguf",
            fileName = "gemma-4-E4B-it-Q4_K_M.gguf",
            sizeBytes = 4_977_171_584L,
            sha256 = "85a896a047553e842f25297ee5b031d64ff30147d9c4af17b1e4b394cd1fab87",
            license = "Apache 2.0",
            descriptionRes = R.string.local_ai_model_gemma4,
        ),
        LocalModel(
            id = "qwen3.5-9b-q4_k_m",
            name = "Qwen3.5 9B",
            url = "$HF/unsloth/Qwen3.5-9B-GGUF/resolve/main/Qwen3.5-9B-Q4_K_M.gguf",
            fileName = "Qwen3.5-9B-Q4_K_M.gguf",
            sizeBytes = 5_680_522_464L,
            sha256 = "03b74727a860a56338e042c4420bb3f04b2fec5734175f4cb9fa853daf52b7e8",
            license = "Apache 2.0",
            descriptionRes = R.string.local_ai_model_qwen9b,
        ),
    )

    /** The one suggested first: see docs/local-ai.md. */
    val recommended: LocalModel = models.first()

    fun byId(id: String?): LocalModel? = models.firstOrNull { it.id == id }
}
