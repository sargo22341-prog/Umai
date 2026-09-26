package org.opensources.umai.llm.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.opensources.umai.llm.data.ActiveBackend
import org.opensources.umai.llm.data.InstallState
import org.opensources.umai.llm.data.LlmBenchmark
import org.opensources.umai.llm.data.LocalAiSettings
import org.opensources.umai.llm.domain.AiBackend
import org.opensources.umai.llm.domain.DeviceProfile
import org.opensources.umai.llm.domain.LocalModel
import org.opensources.umai.llm.domain.LocalModelCatalog
import org.opensources.umai.llm.domain.TensorChip
import org.opensources.umai.speech.data.SpeechInstallState
import org.opensources.umai.speech.data.SpeechSettings
import org.opensources.umai.speech.domain.SpeechModel
import org.opensources.umai.speech.domain.SpeechModelCatalog

@OptIn(ExperimentalCoroutinesApi::class)
class LocalAiViewModelTest {

    private val settings = MutableStateFlow(LocalAiSettings())
    private val install = MutableStateFlow<InstallState>(InstallState.Idle)
    private val active = MutableStateFlow<ActiveBackend?>(null)
    private val installed = mutableListOf<LocalModel>()
    private val benchmark = CompletableDeferred<LlmBenchmark?>()
    private val speech = MutableStateFlow(SpeechSettings())
    private val speechInstall = MutableStateFlow<SpeechInstallState>(SpeechInstallState.Idle)
    private val speechInstalled = mutableListOf<SpeechModel>()

    @Before
    fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(supported: Boolean = true, memory: Long = 16_000_000_000L) = LocalAiViewModel(
        supported = supported,
        device = DeviceProfile("Tensor G5", TensorChip.G5, tpuReachable = true),
        deviceMemoryBytes = memory,
        settings = settings,
        install = install,
        active = active,
        speech = speech,
        speechInstall = speechInstall,
        actions = LocalAiActions(
            install = { installed += it },
            cancel = { install.value = InstallState.Idle },
            uninstall = { settings.value = settings.value.copy(installed = null) },
            dismissFailure = { install.value = InstallState.Idle },
            setEnabled = { settings.value = settings.value.copy(enabled = it) },
            benchmark = { benchmark.await() },
            speech = SpeechActions(
                install = { speechInstalled += it },
                cancel = { speechInstall.value = SpeechInstallState.Idle },
                uninstall = { speech.value = SpeechSettings() },
                dismissFailure = { speechInstall.value = SpeechInstallState.Idle },
            ),
        ),
    )

    @Test
    fun `a model of the catalog is downloaded on request`() {
        val vm = viewModel()
        vm.download(LocalModelCatalog.recommended)
        assertEquals(listOf(LocalModelCatalog.recommended), installed)
    }

    @Test
    fun `nothing else is downloaded while a download runs`() {
        install.value = InstallState.Downloading(LocalModelCatalog.recommended, 10, 100, waiting = false)
        val vm = viewModel()

        vm.download(LocalModelCatalog.models.last())

        assertTrue(vm.state.value.busy)
        assertTrue(installed.isEmpty())
    }

    @Test
    fun `a custom address must point at a litertlm file over https`() {
        val vm = viewModel()

        vm.onCustomUrlChange("http://example.org/model.bin")
        vm.downloadCustom()
        assertTrue(vm.state.value.customUrlInvalid)
        assertTrue(installed.isEmpty())

        vm.onCustomUrlChange("https://huggingface.co/org/repo/resolve/main/gemma-custom.litertlm?download=true")
        vm.downloadCustom()
        assertEquals("gemma-custom", installed.single().name)
        assertTrue(installed.single().isCustom)
        assertNull(installed.single().files.single().sha256)
    }

