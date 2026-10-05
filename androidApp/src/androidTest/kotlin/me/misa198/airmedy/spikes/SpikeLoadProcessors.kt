package me.misa198.airmedy.spikes

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Spike S3 test code for specs/001-media3-migration (ADR-003, ADR-004), not app code.
 *
 * A per-sample gain [BaseAudioProcessor] used purely to give the S3 CPU measurement a
 * representative cost: it multiplies every sample by a gain held in a field (1.0 by default).
 * Accepts PCM 16-bit or float input of any channel count and returns the same format.
 */
@OptIn(UnstableApi::class)
class GainLoadProcessor : BaseAudioProcessor() {

    @Volatile
    var gain: Float = 1.0f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val format = inputAudioFormat
        val channels = format.channelCount
        val bytesPerFrame = format.bytesPerFrame
        val enc = format.encoding
        val frames = inputBuffer.remaining() / bytesPerFrame
        if (frames == 0) return

        val outputBuffer = replaceOutputBuffer(frames * bytesPerFrame)
        outputBuffer.clear()
        val inBuf = inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val outBuf = outputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val g = gain

        if (enc == C.ENCODING_PCM_FLOAT) {
            val samples = frames * channels
            for (i in 0 until samples) {
                outBuf.putFloat(inBuf.getFloat() * g)
            }
        } else {
            val samples = frames * channels
            for (i in 0 until samples) {
                val sample = inBuf.getShort().toInt()
                val scaled = (sample * g).roundToInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                outBuf.putShort(scaled.toShort())
            }
        }
        outputBuffer.flip()
    }
}

/**
 * Spike S3 test code for specs/001-media3-migration (ADR-003, ADR-004), not app code.
 *
 * A stereo width [BaseAudioProcessor] doing a real 2x2 matrix (`L' = aL + bR`, `R' = bL + aR`,
 * a = 1, b = 0 by default) so the S3 CPU measurement sees representative work. Any channel count
 * other than stereo is passed through unchanged. PCM 16-bit or float input.
 */
@OptIn(UnstableApi::class)
class WidthLoadProcessor : BaseAudioProcessor() {

    @Volatile
    var a: Float = 1.0f

    @Volatile
    var b: Float = 0.0f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val format = inputAudioFormat
        val channels = format.channelCount
        val bytesPerFrame = format.bytesPerFrame
        val enc = format.encoding
        val frames = inputBuffer.remaining() / bytesPerFrame
        if (frames == 0) return

        val outputBuffer = replaceOutputBuffer(frames * bytesPerFrame)
        outputBuffer.clear()
        val inBuf = inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val outBuf = outputBuffer.order(ByteOrder.LITTLE_ENDIAN)

        if (channels != 2) {
            if (enc == C.ENCODING_PCM_FLOAT) {
                val samples = frames * channels
                for (i in 0 until samples) outBuf.putFloat(inBuf.getFloat())
            } else {
                val samples = frames * channels
                for (i in 0 until samples) outBuf.putShort(inBuf.getShort())
            }
        } else if (enc == C.ENCODING_PCM_FLOAT) {
            val ma = a
            val mb = b
            for (f in 0 until frames) {
                val left = inBuf.getFloat()
                val right = inBuf.getFloat()
                outBuf.putFloat(ma * left + mb * right)
                outBuf.putFloat(mb * left + ma * right)
            }
        } else {
            val ma = a
            val mb = b
            for (f in 0 until frames) {
                val left = inBuf.getShort().toInt() / 32768.0f
                val right = inBuf.getShort().toInt() / 32768.0f
                val outLeft = (ma * left + mb * right) * 32768.0f
                val outRight = (mb * left + ma * right) * 32768.0f
                outBuf.putShort(clamp16(outLeft))
                outBuf.putShort(clamp16(outRight))
            }
        }
        outputBuffer.flip()
    }

    private fun clamp16(value: Float): Short =
        value.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
}

/**
 * Spike S3 test code for specs/001-media3-migration (ADR-003, ADR-004), not app code.
 *
 * A 10-band peaking equalizer [BaseAudioProcessor] (RBJ formula, Q = 1, +3 dB per band at 32, 64,
 * 125, 250, 500, 1k, 2k, 4k, 8k and 16 kHz, bands at or above Nyquist skipped), with a fixed
 * -10 dB preamp folded in so loud material does not clip. Direct form I, float state per channel;
 * coefficients are computed in `onConfigure` for the input sample rate. PCM 16-bit or float input.
 */
