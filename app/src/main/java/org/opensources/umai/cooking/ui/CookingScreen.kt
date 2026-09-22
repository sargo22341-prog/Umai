package org.opensources.umai.cooking.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.opensources.umai.R
import org.opensources.umai.core.di.LocalAppContainer
import org.opensources.umai.core.markdown.MarkdownText
import org.opensources.umai.core.model.RecipeIngredient
import org.opensources.umai.core.ui.component.EmptyView
import org.opensources.umai.core.ui.component.LoadingView
import org.opensources.umai.core.ui.component.NetworkErrorView
import org.opensources.umai.core.ui.component.RemoteImage

/**
 * Step-by-step cooking mode: one step fills the screen, navigation is reduced
 * to two large touch targets, and the screen is kept awake while the mode is
 * open so the phone can be put down on the worktop.
 */
@Composable
fun CookingScreen(
    slug: String,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val viewModel: CookingViewModel =
        viewModel(factory = CookingViewModel.factory(container, slug), key = "cooking-$slug")
    val state by viewModel.state.collectAsStateWithLifecycle()

    CookingScreen(
        state = state,
        onExit = onExit,
        onPrevious = viewModel::previous,
        onNext = viewModel::next,
        onGoToStep = viewModel::goToStep,
        onRetry = viewModel::load,
        stepImageUrl = { source ->
            container.imageUrls.stepImage(state.recipe?.id.orEmpty(), source)
        },
        modifier = modifier,
    )
}

/** Stateless cooking mode, driven by [CookingUiState]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookingScreen(
    state: CookingUiState,
    onExit: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onGoToStep: (Int) -> Unit,
    onRetry: () -> Unit,
    stepImageUrl: (String) -> String?,
    modifier: Modifier = Modifier,
) {
    var stepListVisible by remember { mutableStateOf(false) }

    KeepScreenOn(enabled = state.keepScreenOn)

    if (stepListVisible) {
        StepListSheet(
            steps = state.steps.map { it.title ?: it.text },
            currentStep = state.currentStep,
            onSelect = {
                onGoToStep(it)
                stepListVisible = false
            },
            onDismiss = { stepListVisible = false },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.recipe?.name ?: stringResource(R.string.cooking_title),
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.cooking_exit),
                        )
                    }
                },
                actions = {
                    if (state.stepCount > 0) {
                        IconButton(onClick = { stepListVisible = true }) {
                            Icon(
                                imageVector = Icons.Outlined.FormatListNumbered,
                                contentDescription = stringResource(R.string.cooking_steps),
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (state.stepCount > 0) {
                CookingControls(
                    hasPrevious = state.hasPrevious,
                    isLastStep = state.isLastStep,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onFinish = onExit,
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val error = state.error
        when {
                state.loading -> LoadingView()

                error != null -> NetworkErrorView(
                    error = error,
                    modifier = Modifier.fillMaxSize(),
                    onRetry = onRetry,
                )

                state.stepCount == 0 -> EmptyView(
                    title = stringResource(R.string.cooking_title),
                    message = stringResource(R.string.cooking_no_steps),
                    modifier = Modifier.fillMaxSize(),
                )

                else -> StepContent(
                    stepIndex = state.currentStep,
                    stepCount = state.stepCount,
                    title = state.step?.title,
                    text = state.step?.text.orEmpty(),
                    imageSources = state.step?.images.orEmpty(),
                    stepImageUrl = stepImageUrl,
                    ingredients = state.ingredientsForStep,
                )
            }
        }
    }
}

@Composable
private fun StepContent(
    stepIndex: Int,
    stepCount: Int,
    title: String?,
    text: String,
    imageSources: List<String>,
    stepImageUrl: (String) -> String?,
    ingredients: List<RecipeIngredient>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.cooking_step_position, stepIndex + 1, stepCount),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            LinearProgressIndicator(
                progress = { (stepIndex + 1f) / stepCount },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        title?.let {
            Text(text = it, style = MaterialTheme.typography.headlineSmall)
        }

        // Mealie embeds step pictures inside the instruction text; they are
        // extracted at mapping time and shown here as real images.
        imageSources.forEach { source ->
            RemoteImage(
                url = stepImageUrl(source),
                contentDescription = stringResource(R.string.cd_step_image, stepIndex + 1),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 320.dp)
                    .clip(MaterialTheme.shapes.large),
                contentScale = ContentScale.Fit,
                placeholderIconSize = 40.dp,
            )
        }

        if (text.isNotBlank()) {
            MarkdownText(
                markdown = text,
                style = MaterialTheme.typography.titleLarge.copy(
                    lineHeight = MaterialTheme.typography.titleLarge.lineHeight * 1.25f,
                ),
            )
        }

        if (ingredients.isNotEmpty()) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.cooking_ingredients_for_step),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    ingredients.forEach { ingredient ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .size(5.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                            )
                            Text(
                                text = ingredient.display,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CookingControls(
    hasPrevious: Boolean,
    isLastStep: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
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
                enabled = hasPrevious,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp),
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.cooking_previous))
            }

            Button(
                onClick = if (isLastStep) onFinish else onNext,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp),
            ) {
                Text(
                    stringResource(
                        if (isLastStep) R.string.cooking_finish else R.string.cooking_next,
                    ),
                )
                if (!isLastStep) {
                    Spacer(Modifier.size(8.dp))
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepListSheet(
    steps: List<String>,
    currentStep: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .fillMaxHeight(0.8f)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.cooking_steps),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            )
            HorizontalDivider()
            steps.forEachIndexed { index, label ->
                ListItem(
                    headlineContent = {
                        Text(
                            text = label.lineSequence().firstOrNull().orEmpty().ifBlank {
                                stringResource(R.string.cooking_step_position, index + 1, steps.size)
                            },
                            maxLines = 2,
                        )
                    },
                    leadingContent = {
                        Surface(
                            shape = CircleShape,
                            color = if (index == currentStep) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = (index + 1).toString(),
                                    textAlign = TextAlign.Center,
                                    color = if (index == currentStep) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(index) },
                )
            }
        }
    }
}

/**
 * Keeps the display awake for as long as this composable is on screen, and
 * restores the normal behaviour on exit even if the screen is left abruptly.
 */
@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled, view) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}
