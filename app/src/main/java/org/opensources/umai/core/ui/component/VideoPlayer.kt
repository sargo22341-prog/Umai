package org.opensources.umai.core.ui.component

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import org.opensources.umai.R

/** A video player and what the screen shows of it, which the player keeps up to date. */
@Stable
class VideoPlayerState internal constructor(val player: ExoPlayer, defaultRatio: Float) {

    /** Width over height of the picture, [defaultRatio] until the video tells. */
    var ratio by mutableFloatStateOf(defaultRatio)
        internal set

    /** The video could not be played; a new address tries again. */
    var failed by mutableStateOf(false)
        internal set

    var playing by mutableStateOf(false)
        internal set
}

/**
 * A player of the video at [url], released when it leaves the screen. The
 * video is read straight from its publisher, without any Mealie credentials:
 * the player makes its own requests.
 */
@OptIn(UnstableApi::class)
@Composable
fun rememberVideoPlayer(url: String, isHls: Boolean, defaultRatio: Float): VideoPlayerState {
    val context = LocalContext.current
    val state = remember { VideoPlayerState(ExoPlayer.Builder(context).build(), defaultRatio) }

    DisposableEffect(state) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    state.ratio = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                state.playing = isPlaying
            }

            override fun onPlayerError(error: PlaybackException) {
                state.failed = true
            }
        }
        state.player.addListener(listener)
        onDispose {
            state.player.removeListener(listener)
            state.player.release()
        }
    }

    LaunchedEffect(state, url, isHls) {
        state.failed = false
        val item = MediaItem.Builder()
            .setUri(url)
            .apply { if (isHls) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .build()
        state.player.setMediaItem(item)
        state.player.prepare()
    }
    return state
}

/**
 * The black frame a video plays in, at its own ratio and at most [maxHeight]
 * high, with [content] laid over the picture. A video that cannot be played
 * leaves a message instead.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoFrame(
    state: VideoPlayerState,
    description: String,
    maxHeight: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .aspectRatio(state.ratio, matchHeightConstraintsFirst = true)
            .clip(MaterialTheme.shapes.large)
            .background(Color.Black)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (state.failed) {
            Text(
                text = stringResource(R.string.cooking_video_failed),
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            ContentFrame(player = state.player, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            content()
        }
    }
}

/** How the sound of a video is played: as the media of a film. */
val VideoAudio: AudioAttributes = AudioAttributes.Builder()
    .setUsage(C.USAGE_MEDIA)
    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
    .build()
