package com.amwolfstein.sistrum.player.dsp

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow
import kotlin.math.roundToInt

internal object Preamp {
    /** Linear gain for a decibel value: `10^(db/20)`, with `0 dB` returning exactly `1f`. */
    fun linearGain(db: Float): Float {
        if (db == 0f) return 1f
        return 10f.pow(db / 20f)
    }
}

/**
 * A [BaseAudioProcessor] applying a linear gain to every channel. Gain changes ramp linearly from
 * the current gain to the target gain over `RAMP_FRAMES` frames; a flush jumps straight to the
 * target. Always active for supported formats; a neutral gain (0 dB -> 0 dB) takes a fast path with
 * no gain math.
 */
@OptIn(UnstableApi::class)
internal class PreampProcessor : BaseAudioProcessor() {

    @Volatile
    private var targetDb: Float = 0f

    private var currentGain: Float = 1f
    private var rampStartGain: Float = 1f
    private var rampTarget: Float = 1f
    private var rampRemaining: Int = 0
    private var rampTotal: Int = 0
    private var rampFrames: Int = 0

    private var work = FloatArray(0)
    private var workShorts = ShortArray(0)

    fun setPreampDb(db: Float) {
        targetDb = db
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

        val targetGain = Preamp.linearGain(targetDb)
        if (targetGain != rampTarget) {
            rampStartGain = currentGain
            rampTarget = targetGain
            rampTotal = rampFrames
            rampRemaining = rampTotal
        }
        if (currentGain != 1f || targetGain != 1f || rampRemaining > 0) {
            for (f in 0 until frames) {
                val gain = advanceGain()
                val base = f * channels
                for (c in 0 until channels) {
                    work[base + c] *= gain
                }
            }
        }

        writeWork(sampleCount)
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        rampFrames = maxOf(1, (0.02f * inputAudioFormat.sampleRate).roundToInt())
        currentGain = Preamp.linearGain(targetDb)
        rampTarget = currentGain
        rampRemaining = 0
    }

    override fun onReset() {
        rampFrames = 0
        currentGain = 1f
        rampStartGain = 1f
        rampTarget = 1f
        rampRemaining = 0
        rampTotal = 0
    }

    private fun advanceGain(): Float {
        if (rampRemaining > 0) {
            rampRemaining--
            if (rampRemaining == 0) {
                currentGain = rampTarget
            } else {
                val progress = 1f - rampRemaining.toFloat() / rampTotal.toFloat()
                currentGain = rampStartGain + (rampTarget - rampStartGain) * progress
            }
        }
        return currentGain
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
