package org.opensources.umai.generation.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.opensources.umai.R
import org.opensources.umai.core.di.LocalAppContainer
import org.opensources.umai.core.ui.component.BackTopAppBar
import org.opensources.umai.core.ui.component.message

@Composable
fun RecipeGenerationRoute(initialSlug: String?, onBack: () -> Unit, onOpenRecipe: (String) -> Unit, onOpenLocalAi: () -> Unit) {
    val container = LocalAppContainer.current
    val model: RecipeGenerationViewModel = viewModel(
        factory = RecipeGenerationViewModel.factory(container, initialSlug), key = "generation-$initialSlug",
    )
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { model.generate() }
    LifecycleResumeEffect(model) {
        model.refreshModel()
        onPauseOrDispose { }
    }
    RecipeGenerationScreen(
        state, onBack, model::changePrompt,
        onGenerate = {
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else model.generate()
        },
        onCancel = model::cancel,
        onOpenRecipe = onOpenRecipe,
        onNewRecipe = model::newRecipe,
        onOpenLocalAi = onOpenLocalAi,
    )
}

@Composable
fun RecipeGenerationScreen(
    state: RecipeGenerationUiState,
    onBack: () -> Unit,
    onPromptChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onCancel: () -> Unit,
    onOpenRecipe: (String) -> Unit,
    onNewRecipe: () -> Unit,
    onOpenLocalAi: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier, topBar = { BackTopAppBar(stringResource(R.string.generation_title), onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.generation_intro))
            OutlinedTextField(
                value = state.prompt,
                onValueChange = onPromptChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                enabled = !state.busy && state.slug == null,
                label = { Text(stringResource(R.string.generation_prompt)) },
                placeholder = { Text(stringResource(R.string.generation_example)) },
                supportingText = { Text("${state.prompt.length}/2000") },
            )
            Text(stringResource(R.string.generation_hint), style = MaterialTheme.typography.bodySmall)
            GenerationStatus(state, onGenerate, onCancel, onOpenRecipe, onNewRecipe, onOpenLocalAi)
        }
    }
}

@Composable
private fun GenerationStatus(
    state: RecipeGenerationUiState,
    onGenerate: () -> Unit,
    onCancel: () -> Unit,
    onOpenRecipe: (String) -> Unit,
    onNewRecipe: () -> Unit,
    onOpenLocalAi: () -> Unit,
) {
    when {
        state.running -> {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(R.string.generation_running))
            TextButton(onCancel) { Text(stringResource(R.string.action_cancel)) }
        }
        state.busy -> Text(stringResource(R.string.generation_busy))
        state.slug != null -> {
            Text(stringResource(R.string.generation_done))
            Button({ onOpenRecipe(state.slug) }) { Text(stringResource(R.string.generation_open)) }
            TextButton(onNewRecipe) { Text(stringResource(R.string.generation_another)) }
        }
        state.checkingModel -> CircularProgressIndicator()
        !state.modelReady -> {
            Text(stringResource(R.string.generation_no_model))
            Button(onOpenLocalAi) { Text(stringResource(R.string.local_ai_title)) }
        }
        else -> {
            if (state.modelFailed) Text(stringResource(R.string.generation_failed), color = MaterialTheme.colorScheme.error)
            state.error?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
            Button(onGenerate, enabled = state.canGenerate) { Text(stringResource(R.string.generation_action)) }
        }
    }
}
