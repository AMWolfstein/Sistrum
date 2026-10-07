package me.misa198.airmedy.player.media3

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessingPipeline
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.exoplayer.audio.ToFloatPcmAudioProcessor
import androidx.media3.exoplayer.audio.TrimmingAudioProcessor
import com.google.common.collect.ImmutableList
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import me.misa198.airmedy.player.dsp.PcmToFloatProcessor

/**
 * Wraps a delegate [AudioSink] so that every linear-PCM buffer is first converted to float and run
 * through [chainProcessors] on the decoder's full resolution, before the inner sink (which then only
 * ever sees float) writes it to the track. Non-PCM buffers bypass straight to the delegate.
 *
 * Playback-thread confined, mirroring [androidx.media3.exoplayer.audio.DefaultAudioSink].
 */
@OptIn(UnstableApi::class)
internal class FloatChainAudioSink(
    private val delegate: AudioSink,
    chainProcessors: List<AudioProcessor>,
) : ForwardingAudioSink(delegate) {

    private val trimming = TrimmingAudioProcessor()

    private val pipeline = AudioProcessingPipeline(
        ImmutableList.copyOf(
            listOf<AudioProcessor>(trimming, PcmToFloatProcessor(), ToFloatPcmAudioProcessor()) + chainProcessors,
        ),
    )

    /** True once [configure] ran on a linear-PCM format (and reset() has not followed). */
    private var pcm = false

    /**
     * True when the pipeline has at least one active processor. When false (float input with an
     * empty/all-inactive chain), PCM buffers are already float and are forwarded straight to the
     * delegate instead of being run through an inoperative pipeline.
     */
    private var operational = false

    /** Growable, reusable output buffer holding processed bytes not yet handed to [delegate]. */
    private var pending = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())

    private var lastPresentationTimeUs = 0L
    private var lastAccessUnitCount = 0

    override fun configure(config: AudioSink.AudioSinkConfig) {
        val format = config.format
        if (!isLinearPcm(format)) {
            pcm = false
            operational = false
            delegate.configure(config)
            return
        }
        trimming.setTrimFrameCount(format.encoderDelay, format.encoderPadding)
        val out = try {
            val output = pipeline.configure(AudioProcessor.AudioFormat(format))
            pipeline.flush(AudioProcessor.StreamMetadata.DEFAULT)
            output
        } catch (e: AudioProcessor.UnhandledAudioFormatException) {
            throw AudioSink.ConfigurationException(e, format)
        }
        dropPending()
        val floatFormat = format.buildUpon()
            .setPcmEncoding(C.ENCODING_PCM_FLOAT)
            .setSampleRate(out.sampleRate)
            .setChannelCount(out.channelCount)
            .setEncoderDelay(0)
            .setEncoderPadding(0)
            .build()
        val builder = AudioSink.AudioSinkConfig.Builder(floatFormat)
            .setPreferredBufferSizeOverride(config.preferredBufferSizeOverride)
        config.outputChannelMapping?.let { builder.setOutputChannelMapping(it) }
        config.timeline?.let { builder.setTimeline(it) }
        config.mediaPeriodId?.let { builder.setMediaPeriodId(it) }
        delegate.configure(builder.build())
        pcm = true
        operational = pipeline.isOperational()
    }

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int,
    ): Boolean {
        if (!pcm || !operational) {
            return delegate.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        }
        if (!pending.hasRemaining()) {
            lastPresentationTimeUs = presentationTimeUs
            lastAccessUnitCount = encodedAccessUnitCount
            processInput(buffer)
        }
        val delivered = delegate.handleBuffer(pending, presentationTimeUs, encodedAccessUnitCount)
        if (delivered) dropPending()
        return delivered
    }

    override fun playToEndOfStream() {
        if (!pcm || !operational) {
            delegate.playToEndOfStream()
            return
        }
        if (pending.hasRemaining() && !deliverPending()) return
        delegate.playToEndOfStream()
    }

    override fun hasPendingData(): Boolean {
        if (pcm && operational && pending.hasRemaining()) return true
        return delegate.hasPendingData()
    }

    override fun isEnded(): Boolean {
        if (pcm && operational && pending.hasRemaining()) return false
        return delegate.isEnded()
    }

    override fun getFormatSupport(format: Format): Int {
        if (!isLinearPcm(format)) return delegate.getFormatSupport(format)
        if (format.pcmEncoding == C.ENCODING_PCM_FLOAT) return delegate.getFormatSupport(format)
        val floatFormat = format.buildUpon().setPcmEncoding(C.ENCODING_PCM_FLOAT).build()
        val floatSupport = delegate.getFormatSupport(floatFormat)
        return if (floatSupport != AudioSink.SINK_FORMAT_UNSUPPORTED) {
            AudioSink.SINK_FORMAT_SUPPORTED_WITH_TRANSCODING
        } else {
            delegate.getFormatSupport(format)
        }
    }

    override fun supportsFormat(format: Format): Boolean =
        getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED

    override fun flush() {
        dropPending()
        if (pcm) pipeline.flush(AudioProcessor.StreamMetadata.DEFAULT)
        delegate.flush()
    }

    override fun reset() {
        dropPending()
        pipeline.reset()
        delegate.reset()
        pcm = false
        operational = false
    }

    /** Feeds [input] through the pipeline and accumulates the output into [pending] (flipped). */
    private fun processInput(input: ByteBuffer) {
        pending.clear()
        pipeline.queueInput(input)
        var output = pipeline.getOutput()
        while (output.hasRemaining()) {
            appendToPending(output)
            output = pipeline.getOutput()
        }
        input.position(input.limit())
        pending.flip()
    }

    /** Copies [output]'s remaining bytes into [pending], growing the buffer when needed. */
    private fun appendToPending(output: ByteBuffer) {
        val bytes = output.remaining()
        if (bytes == 0) return
        ensurePendingCapacity(bytes)
        pending.put(output)
        output.position(output.limit())
    }

    private fun ensurePendingCapacity(additionalBytes: Int) {
        val required = pending.position() + additionalBytes
        if (required <= pending.capacity()) return
        var newCapacity = max(2 * pending.capacity(), required)
        if (newCapacity < 4096) newCapacity = 4096
        val grown = ByteBuffer.allocateDirect(newCapacity).order(ByteOrder.nativeOrder())
        pending.flip()
        grown.put(pending)
        pending = grown
    }

    /** Attempts to drain [pending] into [delegate]; returns true once it is empty. */
    private fun deliverPending(): Boolean {
        if (!pending.hasRemaining()) return true
        if (delegate.handleBuffer(pending, lastPresentationTimeUs, lastAccessUnitCount)) {
            dropPending()
            return true
        }
        return false
    }

    private fun dropPending() {
        pending.position(0)
        pending.limit(0)
    }

    private fun isLinearPcm(format: Format): Boolean =
        format.sampleMimeType == MimeTypes.AUDIO_RAW && Util.isEncodingLinearPcm(format.pcmEncoding)
}
