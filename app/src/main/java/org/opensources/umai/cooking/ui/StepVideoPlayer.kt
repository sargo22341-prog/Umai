package org.opensources.umai.cooking.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.opensources.umai.R
import org.opensources.umai.core.ui.component.VideoAudio
import org.opensources.umai.core.ui.component.VideoFrame
import org.opensources.umai.core.ui.component.rememberVideoPlayer
import org.opensources.umai.recipe.domain.StepClip

/**
 * Plays the part of the recipe video that shows one step, over and over, so
 * the gesture can be watched again while cooking. The phone lies on the
 * worktop and the hands are busy, so the only control is the sound, off by
 * default. Once on, the player stays at full volume: the level is the one of
 * the system media stream, set with the phone's volume keys.
 */
@Composable
fun StepVideoPlayer(clip: StepClip, stepNumber: Int, modifier: Modifier = Modifier) {
    val video = rememberVideoPlayer(clip.videoUrl, clip.isHls, defaultRatio = 1f)
    val player = video.player
    var muted by rememberSaveable { mutableStateOf(true) }

    // A muted clip must not interrupt the music the cook is listening to: the
    // audio focus is only taken once the sound is on.
    LaunchedEffect(player, muted) {
        player.volume = if (muted) 0f else 1f
        player.setAudioAttributes(VideoAudio, !muted)
    }

    // Nothing on screen can pause it, so it stops by itself when the app
    // leaves the foreground.
    LifecycleStartEffect(player) {
        player.playWhenReady = true
        onStopOrDispose { player.playWhenReady = false }
    }

    // Each step starts its own chapter, then loops on it. The whole video stays
    // loaded, so moving to another step is a seek rather than a new download.
    LaunchedEffect(player, clip) {
        player.seekTo(clip.startMillis)
        // Until the step changes or leaves the screen, which cancels this effect.
        while (isActive) {
            val end = clip.endMillis
            val position = player.currentPosition
            if (position < clip.startMillis - SEEK_TOLERANCE_MS ||
                (end != null && position >= end) ||
                player.playbackState == Player.STATE_ENDED
            ) {
                player.seekTo(clip.startMillis)
            }
            delay(POLL_MS)
        }
    }

    VideoFrame(
        state = video,
        description = stringResource(R.string.cd_step_video, stepNumber),
        maxHeight = 360.dp,
        modifier = modifier,
    ) {
        VideoSoundToggle(
            muted = muted,
            onToggle = { muted = !muted },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp),
        )
    }
}

/** Sound switch laid over the bottom-right corner of a step video. */
@Composable
fun VideoSoundToggle(muted: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalIconButton(onClick = onToggle, modifier = modifier) {
        Icon(
            imageVector = if (muted) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp,
            contentDescription = stringResource(
                if (muted) R.string.cooking_video_sound_on else R.string.cooking_video_sound_off,
            ),
        )
    }
}

private val StepClip.startMillis: Long get() = (start * 1000).toLong()
private val StepClip.endMillis: Long? get() = end?.let { (it * 1000).toLong() }

private const val POLL_MS = 200L

/** The player may land slightly before the requested position. */
private const val SEEK_TOLERANCE_MS = 500L
