package org.opensources.umai.youtube.data

import android.graphics.Bitmap
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.opensources.umai.youtube.domain.SoundPiece
import org.opensources.umai.youtube.domain.SpeechSound
import org.opensources.umai.youtube.domain.VideoMedia
import org.opensources.umai.youtube.domain.VideoPicture
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reads the sound and the pictures of a video with the decoders of the phone.
 * The files are read from their address as the player reads them, in the
 * places needed only: a few seconds of pictures, the sound as it is decoded.
 */
class AndroidVideoMedia : VideoMedia {

    override fun sound(url: String, pieceSeconds: Int, maxSeconds: Int): Flow<SoundPiece> = flow {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(url)
            val track = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return@flow
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val decoder = MediaCodec.createDecoderByType(checkNotNull(format.getString(MediaFormat.KEY_MIME)))
            decoder.configure(format, null, null, 0)
            decoder.start()
            codec = decoder
            Decoding(extractor, decoder, format, pieceSeconds, maxSeconds).run(this)
        } finally {
            codec?.run {
                stop()
                release()
            }
            extractor.release()
        }
    }.flowOn(Dispatchers.IO)

    override fun pictures(url: String, seconds: List<Double>): Flow<VideoPicture> = flow {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(url, emptyMap())
            for (second in seconds) {
                val frame = retriever.getScaledFrameAtTime(
                    (second * MICROS_PER_SECOND).toLong(),
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    MAX_PICTURE_SIDE,
                    MAX_PICTURE_SIDE,
                ) ?: continue
                val jpeg = ByteArrayOutputStream().use { out ->
                    frame.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                    out.toByteArray()
                }
                frame.recycle()
                emit(VideoPicture(second, jpeg))
            }
        } finally {
            retriever.release()
        }
    }.flowOn(Dispatchers.IO)

    /** One decoding of a sound track, cut into pieces as it goes. */
    private class Decoding(
        private val extractor: MediaExtractor,
        private val codec: MediaCodec,
        format: MediaFormat,
        private val pieceSeconds: Int,
        private val maxSeconds: Int,
    ) {
        private var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        private var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        private var floats = false
        private var pending = ShortArray(0)
        private var pendingSize = 0
        private var pieceStart = 0.0

        suspend fun run(out: FlowCollector<SoundPiece>) {
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            while (true) {
                if (!inputDone) inputDone = feed()
                val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outputFormat(codec.outputFormat)
                    index >= 0 -> {
                        codec.getOutputBuffer(index)?.let { buffer ->
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            append(buffer.order(ByteOrder.nativeOrder()))
                        }
                        codec.releaseOutputBuffer(index, false)
                        while (pendingSize >= rate * pieceSeconds) out.emit(piece(rate * pieceSeconds))
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
            // The end of the sound, when it is long enough to say something.
            if (pendingSize >= rate * MIN_LAST_PIECE_SECONDS) out.emit(piece(pendingSize))
        }

        /** Gives the decoder the next compressed sample; true once there is none left to give. */
        private fun feed(): Boolean {
            val index = codec.dequeueInputBuffer(TIMEOUT_US)
            if (index < 0) return false
            val buffer = codec.getInputBuffer(index) ?: return false
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0 || extractor.sampleTime > maxSeconds * MICROS_PER_SECOND) {
                codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                return true
            }
            codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
            extractor.advance()
            return false
        }

        private fun outputFormat(format: MediaFormat) {
            rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            floats = format.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                format.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
        }

        /** Adds the decoded samples, their channels mixed into one. */
        private fun append(buffer: ByteBuffer) {
            val frames = if (floats) buffer.remaining() / (Float.SIZE_BYTES * channels) else buffer.remaining() / (Short.SIZE_BYTES * channels)
            if (pending.size < pendingSize + frames) pending = pending.copyOf(maxOf(pending.size * 2, pendingSize + frames))
            if (floats) {
                val samples = buffer.asFloatBuffer()
                for (frame in 0 until frames) {
                    var sum = 0f
                    for (channel in 0 until channels) sum += samples.get(frame * channels + channel)
                    pending[pendingSize++] = (sum / channels * Short.MAX_VALUE).toInt().coerceIn(-32_768, 32_767).toShort()
                }
            } else {
                val samples = buffer.asShortBuffer()
                for (frame in 0 until frames) {
                    var sum = 0
                    for (channel in 0 until channels) sum += samples.get(frame * channels + channel)
                    pending[pendingSize++] = (sum / channels).toShort()
                }
            }
        }

        /** The first [size] samples waiting, as one piece of sound. */
        private fun piece(size: Int): SoundPiece {
            val samples = pending.copyOf(size)
            pending.copyInto(pending, 0, size, pendingSize)
            pendingSize -= size
            val start = pieceStart
            pieceStart += size.toDouble() / rate
            return SoundPiece(start, pieceStart, SpeechSound.wav(SpeechSound.resample(samples, rate)))
        }
    }

    private companion object {
        const val MICROS_PER_SECOND = 1_000_000L
        const val TIMEOUT_US = 10_000L
        const val MIN_LAST_PIECE_SECONDS = 2
        const val MAX_PICTURE_SIDE = 768
        const val JPEG_QUALITY = 85
    }
}
