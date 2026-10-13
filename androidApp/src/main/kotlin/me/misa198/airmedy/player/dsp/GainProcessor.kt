package me.misa198.airmedy.player.dsp

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

internal enum class FadeDirection { In, Out }

/** Milliseconds until a fade with [remainingFrames] curve frames left is audibly finished, given [leadFrames]
 *  processed-but-not-yet-audible frames: ceil((remainingFrames + leadFrames) * 1000 / sampleRate). */
internal fun fadeAudibleEndMs(remainingFrames: Long, leadFrames: Long, sampleRate: Int): Long =
    ((remainingFrames + leadFrames) * 1000L + sampleRate - 1L) / sampleRate

/** Per-player normalization gain, ramped in linear amplitude over 200 ms. */
@OptIn(UnstableApi::class)
internal class GainProcessor : BaseAudioProcessor() {
    @Volatile
    private var targetGain = 1f

    // Only the audio processing thread touches the ramp state.
    private var observedTarget = 1f
    private var appliedGain = 1.0
    private var step = 0.0
    private var remainingFrames = 0

    private data class FadeRequest(
        val direction: FadeDirection,
        val durationMs: Long,
        val startOffsetFrames: Long,
        val snapped: Boolean = false,
    )

    private val fadeRequest = AtomicReference<FadeRequest?>()

    // Only the audio processing thread observes requests and advances the curve.
    private var observedFadeRequest: FadeRequest? = null
    private var fadeDirection: FadeDirection? = null
    private var fadeTotalFrames = 0L
    private var fadeFrame = 0L
    @Volatile
    private var publishedFadeRemainingFrames = 0L

    /**
     * Starts an equal-power fade from any thread; the audio thread observes it at the next buffer (like the gain
     * target). The first frame processed after observation is curve frame `-startOffsetFrames`: a positive offset
     * holds the start value (In 0, Out 1) for that many frames first; a negative offset starts part-way into the
     * curve. A new call replaces any running fade.
     */
    fun startFade(direction: FadeDirection, durationMs: Long, startOffsetFrames: Long = 0L) {
        fadeRequest.set(FadeRequest(direction, durationMs, startOffsetFrames))
    }
    /** Ends the fade at once from any thread: from the next processed frame In = 1, Out = 0. Survives a flush. */
    fun snapFade() {
        // Preserve even an unobserved start's direction; concurrent starts and snaps are ordered atomically.
        fadeRequest.updateAndGet { request -> request?.copy(snapped = true) }
    }
    /** Curve frames still to process (held offset frames included); 0 with no fade, after the end, after a snap. */
    val fadeRemainingFrames: Long get() = publishedFadeRemainingFrames

    private fun observeFadeRequest() {
        val request = fadeRequest.get() ?: return
        // Identity makes identical successive startFade calls restart the curve too.
        if (request === observedFadeRequest || inputAudioFormat.sampleRate < 1) return
        observedFadeRequest = request
        fadeDirection = request.direction
        fadeTotalFrames = (request.durationMs * inputAudioFormat.sampleRate / 1000L).coerceAtLeast(1L)
        fadeFrame = if (request.snapped) fadeTotalFrames else -request.startOffsetFrames
        publishFadeRemainingFrames()
    }

    private fun publishFadeRemainingFrames() {
        publishedFadeRemainingFrames = if (fadeFrame >= fadeTotalFrames) 0L else fadeTotalFrames - fadeFrame
    }

    private fun nextFadeGain(): Float {
        val direction = fadeDirection ?: return 1f
        val gain = when {
            fadeFrame < 0L -> if (direction == FadeDirection.In) 0f else 1f
            fadeFrame >= fadeTotalFrames -> if (direction == FadeDirection.In) 1f else 0f
            else -> {
                val phase = fadeFrame.toFloat() / fadeTotalFrames.toFloat() * (PI / 2).toFloat()
                if (direction == FadeDirection.In) sin(phase) else cos(phase)
            }
        }
        if (fadeFrame < fadeTotalFrames) fadeFrame++
        return gain
    }

    /** Publishes a target from any thread; the audio thread observes it once per buffer. */
    fun setTargetGainDb(db: Float) {
        if (!db.isFinite()) return
        val gain = 10.0.pow(db / 20.0).toFloat()
        if (gain.isFinite()) targetGain = gain
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if ((inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
                inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) ||
            inputAudioFormat.channelCount < 1 || inputAudioFormat.sampleRate < 1
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return AudioProcessor.AudioFormat(
            inputAudioFormat.sampleRate,
            inputAudioFormat.channelCount,
            C.ENCODING_PCM_FLOAT,
        )
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        val target = targetGain
        observedTarget = target
        appliedGain = target.toDouble()
        remainingFrames = 0
        step = 0.0
        // Keep curve progress, but consume a pending snap before any post-flush audio is processed.
        observeFadeRequest()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        observeFadeRequest()
        val format = inputAudioFormat
        val frames = inputBuffer.remaining() / format.bytesPerFrame
        if (frames == 0) return
        val target = targetGain
        if (target != observedTarget) {
            observedTarget = target
            remainingFrames = (format.sampleRate * 0.2).toInt().coerceAtLeast(1)
            step = (target.toDouble() - appliedGain) / remainingFrames
        }
        val output = replaceOutputBuffer(frames * format.channelCount * 4)
            .order(ByteOrder.nativeOrder())
        inputBuffer.order(ByteOrder.nativeOrder())
        if (fadeDirection == null && remainingFrames == 0 && appliedGain == 1.0 &&
            format.encoding == C.ENCODING_PCM_FLOAT
        ) {
            output.put(inputBuffer)
        } else {
            for (frame in 0 until frames) {
                if (remainingFrames > 0) {
                    appliedGain += step
                    remainingFrames--
                    if (remainingFrames == 0) appliedGain = observedTarget.toDouble()
                }
                val gain = appliedGain.toFloat()
                val fadeGain = nextFadeGain()
                for (channel in 0 until format.channelCount) {
                    val sample = if (format.encoding == C.ENCODING_PCM_FLOAT) {
                        inputBuffer.float
                    } else {
                        inputBuffer.short / 32768f
                    }
                    val normalized = if (gain == 1f) sample else sample * gain
                    output.putFloat(if (fadeGain == 1f) normalized else normalized * fadeGain)
                }
            }
        }
        publishFadeRemainingFrames()
        // A finished fade-in holds unity, so the player gets the bulk-copy path back.
        if (fadeDirection == FadeDirection.In && fadeFrame >= fadeTotalFrames) fadeDirection = null
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }
}
