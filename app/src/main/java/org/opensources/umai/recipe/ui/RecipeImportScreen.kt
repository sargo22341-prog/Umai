package org.opensources.umai.recipe.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.opensources.umai.R
import org.opensources.umai.core.di.LocalAppContainer
import org.opensources.umai.core.ui.component.message
import org.opensources.umai.core.ui.component.title

@Composable
fun RecipeImportScreen(
    onBack: () -> Unit,
    onImported: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val viewModel: RecipeImportViewModel =
        viewModel(factory = RecipeImportViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    val slug = state.importedSlug
    LaunchedEffect(slug) {
        if (slug != null) {
            viewModel.consumeImportedSlug()
            onImported(slug)
        }
    }

    RecipeImportScreen(
        state = state,
        onBack = onBack,
        onUrlChange = viewModel::onUrlChange,
        onIncludeTagsChange = viewModel::onIncludeTagsChange,
        onIncludeCategoriesChange = viewModel::onIncludeCategoriesChange,
        onImport = viewModel::import,
        modifier = modifier,
    )
}

/** Stateless import form, driven by [RecipeImportUiState]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeImportScreen(
    state: RecipeImportUiState,
    onBack: () -> Unit,
    onUrlChange: (String) -> Unit,
    onIncludeTagsChange: (Boolean) -> Unit,
    onIncludeCategoriesChange: (Boolean) -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_import_recipe)) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.import_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = state.url,
                onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.import_url_label)) },
                placeholder = { Text(stringResource(R.string.import_url_placeholder)) },
                singleLine = true,
                enabled = !state.importing,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
            )

            SwitchRow(
                title = stringResource(R.string.import_include_tags),
                checked = state.includeTags,
                enabled = !state.importing,
                onCheckedChange = onIncludeTagsChange,
            )
            SwitchRow(
                title = stringResource(R.string.import_include_categories),
                checked = state.includeCategories,
                enabled = !state.importing,
                onCheckedChange = onIncludeCategoriesChange,
            )

            state.error?.let { error ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        text = "${error.title()}\n${error.message()}",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            Button(
                onClick = onImport,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canSubmit,
            ) {
                if (state.importing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(stringResource(R.string.import_running))
                } else {
                    Text(stringResource(R.string.import_action))
                }
            }

            Text(
                text = stringResource(R.string.import_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
