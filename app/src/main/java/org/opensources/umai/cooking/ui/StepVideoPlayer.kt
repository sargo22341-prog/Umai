package org.opensources.umai.cooking.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
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
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
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
 * the gesture can be watched again while cooking. The phone lies on the
 * worktop and the hands are busy, so the only control is the sound, off by
 * default. Once on, the player stays at full volume: the level is the one of
 * the system media stream, set with the phone's volume keys.
 *
 * The video is read straight from its publisher, without any Mealie
 * credentials: the player makes its own requests.
 */
@OptIn(UnstableApi::class)
@Composable
fun StepVideoPlayer(clip: StepClip, stepNumber: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
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

    // A muted clip must not interrupt the music the cook is listening to: the
    // audio focus is only taken once the sound is on.
    LaunchedEffect(player, muted) {
        player.volume = if (muted) 0f else 1f
        player.setAudioAttributes(VIDEO_AUDIO, !muted)
    }

    LaunchedEffect(player, clip.videoUrl) {
        val item = MediaItem.Builder()
            .setUri(clip.videoUrl)
            .apply { if (clip.isHls) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .build()
        player.setMediaItem(item)
        player.prepare()
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
        while (true) {
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
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
            VideoSoundToggle(
                muted = muted,
                onToggle = { muted = !muted },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
            )
        }
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

private val VIDEO_AUDIO = AudioAttributes.Builder()
    .setUsage(C.USAGE_MEDIA)
    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
    .build()

private const val DEFAULT_RATIO = 1f
private const val POLL_MS = 200L

/** The player may land slightly before the requested position. */
private const val SEEK_TOLERANCE_MS = 500L
