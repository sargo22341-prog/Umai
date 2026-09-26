package org.opensources.umai.llm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.opensources.umai.R
import org.opensources.umai.settings.ui.SettingsSectionHeader
import org.opensources.umai.speech.data.SpeechInstallState
import org.opensources.umai.speech.domain.SpeechModel

/** The callbacks of the Whisper section of [LocalAiScreen]. */
class SpeechScreenActions(
    val onDownload: (SpeechModel) -> Unit,
    val onCancelDownload: () -> Unit,
    val onDelete: () -> Unit,
    val onDismissFailure: () -> Unit,
)

/**
 * The Whisper models, in three sizes: the one this phone should take is
 * marked, any can be picked, and only one is kept.
 */
internal fun LazyListScope.speechModelItems(state: LocalAiUiState, actions: SpeechScreenActions) {
    item { SettingsSectionHeader(stringResource(R.string.speech_section)) }
    item { Paragraph(stringResource(R.string.speech_intro)) }
    when (val install = state.speechInstall) {
        is SpeechInstallState.Downloading, is SpeechInstallState.Verifying -> item { SpeechProgressCard(install, actions) }
        is SpeechInstallState.Failed -> item {
            ModelCard {
                Text(
                    text = stringResource(failureMessage(install.failure), install.model.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = actions.onDismissFailure) { Text(stringResource(R.string.action_close)) }
            }
        }
        SpeechInstallState.Idle -> Unit
    }
    items(state.speechModels, key = { it.id }) { model ->
        SpeechModelCard(
            model = model,
            recommended = model.id == state.speechRecommended.id,
            installed = model.id == state.speech.installed?.id,
            enabled = !state.speechBusy,
            actions = actions,
        )
    }
}

@Composable
private fun SpeechModelCard(
    model: SpeechModel,
    recommended: Boolean,
    installed: Boolean,
    enabled: Boolean,
    actions: SpeechScreenActions,
) {
    ModelCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(model.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (recommended) AssistChip(onClick = {}, enabled = false, label = { Text(stringResource(R.string.local_ai_recommended)) })
        }
        Text(stringResource(model.descriptionRes), style = MaterialTheme.typography.bodyMedium)
        Text(
            text = stringResource(R.string.speech_model_meta, megabytes(model.sizeBytes), model.license),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (installed) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.local_ai_model_installed),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = actions.onDelete, enabled = enabled) { Text(stringResource(R.string.action_delete)) }
            }
        } else {
            OutlinedButton(onClick = { actions.onDownload(model) }, enabled = enabled) {
                Text(stringResource(R.string.local_ai_download))
            }
        }
    }
}

@Composable
private fun SpeechProgressCard(install: SpeechInstallState, actions: SpeechScreenActions) {
    ModelCard {
        when (install) {
            is SpeechInstallState.Downloading -> {
                Text(stringResource(R.string.local_ai_downloading, install.model.name), style = MaterialTheme.typography.titleSmall)
                val fraction = if (install.total > 0) install.downloaded.toFloat() / install.total else 0f
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                Text(
                    text = if (install.waiting) {
                        stringResource(R.string.local_ai_download_waiting)
                    } else {
                        stringResource(R.string.speech_download_progress, megabytes(install.downloaded), megabytes(install.total))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = actions.onCancelDownload) { Text(stringResource(R.string.action_cancel)) }
            }
            is SpeechInstallState.Verifying -> {
                Text(stringResource(R.string.local_ai_verifying, install.model.name), style = MaterialTheme.typography.titleSmall)
                LinearProgressIndicator(progress = { install.fraction }, modifier = Modifier.fillMaxWidth())
            }
            else -> Unit
        }
    }
}

private fun megabytes(bytes: Long): String = (bytes / 1_000_000).toString()
