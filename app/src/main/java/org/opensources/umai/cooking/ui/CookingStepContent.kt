package org.opensources.umai.cooking.ui

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.opensources.umai.R
import org.opensources.umai.core.format.IngredientText
import org.opensources.umai.core.markdown.MarkdownText
import org.opensources.umai.core.model.RecipeIngredient
import org.opensources.umai.core.ui.component.RemoteImage
import org.opensources.umai.core.ui.motion.RollingContent
import org.opensources.umai.recipe.domain.StepClip
import java.time.Duration

/**
 * The steps, one at a time. Going forward, the next step slides in over the
 * current one, which draws back more slowly as it fades: a page turned over
 * another. Going back plays it in reverse. The position and its bar stay in
 * place above, the bar running on to the new step.
 */
@Composable
internal fun CookingSteps(
    state: CookingUiState,
    clip: StepClip?,
    stepImageUrl: (String) -> String?,
    stepPhotoUrl: (String) -> String?,
    onStartTimer: (Duration) -> Unit,
    videoContent: @Composable (StepClip, Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        StepPosition(state.currentStep, state.stepCount, Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp))
        AnimatedContent(
            targetState = state,
            contentKey = { it.currentStep },
            transitionSpec = { turnStep(forward = targetState.currentStep > initialState.currentStep) },
            label = "cookingStep",
        ) { shown ->
            val number = shown.currentStep + 1
            StepContent(
                title = shown.step?.title,
                text = shown.step?.text.orEmpty(),
                media = {
                    // Only the step on screen plays its video: the one leaving keeps its pictures.
                    if (shown.currentStep == state.currentStep) clip?.let { videoContent(it, number) }
                    shown.step?.photo?.let { file -> StepImage(url = stepPhotoUrl(file), stepNumber = number) }
                    shown.step?.images.orEmpty().forEach { source -> StepImage(url = stepImageUrl(source), stepNumber = number) }
                },
                ingredients = shown.ingredientsForStep,
                scale = shown.scale,
                durations = shown.stepDurations,
                onStartTimer = onStartTimer,
                // Opaque: the two steps pass over one another.
                modifier = Modifier.background(MaterialTheme.colorScheme.background),
            )
        }
    }
}

private fun AnimatedContentTransitionScope<CookingUiState>.turnStep(forward: Boolean): ContentTransform {
    val towards = if (forward) SlideDirection.Start else SlideDirection.End
    val slide = tween<IntOffset>(STEP_TURN_MILLIS, easing = FastOutSlowInEasing)
    val fade = tween<Float>(STEP_TURN_MILLIS)
    // The step on top moves all the way; the one underneath a fraction of it, fading.
    return if (forward) {
        (slideIntoContainer(towards, slide) togetherWith
            slideOutOfContainer(towards, slide) { it / PARALLAX_FRACTION } + fadeOut(fade))
            .apply { targetContentZIndex = 1f }
    } else {
        (slideIntoContainer(towards, slide) { it / PARALLAX_FRACTION } + fadeIn(fade) togetherWith
            slideOutOfContainer(towards, slide))
            .apply { targetContentZIndex = -1f }
    }
}

// `media` is the slot of the step's video and pictures, which come above its text: not the content.
@SuppressLint("ComposableLambdaParameterNaming")
@Composable
private fun StepContent(
    title: String?,
    text: String,
    ingredients: List<RecipeIngredient>,
    scale: Double,
    durations: List<Duration>,
    onStartTimer: (Duration) -> Unit,
    modifier: Modifier = Modifier,
    media: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        title?.let { Text(text = it, style = MaterialTheme.typography.headlineSmall) }
        // The video of the step, then its photo and the pictures Mealie embeds
        // in the instruction text, extracted at mapping time. A step with none
        // of them shows none: no picture stands in for a missing one.
        media()
        if (text.isNotBlank()) {
            MarkdownText(
                markdown = text,
                style = MaterialTheme.typography.titleLarge.copy(
                    lineHeight = MaterialTheme.typography.titleLarge.lineHeight * 1.25f,
                ),
            )
        }
        if (durations.isNotEmpty()) StepTimerButtons(durations = durations, onStart = onStartTimer)
        if (ingredients.isNotEmpty()) StepIngredients(ingredients, scale)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StepPosition(stepIndex: Int, stepCount: Int, modifier: Modifier = Modifier) {
    val progress = animateFloatAsState(
        targetValue = (stepIndex + 1f) / stepCount,
        animationSpec = tween(STEP_TURN_MILLIS, easing = FastOutSlowInEasing),
        label = "stepProgress",
    )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RollingContent(stepIndex) { shown ->
            Text(
                text = stringResource(R.string.cooking_step_position, shown + 1, stepCount),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        LinearProgressIndicator(progress = { progress.value }, modifier = Modifier.fillMaxWidth())
    }
}

/** The ingredients the step uses, in the quantities of the servings chosen. */
@Composable
private fun StepIngredients(ingredients: List<RecipeIngredient>, scale: Double) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(R.string.cooking_ingredients_for_step), style = MaterialTheme.typography.titleSmall)
            ingredients.forEach { ingredient ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .size(5.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    )
                    Text(text = IngredientText.format(ingredient, scale), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
internal fun StepImage(url: String?, stepNumber: Int) {
    RemoteImage(
        url = url,
        contentDescription = stringResource(R.string.cd_step_image, stepNumber),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp, max = 320.dp)
            .clip(MaterialTheme.shapes.large),
        contentScale = ContentScale.Fit,
        placeholderIconSize = 40.dp,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StepListSheet(
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
                    leadingContent = { StepNumber(number = index + 1, current = index == currentStep) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(index) },
                )
            }
        }
    }
}

@Composable
private fun StepNumber(number: Int, current: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.size(32.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = number.toString(),
                textAlign = TextAlign.Center,
                color = if (current) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private const val STEP_TURN_MILLIS = 380

/** How far the step underneath moves: a third of the way. */
private const val PARALLAX_FRACTION = 3
