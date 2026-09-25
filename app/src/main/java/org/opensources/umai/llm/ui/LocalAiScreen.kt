package org.opensources.umai.llm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.opensources.umai.R
import org.opensources.umai.core.di.LocalAppContainer
import org.opensources.umai.core.format.currentLocale
import org.opensources.umai.llm.data.InstallFailure
import org.opensources.umai.llm.data.InstallState
import org.opensources.umai.llm.data.LlmBenchmark
import org.opensources.umai.llm.domain.AiBackend
import org.opensources.umai.llm.domain.LocalModel
import org.opensources.umai.settings.ui.SettingsSectionHeader
import org.opensources.umai.settings.ui.SettingsSwitchRow

@Composable
fun LocalAiRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: LocalAiViewModel = viewModel(factory = LocalAiViewModel.factory(LocalAppContainer.current))
    val state by viewModel.state.collectAsStateWithLifecycle()
    LocalAiScreen(
        state = state,
        actions = LocalAiScreenActions(
            onBack = onBack,
            onEnabledChange = viewModel::setEnabled,
            onDownload = viewModel::download,
            onCancelDownload = viewModel::cancelDownload,
            onDelete = viewModel::delete,
            onDismissFailure = viewModel::dismissFailure,
            onCustomUrlChange = viewModel::onCustomUrlChange,
            onDownloadCustom = viewModel::downloadCustom,
            onBenchmark = viewModel::runBenchmark,
        ),
        modifier = modifier,
    )
}

/** The callbacks of [LocalAiScreen], grouped so the signature stays readable. */
class LocalAiScreenActions(
    val onBack: () -> Unit,
    val onEnabledChange: (Boolean) -> Unit,
    val onDownload: (LocalModel) -> Unit,
    val onCancelDownload: () -> Unit,
    val onDelete: () -> Unit,
    val onDismissFailure: () -> Unit,
    val onCustomUrlChange: (String) -> Unit,
    val onDownloadCustom: () -> Unit,
    val onBenchmark: () -> Unit,
)

