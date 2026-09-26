package org.opensources.umai.llm.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opensources.umai.core.di.AppContainer
import org.opensources.umai.llm.data.ActiveBackend
import org.opensources.umai.llm.data.InstallState
import org.opensources.umai.llm.data.LlmBenchmark
import org.opensources.umai.llm.data.LocalAiSettings
import org.opensources.umai.llm.domain.DeviceProfile
import org.opensources.umai.llm.domain.LocalModel
import org.opensources.umai.llm.domain.LocalModelCatalog
import org.opensources.umai.speech.data.SpeechInstallState
import org.opensources.umai.speech.data.SpeechSettings
import org.opensources.umai.speech.domain.SpeechModel
import org.opensources.umai.speech.domain.SpeechModelCatalog

data class LocalAiUiState(
    /** Whether LiteRT-LM runs on this phone at all. */
    val supported: Boolean = true,
    val device: DeviceProfile = DeviceProfile(socName = "", tensorChip = null, tpuReachable = false),
    /** Where the model is loaded now, as proven when it was loaded. */
    val active: ActiveBackend? = null,
    val settings: LocalAiSettings = LocalAiSettings(),
    val install: InstallState = InstallState.Idle,
    val models: List<LocalModel> = LocalModelCatalog.models,
    val recommended: LocalModel = LocalModelCatalog.recommended,
    val customUrl: String = "",
    val customUrlInvalid: Boolean = false,
    val benchmarking: Boolean = false,
    val benchmark: LlmBenchmark? = null,
    val benchmarkFailed: Boolean = false,
    /** Memory of the phone, to judge which models fit. */
    val deviceMemoryBytes: Long = 0L,
    val speech: SpeechSettings = SpeechSettings(),
    val speechInstall: SpeechInstallState = SpeechInstallState.Idle,
    val speechModels: List<SpeechModel> = SpeechModelCatalog.models,
) {
    val installed: LocalModel? get() = settings.installed
    val busy: Boolean get() = install is InstallState.Downloading || install is InstallState.Verifying
    val ready: Boolean get() = supported && settings.enabled && installed != null

    /** What this phone downloads for [model]: its TPU build too, on a Tensor chip that has one. */
    val speechRecommended: SpeechModel get() = SpeechModelCatalog.recommendedFor(deviceMemoryBytes)
    val speechBusy: Boolean
        get() = speechInstall is SpeechInstallState.Downloading || speechInstall is SpeechInstallState.Verifying

    fun downloadSize(model: LocalModel): Long = model.sizeFor(device.tensorChip)

    fun hasTpuBuild(model: LocalModel): Boolean = device.tpuReachable && model.runsOnTpu(device.tensorChip)

    /** Only one file of a model is loaded at a time: the largest must fit next to the other apps. */
    fun isTight(model: LocalModel): Boolean =
        (model.filesFor(device.tensorChip).maxOfOrNull { it.sizeBytes } ?: 0L) > deviceMemoryBytes * MEMORY_SHARE

    private companion object {
        /**
         * Above this share of the phone's memory, a model may not load next to
         * the other apps, or runs from swap: on a Pixel 6 Pro (12 GB), Gemma 4
         * E2B (22 %) answers an import in 86 s, E4B (31 %) filled the swap and
         * had not answered after 27 minutes.
         */
        const val MEMORY_SHARE = 0.3
    }
}

/** Actions of the screen that need the device: they are handed in, so the ViewModel stays testable. */
class LocalAiActions(
    val install: suspend (LocalModel) -> Unit,
    val cancel: suspend () -> Unit,
    val uninstall: suspend () -> Unit,
    val dismissFailure: () -> Unit,
    val setEnabled: suspend (Boolean) -> Unit,
    val benchmark: suspend () -> LlmBenchmark?,
    val speech: SpeechActions,
)

/** The same for the Whisper model. */
class SpeechActions(
    val install: suspend (SpeechModel) -> Unit,
    val cancel: suspend () -> Unit,
    val uninstall: suspend () -> Unit,
    val dismissFailure: () -> Unit,
)

