package me.misa198.airmedy.player.dsp

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A [BaseAudioProcessor] wrapping a [BiquadEqualizer]. Always active for supported formats so a
 * later gain change never needs a reconfigure/flush (and the accompanying gap). When the equalizer
 * is neutral the wrapped core skips its DSP math and the samples pass through untouched (16-bit
 * input is still scaled to float).
 */
@OptIn(UnstableApi::class)
internal class EqualizerProcessor : BaseAudioProcessor() {

    @Volatile
    private var gainsTarget: FloatArray = FloatArray(BiquadDesign.FrequenciesHz.size)

    private var equalizer: BiquadEqualizer? = null
    private var work = FloatArray(0)
    private var workShorts = ShortArray(0)

    fun setGains(gainsDb: FloatArray) {
        require(gainsDb.size == BiquadDesign.FrequenciesHz.size)
        gainsTarget = gainsDb.copyOf()
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        if (inputAudioFormat.channelCount < 1) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return AudioProcessor.AudioFormat(
            inputAudioFormat.sampleRate,
            inputAudioFormat.channelCount,
            C.ENCODING_PCM_FLOAT,
        )
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val eq = equalizer ?: return
        val format = inputAudioFormat
        val frames = inputBuffer.remaining() / format.bytesPerFrame
        if (frames == 0) return
        val channels = format.channelCount
        val sampleCount = frames * channels
        ensureWork(sampleCount)
        readToWork(inputBuffer, format.encoding, sampleCount)
        inputBuffer.position(inputBuffer.limit())

        eq.setGains(gainsTarget)
        eq.process(work, 0, frames)

        writeWork(sampleCount)
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        val current = equalizer
        if (current == null ||
            current.channelCount != inputAudioFormat.channelCount ||
            current.sampleRate != inputAudioFormat.sampleRate
        ) {
            equalizer = BiquadEqualizer(inputAudioFormat.channelCount, inputAudioFormat.sampleRate)
        } else {
            current.reset()
        }
        equalizer!!.setGains(gainsTarget)
    }

    override fun onReset() {
        equalizer = null
    }

    private fun ensureWork(sampleCount: Int) {
        if (work.size < sampleCount) work = FloatArray(sampleCount)
        if (workShorts.size < sampleCount) workShorts = ShortArray(sampleCount)
    }

    private fun readToWork(inputBuffer: ByteBuffer, encoding: Int, sampleCount: Int) {
        if (encoding == C.ENCODING_PCM_FLOAT) {
            inputBuffer.order(ByteOrder.nativeOrder()).asFloatBuffer().get(work, 0, sampleCount)
        } else {
            inputBuffer.order(ByteOrder.nativeOrder()).asShortBuffer().get(workShorts, 0, sampleCount)
            for (i in 0 until sampleCount) work[i] = workShorts[i] / 32768f
        }
    }

    private fun writeWork(sampleCount: Int) {
        val outputBuffer = replaceOutputBuffer(sampleCount * 4)
        outputBuffer.order(ByteOrder.nativeOrder()).asFloatBuffer().put(work, 0, sampleCount)
        outputBuffer.position(sampleCount * 4)
        outputBuffer.flip()
    }
}
