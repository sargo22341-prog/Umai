package org.opensources.umai.planning.ui

import androidx.activity.compose.BackHandler
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
import org.opensources.umai.core.format.currentLocale
import org.opensources.umai.core.image.CropRegion
import org.opensources.umai.core.model.MealType
import org.opensources.umai.core.ui.component.message
import org.opensources.umai.core.ui.component.title
import org.opensources.umai.planning.domain.FoodNoteLabels
import org.opensources.umai.planning.domain.FoodUnit
import org.opensources.umai.planning.domain.Nutrient
import org.opensources.umai.recipe.ui.ErrorBanner
import java.time.LocalDate

/** What the food form asks of its screen, grouped so the signatures stay readable. */
class FoodEntryActions(
    val onLeave: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onAdd: () -> Unit,
    val onNameChange: (String) -> Unit,
    val onMealTypeChange: (MealType) -> Unit,
    val onPhotoPicked: (sourceUri: String, region: CropRegion) -> Unit,
    val onRemovePhoto: () -> Unit,
    val onReadLabel: (sourceUri: String) -> Unit,
    val onCancelReading: () -> Unit,
    val onDismissLabelIssue: () -> Unit,
    val onUnitChange: (FoodUnit) -> Unit,
    val onValueChange: (Nutrient, String) -> Unit,
    val onQuantityChange: (String) -> Unit,
    val onDismissError: () -> Unit,
)

/**
 * Adds a food to one day of the plan in three stages, as a recipe is written:
 * the product, its nutrition facts, the portion eaten. The back gesture goes
 * one stage back, then leaves.
 */
@Composable
fun FoodEntryRoute(
    date: LocalDate,
    onBack: () -> Unit,
    onAdded: (FoodAdded) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val viewModel: FoodEntryViewModel = viewModel(
        factory = FoodEntryViewModel.factory(container, date),
        key = "food-entry-$date",
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.added) {
        state.added?.let(onAdded)
    }

    BackHandler { if (!viewModel.previous()) onBack() }

    val labels = rememberFoodNoteLabels()
    val actions = remember(viewModel, labels) {
        FoodEntryActions(
            onLeave = onBack,
            onPrevious = { viewModel.previous() },
            onNext = viewModel::next,
            onAdd = { viewModel.save(labels) },
            onNameChange = viewModel::setName,
            onMealTypeChange = viewModel::setMealType,
            onPhotoPicked = viewModel::setPhoto,
            onRemovePhoto = viewModel::removePhoto,
            onReadLabel = viewModel::readLabel,
            onCancelReading = viewModel::cancelReading,
            onDismissLabelIssue = viewModel::dismissLabelIssue,
            onUnitChange = viewModel::setUnit,
            onValueChange = viewModel::setValue,
            onQuantityChange = viewModel::setQuantity,
            onDismissError = viewModel::dismissError,
        )
    }

    FoodEntryScreen(state = state, actions = actions, modifier = modifier)
}

/** The words of the note written to Mealie, in the language of the app. */
@Composable
private fun rememberFoodNoteLabels(): FoodNoteLabels {
    val locale = currentLocale()
    val names = Nutrient.entries.associateWith { stringResource(it.labelRes()) }
    return remember(locale, names) { FoodNoteLabels(locale, names) }
}

/** Stateless form, driven by [FoodEntryUiState]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodEntryScreen(
    state: FoodEntryUiState,
    actions: FoodEntryActions,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.food_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onLeave) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        bottomBar = { StepControls(state, actions) },
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
                    FoodEntryStep.PRODUCT -> ProductSection(state, actions)
                    FoodEntryStep.NUTRITION -> NutritionSection(state, actions)
                    FoodEntryStep.PORTION -> PortionSection(state, actions)
                }

                Spacer(Modifier.size(12.dp))
            }
        }
    }
}

@Composable
private fun StepHeader(state: FoodEntryUiState) {
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
private fun StepControls(state: FoodEntryUiState, actions: FoodEntryActions) {
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
                onClick = actions.onPrevious,
                enabled = !state.isFirstStep && !state.saving,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.create_previous))
            }

            if (state.isLastStep) {
                Button(onClick = actions.onAdd, enabled = state.canSave, modifier = Modifier.weight(1f)) {
                    if (state.saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.size(10.dp))
                    }
                    Text(stringResource(R.string.action_add))
                }
            } else {
                Button(onClick = actions.onNext, enabled = state.canGoOn, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.create_next))
                }
            }
        }
    }
}

private fun FoodEntryStep.labelRes(): Int = when (this) {
    FoodEntryStep.PRODUCT -> R.string.food_step_product
    FoodEntryStep.NUTRITION -> R.string.food_step_nutrition
    FoodEntryStep.PORTION -> R.string.food_step_portion
}
