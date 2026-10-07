package me.misa198.airmedy.player.media3

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import kotlin.math.abs

/**
 * Shared-session limiter for the Media3 engine (T046, FR-052..055, ADR-004).
 *
 * The pure-Kotlin part (everything down to [LimiterController]) has no `android.*` imports so it
 * runs on the JVM unit-test classpath. The Android adapter ([DynamicsProcessingLimiterEffectFactory])
 * and the session wrapper ([LimiterSession]) sit in this same file but are device-only.
 */

/** Published limiter state for the settings UI: is a limiter attached, and does it have control? */
internal data class LimiterState(val available: Boolean, val controlled: Boolean)

/** The limiter-stage parameters applied to the effect (input gain + every limiter parameter). */
internal data class LimiterParams(
    val inputGainDb: Float,
    val attackMs: Float,
    val releaseMs: Float,
    val ratio: Float,
    val thresholdDb: Float,
    val postGainDb: Float,
    val linkGroup: Int,
)

/** Full DynamicsProcessing configuration: stage usage, frame duration and the limiter stage. */
internal data class LimiterConfig(
    val channelCount: Int,
    val preEqInUse: Boolean,
    val mbcInUse: Boolean,
    val postEqInUse: Boolean,
    val limiterInUse: Boolean,
    val limiterEnabled: Boolean,
    val frameDurationMs: Float,
    val limiter: LimiterParams,
)

/** Limiter-only configurations: active (clip prevention on) and neutral (off, chain still in use). */
internal object LimiterConfigs {
    private const val CHANNEL_COUNT = 2
    private const val ATTACK_MS = 1f
    private const val RELEASE_MS = 60f
    private const val LINK_GROUP = 0

    fun active(frameDurationMs: Float): LimiterConfig = LimiterConfig(
        channelCount = CHANNEL_COUNT,
        preEqInUse = false,
        mbcInUse = false,
        postEqInUse = false,
        limiterInUse = true,
        limiterEnabled = true,
        frameDurationMs = frameDurationMs,
        limiter = LimiterParams(
            inputGainDb = 0f,
            attackMs = ATTACK_MS,
            releaseMs = RELEASE_MS,
            ratio = 10f,
            thresholdDb = -1f,
            postGainDb = 0f,
            linkGroup = LINK_GROUP,
        ),
    )

    fun neutral(frameDurationMs: Float): LimiterConfig = LimiterConfig(
        channelCount = CHANNEL_COUNT,
        preEqInUse = false,
        mbcInUse = false,
        postEqInUse = false,
        limiterInUse = true,
        limiterEnabled = true,
        frameDurationMs = frameDurationMs,
        limiter = LimiterParams(
            inputGainDb = 0f,
            attackMs = ATTACK_MS,
            releaseMs = RELEASE_MS,
            ratio = 1f,
            thresholdDb = 0f,
            postGainDb = 0f,
            linkGroup = LINK_GROUP,
        ),
    )

    fun forClipPrevention(on: Boolean, frameDurationMs: Float): LimiterConfig =
        if (on) active(frameDurationMs) else neutral(frameDurationMs)
}

/** A limiter effect on a session. [enabled] may throw when the effect has been released. */
internal interface LimiterEffect {
    val enabled: Boolean

    fun hasControl(): Boolean

    fun setEnabled(enabled: Boolean)

    fun apply(params: LimiterParams)

    fun release()
}

/** Callbacks the effect fires on its owning looper. */
internal interface LimiterEffectListener {
    fun onControlStatusChange(hasControl: Boolean)

    fun onEnableStatusChange(enabled: Boolean)
}

/** Creates an effect on a session, throwing on failure. */
internal fun interface LimiterEffectFactory {
    fun create(sessionId: Int, config: LimiterConfig, listener: LimiterEffectListener): LimiterEffect
}

/** Schedules a one-shot task after [delayMs]; returns a cancel function. */
internal fun interface LimiterScheduler {
    fun schedule(delayMs: Long, task: () -> Unit): () -> Unit
}

/**
 * Drives one limiter effect on a session, publishing [LimiterState] on change only (FR-053/055).
 *
 * The effect is created with `forClipPrevention` config and enabled; control loss and unexpected
 * disables are treated as a possible mute and, when the effect no longer reports enabled, trigger a
 * release + 5 s probe loop. [setEnabled] is never called with `false` on any path.
 */
