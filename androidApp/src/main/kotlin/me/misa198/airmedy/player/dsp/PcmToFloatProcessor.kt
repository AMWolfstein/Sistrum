package me.misa198.airmedy.player.dsp

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Bulk-converts little-endian 16/24/32-bit PCM to float, matching Media3's
 * `ToFloatPcmAudioProcessor` bit for bit but using bulk array transfers instead of per-sample
 * ByteBuffer reads. Any other linear-PCM encoding (float, 8-bit, big-endian, double) leaves this
 * processor inactive so the downstream `ToFloatPcmAudioProcessor` handles it (and is itself
 * inactive when the input is already float). Non-linear PCM throws.
 */
@OptIn(UnstableApi::class)
internal class PcmToFloatProcessor : BaseAudioProcessor() {

    private var shorts = ShortArray(0)
    private var ints = IntArray(0)
    private var bytes = ByteArray(0)
    private var floats = FloatArray(0)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (!Util.isEncodingLinearPcm(inputAudioFormat.encoding)) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT ->
                AudioProcessor.AudioFormat(
                    inputAudioFormat.sampleRate,
                    inputAudioFormat.channelCount,
                    C.ENCODING_PCM_FLOAT,
                )
            else -> AudioProcessor.AudioFormat.NOT_SET
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val format = inputAudioFormat
        val frames = inputBuffer.remaining() / format.bytesPerFrame
        if (frames == 0) return
        val sampleCount = frames * format.channelCount
        ensureArrays(sampleCount, format.encoding)
        when (format.encoding) {
            C.ENCODING_PCM_16BIT -> {
                inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts, 0, sampleCount)
                for (i in 0 until sampleCount) {
                    floats[i] = (FACTOR * (shorts[i].toInt() shl 16)).toFloat()
                }
            }
            C.ENCODING_PCM_24BIT -> {
                inputBuffer.get(bytes, 0, sampleCount * 3)
                for (i in 0 until sampleCount) {
                    val base = i * 3
                    val b0 = bytes[base]
                    val b1 = bytes[base + 1]
                    val b2 = bytes[base + 2]
                    val pcm32 =
                        (b2.toInt() shl 24) or ((b1.toInt() and 0xFF) shl 16) or ((b0.toInt() and 0xFF) shl 8)
                    floats[i] = (FACTOR * pcm32).toFloat()
                }
            }
            C.ENCODING_PCM_32BIT -> {
                inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(ints, 0, sampleCount)
                for (i in 0 until sampleCount) {
                    floats[i] = (FACTOR * ints[i]).toFloat()
                }
            }
        }
        inputBuffer.position(inputBuffer.limit())
        val output = replaceOutputBuffer(sampleCount * 4)
        output.asFloatBuffer().put(floats, 0, sampleCount)
        output.position(sampleCount * 4)
        output.flip()
    }

    private fun ensureArrays(sampleCount: Int, encoding: Int) {
        if (floats.size < sampleCount) floats = FloatArray(sampleCount)
        when (encoding) {
            C.ENCODING_PCM_16BIT -> if (shorts.size < sampleCount) shorts = ShortArray(sampleCount)
            C.ENCODING_PCM_24BIT -> if (bytes.size < sampleCount * 3) bytes = ByteArray(sampleCount * 3)
            C.ENCODING_PCM_32BIT -> if (ints.size < sampleCount) ints = IntArray(sampleCount)
        }
    }

    private companion object {
        const val FACTOR: Double = 1.0 / 0x7FFFFFFF
    }
}
