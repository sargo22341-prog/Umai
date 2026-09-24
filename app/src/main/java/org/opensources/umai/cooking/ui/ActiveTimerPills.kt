package org.opensources.umai.cooking.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.opensources.umai.R
import org.opensources.umai.cooking.domain.CookingTimer
import org.opensources.umai.cooking.domain.TimerFormat

/**
 * The timers still running once the cooking mode was left, one pill each,
 * floating over the screens: the time left and the recipe. Tapping a pill
 * brings the cooking mode back on the step the timer was started from; a timer
 * that rang can be stopped right there.
 */
@Composable
fun ActiveTimerPills(
    state: ActiveTimersUiState,
    onOpen: (CookingTimer) -> Unit,
    onStop: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = !state.isEmpty,
        modifier = modifier,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.9f),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.Start) {
            state.ordered.forEach { timer ->
                TimerPill(timer = timer, now = state.now, onOpen = { onOpen(timer) }, onStop = { onStop(timer.id) })
            }
        }
    }
}

@Composable
private fun TimerPill(timer: CookingTimer, now: Long, onOpen: () -> Unit, onStop: () -> Unit) {
    val finished = timer.isFinished(now)
    val openLabel = stringResource(R.string.cooking_timer_open)
    val label = timerLabel(timer, withRecipe = true)
    Surface(
        shape = CircleShape,
        color = if (finished) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.inverseSurface,
        contentColor = if (finished) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.inverseOnSurface
        },
        shadowElevation = 6.dp,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onOpen),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = if (finished) 4.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = if (finished) Icons.Rounded.Alarm else Icons.Rounded.Timer,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = if (finished) {
                    stringResource(R.string.cooking_timer_done)
                } else {
                    TimerFormat.countdown(timer.remainingMillis(now))
                },
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(vertical = 10.dp),
            )
            Text(
                text = timer.recipe.name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = RECIPE_NAME_MAX_WIDTH),
            )
            if (finished) {
                IconButton(onClick = onStop) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cooking_timer_stop_named, label),
                    )
                }
            }
        }
    }
}

/** Keeps the pill clear of the button on the right of the recipe page. */
private val RECIPE_NAME_MAX_WIDTH = 110.dp
