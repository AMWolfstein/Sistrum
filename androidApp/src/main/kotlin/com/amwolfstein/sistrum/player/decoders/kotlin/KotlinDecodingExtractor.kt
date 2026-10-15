@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.amwolfstein.sistrum.player.decoders.kotlin

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.ParserException
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SeekPoint
import androidx.media3.extractor.TrackOutput
import com.amwolfstein.sistrum.codecs.codec.adpcm.AdpcmException
import com.amwolfstein.sistrum.codecs.codec.alac.AlacException
import com.amwolfstein.sistrum.codecs.codec.ape.ApeException
import com.amwolfstein.sistrum.codecs.codec.musepack.MusepackException
import com.amwolfstein.sistrum.codecs.codec.wavpack.ErrorCode
import com.amwolfstein.sistrum.codecs.codec.wavpack.WavPackException
import com.amwolfstein.sistrum.codecs.codec.wma.WmaException
import com.amwolfstein.sistrum.codecs.codec.wmalossless.LosslessException
import com.amwolfstein.sistrum.codecs.codec.wmapro.ProException
import com.amwolfstein.sistrum.codecs.codec.wmavoice.VoiceException
import com.amwolfstein.sistrum.codecs.container.ByteBufferSource
import com.amwolfstein.sistrum.codecs.container.RandomAccessSource
import com.amwolfstein.sistrum.codecs.container.aiff.AiffException
import com.amwolfstein.sistrum.codecs.container.asf.AsfException
import java.io.IOException
import java.nio.ByteBuffer

