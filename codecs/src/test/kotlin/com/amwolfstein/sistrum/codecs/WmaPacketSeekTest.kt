package com.amwolfstein.sistrum.codecs

import com.amwolfstein.sistrum.codecs.container.asf.*
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

/** D-30: cold packet restarts must keep position, length, bounded work and audio quality. */
class WmaPacketSeekTest {
    @Test fun standardColdSeekHasExactPositionLengthAndBoundedWork() = checkLong("sine-wmav2.wma", 0x161)
    @Test fun lowRateStandardColdSeekHasExactPositionLengthAndBoundedWork() = checkLong("v2-8000-1ch.wma", 0x161, 47)
    @Test fun noiseCodedStandardColdSeekHasExactPositionLengthAndBoundedWork() = checkLong("v2-16000-1ch.wma", 0x161, 47)
    @Test fun lossless24BitColdSeekHasExactPositionLengthAndBoundedWork() = checkLong("ll-44100-2ch-24.wma", 0x163, 47)
    @Test fun losslessSurroundColdSeekHasExactPositionLengthAndBoundedWork() = checkLong("ll-48000-6ch-24.wma", 0x163, 47)
    @Test fun proColdSeekHasExactPositionLengthAndBoundedWork() = checkLong("pro-44100-2ch-16-128k.wma", 0x162)
    @Test fun losslessColdSeekHasExactPositionLengthAndBoundedWork() = checkLong("ll-44100-2ch-16.wma", 0x163)
    @Test fun voiceColdSeekHasExactPositionLengthAndBoundedWork() = checkLong("voice-16000-12k.wma", 0xa)

    @Test fun noiseScanCountsOnlyCodedChannelsAndLatchesMalformedPackets() {
        val fixture=OracleCorpus.allRows.single{it.name.endsWith("v2-16000-1ch.wma")}
        val demux=Wma.open(fixture.byteBuffer()).demux
        val cfg=com.amwolfstein.sistrum.codecs.codec.wma.Config.parse(ByteBuffer.wrap(demux.codecConfig))
        assertFalse(cfg.reservoir)
        assertFalse(cfg.varBlockLen)
        assertEquals(1,cfg.channels)
        assertEquals(512,cfg.frameLen)
        val packet=demux.next()!!
        val scanner=com.amwolfstein.sistrum.codecs.codec.wma.Decoder(cfg)
        assertEquals(0,scanner.scanNoise(packet.data,packet.size))
        // Fixed-length mono: a zero coded-channel bit means no coefficients and no noise.
        assertEquals(512,scanner.scanNoise(byteArrayOf(0),1))
        assertEquals(512,scanner.scanNoise(packet.data,packet.size))
        assertEquals(1024,scanner.scanNoise(packet.data,packet.size))
        assertEquals("entropy scan must do no synthesis",0L,counter(scanner))
        val failure=assertThrows(java.io.IOException::class.java){scanner.scanNoise(byteArrayOf(),0)}
        assertSame(failure,assertThrows(java.io.IOException::class.java){scanner.scanNoise(packet.data,packet.size)})
        scanner.reset(fromStart=true)
        assertEquals(0,scanner.scanNoise(packet.data,packet.size))
    }