/**
 * The on-device language model: which one is installed, how fast it runs,
 * and the ones that can replace it. Nothing leaves the phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalAiScreen(state: LocalAiUiState, actions: LocalAiScreenActions, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.local_ai_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item { Paragraph(stringResource(R.string.local_ai_intro)) }

            if (!state.supported) {
                item { Paragraph(stringResource(R.string.local_ai_unsupported)) }
                return@LazyColumn
            }

            item { Paragraph(deviceText(state)) }

            item {
                SettingsSwitchRow(
                    title = stringResource(R.string.local_ai_enabled),
                    summary = stringResource(R.string.local_ai_enabled_summary),
                    checked = state.settings.enabled,
                    onCheckedChange = actions.onEnabledChange,
                )
            }

            item { SettingsSectionHeader(stringResource(R.string.local_ai_section_installed)) }
            item {
                val installed = state.installed
                if (installed == null) {
                    Paragraph(stringResource(R.string.local_ai_none_installed))
                } else {
                    InstalledModelCard(state, installed, actions)
                }
            }

            when (val install = state.install) {
                is InstallState.Downloading, is InstallState.Verifying -> item { InstallProgressCard(install, actions) }
                is InstallState.Failed -> item { InstallFailureCard(install, actions) }
                InstallState.Idle -> Unit
            }

            item { SettingsSectionHeader(stringResource(R.string.local_ai_section_models)) }
            item {
                Paragraph(
                    stringResource(
                        R.string.local_ai_device_memory,
                        gigabytes(state.deviceMemoryBytes),
                    ),
                )
            }
            items(state.models, key = { it.id }) { model ->
                CatalogModelCard(
                    model = model,
                    recommended = model.id == state.recommended.id,
                    installed = model.id == state.installed?.id,
                    sizeBytes = state.downloadSize(model),
                    tpuChip = state.device.socName.takeIf { state.hasTpuBuild(model) },
                    tight = state.isTight(model),
                    enabled = !state.busy,
                    onDownload = { actions.onDownload(model) },
                )
            }

            item { SettingsSectionHeader(stringResource(R.string.local_ai_section_custom)) }
            item { CustomModelField(state, actions) }
        }
    }
}

@Composable
private fun InstalledModelCard(state: LocalAiUiState, model: LocalModel, actions: LocalAiScreenActions) {
    ModelCard {
        Text(model.name, style = MaterialTheme.typography.titleMedium)
        val size = state.downloadSize(model)
        if (size > 0) {
            Text(
                text = stringResource(R.string.local_ai_model_meta, gigabytes(size), model.license),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.active?.let { active ->
            Text(
                text = stringResource(R.string.local_ai_running_on, backendName(active.backend)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        state.benchmark?.let { BenchmarkResult(it) }
        if (state.benchmarkFailed) {
            Text(
                text = stringResource(R.string.local_ai_benchmark_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (state.benchmarking) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.local_ai_benchmark_running), style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = actions.onBenchmark, enabled = state.ready && !state.benchmarking) {
                Text(stringResource(R.string.local_ai_benchmark))
            }
            OutlinedButton(onClick = actions.onDelete, enabled = !state.benchmarking) {
                Text(stringResource(R.string.action_delete))
            }
        }
    }
}

@Composable
private fun BenchmarkResult(benchmark: LlmBenchmark) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringResource(R.string.local_ai_benchmark_generation, decimal(benchmark.generationSpeed)),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.local_ai_benchmark_prompt, decimal(benchmark.promptSpeed)),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(
                R.string.local_ai_benchmark_details,
                decimal(benchmark.loadMillis / 1000.0),
                gigabytes(benchmark.memoryBytes),
                backendName(benchmark.backend),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InstallProgressCard(install: InstallState, actions: LocalAiScreenActions) {
    ModelCard {
        when (install) {
            is InstallState.Downloading -> {
                Text(
                    text = stringResource(R.string.local_ai_downloading, install.model.name),
                    style = MaterialTheme.typography.titleSmall,
                )
                val fraction = if (install.total > 0) install.downloaded.toFloat() / install.total else 0f
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                Text(
                    text = if (install.waiting) {
                        stringResource(R.string.local_ai_download_waiting)
                    } else {
                        stringResource(
                            R.string.local_ai_download_progress,
                            gigabytes(install.downloaded),
                            gigabytes(install.total),
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = actions.onCancelDownload) { Text(stringResource(R.string.action_cancel)) }
            }
            is InstallState.Verifying -> {
                Text(
                    text = stringResource(R.string.local_ai_verifying, install.model.name),
                    style = MaterialTheme.typography.titleSmall,
                )
                LinearProgressIndicator(progress = { install.fraction }, modifier = Modifier.fillMaxWidth())
            }
            else -> Unit
        }
    }
}

@Composable
private fun InstallFailureCard(install: InstallState.Failed, actions: LocalAiScreenActions) {
    ModelCard {
        Text(
            text = stringResource(
                when (install.failure) {
                    InstallFailure.NO_STORAGE -> R.string.local_ai_failure_storage
                    InstallFailure.NOT_ENOUGH_SPACE -> R.string.local_ai_failure_space
                    InstallFailure.DOWNLOAD_FAILED -> R.string.local_ai_failure_download
                    InstallFailure.CORRUPTED -> R.string.local_ai_failure_corrupted
                    InstallFailure.NOT_A_MODEL -> R.string.local_ai_failure_not_model
                },
                install.model.name,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = actions.onDismissFailure) { Text(stringResource(R.string.action_close)) }
    }
}

@Composable
private fun CatalogModelCard(
    model: LocalModel,
    recommended: Boolean,
    installed: Boolean,
    sizeBytes: Long,
    /** The chip whose TPU build of the model comes with it, if any. */
    tpuChip: String?,
    tight: Boolean,
    enabled: Boolean,
    onDownload: () -> Unit,
) {
    ModelCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(model.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (recommended) AssistChip(onClick = {}, enabled = false, label = { Text(stringResource(R.string.local_ai_recommended)) })
        }
        model.descriptionRes?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
        Text(
            text = stringResource(R.string.local_ai_model_meta, gigabytes(sizeBytes), model.license),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        tpuChip?.let {
            Text(
                text = stringResource(R.string.local_ai_model_tpu, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (tight) {
            Text(
                text = stringResource(R.string.local_ai_model_tight),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (installed) {
            Text(
                text = stringResource(R.string.local_ai_model_installed),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            OutlinedButton(onClick = onDownload, enabled = enabled) {
                Text(stringResource(R.string.local_ai_download))
            }
        }
    }
}

@Composable
private fun CustomModelField(state: LocalAiUiState, actions: LocalAiScreenActions) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.local_ai_custom_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.customUrl,
            onValueChange = actions.onCustomUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.local_ai_custom_label)) },
            isError = state.customUrlInvalid,
            supportingText = if (state.customUrlInvalid) {
                { Text(stringResource(R.string.local_ai_custom_invalid)) }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        OutlinedButton(onClick = actions.onDownloadCustom, enabled = !state.busy && state.customUrl.isNotBlank()) {
            Text(stringResource(R.string.local_ai_download))
        }
    }
}

@Composable
private fun ModelCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun Paragraph(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun gigabytes(bytes: Long): String = decimal(bytes / 1_000_000_000.0)

@Composable
private fun decimal(value: Double): String = String.format(currentLocale(), "%.1f", value)

@Composable
private fun deviceText(state: LocalAiUiState): String = stringResource(
    if (state.device.tpuReachable) R.string.local_ai_device_tpu else R.string.local_ai_device_no_tpu,
    state.device.socName,
)

@Composable
private fun backendName(backend: AiBackend): String = stringResource(
    when (backend) {
        AiBackend.TPU -> R.string.local_ai_backend_tpu
        AiBackend.GPU -> R.string.local_ai_backend_gpu
        AiBackend.CPU -> R.string.local_ai_backend_cpu
    },
)