/** Pattern A: a stream decoder emits reusable little-endian AUDIO_RAW blocks to Media3. */
internal class KotlinDecodingExtractor @JvmOverloads constructor(
    private val sniff: (ByteArray) -> Boolean,
    private val open: (RandomAccessSource) -> CodecStream,
    private val sniffBytes: Int = SNIFF_BYTES,
) : Extractor {
    private var output: ExtractorOutput? = null
    private var track: TrackOutput? = null
    private var source: ExtractorRandomAccessSource? = null
    private var stream: CodecStream? = null
    private var info: CodecStream.Info? = null
    private val pcm = ParsableByteArray(64 * 1024)
    private var bytesPerSample = 0
    private var mapper: ChannelMapper? = null
    /** Supplied by the playback factory; offline extraction keeps the source layout. */
    internal var maxOutputChannels: () -> Int = { 8 }
    private var pendingSeekUs = C.TIME_UNSET
    private var ended = false

    override fun sniff(input: ExtractorInput): Boolean {
        val header = ByteArray(sniffBytes)
        var count = 0
        input.resetPeekPosition()
        try {
            while (count < header.size) {
                val read = input.peek(header, count, header.size - count)
                if (read == C.RESULT_END_OF_INPUT) break
                if (read <= 0) throw IOException("Media3 sniff made no progress")
                count += read
            }
            return sniff(if (count == header.size) header else header.copyOf(count))
        } finally { input.resetPeekPosition() }
    }

    override fun init(output: ExtractorOutput) {
        this.output = output
        track = output.track(0, C.TRACK_TYPE_AUDIO)
        output.endTracks()
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        try {
            if (stream == null) {
                // The source must see the whole file, even if the initial load starts at a seek point.
                if (input.position != 0L) {
                    seekPosition.position = 0
                    return Extractor.RESULT_SEEK
                }
                val source = source?.also { it.bind(input) }
                    ?: ExtractorRandomAccessSource(input).also { this.source = it }
                val codec = open(source).also { stream = it }
                codec.refusal?.let { throw unsupported(it) }
                val info = codec.info
                if (info.sampleRate <= 0 || info.channels <= 0 || (info.samplesExact && info.totalSamples < 0)) {
                    throw ParserException.createForMalformedContainer("invalid codec stream info", null)
                }
                bytesPerSample = when (info.pcmEncoding) {
                    C.ENCODING_PCM_8BIT -> 1
                    C.ENCODING_PCM_16BIT -> 2
                    C.ENCODING_PCM_24BIT -> 3
                    C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
                    else -> throw ParserException.createForUnsupportedContainerFeature("unsupported codec PCM encoding")
                }
                val mapper = ChannelMapper(info, maxOutputChannels()).also { this.mapper = it }
                if (mapper.downmix) bytesPerSample = 4
                this.info = info
                checkNotNull(track).format(Format.Builder()
                    .setSampleMimeType(MimeTypes.AUDIO_RAW)
                    .setSampleRate(info.sampleRate)
                    .setChannelCount(mapper.channels)
                    .setPcmEncoding(if (mapper.downmix) C.ENCODING_PCM_FLOAT else info.pcmEncoding)
                    .setEncoderDelay(0)
                    .setEncoderPadding(0)
                    .build())
                checkNotNull(output).seekMap(SampleSeekMap(info))
                return Extractor.RESULT_CONTINUE
            }
            val source = checkNotNull(source)
            if (input.position < source.resumePosition) {
                seekPosition.position = source.resumePosition
                return Extractor.RESULT_SEEK
            }
            source.bind(input)
            val codec = checkNotNull(stream)
            codec.refusal?.let { throw unsupported(it) }
            val info = checkNotNull(info)
            if (pendingSeekUs != C.TIME_UNSET) {
                codec.seekSample(sampleAt(pendingSeekUs, info))
                pendingSeekUs = C.TIME_UNSET
            }
            if (ended) return Extractor.RESULT_END_OF_INPUT
            val block = codec.decodeBlock()
            val floats = if (block == null && codec is FloatCodecStream && info.pcmEncoding == C.ENCODING_PCM_FLOAT)
                codec.decodeFloatBlock() else null
            if (block == null && floats == null) { ended = true; return Extractor.RESULT_END_OF_INPUT }
            val channels = block?.channels ?: checkNotNull(floats).channels
            val blockFrames = block?.frames ?: checkNotNull(floats).frames
            val position = block?.position ?: checkNotNull(floats).position
            val capacity = block?.capacityFrames ?: (checkNotNull(floats).samples.size / channels)
            if (channels != info.channels || blockFrames < 0 || blockFrames > capacity ||
                position < 0 || (info.samplesExact && position > info.totalSamples)) {
                throw ParserException.createForMalformedContainer("invalid codec PCM block", null)
            }
            val frames = if (info.samplesExact) minOf(blockFrames.toLong(), info.totalSamples - position).toInt() else blockFrames
            if (frames == 0) return Extractor.RESULT_CONTINUE
            val mapper = checkNotNull(mapper)
            val sampleCount = Math.multiplyExact(frames, mapper.channels)
            val size = Math.multiplyExact(sampleCount, bytesPerSample)
            // Codec blocks can begin short and grow later (ASF superframes). Reserve the
            // borrowed buffer's full capacity once, so packing does not allocate as they grow.
            pcm.ensureCapacity(Math.multiplyExact(Math.multiplyExact(capacity, mapper.channels), bytesPerSample))
            pcm.reset(size)
            val bytes = pcm.data
            var index = 0
            var offset = 0
            while (index < sampleCount) {
                val channel = index % mapper.channels
                val frame = index / mapper.channels
                val sourceChannel = mapper.placedIndex(channel)
                val sourceIndex = frame * info.channels + sourceChannel
                val value = if (mapper.downmix) mapper.mix(channel, frame, block?.samples, floats?.samples, info.pcmEncoding, info.channels)
                    else if (sourceChannel < 0) 0
                    else block?.samples?.get(sourceIndex) ?: checkNotNull(floats).samples[sourceIndex].toRawBits()
                if (bytesPerSample == 1) {
                    // Codec buffers are signed; Media3's 8-bit PCM is unsigned.
                    bytes[offset++] = (value + 128).toByte()
                } else {
                    var byteIndex = 0
                    while (byteIndex < bytesPerSample) {
                        bytes[offset++] = (value ushr (byteIndex * 8)).toByte()
                        byteIndex++
                    }
                }
                index++
            }
            val track = checkNotNull(track)
            track.sampleData(pcm, size)
            track.sampleMetadata(timeAt(position, info.sampleRate), C.BUFFER_FLAG_KEY_FRAME, size, 0, null)
            return Extractor.RESULT_CONTINUE
        } catch (error: ParserException) {
            throw error
        } catch (error: IOException) {
            if (isUnsupported(error)) throw unsupported(error)
            throw ParserException.createForMalformedContainer(error.message ?: "Kotlin codec read failed", error)
        }
    }

    override fun seek(position: Long, timeUs: Long) {
        pendingSeekUs = timeUs.coerceAtLeast(0)
        ended = false
    }

    override fun release() {
        try { stream?.close() } finally {
            stream = null
            info = null
            try { source?.close() } finally { source = null }
        }
    }

    /** Byte position zero lets Media3 reopen its input; the cached codec performs the sample seek. */
    private class SampleSeekMap(private val info: CodecStream.Info) : SeekMap {
        override fun isSeekable() = true
        override fun getDurationUs() = if (info.totalSamples < 0) C.TIME_UNSET else timeAt(info.totalSamples, info.sampleRate)
        override fun getSeekPoints(timeUs: Long): SeekMap.SeekPoints =
            SeekMap.SeekPoints(SeekPoint(timeAt(sampleAt(timeUs, info), info.sampleRate), 0))
    }

    companion object {
        private const val SNIFF_BYTES = 4096

        /** Header-only probes ignore malformed/truncated/unrelated input, but retain typed refusals. */
        fun refusal(header: ByteArray, open: (RandomAccessSource) -> CodecStream): String? = try {
            open(ByteBufferSource(ByteBuffer.wrap(header))).use { it.refusal?.message }
        } catch (error: IOException) {
            if (isUnsupported(error)) error.message else null
        }

        // Media3's unsupported factory fixes its cause to null. Use the protected constructor
        // to preserve both the unsupported classification and the original typed refusal.
        private class RefusalException(error: Exception) : ParserException(
            error.message ?: "Kotlin codec refused stream", error, false, C.DATA_TYPE_MEDIA,
        )

        private fun unsupported(error: Exception): ParserException = RefusalException(error)

        private fun isUnsupported(error: IOException): Boolean = when (error) {
            is ChannelLayoutException -> true
            is DsdUnsupportedException -> true
            is PcmVariantUnsupportedException -> true
            is WavPackException -> error.code == ErrorCode.UNSUPPORTED
            is ApeException -> error.reason == ApeException.Reason.UNSUPPORTED
            is AlacException -> error.reason == AlacException.Reason.UNSUPPORTED
            is AdpcmException -> error.unsupported
            is MusepackException -> error.unsupported
            is AiffException -> error.unsupported
            is AsfException -> error.unsupported
            is WmaException -> error.unsupported
            is LosslessException -> error.unsupported
            is ProException -> error.unsupported
            is VoiceException -> error.unsupported
            else -> false
        }

        // Split before multiplication to keep long sample counts exact without an intermediate overflow.
        private fun timeAt(sample: Long, rate: Int): Long =
            Math.addExact(Math.multiplyExact(sample / rate, C.MICROS_PER_SECOND),
                sample % rate * C.MICROS_PER_SECOND / rate)

        private fun sampleAt(timeUs: Long, info: CodecStream.Info): Long {
            if (!info.samplesExact) {
                val time = timeUs.coerceAtLeast(0)
                return Math.addExact(Math.multiplyExact(time / C.MICROS_PER_SECOND, info.sampleRate.toLong()),
                    time % C.MICROS_PER_SECOND * info.sampleRate / C.MICROS_PER_SECOND)
            }
            val duration = timeAt(info.totalSamples, info.sampleRate)
            if (timeUs >= duration) return info.totalSamples
            val time = timeUs.coerceIn(0, duration)
            return minOf(info.totalSamples, time / C.MICROS_PER_SECOND * info.sampleRate +
                time % C.MICROS_PER_SECOND * info.sampleRate / C.MICROS_PER_SECOND)
        }
    }
}
