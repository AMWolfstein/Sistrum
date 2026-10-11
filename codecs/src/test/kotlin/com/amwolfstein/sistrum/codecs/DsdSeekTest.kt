// SPDX-License-Identifier: GPL-3.0-or-later
package com.amwolfstein.sistrum.codecs

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test
import com.amwolfstein.sistrum.codecs.container.RandomAccessSource
import com.amwolfstein.sistrum.codecs.container.dsd.Dsd

/** Generated on demand: even the ten-minute stream occupies only a header and a byte pattern. */
internal class GeneratedSeekDsd(val blocked: Boolean, val rate: Int, val channels: Int, val byteCount: Long) : RandomAccessSource {
    private val dataSize = (if (blocked) (byteCount + 4095) / 4096 * 4096 else byteCount) * channels
    private val header = if (blocked) {
        ByteBuffer.allocate(92).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("DSD ".toByteArray()); putLong(28); putLong(92 + dataSize); putLong(0)
            put("fmt ".toByteArray()); putLong(52); putInt(1); putInt(0); putInt(if (channels == 1) 1 else 2)
            putInt(channels); putInt(rate); putInt(1); putLong(byteCount * 8); putInt(4096); putInt(0)
            put("data".toByteArray()); putLong(12 + dataSize)
        }.array()
    } else {
        ByteBuffer.allocate(106 + channels * 4).order(ByteOrder.BIG_ENDIAN).apply {
            put("FRM8".toByteArray()); putLong(capacity() - 12L + dataSize); put("DSD ".toByteArray())
            put("FVER".toByteArray()); putLong(4); putInt(0x01050000)
            put("PROP".toByteArray()); putLong(50L + channels * 4); put("SND ".toByteArray())
            put("FS  ".toByteArray()); putLong(4); putInt(rate)
            put("CHNL".toByteArray()); putLong(2L + channels * 4); putShort(channels.toShort())
            repeat(channels) { put((if (it == 0) "SLFT" else "SRGT").toByteArray()) }
            put("CMPR".toByteArray()); putLong(4); put("DSD ".toByteArray())
            put("DSD ".toByteArray()); putLong(dataSize)
        }.array()
    }
    private val pattern = Random(734).nextBytes(65536)
    var audioBytesRead = 0L
    override val length = header.size + dataSize
    override fun read(position: Long, buffer: ByteBuffer): Int {
        if (position >= length) return -1
        val n = minOf(buffer.remaining().toLong(), length - position).toInt()
        repeat(n) { i ->
            val p = position + i
            if (p < header.size) buffer.put(header[p.toInt()]) else {
                val offset = p - header.size
                val channel = if (blocked) (offset / 4096 % channels).toInt() else (offset % channels).toInt()
                val byte = if (blocked) offset / (4096 * channels) * 4096 + offset % 4096 else offset / channels
                val value = pattern[((byte + channel * 997) and 65535).toInt()]
                buffer.put(if (blocked) (Integer.reverse(value.toInt() and 255) ushr 24).toByte() else value)
                audioBytesRead++
            }
        }
        return n
    }
}

class DsdSeekTest {
    @Test fun randomSeeksMatchUninterruptedBitsAtEverySupportedRate() {
        for (rate in intArrayOf(2822400, 5644800, 11289600)) {
            val targets = when (rate) {
                2822400 -> intArrayOf(44100, 88200, 176400)
                5644800 -> intArrayOf(44100, 88200, 176400, 352800)
                else -> intArrayOf(88200, 176400, 352800, 705600)
            }
            for (blocked in listOf(false, true)) for (target in targets) {
                val stream = Dsd.open(GeneratedSeekDsd(blocked, rate, 2, 131079), target)
                val all = ArrayList<Int>()
                while (true) { val b = stream.decodeBlock() ?: break; repeat(b.frames * 2) { all.add(b.samples[it].toRawBits()) } }
                val random = Random(15)
                val blockFrames = 4096L * 8 * target / rate
                val positions = listOf(0L, 1L, blockFrames - 1, blockFrames, blockFrames + 1, stream.info.totalSamples - 1) +
                    List(24) { random.nextLong(stream.info.totalSamples) }
                for (position in positions) {
                    stream.seekSample(position)
                    var cursor = position
                    var first = true
                    while (true) {
                        val b = stream.decodeBlock() ?: break
                        assertEquals(cursor, b.position); assertEquals(first, b.discontinuity); first = false
                        repeat(b.frames * 2) { assertEquals("rate=$rate target=$target blocked=$blocked seek=$position", all[(cursor * 2).toInt() + it], b.samples[it].toRawBits()) }
                        cursor += b.frames
                    }
                    assertEquals(stream.info.totalSamples, cursor)
                }
                stream.seekSample(stream.info.totalSamples); assertNull(stream.decodeBlock())
                stream.seekSample(0); assertEquals(0L, stream.decodeBlock()!!.position)
            }
        }
    }

    @Test fun tenMinuteDsd256SeekCostIsBounded() {
        val wavpack = measureWavPackSeeks(9)
        assertTrue("WavPack 1% vs 99% seek+decode ratio: ${wavpack.ratio}", wavpack.ratio < 2.0)
        assertTrue("bounded WavPack source work: ${wavpack.maxBytes}", wavpack.maxBytes < 8 * 1024 * 1024)
        for (blocked in listOf(false, true)) {
            val result = measureDsdSeeks(blocked,  trials = 9)
            assertTrue("1% vs 99% seek+decode ratio: ${result.ratio}", result.ratio < 2.0)
            assertTrue("bounded source work: ${result.maxBytes}", result.maxBytes <= 32768)
        }
    }
}

