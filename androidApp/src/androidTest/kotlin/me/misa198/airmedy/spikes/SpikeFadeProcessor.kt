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
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Spike S1 test code for specs/001-media3-migration (ADR-003), not app code.
 *
 * An equal-power per-sample fade [BaseAudioProcessor] that mirrors the native engine's crossfade
 * curve exactly: with `total` = fade length in frames and `at` = frames rendered since the fade
 * started, `phase = at / total * (PI / 2)`, outgoing gain = `cos(phase)`, incoming gain =
 * `sin(phase)`, computed in Float. The same gain multiplies every channel of a frame.
 */
@OptIn(UnstableApi::class)
class SpikeFadeProcessor : BaseAudioProcessor() {

    enum class Role { Outgoing, Incoming, None }

    @Volatile
    var role: Role = Role.None
        private set

    @Volatile
    private var frameCounter: Long = 0L

    @Volatile
    private var totalFrames: Int = 0

    @Volatile
    private var sampleRate: Int = 0

    @Volatile
    private var channelCount: Int = 0

    @Volatile
    private var encoding: Int = C.ENCODING_INVALID

    @Volatile
    private var snapped: Boolean = false

    @Volatile
    private var snapGain: Float = 1.0f

    /** Begins a fade of [fadeMs] milliseconds for [role], resetting the frame counter to zero. */
    fun startFade(role: Role, fadeMs: Long) {
        this.role = role
        this.snapped = false
        this.frameCounter = 0L
        this.totalFrames = if (sampleRate > 0) {
            (sampleRate.toFloat() * fadeMs / 1000f).roundToInt()
        } else {
            0
        }
    }

    /** Snaps the gain to its end value from the next processed frame on: 1.0 for Incoming, 0.0 for Outgoing. */
    fun snap() {
        val previous = role
        role = Role.None
        snapped = true
        snapGain = if (previous == Role.Incoming) 1.0f else 0.0f
    }

    /** The gain that would be applied to the frame at the current counter, without advancing it. */
    fun currentGain(): Float = gainForFrame(frameCounter)

    private fun gainForFrame(at: Long): Float {
        if (snapped) return snapGain
        if (role == Role.None) return 1.0f
        if (totalFrames <= 0 || at >= totalFrames) {
            return if (role == Role.Outgoing) 0.0f else 1.0f
        }
        val phase = at.toFloat() / totalFrames.toFloat() * (PI / 2).toFloat()
        return if (role == Role.Outgoing) cos(phase) else sin(phase)
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val format = inputAudioFormat
        val channels = format.channelCount
        val bytesPerFrame = format.bytesPerFrame
        val enc = format.encoding
        val frames = inputBuffer.remaining() / bytesPerFrame

        val outputBuffer = replaceOutputBuffer(frames * bytesPerFrame)
        outputBuffer.clear()
        val inBuf = inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val outBuf = outputBuffer.order(ByteOrder.LITTLE_ENDIAN)

        if (enc == C.ENCODING_PCM_FLOAT) {
            for (f in 0 until frames) {
                val gain = nextFrameGain()
                for (c in 0 until channels) {
                    outBuf.putFloat(inBuf.getFloat() * gain)
                }
            }
        } else {
            for (f in 0 until frames) {
                val gain = nextFrameGain()
                for (c in 0 until channels) {
                    val sample = inBuf.getShort().toInt()
                    val scaled = (sample * gain).roundToInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    outBuf.putShort(scaled.toShort())
                }
            }
        }

        outputBuffer.flip()
    }

    private fun nextFrameGain(): Float {
        val gain = gainForFrame(frameCounter)
        frameCounter++
        return gain
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        // The fade is time-based and driven by the test thread; a sink flush only discards
        // buffered audio, so leave the fade state intact.
    }

    override fun onReset() {
        role = Role.None
        snapped = false
        snapGain = 1.0f
        frameCounter = 0L
        totalFrames = 0
        sampleRate = 0
        channelCount = 0
        encoding = C.ENCODING_INVALID
    }
}
