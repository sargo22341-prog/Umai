package org.opensources.umai.llm

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.umai.R
import org.opensources.umai.core.ui.theme.UmaiTheme
import org.opensources.umai.llm.data.InstallFailure
import org.opensources.umai.llm.data.ActiveBackend
import org.opensources.umai.llm.data.InstallState
import org.opensources.umai.llm.data.LlmBenchmark
import org.opensources.umai.llm.data.LocalAiSettings
import org.opensources.umai.llm.domain.AiBackend
import org.opensources.umai.llm.domain.DeviceProfile
import org.opensources.umai.llm.domain.LocalModel
import org.opensources.umai.llm.domain.LocalModelCatalog
import org.opensources.umai.llm.domain.TensorChip
import org.opensources.umai.llm.ui.LocalAiScreen
import org.opensources.umai.llm.ui.LocalAiScreenActions
import org.opensources.umai.llm.ui.LocalAiUiState

/** Choosing, downloading and testing the on-device language model. */
@RunWith(AndroidJUnit4::class)
class LocalAiScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

    private val downloads = mutableListOf<LocalModel>()
    private var benchmarks = 0

    private fun render(state: LocalAiUiState) {
        rule.setContent {
            UmaiTheme {
                LocalAiScreen(
                    state = state,
                    actions = LocalAiScreenActions(
                        onBack = {},
                        onEnabledChange = {},
                        onDownload = { downloads += it },
                        onCancelDownload = {},
                        onDelete = {},
                        onDismissFailure = {},
                        onCustomUrlChange = {},
                        onDownloadCustom = {},
                        onBenchmark = { benchmarks++ },
                    ),
                )
            }
        }
    }

    private val recommended = LocalModelCatalog.recommended

    @Test
    fun withoutAModelTheCatalogIsOffered() {
        render(LocalAiUiState(deviceMemoryBytes = 16_000_000_000L))

        rule.onNodeWithText(string(R.string.local_ai_none_installed)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.local_ai_recommended)).assertIsDisplayed()
        rule.onAllNodesWithText(string(R.string.local_ai_download)).onFirst().performClick()

        assertEquals(listOf(recommended), downloads)
    }

    @Test
    fun theInstalledModelCanBeTimed() {
        val figures = LlmBenchmark(5_000, 300, 44.0, 64, 6.5, 4_200_000_000, AiBackend.TPU)
        render(
            LocalAiUiState(
                settings = LocalAiSettings(installed = recommended),
                benchmark = figures,
                deviceMemoryBytes = 16_000_000_000L,
            ),
        )

        rule.onNodeWithText(string(R.string.local_ai_benchmark_generation, decimal(6.5))).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.local_ai_benchmark_details, decimal(5.0), decimal(4.2), "TPU")).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.local_ai_benchmark)).performClick()

        assertEquals(1, benchmarks)
    }

    @Test
    fun aDownloadShowsItsProgress() {
        render(
            LocalAiUiState(
                install = InstallState.Downloading(recommended, 1_000_000_000, 2_700_000_000, waiting = true),
                deviceMemoryBytes = 16_000_000_000L,
            ),
        )

        rule.onNodeWithText(string(R.string.local_ai_downloading, recommended.name)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.local_ai_download_waiting)).assertIsDisplayed()
    }

    @Test
    fun aDamagedDownloadIsExplained() {
        render(
            LocalAiUiState(
                install = InstallState.Failed(recommended, InstallFailure.CORRUPTED),
                deviceMemoryBytes = 16_000_000_000L,
            ),
        )

        rule.onNodeWithText(string(R.string.local_ai_failure_corrupted, recommended.name)).assertIsDisplayed()
    }

    @Test
    fun aPhoneThatCannotRunItSaysSo() {
        render(LocalAiUiState(supported = false))
        rule.onNodeWithText(string(R.string.local_ai_unsupported)).assertIsDisplayed()
    }

    @Test
    fun aTensorPhoneShowsItsTpuAndWhereTheModelRuns() {
        render(
            LocalAiUiState(
                device = DeviceProfile("Tensor G5", TensorChip.G5, tpuReachable = true),
                settings = LocalAiSettings(installed = recommended),
                active = ActiveBackend(AiBackend.TPU, recommended.name, "Tensor G5"),
                deviceMemoryBytes = 16_000_000_000L,
            ),
        )

        rule.onNodeWithText(string(R.string.local_ai_device_tpu, "Tensor G5")).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.local_ai_running_on, "TPU")).assertIsDisplayed()
    }

    @Test
    fun aPhoneWithoutTpuNeverShowsOne() {
        render(
            LocalAiUiState(
                device = DeviceProfile("Tensor G2", tensorChip = null, tpuReachable = false),
                settings = LocalAiSettings(installed = recommended),
                active = ActiveBackend(AiBackend.GPU, recommended.name, "Tensor G2"),
                deviceMemoryBytes = 16_000_000_000L,
            ),
        )

        rule.onNodeWithText(string(R.string.local_ai_device_no_tpu, "Tensor G2")).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.local_ai_running_on, "GPU")).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.local_ai_running_on, "TPU")).assertDoesNotExist()
    }

    private fun decimal(value: Double) = String.format(context.resources.configuration.locales[0], "%.1f", value)
}
