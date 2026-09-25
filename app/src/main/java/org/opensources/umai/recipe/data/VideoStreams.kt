package org.opensources.umai.recipe.data

import org.opensources.umai.recipe.domain.VideoStream
import org.opensources.umai.youtube.data.YouTubeClient
import org.opensources.umai.youtube.domain.YouTubeLinks

/**
 * Turns the address of a recipe video into a stream the player reads. A file
 * or a playlist is read as it is; a YouTube page is looked up at YouTube each
 * time, since the streams it gives expire within hours.
 */
class VideoStreams(private val youTube: YouTubeClient) {

    /** `null` when a YouTube video cannot be read at the moment. */
    suspend fun streamFor(videoUrl: String): VideoStream? {
        YouTubeLinks.videoId(videoUrl)?.let { return youTube.stream(it) }
        return VideoStream(videoUrl, isHls = videoUrl.substringBefore('?').endsWith(".m3u8", ignoreCase = true))
    }
}