internal data class SeekTiming(val earlyMs: Double, val lateMs: Double, val maxBytes: Long) {
    val ratio get() = maxOf(earlyMs, lateMs) / minOf(earlyMs, lateMs)
}

internal fun measureDsdSeeks(blocked: Boolean, trials: Int): SeekTiming {
    val source = GeneratedSeekDsd(blocked, 11289600, 1, 11289600L / 8 * 600)
    val stream = Dsd.open(source)
    val positions = longArrayOf(stream.info.totalSamples / 100, stream.info.totalSamples * 99 / 100)
    // Compile/hot-loop warmup without replaying a long stream in the baseline implementation.
    repeat(100) { stream.seekSample(0); repeat(4) { stream.decodeBlock() } }
    val times = Array(2) { LongArray(trials) }
    var maxBytes = 0L
    repeat(trials) { trial ->
        for (index in if (trial % 2 == 0) listOf(0, 1) else listOf(1, 0)) {
            source.audioBytesRead = 0
            val start = System.nanoTime()
            stream.seekSample(positions[index]); val block = stream.decodeBlock()!!
            times[index][trial] = System.nanoTime() - start
            assertEquals(positions[index], block.position)
            maxBytes = maxOf(maxBytes, source.audioBytesRead)
        }
    }
    return SeekTiming(times[0].sorted()[trials / 2] / 1e6, times[1].sorted()[trials / 2] / 1e6, maxBytes)
}

object DsdSeekBenchmark {
    @JvmStatic fun main(args: Array<String>) {
        if (args.getOrNull(1) != "wavpack") for (blocked in listOf(true, false)) {
            val result = measureDsdSeeks(blocked, args.firstOrNull()?.toInt() ?: 9)
            println("${if (blocked) "DSF" else "DFF"}: 1%=${result.earlyMs} ms 99%=${result.lateMs} ms ratio=${result.ratio} bytes=${result.maxBytes}")
        }
        val result = measureWavPackSeeks(args.firstOrNull()?.toInt() ?: 9)
        println("WavPack: 1%=${result.earlyMs} ms 99%=${result.lateMs} ms ratio=${result.ratio} bytes=${result.maxBytes}")
    }
}

/** Valid raw-mode WavPack blocks, generated without storing ten minutes of audio. */
internal class GeneratedSeekWavPack : RandomAccessSource {
    private val totalBytes = 11289600 / 8 * 600
    private val full = block(4096)
    private val tail = block(totalBytes % 4096)
    private val fullCount = totalBytes / 4096
    override val length = fullCount.toLong() * full.size + tail.size
    var bytesRead = 0L
    private fun block(n: Int): ByteArray {
        val pattern = Random(913).nextBytes(n)
        var crc = -1
        for (value in pattern) crc = crc * 3 + (value.toInt() and 255)
        return ByteBuffer.allocate(38 + n).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("wvpk".toByteArray()); putInt(capacity() - 8); putShort(0x410); putShort(0)
            putInt(totalBytes); putInt(0); putInt(n)
            putInt(Int.MIN_VALUE or 4 or 0x1800 or (9 shl 23)); putInt(crc)
            val words = (n + 2) / 2
            put(0x8e.toByte()); put(words.toByte()); put((words shr 8).toByte()); put(0)
            put(5); put(0); put(pattern)
        }.array()
    }
    override fun read(position: Long, buffer: ByteBuffer): Int {
        if (position >= length) return -1
        val count = minOf(buffer.remaining().toLong(), length - position).toInt()
        var done = 0
        while (done < count) {
            val p = position + done
            val index = minOf(p / full.size, fullCount.toLong())
            val template = if (index == fullCount.toLong()) tail else full
            val offset = (p - index * full.size).toInt()
            val n = minOf(count - done, template.size - offset)
            val start = buffer.position()
            buffer.put(template, offset, n)
            // Only the sample index varies between full blocks; the raw-bit CRC is unchanged.
            for (byte in 16..19) if (byte in offset until offset + n) {
                buffer.put(start + byte - offset, ((index * 4096) ushr ((byte - 16) * 8)).toByte())
            }
            done += n
        }
        bytesRead += count
        return count
    }
}

internal fun measureWavPackSeeks(trials: Int): SeekTiming {
    val source = GeneratedSeekWavPack()
    val stream = com.amwolfstein.sistrum.codecs.container.wv.WavPackDsd.open(source)
    val positions = longArrayOf(stream.info.totalSamples / 100, stream.info.totalSamples * 99 / 100)
    repeat(100) { stream.seekSample(0); repeat(4) { stream.decodeBlock() } }
    val times = Array(2) { LongArray(trials) }
    var maxBytes = 0L
    repeat(trials) { trial ->
        for (index in if (trial % 2 == 0) listOf(0, 1) else listOf(1, 0)) {
            source.bytesRead = 0
            val start = System.nanoTime()
            stream.seekSample(positions[index]); val block = stream.decodeBlock()!!
            times[index][trial] = System.nanoTime() - start
            assertEquals(positions[index], block.position)
            maxBytes = maxOf(maxBytes, source.bytesRead)
        }
    }
    return SeekTiming(times[0].sorted()[trials / 2] / 1e6, times[1].sorted()[trials / 2] / 1e6, maxBytes)
}
