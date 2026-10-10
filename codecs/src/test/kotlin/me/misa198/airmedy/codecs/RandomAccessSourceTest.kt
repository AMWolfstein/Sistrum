package me.misa198.airmedy.codecs

import java.nio.ByteBuffer
import java.security.MessageDigest
import me.misa198.airmedy.codecs.container.*
import me.misa198.airmedy.codecs.container.wv.Wv
import org.junit.Assert.*
import org.junit.Test

class RandomAccessSourceTest {
    @Test fun partialReadsDecodeAndSeekWithoutWholeFileRequests() {
        val fixture=OracleCorpus.rows.single { it.name=="waxflow-testdata/sine-s16.wv" }
        val backing=ByteBufferSource(fixture.byteBuffer())
        var maximum=0
        val source=object: RandomAccessSource {
            override val length=backing.length
            override fun read(position: Long, buffer: ByteBuffer): Int {
                maximum=maxOf(maximum,buffer.remaining())
                val limit=buffer.limit()
                buffer.limit(minOf(limit,buffer.position()+7))
                return try { backing.read(position,buffer) } finally { buffer.limit(limit) }
            }
        }
        val stream=Wv.open(source)
        val digest=MessageDigest.getInstance("SHA-256")
        while (true) { val block=stream.decodeBlock() ?: break; digest.update(pcmBytes(block)) }
        assertEquals(fixture.value("pcm_sha256"),digest.digest().hex())
        assertTrue("Reads must be bounded",maximum<=262144)
        stream.seekSample(123)
        assertEquals(123L,stream.decodeBlock()!!.position)
    }
    @Test fun byteBufferSourceHonorsRegionPositionAndEof() {
        val bytes=ByteBuffer.wrap(byteArrayOf(9,1,2,3,9)); bytes.position(1); bytes.limit(4)
        val source=ByteBufferSource(bytes)
        val destination=ByteBuffer.allocate(5); destination.position(1); destination.limit(3)
        assertEquals(2,source.read(1,destination)); assertEquals(3,destination.position())
        assertEquals(3,destination.limit()); assertEquals(1,bytes.position())
        assertEquals(2,destination.get(1).toInt()); assertEquals(3,destination.get(2).toInt())
        destination.clear(); assertEquals(-1,source.read(3,destination))
        destination.limit(0); assertEquals(0,source.read(0,destination))
    }
    @Test fun windowsUseLongOffsetsAndRetainReadFailures() {
        val origin=(1L shl 31)+123
        val source=object: RandomAccessSource {
            override val length=origin+32
            override fun read(position: Long, buffer: ByteBuffer): Int {
                val count=minOf(buffer.remaining().toLong(),length-position).toInt()
                repeat(count) { buffer.put((position+it-origin).toByte()) }
                return count
            }
        }
        val window=me.misa198.airmedy.codecs.container.internal.srcwin.Window(source)
        assertEquals(16,window.ensure(origin,16))
        assertEquals(15,window.data.get(window.index(origin+15)).toInt())
        var reads=0
        val broken=object: RandomAccessSource {
            override val length=100L
            override fun read(position: Long, buffer: ByteBuffer): Int { reads++; return 0 }
        }
        val failed=me.misa198.airmedy.codecs.container.internal.srcwin.Window(broken)
        var first: java.io.IOException?=null
        repeat(2) {
            try { failed.ensure(0,4); fail("Zero-progress source accepted") }
            catch (e: java.io.IOException) {
                if (first==null) first=e else assertSame(first,e)
                assertEquals("wavpack: reading block data",e.message)
            }
        }
        assertEquals(1,reads)
    }
    @Test fun extendedIntegerMaximumWidthMatchesPinnedGo() {
        val bytes=java.io.File(System.getProperty("oracle.owned"),"wavpack/extended-max-width.wv").readBytes()
        val reference=javaClass.getResourceAsStream("/wavpack/extended-max-width.json")!!.bufferedReader().use { it.readText() }
        fun field(name: String)=Regex(""""$name": "([^"]+)"""").find(reference)!!.groupValues[1]
        assertEquals(OracleCorpus.PIN,field("waxflow_commit"))
        assertEquals(field("file_sha256"),MessageDigest.getInstance("SHA-256").digest(bytes).hex())
        val expected=Regex("\"samples\": \\[([^]]+)\\]",RegexOption.DOT_MATCHES_ALL).find(reference)!!.groupValues[1]
            .split(',').map { it.trim().toInt() }.toIntArray()
        val stream=Wv.open(ByteBuffer.wrap(bytes)); val block=stream.decodeBlock()!!
        assertEquals(44100,stream.info.sampleRate); assertEquals(1,block.channels); assertEquals(16,block.bits)
        assertEquals(16L,stream.info.totalSamples); assertEquals(16,block.frames)
        assertArrayEquals(expected,block.samples.copyOf(block.frames))
        assertEquals(field("pcm_sha256"),MessageDigest.getInstance("SHA-256").digest(pcmBytes(block)).hex())
        assertNull(stream.decodeBlock()); stream.seekSample(7)
        assertArrayEquals(expected.copyOfRange(7,16),stream.decodeBlock()!!.samples.copyOf(9))
    }
}
