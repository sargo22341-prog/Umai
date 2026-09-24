package org.opensources.umai.cooking.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.opensources.umai.R
import org.opensources.umai.cooking.domain.CookingTimer
import java.time.Duration
import java.util.Locale

/** One button per duration written in the step: "Start 15 min". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StepTimerButtons(durations: List<Duration>, onStart: (Duration) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        durations.forEach { duration ->
            AssistChip(
                onClick = { onStart(duration) },
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
 * Every timer started in the cooking mode, whatever the step on screen: each
 * counts down on its own, can be paused or cancelled, and one that reached zero
 * rings until it is stopped here.
 */
@Composable
internal fun TimersPanel(
    timers: List<CookingTimer>,
    now: Long,
    onPause: (Int) -> Unit,
    onResume: (Int) -> Unit,
    onDismiss: (Int) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column {
            timers.forEachIndexed { index, timer ->
                if (index > 0) HorizontalDivider()
                TimerRow(
                    timer = timer,
                    now = now,
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
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDismiss: () -> Unit,
) {
    val finished = timer.isFinished(now)
    val label = stringResource(R.string.cooking_timer_label, timer.stepIndex + 1, durationLabel(timer.duration))
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
                .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(imageVector = if (finished) Icons.Outlined.Alarm else Icons.Outlined.Timer, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.labelMedium)
                if (finished) {
                    Text(
                        text = stringResource(R.string.cooking_timer_done),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                } else {
                    Text(text = countdown(timer.remainingMillis(now)), style = MaterialTheme.typography.titleLarge)
                }
            }
            if (finished) {
                FilledTonalButton(onClick = onDismiss) { Text(stringResource(R.string.cooking_timer_stop)) }
            } else {
                IconButton(onClick = if (timer.isRunning) onPause else onResume) {
                    Icon(
                        imageVector = if (timer.isRunning) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = stringResource(
                            if (timer.isRunning) R.string.cooking_timer_pause else R.string.cooking_timer_resume,
                            label,
                        ),
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.cooking_timer_cancel, label),
                    )
                }
            }
        }
    }
}

/** "1 h 30", "15 min", "1 min 30 s", "45 s". */
@Composable
private fun durationLabel(duration: Duration): String {
    val hours = duration.toHours()
    val minutes = duration.toMinutesPart()
    val seconds = duration.toSecondsPart()
    val hourUnit = stringResource(R.string.unit_hour_short)
    val minuteUnit = stringResource(R.string.unit_minute_short)
    val secondUnit = stringResource(R.string.unit_second_short)
    return buildList {
        if (hours > 0) add("$hours $hourUnit")
        if (minutes > 0) add(if (hours > 0 && seconds == 0) "$minutes" else "$minutes $minuteUnit")
        if (seconds > 0) add("$seconds $secondUnit")
    }.joinToString(" ")
}

/** What is left, rounded up to the second: "1:05:00", "14:59", "0:07". */
private fun countdown(millis: Long): String {
    val total = (millis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND
    val hours = total / 3_600
    val minutes = total % 3_600 / 60
    val seconds = total % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

private const val MILLIS_PER_SECOND = 1_000L
