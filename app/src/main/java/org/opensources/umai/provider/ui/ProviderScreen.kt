package org.opensources.umai.provider.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.opensources.umai.R
import org.opensources.umai.core.di.LocalAppContainer
import org.opensources.umai.settings.ui.SettingsSectionHeader
import org.opensources.umai.settings.ui.SettingsSwitchRow

@Composable
fun ProviderScreen(
    providerId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val viewModel: ProviderViewModel =
        viewModel(factory = ProviderViewModel.factory(container, providerId), key = "provider-$providerId")
    val state by viewModel.state.collectAsStateWithLifecycle()

    ProviderScreen(
        state = state,
        onBack = onBack,
        onImportsMediaChange = viewModel::setImportsMedia,
        modifier = modifier,
    )
}

/** Stateless page of one provider, driven by [ProviderUiState]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderScreen(
    state: ProviderUiState,
    onBack: () -> Unit,
    onImportsMediaChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val provider = state.provider
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(provider?.name ?: stringResource(R.string.providers_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (provider == null) {
            Text(
                text = stringResource(R.string.provider_unknown),
                modifier = Modifier.padding(padding).padding(16.dp),
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Text(
                    text = stringResource(provider.descriptionRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item { SettingsSectionHeader(stringResource(R.string.provider_section_import)) }
            item {
                SettingsSwitchRow(
                    title = stringResource(R.string.provider_import_media),
                    summary = stringResource(R.string.provider_import_media_summary),
                    checked = state.importsMedia,
                    onCheckedChange = onImportsMediaChange,
                )
            }
        }
    }
}
