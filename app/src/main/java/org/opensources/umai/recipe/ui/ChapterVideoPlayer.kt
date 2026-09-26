package org.opensources.umai.recipe.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.semantics.stateDescription
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
@OptIn(UnstableApi::class)
@Composable
fun ChapterVideoPlayer(stream: VideoStream, state: ChapterPlayerState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    var ratio by remember { mutableFloatStateOf(DEFAULT_RATIO) }
    var failed by remember(stream.url) { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    /** The position being dragged on the bar, which the player only follows once released. */
    var dragged by remember { mutableStateOf<Float?>(null) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    ratio = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlayerError(error: PlaybackException) {
                failed = true
            }
        }
        player.addListener(listener)
        player.setAudioAttributes(VIDEO_AUDIO, true)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player, stream) {
        val item = MediaItem.Builder()
            .setUri(stream.url)
            .apply { if (stream.isHls) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .build()
        player.setMediaItem(item)
        player.prepare()
    }

    LifecycleStartEffect(player) {
        onStopOrDispose { player.pause() }
    }

    LaunchedEffect(player) {
        while (true) {
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

    val description = stringResource(R.string.cd_edit_video)
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = MAX_PICTURE_HEIGHT)
                .aspectRatio(ratio, matchHeightConstraintsFirst = true)
                .align(Alignment.CenterHorizontally)
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
                ContentFrame(player = player, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = { if (playing) player.pause() else player.play() }, enabled = !failed) {
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
                enabled = !failed && state.durationMillis > 0,
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
}

private val VIDEO_AUDIO = AudioAttributes.Builder()
    .setUsage(C.USAGE_MEDIA)
    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
    .build()

private const val DEFAULT_RATIO = 16f / 9f
private const val POLL_MS = 250L
private const val MILLIS_PER_SECOND = 1_000L

/** Leaves room below for the chapters, even with large fonts. */
private val MAX_PICTURE_HEIGHT = 220.dp