internal class LimiterController(
    private val sessionId: Int,
    private var frameDurationMs: Float,
    private val factory: LimiterEffectFactory,
    private val scheduler: LimiterScheduler,
    private val onState: (LimiterState) -> Unit,
) {
    private val lock = Any()

    private var effect: LimiterEffect? = null
    private var clipPrevention = true
    private var released = false
    private var probeCancel: (() -> Unit)? = null
    private var currentState: LimiterState = LimiterState(available = false, controlled = false)

    val state: LimiterState get() = synchronized(lock) { currentState }

    /** Current frame duration in ms, exposed for the device test. */
    val frameDurationMsForTest: Float get() = synchronized(lock) { frameDurationMs }

    private val listener = object : LimiterEffectListener {
        override fun onControlStatusChange(hasControl: Boolean) = synchronized(lock) {
            if (released) return@synchronized
            if (hasControl) onRegainedLocked() else handleControlLossLocked()
        }

        override fun onEnableStatusChange(enabled: Boolean) = synchronized(lock) {
            if (released) return@synchronized
            if (enabled) return@synchronized
            handleUnexpectedDisableLocked()
        }
    }

    fun start(clipPrevention: Boolean) = synchronized(lock) {
        if (released) return@synchronized
        this.clipPrevention = clipPrevention
        createAndEnableLocked(probeOnFailure = false)
    }

    /**
     * Updates the limiter's frame duration. When an effect already exists it is released and
     * re-created with the new value (the DynamicsProcessing frame duration cannot be changed in
     * place); if that re-creation fails the controller falls back to the release + probe path.
     * When no effect exists (unavailable/probing/not started) the value is stored for the
     * next probe/creation. [setEnabled] is never called with `false` on any path.
     */
    fun setFrameDurationMs(ms: Float) = synchronized(lock) {
        if (released) return@synchronized
        if (!ms.isFinite() || ms <= 0f) return@synchronized
        if (abs(ms - frameDurationMs) < 0.5f) return@synchronized
        frameDurationMs = ms
        if (effect == null) return@synchronized
        cancelProbeLocked()
        releaseEffectLocked()
        createAndEnableLocked(probeOnFailure = true)
    }

    fun setClipPrevention(on: Boolean) = synchronized(lock) {
        clipPrevention = on
        val e = effect ?: return@synchronized
        if (!e.hasControlSafe()) return@synchronized
        if (!tryApply(e)) releaseAndProbeLocked()
    }

    fun release() = synchronized(lock) {
        if (released) return@synchronized
        released = true
        cancelProbeLocked()
        releaseEffectLocked()
    }

    private fun createAndEnableLocked(probeOnFailure: Boolean) {
        val created = try {
            factory.create(sessionId, config(), listener)
        } catch (t: Throwable) {
            null
        }
        if (created == null) {
            effect = null
            if (probeOnFailure) {
                releaseAndProbeLocked()
            } else {
                publishLocked(LimiterState(available = false, controlled = false))
            }
            return
        }
        effect = created
        if (!tryEnable(created)) {
            releaseAndProbeLocked()
            return
        }
        publishLocked(LimiterState(available = true, controlled = created.hasControlSafe()))
    }

    private fun config() = LimiterConfigs.forClipPrevention(clipPrevention, frameDurationMs)

    private fun currentParams() = config().limiter

    private fun onRegainedLocked() {
        val e = effect ?: return
        if (!tryEnable(e) || !tryApply(e)) {
            releaseAndProbeLocked()
            return
        }
        publishLocked(LimiterState(available = true, controlled = true))
    }

    private fun handleControlLossLocked() {
        val e = effect ?: return
        if (e.enabledSafe() == true) {
            publishLocked(LimiterState(available = true, controlled = false))
            return
        }
        releaseAndProbeLocked()
    }

    private fun handleUnexpectedDisableLocked() {
        if (effect == null) return
        releaseAndProbeLocked()
    }

    private fun releaseAndProbeLocked() {
        releaseEffectLocked()
        publishLocked(LimiterState(available = false, controlled = false))
        scheduleProbeLocked()
    }

    private fun tryEnable(e: LimiterEffect): Boolean = try {
        e.setEnabled(true)
        true
    } catch (t: Throwable) {
        false
    }

    private fun tryApply(e: LimiterEffect): Boolean = try {
        e.apply(currentParams())
        true
    } catch (t: Throwable) {
        false
    }

    private fun LimiterEffect.hasControlSafe(): Boolean = try {
        hasControl()
    } catch (t: Throwable) {
        false
    }

    private fun LimiterEffect.enabledSafe(): Boolean? = try {
        enabled
    } catch (t: Throwable) {
        null
    }

    private fun scheduleProbeLocked() {
        cancelProbeLocked()
        probeCancel = scheduler.schedule(PROBE_DELAY_MS) {
            synchronized(lock) { probeOnceLocked() }
        }
    }

    private fun cancelProbeLocked() {
        probeCancel?.invoke()
        probeCancel = null
    }

    private fun probeOnceLocked() {
        if (released) return
        probeCancel = null
        val created = try {
            factory.create(sessionId, config(), listener)
        } catch (t: Throwable) {
            null
        }
        if (created == null) {
            scheduleProbeLocked()
            return
        }
        if (!created.hasControlSafe()) {
            releaseIgnoringErrors(created)
            scheduleProbeLocked()
            return
        }
        effect = created
        if (!tryApply(created) || !tryEnable(created)) {
            releaseAndProbeLocked()
            return
        }
        publishLocked(LimiterState(available = true, controlled = true))
    }

    private fun releaseEffectLocked() {
        val e = effect
        effect = null
        if (e != null) releaseIgnoringErrors(e)
    }

    private fun releaseIgnoringErrors(e: LimiterEffect) {
        try {
            e.release()
        } catch (t: Throwable) {
            // release errors are ignored
        }
    }

    private fun publishLocked(newState: LimiterState) {
        if (newState == currentState) return
        currentState = newState
        onState(newState)
    }

    private companion object {
        const val PROBE_DELAY_MS = 5_000L
    }
}