class LocalAiViewModel(
    supported: Boolean,
    device: DeviceProfile,
    deviceMemoryBytes: Long,
    settings: Flow<LocalAiSettings>,
    install: Flow<InstallState>,
    active: Flow<ActiveBackend?>,
    speech: Flow<SpeechSettings>,
    speechInstall: Flow<SpeechInstallState>,
    private val actions: LocalAiActions,
) : ViewModel() {

    private val _state = MutableStateFlow(
        LocalAiUiState(supported = supported, device = device, deviceMemoryBytes = deviceMemoryBytes),
    )
    val state: StateFlow<LocalAiUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(settings, install) { s, i -> s to i }.collect { (s, i) ->
                _state.update {
                    // A benchmark belongs to the model it measured.
                    val sameModel = it.settings.installed?.id == s.installed?.id
                    it.copy(settings = s, install = i, benchmark = it.benchmark.takeIf { sameModel })
                }
            }
        }
        viewModelScope.launch { active.collect { backend -> _state.update { it.copy(active = backend) } } }
        viewModelScope.launch {
            combine(speech, speechInstall) { s, i -> s to i }.collect { (s, i) ->
                _state.update { it.copy(speech = s, speechInstall = i) }
            }
        }
    }

    fun download(model: LocalModel) {
        if (_state.value.busy) return
        viewModelScope.launch { actions.install(model) }
    }

    fun onCustomUrlChange(value: String) = _state.update { it.copy(customUrl = value, customUrlInvalid = false) }

    fun downloadCustom() {
        val model = LocalModel.custom(_state.value.customUrl)
        if (model == null) {
            _state.update { it.copy(customUrlInvalid = true) }
            return
        }
        download(model)
    }

    fun cancelDownload() {
        viewModelScope.launch { actions.cancel() }
    }

    fun delete() {
        viewModelScope.launch { actions.uninstall() }
    }

    fun dismissFailure() = actions.dismissFailure()

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { actions.setEnabled(enabled) }
    }

    fun runBenchmark() {
        if (_state.value.benchmarking) return
        _state.update { it.copy(benchmarking = true, benchmarkFailed = false) }
        viewModelScope.launch {
            val result = actions.benchmark()
            _state.update { it.copy(benchmarking = false, benchmark = result, benchmarkFailed = result == null) }
        }
    }

    fun downloadSpeech(model: SpeechModel) {
        if (_state.value.speechBusy) return
        viewModelScope.launch { actions.speech.install(model) }
    }

    fun cancelSpeechDownload() {
        viewModelScope.launch { actions.speech.cancel() }
    }

    fun deleteSpeech() {
        viewModelScope.launch { actions.speech.uninstall() }
    }

    fun dismissSpeechFailure() = actions.speech.dismissFailure()

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                LocalAiViewModel(
                    supported = container.localLanguageModel.isSupported,
                    device = container.aiDevice,
                    deviceMemoryBytes = container.deviceMemoryBytes,
                    settings = container.localAiSettings.settings,
                    install = container.modelInstaller.state,
                    active = container.localLanguageModel.active,
                    speech = container.speechSettings.settings,
                    speechInstall = container.speechModelInstaller.state,
                    actions = LocalAiActions(
                        install = container.modelInstaller::install,
                        cancel = container.modelInstaller::cancel,
                        uninstall = container.modelInstaller::uninstall,
                        dismissFailure = container.modelInstaller::dismissFailure,
                        setEnabled = { container.localAiSettings.setEnabled(it) },
                        benchmark = container.localLanguageModel::benchmark,
                        speech = SpeechActions(
                            install = container.speechModelInstaller::install,
                            cancel = container.speechModelInstaller::cancel,
                            uninstall = container.speechModelInstaller::uninstall,
                            dismissFailure = container.speechModelInstaller::dismissFailure,
                        ),
                    ),
                )
            }
        }
    }
}
