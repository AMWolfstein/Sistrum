package me.misa198.airmedy.player.dsp

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A unity-gain pass-through [BaseAudioProcessor]. It accepts 16-bit or float PCM and always
 * outputs float, converting 16-bit input to float by bulk conversion; float input is bulk-copied
 * through unchanged. This is a placeholder: the normalization gain × fade curve with its ramps
 * (FR-046a) is added in a later task, so it has no public setters today.
 */
@OptIn(UnstableApi::class)
internal class GainProcessor : BaseAudioProcessor() {

    private var work = FloatArray(0)
    private var workShorts = ShortArray(0)

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
        val format = inputAudioFormat
        val frames = inputBuffer.remaining() / format.bytesPerFrame
        if (frames == 0) return
        val channels = format.channelCount
        val sampleCount = frames * channels
        ensureWork(sampleCount)
        readToWork(inputBuffer, format.encoding, sampleCount)
        inputBuffer.position(inputBuffer.limit())
        writeWork(sampleCount)
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