/** Android DynamicsProcessing-backed [LimiterEffectFactory] (device only). */
internal class DynamicsProcessingLimiterEffectFactory : LimiterEffectFactory {

    override fun create(sessionId: Int, config: LimiterConfig, listener: LimiterEffectListener): LimiterEffect {
        val limiterInUse = config.limiterInUse
        val limiterEnabled = config.limiterEnabled
        val dpConfig = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            config.channelCount,
            config.preEqInUse, 0,
            config.mbcInUse, 0,
            config.postEqInUse, 0,
            config.limiterInUse,
        ).setPreferredFrameDuration(config.frameDurationMs).build()
        dpConfig.setInputGainAllChannelsTo(config.limiter.inputGainDb)
        dpConfig.setLimiterAllChannelsTo(limiter(limiterInUse, limiterEnabled, config.limiter))
        val processing = DynamicsProcessing(0, sessionId, dpConfig)
        processing.setControlStatusListener { _, granted -> listener.onControlStatusChange(granted) }
        processing.setEnableStatusListener { _, enabled -> listener.onEnableStatusChange(enabled) }
        return object : LimiterEffect {
            override val enabled: Boolean get() = processing.enabled

            override fun hasControl(): Boolean = processing.hasControl()

            override fun setEnabled(enabled: Boolean) {
                processing.enabled = enabled
            }

            override fun apply(params: LimiterParams) {
                processing.setInputGainAllChannelsTo(params.inputGainDb)
                processing.setLimiterAllChannelsTo(limiter(limiterInUse, limiterEnabled, params))
            }

            override fun release() {
                processing.release()
            }
        }
    }

    private fun limiter(inUse: Boolean, enabled: Boolean, params: LimiterParams) =
        DynamicsProcessing.Limiter(
            inUse,
            enabled,
            params.linkGroup,
            params.attackMs,
            params.releaseMs,
            params.ratio,
            params.thresholdDb,
            params.postGainDb,
        )

    companion object {
        /**
         * The real output buffer duration in ms: `PROPERTY_OUTPUT_FRAMES_PER_BUFFER * 1000 /
         * PROPERTY_OUTPUT_SAMPLE_RATE`, falling back to 10 ms.
         */
        fun outputFrameDurationMs(context: Context): Float {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val frames = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull()
            val sampleRate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()
            return if (frames != null && frames > 0 && sampleRate != null && sampleRate > 0) {
                frames.toFloat() * 1000f / sampleRate.toFloat()
            } else {
                10f
            }
        }
    }
}

/**
 * Owns one shared audio session for an engine's lifetime: broadcasts OPEN on [open] and CLOSE on
 * [close], driving the underlying [LimiterController]. Idempotent.
 */
internal class LimiterSession(
    private val context: Context,
    val sessionId: Int,
    private val controller: LimiterController?,
) {
    @Volatile
    private var opened = false

    @Volatile
    private var closed = false

    /** True once [close] has been called. */
    internal val isClosed: Boolean get() = closed

    /** Current limiter frame duration in ms (0 when no controller was created). */
    internal val frameDurationMsForTest: Float get() = controller?.frameDurationMsForTest ?: 0f

    fun open(clipPrevention: Boolean) {
        if (opened) return
        val c = controller ?: return
        opened = true
        sendBroadcast(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
        c.start(clipPrevention)
    }

    fun setClipPrevention(on: Boolean) {
        controller?.setClipPrevention(on)
    }

    /** Forwards a new frame duration to the controller (no-op without a controller). */
    fun setFrameDurationMs(ms: Float) {
        controller?.setFrameDurationMs(ms)
    }

    fun close() {
        closed = true
        if (!opened) return
        opened = false
        val c = controller ?: return
        c.release()
        sendBroadcast(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
    }

    private fun sendBroadcast(action: String) {
        val intent = Intent(action)
            .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
            .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        context.sendBroadcast(intent)
    }

    companion object {
        fun create(
            context: Context,
            looper: Looper,
            effectFactory: LimiterEffectFactory,
            onState: (LimiterState) -> Unit,
        ): LimiterSession {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val generated = audioManager.generateAudioSessionId()
            if (generated == AudioManager.ERROR || generated == C.AUDIO_SESSION_ID_UNSET) {
                onState(LimiterState(available = false, controlled = false))
                return LimiterSession(context, C.AUDIO_SESSION_ID_UNSET, null)
            }
            val sessionId = generated
            val handler = Handler(looper)
            val scheduler = LimiterScheduler { delayMs, task ->
                val runnable = Runnable { task() }
                handler.postDelayed(runnable, delayMs)
                val cancel: () -> Unit = { handler.removeCallbacks(runnable) }
                cancel
            }
            val frameDurationMs = DynamicsProcessingLimiterEffectFactory.outputFrameDurationMs(context)
            val controller = LimiterController(sessionId, frameDurationMs, effectFactory, scheduler, onState)
            return LimiterSession(context, sessionId, controller)
        }
    }
}
