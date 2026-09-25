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
import org.opensources.umai.llm.data.InstallState
import org.opensources.umai.llm.data.LlmBenchmark
import org.opensources.umai.llm.data.LocalAiSettings
import org.opensources.umai.llm.domain.LocalModel
import org.opensources.umai.llm.domain.LocalModelCatalog

data class LocalAiUiState(
    /** Whether llama.cpp runs on this phone at all. */
    val supported: Boolean = true,
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
) {
    val installed: LocalModel? get() = settings.installed
    val busy: Boolean get() = install is InstallState.Downloading || install is InstallState.Verifying
    val ready: Boolean get() = supported && settings.enabled && installed != null
}

/** Actions of the screen that need the device: they are handed in, so the ViewModel stays testable. */
class LocalAiActions(
    val install: suspend (LocalModel) -> Unit,
    val cancel: suspend () -> Unit,
    val uninstall: suspend () -> Unit,
    val dismissFailure: () -> Unit,
    val setEnabled: suspend (Boolean) -> Unit,
    val benchmark: suspend () -> LlmBenchmark?,
)

class LocalAiViewModel(
    supported: Boolean,
    deviceMemoryBytes: Long,
    settings: Flow<LocalAiSettings>,
    install: Flow<InstallState>,
    private val actions: LocalAiActions,
) : ViewModel() {

    private val _state = MutableStateFlow(LocalAiUiState(supported = supported, deviceMemoryBytes = deviceMemoryBytes))
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

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                LocalAiViewModel(
                    supported = container.localLanguageModel.isSupported,
                    deviceMemoryBytes = container.deviceMemoryBytes,
                    settings = container.localAiSettings.settings,
                    install = container.modelInstaller.state,
                    actions = LocalAiActions(
                        install = container.modelInstaller::install,
                        cancel = container.modelInstaller::cancel,
                        uninstall = container.modelInstaller::uninstall,
                        dismissFailure = container.modelInstaller::dismissFailure,
                        setEnabled = { container.localAiSettings.setEnabled(it) },
                        benchmark = container.localLanguageModel::benchmark,
                    ),
                )
            }
        }
    }
}