    @Test fun indexHintsAndMalformedIndexesPreserveExactSeek() {
        val f=OracleCorpus.allRows.single{it.name.endsWith("sine-wmav2.wma")}
        val original=f.source.readBytes()
        val dataEnd=u64(original,16).toInt().let{it+u64(original,it+16).toInt()}
        val baseline=Wma.open(ByteBuffer.wrap(original))
        val linear=ByteArrayOutputStream()
        while(true){val b=baseline.decodeBlock()?:break;linear.write(pcmBytes(b))}
        assertEquals(f.value("pcm_sha256"),MessageDigest.getInstance("SHA-256").digest(linear.toByteArray()).hex())
        for(entries in listOf(longArrayOf(0,1,2),longArrayOf(0,0,0),longArrayOf(2,1,0),longArrayOf(0,Long.MAX_VALUE))){
            val index=ByteBuffer.allocate(56+entries.size*6).order(ByteOrder.LITTLE_ENDIAN)
            index.put(guidSimpleIndex.chunked(2).map{it.toInt(16).toByte()}.toByteArray())
            index.putLong(index.capacity().toLong()).put(ByteArray(16)).putLong(10_000_000).putInt(1).putInt(entries.size)
            entries.forEach{index.putInt(it.toInt()).putShort(1)}
            val stream=Wma.open(ByteBuffer.wrap(original.copyOf(dataEnd)+index.array()))
            if(entries.last()<entries.first()||entries.last()==Long.MAX_VALUE)assertTrue(stream.demux.warnings.any{it.contains("Simple Index")})
            for(target in listOf(0L,1L,f.value("frames").toLong()/2,f.value("frames").toLong()-1,f.value("frames").toLong())){
                stream.seekSample(target)
                val tail=ByteArrayOutputStream();var position=target
                while(true){val b=stream.decodeBlock()?:break;assertEquals(position,b.position);position+=b.frames;tail.write(pcmBytes(b))}
                assertEquals(f.value("frames").toLong(),position)
                assertArrayEquals(linear.toByteArray().copyOfRange((target*8).toInt(),linear.size()),tail.toByteArray())
            }
        }
    }

    private fun checkLong(suffix: String, tag: Int, targetPercent: Int = 50) {
        val fixture = OracleCorpus.allRows.single { it.name.endsWith(suffix) }
        val original = fixture.source.readBytes()
        val demux = open(original, tag).demux
        val objects = mutableListOf<Pair<Long, ByteArray>>()
        while (true) {
            val p = demux.next() ?: break
            assertFalse(p.gap)
            objects += p.pts to p.data.copyOf(p.size)
        }
        if (tag == 0xa) {
            // One complete pinned speech superframe per object gives exact 30 ms timestamps.
            // Repeating a fixture's trimmed final frame changes the frame-grid phase.
            val packet = oneSpeechSuperframe(demux.codecConfig, objects.first().second)
            objects.clear(); objects += 0L to packet
        }
        val rate = demux.format.rate
        val period = if (tag == 0xa) 480L else fixture.value("frames").toLong()
        val repeats = ((190L * rate + period - 1) / period).toInt()
        val bytes = remux(original, objects, period, rate, repeats, tag)
        val baseline = open(bytes, tag)
        var total = 0L
        while (true) { val b = baseline.next() ?: break; total += b.frames }
        println("LONG $suffix decoded=$total declared=${period * repeats} period=$period objects=${objects.size}")
        assertTrue("generated stream must decode at least three minutes", total >= 180L * rate)
        assertEquals("length from pinned payload frame count", period * repeats, total)
        val target = total * targetPercent / 100 + 17
        baseline.seek(0)
        val expected = ArrayList<Float>()
        val expectedHash = MessageDigest.getInstance("SHA-256")
        var linearTail = 0L
        while (true) {
            val b = baseline.next() ?: break
            val skip = maxOf(0L, target - b.position).coerceAtMost(b.frames.toLong()).toInt()
            linearTail += b.frames - skip
            for (i in skip * b.channels until b.frames * b.channels) { val bits = b.samples[i].toRawBits(); repeat(4) { expectedHash.update((bits ushr (it * 8)).toByte()) } }
            if (expected.size < rate * b.channels / 2) {
                for (i in skip * b.channels until b.frames * b.channels) {
                    if (expected.size == rate * b.channels / 2) break
                    expected += b.samples[i]
                }
            }
        }
        assertEquals(total - target, linearTail)
        val cold = open(bytes, tag)
        val before = cold.work()
        cold.seek(target)
        val actual = ArrayList<Float>()
        val actualHash = MessageDigest.getInstance("SHA-256")
        val first = cold.next()!!
        assertEquals(target, first.position)
        // Counts synthesis inside the decoder, including startup/discarded frames.
        val cost = cold.work() - before
        val maxPacketFrames = packetWork(demux.codecConfig, objects.map { it.second }, tag)
        // D-30: Voice retains 18 media objects of history for the unchanged short-file app oracle.
        val packetLimit = if (tag == 0xa) 19 else if (tag == 0x163) 4 else 3
        assertTrue("seek synthesized $cost frames; limit=${packetLimit * maxPacketFrames}", cost <= packetLimit * maxPacketFrames)
        var remaining = first.frames.toLong()
        fun collect(b: Chunk) {
            for (i in 0 until b.frames * b.channels) { assertTrue("seek PCM must remain finite", b.samples[i].isFinite()); val bits = b.samples[i].toRawBits(); repeat(4) { actualHash.update((bits ushr (it * 8)).toByte()) } }
            for (i in 0 until b.frames * b.channels) {
                if (actual.size == expected.size) break
                actual += b.samples[i]
            }
        }
        collect(first)
        while (true) { val b = cold.next() ?: break; remaining += b.frames; collect(b) }
        assertEquals("seek tail length", total - target, remaining)
        assertEquals(expected.size, actual.size)
        if (tag == 0xa) {
            val referencePower = expected.sumOf { it.toDouble() * it }
            val actualPower = actual.sumOf { it.toDouble() * it }
            assertTrue("reference speech must be non-silent", referencePower > 0)
            assertTrue("seek speech must be non-silent", actualPower > 0)
            val db = 10 * kotlin.math.log10(actualPower / referencePower)
            assertTrue("Voice RMS difference $db dB", kotlin.math.abs(db) <= 1)
        } else {
            assertArrayEquals("entire seek tail must equal linear decode", expectedHash.digest(), actualHash.digest())
            assertArrayEquals("PCM after preroll must equal linear decode", expected.map { it.toRawBits() }.toIntArray(), actual.map { it.toRawBits() }.toIntArray())
        }
    }

