package me.misa198.airmedy.analysis

import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessingPipeline
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ToFloatPcmAudioProcessor
import androidx.media3.exoplayer.audio.TrimmingAudioProcessor
import com.google.common.collect.ImmutableList
import java.nio.ByteBuffer
import java.nio.ByteOrder
import me.misa198.airmedy.player.dsp.PcmToFloatProcessor

/**
 * Receives decoded interleaved float PCM; [frames] frames are valid in [samples]
 * (length >= frames * channels).
 */
internal fun interface PcmConsumer {
    fun accept(samples: FloatArray, frames: Int, sampleRate: Int, channels: Int)
}

/**
 * An [AudioSink] that never touches an AudioTrack: it converts every linear-PCM
 * buffer to float with the same pipeline as playback (trimming → bulk PCM-to-float
 * → `ToFloatPcmAudioProcessor`) and hands the frames straight to [consumer]. It
 * consumes every buffer immediately and reports [getCurrentPositionUs] from the
 * frames written, so a player driving it is never throttled to real time.
 */
@OptIn(UnstableApi::class)
internal class CaptureAudioSink(
    private val consumer: PcmConsumer,
    private val startPositionUs: Long = 0L,
) : AudioSink {

    private val trimming = TrimmingAudioProcessor()

    private val pipeline = AudioProcessingPipeline(
        ImmutableList.copyOf(
            listOf<AudioProcessor>(trimming, PcmToFloatProcessor(), ToFloatPcmAudioProcessor()),
        ),
    )

    /** True once [configure] ran on a linear-PCM format (and reset() has not followed). */
    private var pcm = false

    /** True when the pipeline has at least one active processor (non-float or trimmed input). */
    private var operational = false

    private var configured = false
    private var dataHandled = false
    private var rate = 0
    private var channels = 0
    private var frameCount = 0L
    private var totalFrames = 0L
    private var startUs = AudioSink.CURRENT_POSITION_NOT_SET
    private var started = false
    private var ended = false
    private var formatChangedFlag = false
    private var scratch = FloatArray(0)
    private var playbackParameters = PlaybackParameters.DEFAULT
    private var audioAttributes = AudioAttributes.DEFAULT
    private var skipSilenceEnabled = false
    private var outputStreamOffsetUs = 0L

    /** Total frames handed to [consumer], for the decoder's [DecodeOutcome.Completed]. */
    val framesWritten: Long get() = frameCount

    /** The sink's configured output sample rate (0 before the first configure). */
    val sampleRate: Int get() = rate

    /** The sink's configured output channel count (0 before the first configure). */
    val channelCount: Int get() = channels

    /** True when a mid-stream configure changed the rate or channel count after data was handled. */
    val formatChanged: Boolean get() = formatChangedFlag

    override fun setListener(listener: AudioSink.Listener) = Unit

    override fun supportsFormat(format: Format): Boolean =
        getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED

    override fun getFormatSupport(format: Format): Int {
        if (!isLinearPcm(format)) return AudioSink.SINK_FORMAT_UNSUPPORTED
        return if (format.pcmEncoding == C.ENCODING_PCM_FLOAT) {
            AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
        } else {
            AudioSink.SINK_FORMAT_SUPPORTED_WITH_TRANSCODING
        }
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long =
        if (!started) AudioSink.CURRENT_POSITION_NOT_SET
        else startUs + frameCount * C.MICROS_PER_SECOND / rate

    /** Records the renderer's output-stream offset so [deliver] can map renderer time back to media time. */
    override fun setOutputStreamOffsetUs(outputStreamOffsetUs: Long) {
        this.outputStreamOffsetUs = outputStreamOffsetUs
    }

    override fun configure(config: AudioSink.AudioSinkConfig) {
        val format = config.format
        if (!isLinearPcm(format)) {
            throw AudioSink.ConfigurationException("non-PCM format", format)
        }
        if (dataHandled && (format.sampleRate != rate || format.channelCount != channels)) {
            formatChangedFlag = true
        }
        trimming.setTrimFrameCount(format.encoderDelay, format.encoderPadding)
        val output = try {
            val out = pipeline.configure(AudioProcessor.AudioFormat(format))
            pipeline.flush(AudioProcessor.StreamMetadata.DEFAULT)
            out
        } catch (e: AudioProcessor.UnhandledAudioFormatException) {
            throw AudioSink.ConfigurationException(e, format)
        }
        rate = output.sampleRate
        channels = output.channelCount
        pcm = true
        operational = pipeline.isOperational()
        configured = true
        ended = false
        startUs = AudioSink.CURRENT_POSITION_NOT_SET
        started = false
    }

    override fun play() = Unit

    override fun handleDiscontinuity() = Unit

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int,
    ): Boolean {
        if (!pcm || !configured) {
            buffer.position(buffer.limit())
            return true
        }
        dataHandled = true
        if (!started) {
            startUs = presentationTimeUs
            started = true
        }
        if (formatChangedFlag) {
            buffer.position(buffer.limit())
            return true
        }
        if (!operational) {
            val sampleCount = buffer.remaining() / BYTES_PER_FLOAT_SAMPLE
            if (sampleCount > 0) {
                ensureScratch(sampleCount)
                buffer.duplicate().order(ByteOrder.nativeOrder()).asFloatBuffer().get(scratch, 0, sampleCount)
                buffer.position(buffer.limit())
                deliver(sampleCount)
            }
            return true
        }
        pipeline.queueInput(buffer)
        var totalSamples = 0
        var output = pipeline.getOutput()
        while (output.hasRemaining()) {
            val sampleCount = output.remaining() / BYTES_PER_FLOAT_SAMPLE
            ensureScratch(totalSamples + sampleCount)
            output.order(ByteOrder.nativeOrder()).asFloatBuffer().get(scratch, totalSamples, sampleCount)
            output.position(output.limit())
            totalSamples += sampleCount
            output = pipeline.getOutput()
        }
        buffer.position(buffer.limit())
        if (totalSamples > 0) deliver(totalSamples)
        return true
    }

    override fun playToEndOfStream() {
        ended = true
    }

    override fun isEnded(): Boolean = ended

    /**
     * True from the first buffer until end of stream. The sink drains every buffer at once, so without
     * this the renderer is never "ready", the player never leaves BUFFERING, its clock never starts and
     * loading stops once the buffer is full: the decode deadlocks on files longer than the buffer.
     */
    override fun hasPendingData(): Boolean = started && !ended

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        this.playbackParameters = playbackParameters
    }

    override fun getPlaybackParameters(): PlaybackParameters = playbackParameters

    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) {
        this.skipSilenceEnabled = skipSilenceEnabled
    }

    override fun getSkipSilenceEnabled(): Boolean = skipSilenceEnabled

    override fun setAudioAttributes(audioAttributes: AudioAttributes) {
        this.audioAttributes = audioAttributes
    }

    override fun getAudioAttributes(): AudioAttributes = audioAttributes

    override fun setAudioSessionId(audioSessionId: Int) = Unit

    override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) = Unit

    override fun getAudioTrackBufferSizeUs(): Long = 0L

    override fun enableTunnelingV21() = Unit

    override fun disableTunneling() = Unit

    override fun setVolume(volume: Float) = Unit

    override fun pause() = Unit

    override fun flush() {
        if (pcm) pipeline.flush(AudioProcessor.StreamMetadata.DEFAULT)
        ended = false
        frameCount = 0L
        totalFrames = 0L
        startUs = AudioSink.CURRENT_POSITION_NOT_SET
        started = false
    }

    override fun reset() {
        pipeline.reset()
        pcm = false
        operational = false
        configured = false
        dataHandled = false
        rate = 0
        channels = 0
        frameCount = 0L
        totalFrames = 0L
        startUs = AudioSink.CURRENT_POSITION_NOT_SET
        started = false
        ended = false
    }

    /** Hands [samples] samples of [scratch] to [consumer] and advances the frame count. */
    private fun deliver(samples: Int) {
        var frames = samples / channels
        if (frames <= 0) return
        if (startPositionUs > 0L) {
            var drop = 0
            while (drop < frames &&
                startUs - outputStreamOffsetUs + (totalFrames + drop) * C.MICROS_PER_SECOND / rate < startPositionUs
            ) {
                drop++
            }
            totalFrames += frames
            if (drop > 0) scratch.copyInto(scratch, 0, drop * channels, frames * channels)
            frames -= drop
            if (frames <= 0) return
            frameCount += frames
            consumer.accept(scratch, frames, rate, channels)
            return
        }
        totalFrames += frames
        frameCount += frames
        consumer.accept(scratch, frames, rate, channels)
    }

    /** Grows [scratch] to hold at least [samples] samples, reusing it across buffers. */
    private fun ensureScratch(samples: Int) {
        if (scratch.size < samples) scratch = FloatArray(samples)
    }

    private fun isLinearPcm(format: Format): Boolean =
        format.sampleMimeType == MimeTypes.AUDIO_RAW && Util.isEncodingLinearPcm(format.pcmEncoding)

    private companion object {
        const val BYTES_PER_FLOAT_SAMPLE = 4
    }
}
