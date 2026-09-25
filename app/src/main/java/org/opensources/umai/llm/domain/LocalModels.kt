package org.opensources.umai.llm.domain

import androidx.annotation.StringRes
import org.opensources.umai.R

/**
 * One `.litertlm` file of a model. A file compiled for the TPU of one Tensor
 * chip ([chip]) runs there only; a file without a chip runs on the GPU and the
 * CPU of any phone.
 *
 * [sha256] is checked once the file is downloaded; a model added by address
 * has none, and is only checked to be a LiteRT-LM file.
 */
data class ModelFile(
    val url: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String?,
    val backends: Set<AiBackend>,
    /** The context the file runs with: fixed when the model was compiled for a TPU. */
    val contextSize: Int,
    val chip: TensorChip? = null,
) {
    /**
     * The context [backend] runs this file with. The CPU keeps its cache in the
     * app's own memory, which Android caps (4 GiB on a Pixel 10 Pro XL): the
     * full context would have the app killed, see docs/local-ai.md.
     */
    fun contextSizeOn(backend: AiBackend): Int =
        if (backend == AiBackend.CPU) minOf(contextSize, LocalModel.CPU_CONTEXT_SIZE) else contextSize
}

/**
 * A model the app can download and run with LiteRT-LM. The models are
 * published on Hugging Face, not bundled: the app installs without them, and a
 * model is replaced without updating the app.
 */
data class LocalModel(
    val id: String,
    val name: String,
    val files: List<ModelFile>,
    val license: String,
    @param:StringRes val descriptionRes: Int?,
) {
    val isCustom: Boolean get() = id == CUSTOM_ID

    /**
     * The files this phone downloads: the one for its GPU and CPU, and the TPU
     * build for its chip when there is one, so a prompt too long for the TPU,
     * or a TPU that fails, still has somewhere to run.
     */
    fun filesFor(chip: TensorChip?): List<ModelFile> = files.filter { it.chip == null || it.chip == chip }

    fun sizeFor(chip: TensorChip?): Long = filesFor(chip).sumOf { it.sizeBytes }

    fun runsOnTpu(chip: TensorChip?): Boolean = filesFor(chip).any { AiBackend.TPU in it.backends }

    companion object {
        const val CUSTOM_ID = "custom"

        /** The context the GPU runs with: the transcript of a twenty-minute video and its answer. */
        const val CONTEXT_SIZE = 16_384

        /** The largest context the CPU runs within the app's memory cap. */
        const val CPU_CONTEXT_SIZE = 4_096

        /**
         * A model the user points at by its address. [url] must end with the
         * file name, as Hugging Face download links do. It runs on the GPU or
         * the CPU: a TPU build only runs on the chip it was compiled for.
         */
        fun custom(url: String): LocalModel? {
            val trimmed = url.trim()
            val fileName = trimmed.substringBefore('?').substringAfterLast('/')
            if (!trimmed.startsWith("https://") || !fileName.endsWith(EXTENSION, ignoreCase = true)) return null
            return LocalModel(
                id = CUSTOM_ID,
                name = fileName.dropLast(EXTENSION.length),
                files = listOf(
                    ModelFile(
                        url = trimmed,
                        fileName = fileName,
                        sizeBytes = 0L,
                        sha256 = null,
                        backends = setOf(AiBackend.GPU, AiBackend.CPU),
                        contextSize = CONTEXT_SIZE,
                    ),
                ),
                license = "",
                descriptionRes = null,
            )
        }

        private const val EXTENSION = ".litertlm"
    }
}

/**
 * The models offered, published by Google's LiteRT community: see
 * docs/local-ai.md for the figures and why these ones. Both are instruction
 * models under Apache 2.0, good in French and English.
 */
object LocalModelCatalog {

    private const val HF = "https://huggingface.co/litert-community"

    /** The context the Tensor TPU builds of Gemma 4 E2B were compiled with. */
    private const val TPU_CONTEXT_SIZE = 4_096

    private val gpuAndCpu = setOf(AiBackend.GPU, AiBackend.CPU)

    val models: List<LocalModel> = listOf(
        LocalModel(
            id = "gemma-4-e2b-litertlm",
            name = "Gemma 4 E2B",
            files = listOf(
                ModelFile(
                    url = "$HF/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
                    fileName = "gemma-4-E2B-it.litertlm",
                    sizeBytes = 2_588_147_712L,
                    sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
                    backends = gpuAndCpu,
                    contextSize = LocalModel.CONTEXT_SIZE,
                ),
                ModelFile(
                    url = "$HF/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it_Google_Tensor_G5.litertlm",
                    fileName = "gemma-4-E2B-it_Google_Tensor_G5.litertlm",
                    sizeBytes = 3_113_545_589L,
                    sha256 = "af1082986639ecde7db95d91be6fe54f8b6b458104734c5bafc204e69d6852dc",
                    backends = setOf(AiBackend.TPU),
                    contextSize = TPU_CONTEXT_SIZE,
                    chip = TensorChip.G5,
                ),
                ModelFile(
                    url = "$HF/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it_Google_Tensor_G6.litertlm",
                    fileName = "gemma-4-E2B-it_Google_Tensor_G6.litertlm",
                    sizeBytes = 3_313_938_293L,
                    sha256 = "f86c7c19c736e9307267946edef58b0a45494a9127036d54d90dd6b7617c95b7",
                    backends = setOf(AiBackend.TPU),
                    contextSize = TPU_CONTEXT_SIZE,
                    chip = TensorChip.G6,
                ),
            ),
            license = "Apache 2.0",
            descriptionRes = R.string.local_ai_model_gemma4_e2b,
        ),
        LocalModel(
            id = "gemma-4-e4b-litertlm",
            name = "Gemma 4 E4B",
            files = listOf(
                ModelFile(
                    url = "$HF/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
                    fileName = "gemma-4-E4B-it.litertlm",
                    sizeBytes = 3_659_530_240L,
                    sha256 = "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0",
                    backends = gpuAndCpu,
                    contextSize = LocalModel.CONTEXT_SIZE,
                ),
            ),
            license = "Apache 2.0",
            descriptionRes = R.string.local_ai_model_gemma4_e4b,
        ),
    )

    /** The one suggested first: see docs/local-ai.md. */
    val recommended: LocalModel = models.first()

    fun byId(id: String?): LocalModel? = models.firstOrNull { it.id == id }
}