    private data class Chunk(val position: Long, val frames: Int, val channels: Int, val samples: FloatArray)
    private class Stream(val demux: Demux, val seek: (Long) -> Unit, val next: () -> Chunk?, val work: () -> Long)
    private fun open(bytes: ByteArray, tag: Int): Stream {
        val input = ByteBuffer.wrap(bytes)
        return when (tag) {
            0x161 -> { val s = Wma.open(input); Stream(s.demux, s::seekSample, { s.decodeBlock()?.let { Chunk(it.position, it.frames, it.channels, it.samples) } }, { counter(s.javaClass.getDeclaredField("decoder").also { it.isAccessible = true }.get(s)) }) }
            0x162 -> { val s = WmaPro.open(input); Stream(s.demux, s::seekSample, { s.decodeBlock()?.let { Chunk(it.position, it.frames, it.channels, it.samples) } }, { counter(s.javaClass.getDeclaredField("decoder").also { it.isAccessible = true }.get(s)) }) }
            0x163 -> { val s = WmaLossless.open(input); Stream(s.demux, s::seekSample, { s.decodeBlock()?.let { b -> Chunk(b.position, b.frames, b.channels, FloatArray(b.frames * b.channels) { b.samples[it].toFloat() }) } }, { counter(s.javaClass.getDeclaredField("decoder").also { it.isAccessible = true }.get(s)) }) }
            else -> { val s = WmaVoice.open(input); Stream(s.demux, s::seekSample, { s.decodeBlock()?.let { Chunk(it.position, it.frames, it.channels, it.samples) } }, { counter(s.javaClass.getDeclaredField("decoder").also { it.isAccessible = true }.get(s)) }) }
        }
    }

    private fun counter(decoder: Any): Long = decoder.javaClass.getDeclaredField("decodedFrames").also { it.isAccessible = true }.getLong(decoder)

    private fun packetWork(config: ByteArray, packets: List<ByteArray>, tag: Int): Long {
        val bytes = ByteBuffer.wrap(config)
        val decoder: Any
        val consume: (ByteArray) -> Unit
        when (tag) {
            0x161 -> { val d = com.amwolfstein.sistrum.codecs.codec.wma.Decoder(com.amwolfstein.sistrum.codecs.codec.wma.Config.parse(bytes)); decoder = d; consume = { d.decode(it, it.size) {} } }
            0x162 -> { val d = com.amwolfstein.sistrum.codecs.codec.wmapro.Decoder(com.amwolfstein.sistrum.codecs.codec.wmapro.Config.parse(bytes)); decoder = d; consume = { d.decode(it, it.size) {} } }
            0x163 -> { val d = com.amwolfstein.sistrum.codecs.codec.wmalossless.Decoder(com.amwolfstein.sistrum.codecs.codec.wmalossless.Config.parse(bytes)); decoder = d; consume = { d.acceptPacket(it, it.size); while (d.nextFrame() != null) {} } }
            else -> { val d = com.amwolfstein.sistrum.codecs.codec.wmavoice.Decoder(com.amwolfstein.sistrum.codecs.codec.wmavoice.Config.parse(bytes)); decoder = d; consume = { d.acceptPacket(it); while (d.nextFrame() != null) {} } }
        }
        var max = 0L
        for (packet in packets + packets) { val before = counter(decoder); consume(packet); max = maxOf(max, counter(decoder) - before) }
        assertTrue(max > 0)
        return max
    }

