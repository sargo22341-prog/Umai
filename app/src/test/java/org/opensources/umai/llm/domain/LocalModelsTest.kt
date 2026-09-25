package org.opensources.umai.llm.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelsTest {

    @Test
    fun `only the Tensor chips with a TPU build are recognized`() {
        assertEquals(TensorChip.G5, TensorChip.of("Google", "Tensor G5"))
        assertEquals(TensorChip.G6, TensorChip.of("Google", "Tensor G6"))
        assertNull(TensorChip.of("Google", "Tensor G4"))
        assertNull(TensorChip.of("QTI", "SM8750"))
        assertNull(TensorChip.of("Other", "Tensor G5"))
    }

    @Test
    fun `each chip gets its own TPU build, and every phone the universal file`() {
        val e2b = LocalModelCatalog.recommended

        val onG5 = e2b.filesFor(TensorChip.G5).map { it.fileName }
        assertEquals(listOf("gemma-4-E2B-it.litertlm", "gemma-4-E2B-it_Google_Tensor_G5.litertlm"), onG5)
        assertEquals(listOf("gemma-4-E2B-it.litertlm"), e2b.filesFor(null).map { it.fileName })
        assertTrue(e2b.runsOnTpu(TensorChip.G6))
        assertFalse(e2b.runsOnTpu(null))
    }

    @Test
    fun `the file every phone runs covers the GPU and the CPU with the large context`() {
        LocalModelCatalog.models.forEach { model ->
            val universal = model.files.single { it.chip == null }
            assertEquals(setOf(AiBackend.GPU, AiBackend.CPU), universal.backends)
            assertEquals(LocalModel.CONTEXT_SIZE, universal.contextSize)
            assertTrue(model.files.all { it.sha256?.length == 64 })
        }
    }

    @Test
    fun `a custom model is a litertlm file over https, run on the GPU or the CPU`() {
        assertNull(LocalModel.custom("http://example.org/model.litertlm"))
        assertNull(LocalModel.custom("https://example.org/model.gguf"))

        val custom = LocalModel.custom(" https://huggingface.co/org/repo/resolve/main/My-Model.litertlm?download=true ")!!
        assertEquals("My-Model", custom.name)
        assertTrue(custom.isCustom)
        assertEquals(setOf(AiBackend.GPU, AiBackend.CPU), custom.files.single().backends)
        assertFalse(custom.runsOnTpu(TensorChip.G5))
    }
}
