package org.opensources.umai.cooking.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.opensources.umai.R
import org.opensources.umai.cooking.domain.CookingTimer
import org.opensources.umai.cooking.domain.TimerFormat
import org.opensources.umai.core.ui.motion.scaledBy
import java.time.Duration

/** One button per duration written in the step: "Start 15 min". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StepTimerButtons(durations: List<Duration>, onStart: (Duration) -> Unit) {
    val haptics = LocalHapticFeedback.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        durations.forEach { duration ->
            AssistChip(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onStart(duration)
                },
                label = { Text(stringResource(R.string.cooking_timer_start, durationLabel(duration))) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                    )
                },
            )
        }
    }
}

/**
 * Every timer of the app, whatever the step on screen and whatever the recipe:
 * each counts down on its own, can be paused or cancelled, and one that reached
 * zero rings until it is stopped. A timer of another recipe is named after it.
 * Tapping a timer opens the step it was started from.
 */
@Composable
internal fun TimersPanel(
    timers: List<CookingTimer>,
    now: Long,
    recipeSlug: String?,
    notificationsAllowed: Boolean,
    onOpen: (CookingTimer) -> Unit,
    onPause: (Int) -> Unit,
    onResume: (Int) -> Unit,
    onDismiss: (Int) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column {
            if (!notificationsAllowed) {
                Text(
                    text = stringResource(R.string.cooking_timer_notifications_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                HorizontalDivider()
            }
            timers.forEachIndexed { index, timer ->
                if (index > 0) HorizontalDivider()
                TimerRow(
                    timer = timer,
                    now = now,
                    showRecipe = timer.recipe.slug != recipeSlug,
                    onOpen = { onOpen(timer) },
                    onPause = { onPause(timer.id) },
                    onResume = { onResume(timer.id) },
                    onDismiss = { onDismiss(timer.id) },
                )
            }
        }
    }
}

@Composable
private fun TimerRow(
    timer: CookingTimer,
    now: Long,
    showRecipe: Boolean,
    onOpen: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDismiss: () -> Unit,
) {
    val finished = timer.isFinished(now)
    val label = timerLabel(timer, showRecipe)
    Surface(
        color = if (finished) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (finished) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = stringResource(R.string.cooking_timer_open), onClick = onOpen)
                .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TimerDial(timer, now)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (finished) {
                    Text(
                        text = stringResource(R.string.cooking_timer_done),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                } else {
                    Text(text = TimerFormat.countdown(timer.remainingMillis(now)), style = MaterialTheme.typography.titleLarge)
                }
            }
            if (finished) {
                FilledTonalButton(onClick = onDismiss) { Text(stringResource(R.string.cooking_timer_stop)) }
            } else {
                TimerButtons(running = timer.isRunning, label = label, onPause = onPause, onResume = onResume, onDismiss = onDismiss)
            }
        }
    }
}

/**
 * The icon of a timer in a ring that fills as it counts down, smoothly between
 * the seconds. Over the last ten seconds it beats once a second; at zero it
 * turns into an alarm clock.
 */
@Composable
private fun TimerDial(timer: CookingTimer, now: Long) {
    val finished = timer.isFinished(now)
    val progress = animateFloatAsState(
        targetValue = timer.progress(now),
        // The clock moves once a second: the ring runs at the same pace in between.
        animationSpec = if (timer.isRunning) tween(DIAL_TICK_MILLIS.toInt(), easing = LinearEasing) else snap(),
        label = "timerDial",
    )
    val beat = remember { Animatable(1f) }
    val secondsLeft = ((timer.remainingMillis(now) + DIAL_TICK_MILLIS - 1) / DIAL_TICK_MILLIS).toInt()
    LaunchedEffect(secondsLeft, timer.isRunning) {
        if (!timer.isRunning || secondsLeft !in 1..FINAL_SECONDS) return@LaunchedEffect
        beat.animateTo(BEAT_SCALE, tween(BEAT_MILLIS, easing = FastOutSlowInEasing))
        beat.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }
    Box(modifier = Modifier.size(DIAL_SIZE).scaledBy(beat.asState()), contentAlignment = Alignment.Center) {
        if (!finished) {
            CircularProgressIndicator(
                progress = { progress.value },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 3.dp,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
            )
        }
        Icon(
            imageVector = if (finished) Icons.Outlined.Alarm else Icons.Outlined.Timer,
            contentDescription = null,
            modifier = Modifier.size(if (finished) 24.dp else 18.dp),
        )
    }
}

@Composable
private fun TimerButtons(running: Boolean, label: String, onPause: () -> Unit, onResume: () -> Unit, onDismiss: () -> Unit) {
    IconButton(onClick = if (running) onPause else onResume) {
        Icon(
            imageVector = if (running) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
            contentDescription = stringResource(if (running) R.string.cooking_timer_pause else R.string.cooking_timer_resume, label),
        )
    }
    IconButton(onClick = onDismiss) {
        Icon(imageVector = Icons.Outlined.Close, contentDescription = stringResource(R.string.cooking_timer_cancel, label))
    }
}

/** "Step 2 · 15 min", preceded by the recipe name when [withRecipe]. */
@Composable
internal fun timerLabel(timer: CookingTimer, withRecipe: Boolean): String {
    val step = stringResource(R.string.cooking_timer_label, timer.stepIndex + 1, durationLabel(timer.duration))
    return if (withRecipe) stringResource(R.string.cooking_timer_of_recipe, timer.recipe.name, step) else step
}

/** "1 h 30", "15 min", "1 min 30 s", "45 s". */
@Composable
private fun durationLabel(duration: Duration): String = TimerFormat.duration(
    duration,
    TimerFormat.Units(
        hour = stringResource(R.string.unit_hour_short),
        minute = stringResource(R.string.unit_minute_short),
        second = stringResource(R.string.unit_second_short),
    ),
)

private const val DIAL_TICK_MILLIS = 1_000L
private val DIAL_SIZE = 36.dp

/** The countdown beats over its last seconds, once each. */
private const val FINAL_SECONDS = 10
private const val BEAT_MILLIS = 120
private const val BEAT_SCALE = 1.18f