    private fun oneSpeechSuperframe(config: ByteArray, packet: ByteArray): ByteArray {
        val cfg = com.amwolfstein.sistrum.codecs.codec.wmavoice.Config.parse(ByteBuffer.wrap(config))
        val decoder = com.amwolfstein.sistrum.codecs.codec.wmavoice.Decoder(cfg)
        decoder.acceptPacket(packet)
        val reader = decoder.javaClass.getDeclaredField("r").also { it.isAccessible = true }.get(decoder) as com.amwolfstein.sistrum.codecs.codec.wmavoice.BitReader
        val from = reader.pos
        assertEquals(480, decoder.nextFrame()!!.frames)
        val to = reader.pos
        val bits = ArrayList<Int>()
        fun put(v: Int, n: Int) { for (i in n - 1 downTo 0) bits += (v ushr i) and 1 }
        put(0, 4); put((packet[0].toInt() ushr 3) and 1, 1); put(1, 6); put(0, cfg.geom().spilloverBits)
        for (i in from until to) bits += (packet[i / 8].toInt() ushr (7 - i % 8)) and 1
        return ByteArray((bits.size + 7) / 8).also { out ->
            for (i in bits.indices) out[i / 8] = (out[i / 8].toInt() or (bits[i] shl (7 - i % 8))).toByte()
        }
    }

    /** Repackage unchanged pinned codec payloads into fixed-size ASF data packets. */
    private fun remux(source: ByteArray, objects: List<Pair<Long, ByteArray>>, period: Long, rate: Int, repeats: Int, tag: Int): ByteArray {
        val count = objects.size * repeats
        val packetSize = 28 + objects.maxOf { it.second.size }
        val headerSize = u64(source, 16).toInt()
        val header = source.copyOf(headerSize + 50)
        val view = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        var at = 30
        while (at < headerSize) {
            val size = u64(header, at + 16).toInt()
            if (guidAt(header, at) == guidFileProperties) {
                val p = at + 24
                view.putLong(p + 16, header.size.toLong() + count.toLong() * packetSize)
                view.putLong(p + 32, count.toLong())
                view.putLong(p + 40, period * repeats * 10000000 / rate)
                view.putLong(p + 48, period * repeats * 10000000 / rate)
                view.putLong(p + 56, 0)
                view.putInt(p + 68, packetSize); view.putInt(p + 72, packetSize)
            }
            at += size
        }
        view.putLong(headerSize + 16, 50L + count.toLong() * packetSize)
        view.putLong(headerSize + 40, count.toLong())
        val result = ByteArrayOutputStream()
        result.write(header)
        for (n in 0 until count) {
            val (pts, payload) = objects[n % objects.size]
            val data = payload.copyOf()
            if (tag == 0x162 || tag == 0x163) data[0] = ((data[0].toInt() and 15) or ((n and 15) shl 4)).toByte()
            val ms = ((n / objects.size * period + pts) * 1000 / rate).toInt()
            val packet = ByteBuffer.allocate(packetSize).order(ByteOrder.LITTLE_ENDIAN)
            packet.put(0x10.toByte()).put(0x7d.toByte())
            packet.putShort((packetSize - 28 - data.size).toShort())
            packet.putInt(ms).putShort(0)
            packet.put(1.toByte()).putInt(n).putInt(0).put(8.toByte())
            packet.putInt(data.size).putInt(ms).put(data)
            result.write(packet.array())
        }
        return result.toByteArray()
    }
}
