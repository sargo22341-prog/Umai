package org.opensources.umai.recipe.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.opensources.umai.R
import org.opensources.umai.core.di.LocalAppContainer
import org.opensources.umai.core.ui.component.message
import org.opensources.umai.core.ui.component.title

/**
 * Writes a recipe in four stages rather than in one long form, so each screen
 * stays readable on a phone. Everything typed is kept as a draft, which is why
 * leaving is never destructive.
 */
@Composable
fun RecipeCreateScreen(
    draftId: String?,
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val viewModel: RecipeCreateViewModel = viewModel(
        factory = RecipeCreateViewModel.factory(container, draftId),
        key = "recipe-create-${draftId.orEmpty()}",
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    val slug = state.createdSlug
    LaunchedEffect(slug) {
        if (slug != null) {
            viewModel.consumeCreatedSlug()
            onCreated(slug)
        }
    }

    // Bound once to the ViewModel: rebuilding the callbacks on every state
    // change would make the whole form recompose for a single keystroke.
    val actions = remember(viewModel) { RecipeCreateActions(viewModel) }

    RecipeCreateScreen(
        state = state,
        actions = actions,
        onBack = {
            viewModel.saveDraftNow()
            onBack()
        },
        modifier = modifier,
    )
}

/** The callbacks the form raises, grouped so the signature stays readable. */
@Immutable
class RecipeCreateActions(
    val onNameChange: (String) -> Unit,
    val onDescriptionChange: (String) -> Unit,
    val onServingsChange: (Int) -> Unit,
    val onPrepTimeChange: (String) -> Unit,
    val onCookTimeChange: (String) -> Unit,
    val onTotalTimeChange: (String) -> Unit,
    val onIngredientChange: (Int, String) -> Unit,
    val onAddIngredient: () -> Unit,
    val onRemoveIngredient: (Int) -> Unit,
    val onStepTitleChange: (Int, String) -> Unit,
    val onStepTextChange: (Int, String) -> Unit,
    val onAddStep: () -> Unit,
    val onRemoveStep: (Int) -> Unit,
    val onToggleCategory: (org.opensources.umai.core.model.Organizer) -> Unit,
    val onToggleTag: (org.opensources.umai.core.model.Organizer) -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onCreate: () -> Unit,
    val onSaveDraft: () -> Unit,
    val onDismissError: () -> Unit,
) {
    constructor(viewModel: RecipeCreateViewModel) : this(
        onNameChange = viewModel::onNameChange,
        onDescriptionChange = viewModel::onDescriptionChange,
        onServingsChange = viewModel::onServingsChange,
        onPrepTimeChange = viewModel::onPrepTimeChange,
        onCookTimeChange = viewModel::onCookTimeChange,
        onTotalTimeChange = viewModel::onTotalTimeChange,
        onIngredientChange = viewModel::onIngredientChange,
        onAddIngredient = viewModel::addIngredient,
        onRemoveIngredient = viewModel::removeIngredient,
        onStepTitleChange = viewModel::onStepTitleChange,
        onStepTextChange = viewModel::onStepTextChange,
        onAddStep = viewModel::addStep,
        onRemoveStep = viewModel::removeStep,
        onToggleCategory = viewModel::toggleCategory,
        onToggleTag = viewModel::toggleTag,
        onNext = viewModel::next,
        onPrevious = viewModel::previous,
        onCreate = viewModel::create,
        onSaveDraft = viewModel::saveDraftNow,
        onDismissError = viewModel::dismissError,
    )
}

/** Stateless form, driven by [RecipeCreateUiState]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeCreateScreen(
    state: RecipeCreateUiState,
    actions: RecipeCreateActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_create_recipe)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = actions.onSaveDraft) {
                        Icon(
                            imageVector = Icons.Outlined.Save,
                            contentDescription = stringResource(R.string.create_save_draft),
                        )
                    }
                },
            )
        },
        bottomBar = {
            StepControls(
                state = state,
                onPrevious = actions.onPrevious,
                onNext = actions.onNext,
                onCreate = actions.onCreate,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            StepHeader(state)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.error?.let { error ->
                    ErrorBanner(
                        message = "${error.title()}\n${error.message()}",
                        onDismiss = actions.onDismissError,
                    )
                }

                when (state.step) {
                    RecipeCreateStep.BASICS -> BasicsStep(state.draft, actions)
                    RecipeCreateStep.INGREDIENTS -> IngredientsStep(state.draft, actions)
                    RecipeCreateStep.INSTRUCTIONS -> InstructionsStep(state.draft, actions)
                    RecipeCreateStep.ORGANIZERS -> OrganizersStep(state, actions)
                }

                Spacer(Modifier.size(12.dp))
            }
        }
    }
}

@Composable
private fun StepHeader(state: RecipeCreateUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(
                R.string.create_step_position,
                state.stepNumber,
                state.stepCount,
                stringResource(state.step.labelRes()),
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        LinearProgressIndicator(
            progress = { state.stepNumber.toFloat() / state.stepCount },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StepControls(
    state: RecipeCreateUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCreate: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onPrevious,
                enabled = !state.isFirstStep,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.create_previous))
            }

            if (state.isLastStep) {
                Button(
                    onClick = onCreate,
                    enabled = state.canCreate,
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.creating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.size(10.dp))
                    }
                    Text(stringResource(R.string.create_publish))
                }
            } else {
                Button(onClick = onNext, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.create_next))
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Surface(
        onClick = onDismiss,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

internal fun RecipeCreateStep.labelRes(): Int = when (this) {
    RecipeCreateStep.BASICS -> R.string.create_step_basics
    RecipeCreateStep.INGREDIENTS -> R.string.recipe_ingredients
    RecipeCreateStep.INSTRUCTIONS -> R.string.recipe_instructions
    RecipeCreateStep.ORGANIZERS -> R.string.create_step_organizers
}