    @Test
    fun `the speed test reports its figures, or its failure`() {
        settings.value = LocalAiSettings(installed = LocalModelCatalog.recommended)
        val vm = viewModel()

        vm.runBenchmark()
        assertTrue(vm.state.value.benchmarking)
        val figures = LlmBenchmark(5_000, 300, 44.0, 64, 6.5, 4_000_000_000, AiBackend.TPU)
        benchmark.complete(figures)

        assertFalse(vm.state.value.benchmarking)
        assertEquals(figures, vm.state.value.benchmark)
        assertTrue(vm.state.value.ready)
    }

    @Test
    fun `turning the local AI off keeps the model`() {
        settings.value = LocalAiSettings(installed = LocalModelCatalog.recommended)
        val vm = viewModel()

        vm.setEnabled(false)

        assertFalse(vm.state.value.ready)
        assertEquals(LocalModelCatalog.recommended, vm.state.value.installed)
    }

    @Test
    fun `the backend the model really runs on reaches the screen`() {
        val vm = viewModel()
        assertNull(vm.state.value.active)

        active.value = ActiveBackend(AiBackend.GPU, "Gemma 4 E2B", "Tensor G5")

        assertEquals(AiBackend.GPU, vm.state.value.active?.backend)
    }

    @Test
    fun `a Tensor G5 downloads the TPU build next to the file every phone runs`() {
        val state = viewModel().state.value
        val e2b = LocalModelCatalog.recommended
        val e4b = LocalModelCatalog.models.last()

        assertEquals(2_588_147_712L + 3_113_545_589L, state.downloadSize(e2b))
        assertTrue(state.hasTpuBuild(e2b))
        assertFalse(state.hasTpuBuild(e4b))
        assertEquals(e4b.files.single().sizeBytes, state.downloadSize(e4b))
    }

    @Test
    fun `Gemma 4 E4B is flagged as large on a Pixel 6 Pro, where it swapped, E2B is not`() {
        // The total memory Android reports on a Pixel 6 Pro.
        val state = viewModel(memory = 11_822_308_000L).state.value
        val e2b = LocalModelCatalog.models.first { it.id == "gemma-4-e2b-litertlm" }
        val e4b = LocalModelCatalog.models.first { it.id == "gemma-4-e4b-litertlm" }

        assertFalse(state.isTight(e2b))
        assertTrue(state.isTight(e4b))
    }

    @Test
    fun `a phone that cannot run the model says so`() {
        assertFalse(viewModel(supported = false).state.value.supported)
    }

    @Test
    fun `Whisper Small is recommended from 6 GB of memory, Base below`() {
        // A Pixel 6 Pro has 12 GB, a Pixel 10 Pro XL 16 GB.
        assertEquals(SpeechModelCatalog.small, viewModel(memory = 12_000_000_000L).state.value.speechRecommended)
        assertEquals(SpeechModelCatalog.base, viewModel(memory = 4_000_000_000L).state.value.speechRecommended)
    }

    @Test
    fun `every Whisper size is offered, and the one picked is downloaded`() {
        val vm = viewModel()

        assertEquals(listOf("Whisper Base", "Whisper Small", "Whisper Large v3 Turbo"), vm.state.value.speechModels.map { it.name })
        vm.downloadSpeech(SpeechModelCatalog.turbo)

        assertEquals(listOf(SpeechModelCatalog.turbo), speechInstalled)
    }

    @Test
    fun `a Whisper download in progress blocks another one, and is followed`() {
        val vm = viewModel()
        speechInstall.value = SpeechInstallState.Downloading(SpeechModelCatalog.small, 10L, 100L, waiting = false)

        vm.downloadSpeech(SpeechModelCatalog.base)

        assertTrue(vm.state.value.speechBusy)
        assertTrue(speechInstalled.isEmpty())
    }

    @Test
    fun `the installed Whisper model is shown and can be deleted`() {
        val vm = viewModel()
        speech.value = SpeechSettings(installed = SpeechModelCatalog.small)
        assertEquals(SpeechModelCatalog.small, vm.state.value.speech.installed)

        vm.deleteSpeech()

        assertNull(vm.state.value.speech.installed)
    }
}
