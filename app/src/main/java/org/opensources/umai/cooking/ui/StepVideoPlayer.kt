package org.opensources.umai.cooking.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import kotlinx.coroutines.delay
import org.opensources.umai.R
import org.opensources.umai.recipe.domain.StepClip

/**
 * Plays the part of the recipe video that shows one step, over and over, so
 * the gesture can be watched again while cooking. It starts muted: the phone
 * lies on the worktop, and sound is a tap away.
 *
 * The video is read straight from its publisher, without any Mealie
 * credentials: the player makes its own requests.
 */
@OptIn(UnstableApi::class)
@Composable
fun StepVideoPlayer(clip: StepClip, stepNumber: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    var playing by remember { mutableStateOf(true) }
    var muted by rememberSaveable { mutableStateOf(true) }
    var ratio by remember { mutableFloatStateOf(DEFAULT_RATIO) }
    var failed by remember(clip.videoUrl) { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    ratio = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                failed = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(clip.videoUrl) {
        player.setMediaItem(MediaItem.fromUri(clip.videoUrl))
        player.prepare()
    }

    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }

    // Each step starts its own chapter, then loops on it.
    LaunchedEffect(clip) {
        player.seekTo(clip.startMillis)
        playing = true
        while (true) {
            player.playWhenReady = playing
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

    val description = stringResource(R.string.cd_step_video, stepNumber)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp)
            .aspectRatio(ratio, matchHeightConstraintsFirst = true)
            .clip(MaterialTheme.shapes.large)
            .background(Color.Black)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (failed) {
            Text(
                text = stringResource(R.string.cooking_video_failed),
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            ContentFrame(
                player = player,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { playing = !playing },
                contentScale = ContentScale.Fit,
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalIconButton(onClick = { player.seekTo(clip.startMillis) }) {
                    Icon(Icons.Outlined.Replay, contentDescription = stringResource(R.string.cooking_video_replay))
                }
                FilledTonalIconButton(onClick = { playing = !playing }) {
                    Icon(
                        imageVector = if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = stringResource(if (playing) R.string.cooking_video_pause else R.string.cooking_video_play),
                    )
                }
                FilledTonalIconButton(onClick = { muted = !muted }) {
                    Icon(
                        imageVector = if (muted) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp,
                        contentDescription = stringResource(if (muted) R.string.cooking_video_unmute else R.string.cooking_video_mute),
                    )
                }
            }
        }
    }
}

private val StepClip.startMillis: Long get() = (start * 1000).toLong()
private val StepClip.endMillis: Long? get() = end?.let { (it * 1000).toLong() }

private const val DEFAULT_RATIO = 1f
private const val POLL_MS = 200L

/** The player may land slightly before the requested position. */
private const val SEEK_TOLERANCE_MS = 500L
