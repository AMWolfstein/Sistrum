package com.amwolfstein.sistrum.player.media3

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

@OptIn(UnstableApi::class)
class NightModeAllocationTest {
    @Test
    fun warmedVoiceStageReusesBuffersAtNonzeroInputPosition() {
        val processor = Class.forName("com.amwolfstein.sistrum.player.media3.NightModeProcessor")
            .getDeclaredConstructor(Function0::class.java).apply { isAccessible = true }
            .newInstance({ true }) as AudioProcessor
        processor.configure(AudioProcessor.AudioFormat(48_000, 3, C.ENCODING_PCM_FLOAT))
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        val input = ByteBuffer.allocateDirect(4 + 48 * 3 * 4).order(ByteOrder.nativeOrder())
        input.putFloat(Float.NaN) // Prefix must never be read as PCM.
        repeat(48 * 3) { input.putFloat(0.04f) }
        val bean = ManagementFactory.getThreadMXBean() as ThreadMXBean
        assertTrue(bean.isThreadAllocatedMemorySupported)
        bean.isThreadAllocatedMemoryEnabled = true
        val thread = Thread.currentThread().id
        repeat(20_000) {
            input.position(4)
            processor.queueInput(input)
            processor.output.position(48 * 3 * 4)
        }
        repeat(10) { bean.getThreadAllocatedBytes(thread) }
        val before = bean.getThreadAllocatedBytes(thread)
        var blocks = 0
        while (blocks < 512) {
            input.position(4)
            processor.queueInput(input)
            processor.output.position(48 * 3 * 4)
            blocks++
        }
        val allocated = bean.getThreadAllocatedBytes(thread) - before
        assertEquals("steady ON must not allocate views per block", 0L, allocated)
        input.position(4)
        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.nativeOrder())
        repeat(48 * 3) { assertEquals(0.04f * 1.9952623f, output.float, 1e-7f) }
        val control = bean.getThreadAllocatedBytes(thread)
        repeat(512) { allocationEscape = ByteArray(1024) }
        assertTrue("allocation counter positive control", bean.getThreadAllocatedBytes(thread) - control >= 512 * 1024)
        processor.reset()
    }

    companion object { @Volatile private var allocationEscape: Any? = null }
}
