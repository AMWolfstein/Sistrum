package me.misa198.airmedy.player.media3

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.runBlocking
import me.misa198.airmedy.player.NormalizationSettings
import me.misa198.airmedy.player.PlaybackItem
import me.misa198.airmedy.player.engine.ItemGain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented shared-session limiter tests for [Media3Engine] and [Media3PlayerFactory] (T046).
 * Drives an engine on the default (DynamicsProcessing-backed) factory, muted via
 * [Media3Engine.setFocusGain], and asserts the session id, the effect-control broadcasts and the
 * published [Media3PlayerFactory.limiterState].
 */
@RunWith(AndroidJUnit4::class)
class Media3LimiterTest {

    private fun context() = InstrumentationRegistry.getInstrumentation().targetContext

    private data class Received(val action: String, val sessionId: Int, val packageName: String?)

    @Test(timeout = 120_000)
    fun limiterSessionOpenCloseAndPlayerOnSharedSession() {
        val factory = Media3PlayerFactory(context())
        val engine = Media3Engine(factory)
        val received = ConcurrentLinkedQueue<Received>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent ?: return
                received += Received(
                    intent.action ?: "",
                    intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0),
                    intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME),
                )
            }
        }
        registerReceiver(receiver)
        try {
            val wav = writeSineWav(File(context().cacheDir, "limiter_${System.nanoTime()}.wav"))
            runBlocking { engine.prepare(item(wav.absolutePath), ItemGain.Unity, 0L, startPaused = false) }
            engine.setFocusGain(0f)

            val sessionId = engine.audioSessionIdForTest
            assertTrue("shared session id should be non-zero", sessionId != 0)
            assertEquals(sessionId, engine.playerAudioSessionIdForTest())

            val open = awaitBroadcast(received, AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            assertEquals(sessionId, open.sessionId)
            assertEquals(context().packageName, open.packageName)

            val state = factory.limiterState.value
            assertNotNull("limiterState should be published after prepare", state)
            Log.i("Media3LimiterTest", "limiterState=$state")
            InstrumentationRegistry.getInstrumentation().sendStatus(
                0,
                Bundle().apply { putString("limiterState", state.toString()) },
            )

            engine.setNormalization(NormalizationSettings(preventClip = false))
            engine.setNormalization(NormalizationSettings(preventClip = true))

            engine.close()

            val close = awaitBroadcast(received, AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
            assertEquals(sessionId, close.sessionId)
            assertEquals(context().packageName, close.packageName)
            assertEquals("live players leaked", 0, factory.livePlayers)
        } finally {
            engine.close()
            unregisterReceiver(receiver)
        }
    }

    @Test(timeout = 120_000)
    fun twoEnginesGetDifferentSessionsEachPlayerOnOwnSession() {
        val factory = Media3PlayerFactory(context())
        val engine1 = Media3Engine(factory)
        val engine2 = Media3Engine(factory)
        val wav = writeSineWav(File(context().cacheDir, "limiter2_${System.nanoTime()}.wav"))
        try {
            runBlocking { engine1.prepare(item(wav.absolutePath), ItemGain.Unity, 0L, startPaused = true) }
            engine1.setFocusGain(0f)
            runBlocking { engine2.prepare(item(wav.absolutePath), ItemGain.Unity, 0L, startPaused = true) }
            engine2.setFocusGain(0f)

            val session1 = engine1.audioSessionIdForTest
            val session2 = engine2.audioSessionIdForTest
            assertTrue("session ids should be non-zero", session1 != 0 && session2 != 0)
            assertTrue("two engines must get different session ids", session1 != session2)
            assertEquals(session1, engine1.playerAudioSessionIdForTest())
            assertEquals(session2, engine2.playerAudioSessionIdForTest())
        } finally {
            engine2.close()
            engine1.close()
            assertEquals("live players leaked", 0, factory.livePlayers)
        }
    }

    private fun registerReceiver(receiver: BroadcastReceiver) {
        val filter = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context().registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context().registerReceiver(receiver, filter)
        }
    }

    private fun unregisterReceiver(receiver: BroadcastReceiver) {
        try {
            context().unregisterReceiver(receiver)
        } catch (t: Throwable) {
            Log.i("Media3LimiterTest", "unregister failed: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun awaitBroadcast(received: Collection<Received>, action: String, timeoutMs: Long = 5_000L): Received {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            received.firstOrNull { it.action == action }?.let { return it }
            SystemClock.sleep(20)
        }
        throw AssertionError("no broadcast $action received; got: ${received.toList()}")
    }

    private fun item(path: String) = PlaybackItem(
        trackId = path,
        title = path,
        artist = "test",
        audioPath = path,
    )

    /** 3 s, 48 kHz, 16-bit stereo WAV of a 440 Hz sine at amplitude 0.1. */
    private fun writeSineWav(file: File): File {
        val sampleRate = 48_000
        val channels = 2
        val bitsPerSample = 16
        val bytesPerSample = bitsPerSample / 8
        val blockAlign = channels * bytesPerSample
        val numFrames = sampleRate * 3
        val dataSize = numFrames * blockAlign
        val amplitude = 0.1
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(36 + dataSize)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * blockAlign)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(bitsPerSample.toShort())
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataSize)
        for (i in 0 until numFrames) {
            val value = (sin(2.0 * PI * 440.0 * i / sampleRate) * amplitude * 32767.0)
                .roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
            buffer.putShort(value)
            buffer.putShort(value)
        }
        file.outputStream().use { it.write(buffer.array()) }
        return file
    }
}
