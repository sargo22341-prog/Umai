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

@OptIn(ExperimentalCoroutinesApi::class)
class LocalAiViewModelTest {

    private val settings = MutableStateFlow(LocalAiSettings())
    private val install = MutableStateFlow<InstallState>(InstallState.Idle)
    private val active = MutableStateFlow<ActiveBackend?>(null)
    private val installed = mutableListOf<LocalModel>()
    private val benchmark = CompletableDeferred<LlmBenchmark?>()

    @Before
    fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(supported: Boolean = true) = LocalAiViewModel(
        supported = supported,
        device = DeviceProfile("Tensor G5", TensorChip.G5, tpuReachable = true),
        deviceMemoryBytes = 16_000_000_000L,
        settings = settings,
        install = install,
        active = active,
        actions = LocalAiActions(
            install = { installed += it },
            cancel = { install.value = InstallState.Idle },
            uninstall = { settings.value = settings.value.copy(installed = null) },
            dismissFailure = { install.value = InstallState.Idle },
            setEnabled = { settings.value = settings.value.copy(enabled = it) },
            benchmark = { benchmark.await() },
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
    fun `a phone that cannot run the model says so`() {
        assertFalse(viewModel(supported = false).state.value.supported)
    }
}
