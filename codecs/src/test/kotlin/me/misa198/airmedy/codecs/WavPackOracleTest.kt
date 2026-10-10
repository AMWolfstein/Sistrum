package me.misa198.airmedy.codecs

import java.nio.ByteBuffer
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import me.misa198.airmedy.codecs.codec.wavpack.*
import me.misa198.airmedy.codecs.container.wv.Wv

@RunWith(Parameterized::class)
internal class WavPackOracleTest(private val fixture: Fixture) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name="{0}")
        fun corpus(): Collection<Array<Any>> = OracleCorpus.rows.map { arrayOf<Any>(it) }
    }
    @Test fun matchesOracle() {
        val data=fixture.byteBuffer()
        if (fixture.status.contains("hybrid streams") && fixture.name.endsWith(".wvc")) {
            try { val stream=Wv.open(data);while (stream.decodeBlock()!=null) { };fail("Correction-only input decoded") }
            catch (e: WavPackException) { assertEquals("wavpack: block has no wv bitstream",e.message) }
            return
        }
        if (fixture.status.contains("hybrid streams") || fixture.status.contains("32-bit float streams") || fixture.name.endsWith("vers-480-sfx.wv")) {
            // This mode has moved to the pinned libwavpack oracle, including damaged inputs.
            assertNotNull(LibWavPackCorpus.rows.single { it.name==fixture.name && !it.correction })
            return
        }
        if (fixture.status!="ok") {
            if (fixture.status=="refused: waxflow: format: unrecognized input (no magic bytes matched)") {
                // This correction file is rejected by WaxFlow's format registry before wv.NewDemuxer.
                assertFalse(fixture.name,match(data))
                try { Wv.open(data); fail("Accepted unrecognized WavPack input") }
                catch (e: WavPackException) { assertEquals(ErrorCode.MALFORMED,e.code); assertEquals("wavpack: not a WavPack file",e.message) }
                return
            }
            val expected=fixture.status.removePrefix("refused: waxflow: ")
            try { val stream=Wv.open(data); while (stream.decodeBlock()!=null) { }; fail("Accepted ${fixture.name}: $expected") }
            catch (e: WavPackException) { assertEquals(fixture.name,expected,e.message) }
            return
        }
        val stream=Wv.open(data)
        assertEquals(fixture.value("rate").toInt(),stream.info.sampleRate)
        assertEquals(fixture.value("channels").toInt(),stream.info.channels)
        assertEquals(fixture.value("bits").toInt(),stream.info.bits)
        assertEquals(fixture.value("frames").toLong(),stream.info.totalSamples)
        assertEquals("int",fixture.value("sample_format"))
        val digest=MessageDigest.getInstance("SHA-256")
        var frames=0L
        var borrowed: Any?=null
        var samples: IntArray?=null
        while (true) {
            val block=stream.decodeBlock() ?: break
            if (borrowed!=null) { assertSame(borrowed,block); assertSame(samples,block.samples) }
            borrowed=block; samples=block.samples
            digest.update(pcmBytes(block)); frames+=block.frames
        }
        assertEquals(fixture.value("frames").toLong(),frames)
        assertEquals(fixture.name,fixture.value("pcm_sha256"),digest.digest().hex())
        assertNull(stream.decodeBlock())
    }
    @Test fun seeksToExactSamples() {
        if (fixture.status!="ok") return
        val stream=Wv.open(fixture.byteBuffer())
        val total=fixture.value("frames").toLong()
        // Expected seek output is taken from linear PCM already checked by matchesOracle,
        // never a fabricated checksum or an assertion against a second port decoder.
        val targets=longArrayOf(0,1,total/3,total/2,total-1,total,total+1)
        val expected=HashMap<Long,IntArray>()
        val savedTargets=targets.filter { it<total }
        var position=0L
        while (true) {
            val block=stream.decodeBlock() ?: break
            for (target in savedTargets) {
                if (target>=position && target<position+block.frames) {
                    val start=(target-position).toInt()*block.channels
                    expected[target]=block.samples.copyOfRange(start,minOf(start+16*block.channels,block.frames*block.channels))
                }
            }
            position+=block.frames
        }
        for (target in targets.reversed()) {
            stream.seekSample(target)
            val block=stream.decodeBlock()
            if (target>=total) { assertNull("${fixture.name}: seek $target",block); continue }
            assertNotNull(block)
            block!!
            assertEquals(target,block.position)
            assertTrue(block.discontinuity)
            val wanted=expected.getValue(target)
            assertArrayEquals("${fixture.name}: seek $target",wanted,block.samples.copyOfRange(0,wanted.size))
            var frames=block.frames.toLong()
            while (true) { val next=stream.decodeBlock() ?: break; assertFalse(next.discontinuity); frames+=next.frames }
            assertEquals(total-target,frames)
        }
    }
    @Test fun inputStreamAndBufferRegion() {
        if (fixture.status!="ok") return
        val bytes=fixture.source.readBytes()
        val regions=listOf(ByteBuffer.allocate(bytes.size+19),ByteBuffer.allocateDirect(bytes.size+19))
        val streams=regions.map { region ->
            region.position(7); region.put(bytes); region.limit(7+bytes.size); region.position(7)
            val stream=Wv.open(region)
            assertEquals(7,region.position())
            stream
        }
        for (stream in streams) {
            val digest=MessageDigest.getInstance("SHA-256")
            while (true) { val block=stream.decodeBlock() ?: break; digest.update(pcmBytes(block)) }
            assertEquals(fixture.value("pcm_sha256"),digest.digest().hex())
        }
        TestInputStreamSource(fixture.source.inputStream()).use { source ->
            val stream=Wv.open(source)
            val digest=MessageDigest.getInstance("SHA-256")
            while (true) { val block=stream.decodeBlock() ?: break; digest.update(pcmBytes(block)) }
            assertEquals(fixture.value("pcm_sha256"),digest.digest().hex())
            stream.seekSample(1)
            assertEquals(1L,stream.decodeBlock()!!.position)
        }
    }
}
