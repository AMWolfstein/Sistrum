package me.misa198.airmedy.player.dsp

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

internal object StereoWidth {
    /**
     * Mid/side width processing matching the native engine: `mid = (L+R)/2`,
     * `side = (L-R)/2 * width`, `L' = mid + side`, `R' = mid - side`. Width 1 is identity,
     * width 0 is mono.
     */
    fun apply(buffer: FloatArray, offset: Int, frameCount: Int, width: Float) {
        for (f in 0 until frameCount) {
            val base = offset + f * 2
            val left = buffer[base]
            val right = buffer[base + 1]
            val mid = (left + right) * 0.5f
            val side = (left - right) * 0.5f * width
            buffer[base] = mid + side
            buffer[base + 1] = mid - side
        }
    }
}

/**
 * A [BaseAudioProcessor] that widens (or narrows) stereo material. Mono and other channel counts
 * are passed through converted to float. Width changes ramp linearly to their target over
 * `RAMP_FRAMES` frames; a flush jumps straight to the target. Always active for supported formats;
 * a neutral width (1 -> 1) takes a fast path with no width math.
 */
@OptIn(UnstableApi::class)
internal class StereoWidthProcessor : BaseAudioProcessor() {

    @Volatile
    private var targetWidth: Float = 1f

    private var currentWidth: Float = 1f
    private var rampStartWidth: Float = 1f
    private var rampTarget: Float = 1f
    private var rampRemaining: Int = 0
    private var rampTotal: Int = 0
    private var rampFrames: Int = 0

    private var work = FloatArray(0)
    private var workShorts = ShortArray(0)

    fun setWidth(width: Float) {
        targetWidth = width
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
        val format = inputAudioFormat
        val frames = inputBuffer.remaining() / format.bytesPerFrame
        if (frames == 0) return
        val channels = format.channelCount
        val sampleCount = frames * channels
        ensureWork(sampleCount)
        readToWork(inputBuffer, format.encoding, sampleCount)
        inputBuffer.position(inputBuffer.limit())

        val target = targetWidth
        if (channels == 2) {
            if (target != rampTarget) {
                rampStartWidth = currentWidth
                rampTarget = target
                rampTotal = rampFrames
                rampRemaining = rampTotal
            }
            if (currentWidth != 1f || target != 1f || rampRemaining > 0) {
                for (f in 0 until frames) {
                    val w = advanceWidth()
                    val li = f * 2
                    val ri = li + 1
                    val left = work[li]
                    val right = work[ri]
                    val mid = (left + right) * 0.5f
                    val side = (left - right) * 0.5f * w
                    work[li] = mid + side
                    work[ri] = mid - side
                }
            }
        } else {
            currentWidth = target
            rampTarget = target
            rampRemaining = 0
        }

        writeWork(sampleCount)
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        rampFrames = maxOf(1, (0.02f * inputAudioFormat.sampleRate).roundToInt())
        currentWidth = targetWidth
        rampTarget = targetWidth
        rampRemaining = 0
    }

    override fun onReset() {
        rampFrames = 0
        currentWidth = 1f
        rampStartWidth = 1f
        rampTarget = 1f
        rampRemaining = 0
        rampTotal = 0
    }

    private fun advanceWidth(): Float {
        if (rampRemaining > 0) {
            rampRemaining--
            if (rampRemaining == 0) {
                currentWidth = rampTarget
            } else {
                val progress = 1f - rampRemaining.toFloat() / rampTotal.toFloat()
                currentWidth = rampStartWidth + (rampTarget - rampStartWidth) * progress
            }
        }
        return currentWidth
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
