package com.amwolfstein.sistrum.player.media3

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.pow
import org.junit.Assert.*
import org.junit.Test

@OptIn(UnstableApi::class)
class FloatChainNightModeLifecycleTest {
    @Test
    fun flushAndRateChannelReconfigureResetVoiceHistory() {
        var enabled = true
        val capture = Capture()
        val sink = FloatChainAudioSink(capture.sink, emptyList(), nightMode = { enabled })
        configure(sink, 48_000, 2)
        feed(sink, FloatArray(48_000 * 2) { 0.8f })
        sink.flush()
        capture.samples.clear()
        feed(sink, FloatArray(4800 * 2) { 0.04f })
        assertEquals("flush clears old gain reduction", 0.04f * makeup, capture.samples.last(), 1e-7f)

        configure(sink, 96_000, 3)
        capture.samples.clear()
        feed(sink, FloatArray(9600 * 3) { 0.04f })
        assertEquals(9600 * 3, capture.samples.size)
        assertEquals(0.04f * makeup, capture.samples.last(), 1e-7f)
        sink.reset()
        enabled = false
        configure(sink, 44_100, 1)
        capture.samples.clear()
        val dry = floatArrayOf(-0.0f, 0f, 0.125f, -1.5f)
        feed(sink, dry)
        assertEquals(dry.map { it.toRawBits() }, capture.samples.map { it.toRawBits() })
    }

    @Test
    fun pendingOutputIsNotReprocessedWhenSettingChangesUnderBackpressure() {
        var enabled = true
        val capture = Capture()
        val sink = FloatChainAudioSink(capture.sink, emptyList(), nightMode = { enabled })
        configure(sink, 48_000, 1)
        capture.samplesPerCall = 11
        val input = buffer(FloatArray(4800) { 0.04f })
        assertFalse(sink.handleBuffer(input, 0, 1))
        enabled = false
        var attempts = 0
        while (!sink.handleBuffer(input, 0, 1)) {
            assertTrue("pending output must drain", ++attempts < 500)
        }
        assertEquals(4800, capture.samples.size)
        assertEquals("pending ON block keeps its original processing", 0.04f * makeup, capture.samples.last(), 1e-7f)
        capture.samplesPerCall = Int.MAX_VALUE
        feed(sink, FloatArray(4800) { 0.04f })
        assertEquals(0.04f, capture.samples.last(), 0f)
    }

    @Test
    fun rapidReversalStartsFromCurrentBlendAndEmptyBuffersDoNotAdvanceIt() {
        var enabled = false
        val capture = Capture()
        val sink = FloatChainAudioSink(capture.sink, emptyList(), nightMode = { enabled })
        configure(sink, 48_000, 1)
        enabled = true
        feed(sink, FloatArray(600) { 0.04f }) // Reverse after 12.5 ms of the 50 ms blend.
        val turning = capture.samples.last()
        enabled = false
        repeat(10) { feed(sink, floatArrayOf()) }
        assertEquals(600, capture.samples.size)
        feed(sink, FloatArray(2400) { 0.04f })
        val bound = 0.04f * (makeup - 1f) / 2400 + 2e-7f
        assertTrue(abs(turning - capture.samples[600]) <= bound)
        capture.samples.zipWithNext().forEach { (a, b) -> assertTrue(abs(a - b) <= bound) }
        assertEquals(0.04f.toRawBits(), capture.samples.last().toRawBits())
    }

    private fun configure(sink: FloatChainAudioSink, rate: Int, channels: Int) {
        sink.configure(AudioSink.AudioSinkConfig.Builder(Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW).setPcmEncoding(C.ENCODING_PCM_FLOAT)
            .setSampleRate(rate).setChannelCount(channels).build()).build())
    }

    private fun feed(sink: FloatChainAudioSink, samples: FloatArray) {
        assertTrue(sink.handleBuffer(buffer(samples), 0, 1))
    }

    private fun buffer(samples: FloatArray): ByteBuffer = ByteBuffer.allocateDirect(samples.size * 4)
        .order(ByteOrder.nativeOrder()).apply { asFloatBuffer().put(samples) }

    private class Capture {
        val samples = ArrayList<Float>()
        var samplesPerCall = Int.MAX_VALUE
        val sink = Proxy.newProxyInstance(AudioSink::class.java.classLoader, arrayOf(AudioSink::class.java)) { _, method, args ->
            when (method.name) {
                "configure", "flush", "reset" -> null
                "handleBuffer" -> {
                    val buffer = (args!![0] as ByteBuffer).order(ByteOrder.nativeOrder())
                    repeat(minOf(buffer.remaining() / 4, samplesPerCall)) { samples += buffer.float }
                    !buffer.hasRemaining()
                }
                else -> error("Unexpected AudioSink call: ${method.name}")
            }
        } as AudioSink
    }

    private companion object {
        val makeup = 10.0.pow(6.0 / 20).toFloat()
    }
}
