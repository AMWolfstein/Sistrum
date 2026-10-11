// SPDX-License-Identifier: GPL-3.0-or-later
package com.amwolfstein.sistrum.codecs

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test
import com.amwolfstein.sistrum.codecs.container.RandomAccessSource
import com.amwolfstein.sistrum.codecs.container.dsd.Dsd

class DsdLengthTest {
    private fun decode(stream: Dsd): FloatArray {
        val result = ArrayList<Float>()
        var position = 0L
        while (true) {
            val b = stream.decodeBlock() ?: break
            assertEquals(position, b.position)
            repeat(b.frames * b.channels) { result.add(b.samples[it]) }
            position += b.frames
        }
        assertEquals(stream.info.totalSamples, position)
        return result.toFloatArray()
    }

    @Test fun exactRoundedLengthIncludesShortAndPartialFinalGroups() {
        for (rate in intArrayOf(2822400, 5644800, 11289600)) {
            val targets = when(rate) {
                2822400 -> intArrayOf(44100, 88200, 176400)
                5644800 -> intArrayOf(44100, 88200, 176400, 352800)
                else -> intArrayOf(88200, 176400, 352800, 705600)
            }
            for (target in targets) for (blocked in listOf(false, true)) {
                val d = rate / target
                for (bytes in listOf(1L, 3L, 7L, 4095L, 4096L, 4097L, 131079L)) {
                    val stream = Dsd.open(GeneratedSeekDsd(blocked, rate, 2, bytes), target)
                    val expected = (bytes * 8 + d / 2) / d
                    assertEquals(expected, stream.info.totalSamples)
                    assertEquals(expected * 2, decode(stream).size.toLong())
                }
            }
        }
    }

    @Test fun stepIsAlignedAndTheFinalSamplesAreDrained() {
        for (rate in intArrayOf(2822400, 5644800, 11289600)) {
            val target = Dsd.bestTargetRate(rate)
            val d = rate / target
            val stepFrame = 1000
            val byteCount = 2401L * d / 8 + 1
            for (blocked in listOf(false, true)) {
                val generated = GeneratedSeekDsd(blocked, rate, 1, byteCount)
                val headerSize = if(blocked) 92 else 110
                val source = object : RandomAccessSource {
                    override val length = generated.length
                    override fun read(position: Long, buffer: ByteBuffer): Int {
                        val start = buffer.position()
                        val n = generated.read(position, buffer)
                        for (i in 0 until n) if(position+i >= headerSize) {
                            val byte = position+i-headerSize
                            val value = if(byte * 8 < stepFrame * d) 0 else 255
                            buffer.put(start+i, value.toByte())
                        }
                        return n
                    }
                }
                val stream = Dsd.open(source, target)
                val all = decode(stream)
                assertEquals(-0.5f, all[0], 0.06f) // zero initial history at the first input sample
                assertEquals(0f, all[stepFrame], 0.06f)
                assertTrue(all[stepFrame-1] < 0f)
                assertTrue(all[stepFrame+1] > 0f)
                assertTrue("tail must reach the final source frames", all[all.size-10] > 0.8f)
                val seek = all.size - 7L
                stream.seekSample(seek)
                val block = stream.decodeBlock()!!
                assertEquals(seek, block.position)
                assertTrue(block.discontinuity)
                repeat(block.frames) { assertEquals(all[seek.toInt()+it].toRawBits(), block.samples[it].toRawBits()) }
                assertNull(stream.decodeBlock())
            }
        }
    }
}
