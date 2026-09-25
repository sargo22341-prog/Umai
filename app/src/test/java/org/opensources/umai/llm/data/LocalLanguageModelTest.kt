package org.opensources.umai.llm.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.umai.llm.domain.AiBackend
import org.opensources.umai.llm.domain.AiBackendUnavailable
import org.opensources.umai.llm.domain.AiEngine
import org.opensources.umai.llm.domain.AiEngineLoader
import org.opensources.umai.llm.domain.AiSpeed
import org.opensources.umai.llm.domain.DeviceProfile
import org.opensources.umai.llm.domain.LlmFailure
import org.opensources.umai.llm.domain.LlmOutcome
import org.opensources.umai.llm.domain.LlmProgress
import org.opensources.umai.llm.domain.LlmRequest
import org.opensources.umai.llm.domain.LocalModelCatalog
import org.opensources.umai.llm.domain.TensorChip

/** The TPU → GPU → CPU chain, with a runtime that fails where it is told to. */
class LocalLanguageModelTest {

    private val scope = CoroutineScope(SupervisorJob())
    private val model = LocalModelCatalog.recommended
    private val tensorG5 = DeviceProfile("Tensor G5", TensorChip.G5, tpuReachable = true)

    /** Every file of the model for the chip, "on disk". */
    private fun installed(chip: TensorChip? = TensorChip.G5) =
        InstalledModel(model, model.filesFor(chip).associateWith { "/models/${it.fileName}" })

    private class FakeLoader(
        private val failingLoads: Set<AiBackend> = emptySet(),
        private val failingAnswers: Set<AiBackend> = emptySet(),
    ) : AiEngineLoader {
        val loads = mutableListOf<Pair<AiBackend, String>>()

        override fun load(path: String, backend: AiBackend, contextSize: Int): AiEngine {
            loads += backend to path
            if (backend in failingLoads) throw AiBackendUnavailable("$backend refused")
            return object : AiEngine {
                override val backend = backend
                override val lastSpeed = AiSpeed(10, 100.0, 5, 10.0)

                override fun generate(request: LlmRequest): Flow<String> =
                    if (backend in failingAnswers) flow { error("$backend broke") } else flowOf("{\"on\":", "\"$backend\"}")

                override fun close() = Unit
            }
        }
    }

    private fun languageModel(
        loader: AiEngineLoader,
        device: DeviceProfile = tensorG5,
        installed: InstalledModel? = installed(),
    ) = LocalLanguageModel(
        installed = { installed },
        device = device,
        loader = loader,
        isSupported = true,
        work = object : ModelWork {
            override fun begin() = Unit
            override fun progress(progress: LlmProgress) = Unit
            override fun end() = Unit
        },
        scope = scope,
        memoryBytes = { 0L },
    )

    private fun request(userChars: Int = 100) =
        LlmRequest(system = "Sort.", user = "x".repeat(userChars), jsonSchema = "{}", maxTokens = 256)

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `a Tensor phone answers on its TPU, with the TPU build`() = runBlocking {
        val loader = FakeLoader()
        val llm = languageModel(loader)

        val outcome = llm.generate(request())

        assertEquals(LlmOutcome.Success("{\"on\":\"TPU\"}"), outcome)
        assertEquals(AiBackend.TPU to "/models/gemma-4-E2B-it_Google_Tensor_G5.litertlm", loader.loads.single())
        assertEquals(ActiveBackend(AiBackend.TPU, model.name, "Tensor G5"), llm.active.value)
    }

    @Test
    fun `a TPU that does not load falls back to the GPU, and is never shown`() = runBlocking {
        val loader = FakeLoader(failingLoads = setOf(AiBackend.TPU))
        val llm = languageModel(loader)

        assertEquals(LlmOutcome.Success("{\"on\":\"GPU\"}"), llm.generate(request()))
        assertEquals(AiBackend.GPU, llm.active.value?.backend)
        assertEquals(AiBackend.GPU to "/models/gemma-4-E2B-it.litertlm", loader.loads.last())

        // The failed TPU is not tried again for the next answer.
        llm.generate(request())
        assertEquals(listOf(AiBackend.TPU, AiBackend.GPU), loader.loads.map { it.first })
    }

