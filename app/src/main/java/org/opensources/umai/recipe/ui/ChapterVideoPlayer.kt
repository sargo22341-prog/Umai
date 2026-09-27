package org.opensources.umai.recipe.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.C
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.opensources.umai.R
import org.opensources.umai.core.ui.component.VideoAudio
import org.opensources.umai.core.ui.component.VideoFrame
import org.opensources.umai.core.ui.component.VideoPlayerState
import org.opensources.umai.core.ui.component.rememberVideoPlayer
import org.opensources.umai.recipe.domain.VideoStream
import org.opensources.umai.recipe.domain.VideoTime

/**
 * What the chapter rows share with the player of the video section: where it
 * is, to place a step there, and where to go, to watch a step from its start.
 */
@Stable
class ChapterPlayerState {
    /** The position of the player, updated as it plays. */
    var positionMillis by mutableLongStateOf(0L)

    /** The length of the video, once the player knows it. */
    var durationMillis by mutableLongStateOf(0L)

    /** A position the player plays from, once; it clears it on its way. */
    var seekTarget by mutableStateOf<Double?>(null)

    val positionSeconds: Double get() = VideoTime.fromMillis(positionMillis)

    fun playFrom(seconds: Double) {
        seekTarget = seconds
        positionMillis = (seconds * MILLIS_PER_SECOND).toLong()
    }
}

/**
 * The recipe video with the controls needed to find where a step starts:
 * play, pause and a position bar. Unlike the cooking mode, the sound is on:
 * the cook is listening for the moment a step begins.
 */
@Composable
fun ChapterVideoPlayer(stream: VideoStream, state: ChapterPlayerState, modifier: Modifier = Modifier) {
    val video = rememberVideoPlayer(stream.url, stream.isHls, defaultRatio = 16f / 9f)
    val player = video.player

    LaunchedEffect(player) { player.setAudioAttributes(VideoAudio, true) }

    LifecycleStartEffect(player) {
        onStopOrDispose { player.pause() }
    }

    LaunchedEffect(player) {
        // Until the player leaves the screen, which cancels this effect.
        while (isActive) {
            state.seekTarget?.let { target ->
                player.seekTo((target * MILLIS_PER_SECOND).toLong())
                player.play()
                state.seekTarget = null
            }
            state.positionMillis = player.currentPosition
            player.duration.takeIf { it != C.TIME_UNSET && it > 0 }?.let { state.durationMillis = it }
            delay(POLL_MS)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        VideoFrame(
            state = video,
            description = stringResource(R.string.cd_edit_video),
            maxHeight = MAX_PICTURE_HEIGHT,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        PlayerControls(video, state)
    }
}

/** Play or pause, and the bar to move in the video, which the player follows once released. */
@Composable
private fun PlayerControls(video: VideoPlayerState, state: ChapterPlayerState) {
    val player = video.player
    var dragged by remember { mutableStateOf<Float?>(null) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val playing = video.playing
        IconButton(onClick = { if (playing) player.pause() else player.play() }, enabled = !video.failed) {
            Icon(
                imageVector = if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                contentDescription = stringResource(if (playing) R.string.edit_video_pause else R.string.edit_video_play),
            )
        }
        val duration = state.durationMillis.coerceAtLeast(1L).toFloat()
        val position = VideoTime.format(state.positionSeconds)
        Slider(
            value = dragged ?: state.positionMillis.coerceIn(0L, duration.toLong()).toFloat(),
            onValueChange = { dragged = it },
            onValueChangeFinished = {
                dragged?.let { player.seekTo(it.toLong()) }
                dragged = null
            },
            valueRange = 0f..duration,
            enabled = !video.failed && state.durationMillis > 0,
            modifier = Modifier
                .weight(1f)
                .semantics { stateDescription = position },
        )
        Text(
            text = stringResource(
                R.string.edit_video_position,
                position,
                VideoTime.format(state.durationMillis / MILLIS_PER_SECOND.toDouble()),
            ),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

private const val POLL_MS = 250L
private const val MILLIS_PER_SECOND = 1_000L

/** Leaves room below for the chapters, even with large fonts. */
private val MAX_PICTURE_HEIGHT = 220.dp