@OptIn(UnstableApi::class)
class EqLoadProcessor : BaseAudioProcessor() {

    private companion object {
        const val MAX_BANDS = 10
        const val BAND_GAIN_DB = 3.0
        const val Q = 1.0
        val FREQUENCIES_HZ = doubleArrayOf(
            32.0, 64.0, 125.0, 250.0, 500.0,
            1_000.0, 2_000.0, 4_000.0, 8_000.0, 16_000.0,
        )
        val PREAMP_GAIN = 10.0.pow(-10.0 / 20.0).toFloat()
    }

    private var bands = 0
    private val b0 = FloatArray(MAX_BANDS)
    private val b1 = FloatArray(MAX_BANDS)
    private val b2 = FloatArray(MAX_BANDS)
    private val a1 = FloatArray(MAX_BANDS)
    private val a2 = FloatArray(MAX_BANDS)

    private var x1 = FloatArray(0)
    private var x2 = FloatArray(0)
    private var y1 = FloatArray(0)
    private var y2 = FloatArray(0)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        computeCoefficients(inputAudioFormat.sampleRate)
        val stateSize = inputAudioFormat.channelCount * bands
        x1 = FloatArray(stateSize)
        x2 = FloatArray(stateSize)
        y1 = FloatArray(stateSize)
        y2 = FloatArray(stateSize)
        return inputAudioFormat
    }

    private fun computeCoefficients(sampleRate: Int) {
        val nyquist = sampleRate / 2.0
        var count = 0
        for (frequency in FREQUENCIES_HZ) {
            if (frequency >= nyquist) break
            val amplitude = 10.0.pow(BAND_GAIN_DB / 40.0)
            val w0 = 2.0 * PI * frequency / sampleRate
            val cosW0 = cos(w0)
            val alpha = sin(w0) / (2.0 * Q)
            val a0 = 1.0 + alpha / amplitude
            b0[count] = ((1.0 + alpha * amplitude) / a0).toFloat()
            b1[count] = ((-2.0 * cosW0) / a0).toFloat()
            b2[count] = ((1.0 - alpha * amplitude) / a0).toFloat()
            a1[count] = ((-2.0 * cosW0) / a0).toFloat()
            a2[count] = ((1.0 - alpha / amplitude) / a0).toFloat()
            count++
        }
        bands = count
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val format = inputAudioFormat
        val channels = format.channelCount
        val bytesPerFrame = format.bytesPerFrame
        val enc = format.encoding
        val frames = inputBuffer.remaining() / bytesPerFrame
        if (frames == 0) return

        val outputBuffer = replaceOutputBuffer(frames * bytesPerFrame)
        outputBuffer.clear()
        val inBuf = inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val outBuf = outputBuffer.order(ByteOrder.LITTLE_ENDIAN)

        if (enc == C.ENCODING_PCM_FLOAT) {
            for (f in 0 until frames) {
                for (c in 0 until channels) {
                    outBuf.putFloat(processSample(c, inBuf.getFloat()))
                }
            }
        } else {
            for (f in 0 until frames) {
                for (c in 0 until channels) {
                    val x = inBuf.getShort().toInt() / 32768.0f
                    val y = processSample(c, x) * 32768.0f
                    outBuf.putShort(
                        y.roundToInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                            .toShort()
                    )
                }
            }
        }
        outputBuffer.flip()
    }

    private fun processSample(channel: Int, input: Float): Float {
        var x = input * PREAMP_GAIN
        val base = channel * bands
        for (band in 0 until bands) {
            val index = base + band
            val y = b0[band] * x + b1[band] * x1[index] + b2[band] * x2[index] -
                a1[band] * y1[index] - a2[band] * y2[index]
            x2[index] = x1[index]
            x1[index] = x
            y2[index] = y1[index]
            y1[index] = y
            x = y
        }
        return x
    }

    override fun onReset() {
        bands = 0
        x1 = FloatArray(0)
        x2 = FloatArray(0)
        y1 = FloatArray(0)
        y2 = FloatArray(0)
    }
}