    @Test
    fun `the CPU is the last resort`() = runBlocking {
        val loader = FakeLoader(failingLoads = setOf(AiBackend.TPU, AiBackend.GPU))
        val llm = languageModel(loader)

        assertEquals(LlmOutcome.Success("{\"on\":\"CPU\"}"), llm.generate(request()))
        assertEquals(listOf(AiBackend.TPU, AiBackend.GPU, AiBackend.CPU), loader.loads.map { it.first })
        assertEquals(AiBackend.CPU, llm.active.value?.backend)
    }

    @Test
    fun `an answer that breaks on one backend is written on the next`() = runBlocking {
        val loader = FakeLoader(failingAnswers = setOf(AiBackend.TPU))
        val llm = languageModel(loader)

        assertEquals(LlmOutcome.Success("{\"on\":\"GPU\"}"), llm.generate(request()))
        assertEquals(AiBackend.GPU, llm.active.value?.backend)
    }

    @Test
    fun `a prompt too long for the TPU context goes straight to the GPU`() = runBlocking {
        val loader = FakeLoader()
        val llm = languageModel(loader)

        // About 5,000 tokens: more than the 4,096 of the TPU build, well within the 16,384 of the GPU.
        val outcome = llm.generate(request(userChars = 15_000))

        assertEquals(LlmOutcome.Success("{\"on\":\"GPU\"}"), outcome)
        assertEquals(listOf(AiBackend.GPU), loader.loads.map { it.first })
    }

    @Test
    fun `a long prompt the GPU cannot run is sent back to be shortened for the TPU or the CPU`() = runBlocking {
        val loader = FakeLoader(failingLoads = setOf(AiBackend.GPU))
        val llm = languageModel(loader)

        assertEquals(LlmOutcome.Failure(LlmFailure.TOO_LONG), llm.generate(request(userChars = 15_000)))

        // Shortened, it runs on the TPU.
        assertEquals(LlmOutcome.Success("{\"on\":\"TPU\"}"), llm.generate(request()))
    }

    @Test
    fun `the CPU runs with a context that fits the app's memory`() {
        val universal = model.files.single { it.chip == null }

        assertEquals(16_384, universal.contextSizeOn(AiBackend.GPU))
        assertEquals(4_096, universal.contextSizeOn(AiBackend.CPU))
    }

    @Test
    fun `a prompt too long for every backend is reported as such`() = runBlocking {
        val loader = FakeLoader()

        val outcome = languageModel(loader).generate(request(userChars = 60_000))

        assertEquals(LlmOutcome.Failure(LlmFailure.TOO_LONG), outcome)
        assertTrue(loader.loads.isEmpty())
    }

    @Test
    fun `a phone whose TPU the app cannot reach never tries it`() = runBlocking {
        val loader = FakeLoader()
        val llm = languageModel(loader, device = tensorG5.copy(tpuReachable = false))

        assertEquals(LlmOutcome.Success("{\"on\":\"GPU\"}"), llm.generate(request()))
        assertEquals(listOf(AiBackend.GPU), loader.loads.map { it.first })
    }

    @Test
    fun `a phone without a Tensor chip runs the file every phone runs`() = runBlocking {
        val loader = FakeLoader()
        val other = DeviceProfile("Snapdragon", tensorChip = null, tpuReachable = false)
        val llm = languageModel(loader, device = other, installed = installed(chip = null))

        assertEquals(LlmOutcome.Success("{\"on\":\"GPU\"}"), llm.generate(request()))
        assertEquals(AiBackend.GPU to "/models/gemma-4-E2B-it.litertlm", loader.loads.single())
    }

    @Test
    fun `no backend that loads means the model could not be loaded`() = runBlocking {
        val loader = FakeLoader(failingLoads = AiBackend.entries.toSet())
        val llm = languageModel(loader)

        assertEquals(LlmOutcome.Failure(LlmFailure.LOAD_FAILED), llm.generate(request()))
        assertNull(llm.active.value)
    }

    @Test
    fun `without an installed model nothing runs`() = runBlocking {
        val loader = FakeLoader()
        val llm = languageModel(loader, installed = null)

        assertEquals(false, llm.isReady())
        assertEquals(LlmOutcome.Failure(LlmFailure.NOT_READY), llm.generate(request()))
        assertTrue(loader.loads.isEmpty())
    }

    @Test
    fun `the speed test reports the backend it measured`() = runBlocking {
        val loader = FakeLoader(failingLoads = setOf(AiBackend.TPU))

        val benchmark = languageModel(loader).benchmark()

        assertEquals(AiBackend.GPU, benchmark?.backend)
        assertEquals(10.0, benchmark?.generationSpeed)
    }
}
