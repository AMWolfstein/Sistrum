// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/g711/g711_test.go and container/riff/demux.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs

import java.nio.ByteBuffer
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import me.misa198.airmedy.codecs.container.adpcm.BlockStream
import me.misa198.airmedy.codecs.container.riff.AdpcmWav
import me.misa198.airmedy.codecs.container.aiff.Ima4
import me.misa198.airmedy.codecs.container.RandomAccessSource
import me.misa198.airmedy.codecs.container.ByteBufferSource
import me.misa198.airmedy.codecs.audio.Buffer

@RunWith(Parameterized::class)
internal class G711OracleTest(private val fixture: Fixture) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name="{0}")
        fun corpus(): Collection<Array<Any>> = OracleCorpus.allRows.filter { (it.name.startsWith("waxflow-g711-tests/") || it.name=="waxflow-testdata/sine-alaw.wav" || it.name=="waxflow-testdata/sine-ulaw.wav") }.also { check(it.size==6) { "G.711 test corpus missing" } }.map { arrayOf<Any>(it) }
    }
    private fun open(source: RandomAccessSource): me.misa198.airmedy.codecs.codec.g711.G711 {
        val meta=g711Metadata(fixture.source.readBytes())
        return me.misa198.airmedy.codecs.codec.g711.G711.open(meta.law,meta.rate,meta.channels,source,meta.offset,meta.frames)
    }
    private fun open(source: ByteBuffer)=open(ByteBufferSource(source))
    @Test fun matchesOracleAndReusesBuffers() {
        val stream=open(fixture.byteBuffer())
        assertEquals("ok",fixture.status)
        assertEquals(fixture.value("rate").toInt(),stream.info.sampleRate)
        assertEquals(fixture.value("channels").toInt(),stream.info.channels)
        assertEquals(fixture.value("bits").toInt(),stream.info.bitsPerSample)
        assertEquals(fixture.value("frames").toLong(),stream.info.totalSamples)
        val digest=MessageDigest.getInstance("SHA-256"); var frames=0L; var borrowed: Buffer?=null
        while (true) {
            val block=stream.decodeBlock() ?: break
            if (borrowed!=null) { assertSame(borrowed,block); assertSame(borrowed.samples,block.samples) }
            borrowed=block; assertEquals(frames,block.position); digest.update(pcmBytes(block)); frames+=block.frames
        }
        assertEquals(stream.info.totalSamples,frames); assertEquals(fixture.value("pcm_sha256"),digest.digest().hex())
        assertNull(stream.decodeBlock())
    }
    @Test fun exactSeekingAgainstOracleVerifiedLinearPcm() {
        val stream=open(fixture.byteBuffer()); val digest=MessageDigest.getInstance("SHA-256")
        val targets=longArrayOf(0,1,stream.info.totalSamples/3,stream.info.totalSamples/2,4096L,stream.info.totalSamples-1)
            .filter { it<stream.info.totalSamples }.distinct()
        val expected=HashMap<Long,IntArray>()
        while (true) {
            val b=stream.decodeBlock() ?: break; digest.update(pcmBytes(b))
            for (target in targets) if (target in b.position until b.position+b.frames) {
                val start=(target-b.position).toInt()*b.channels
                expected[target]=b.samples.copyOfRange(start,minOf(start+16*b.channels,b.frames*b.channels))
            }
        }
        assertEquals(fixture.value("pcm_sha256"),digest.digest().hex())
        for (target in targets.reversed()) {
            stream.seekSample(target); val block=stream.decodeBlock()!!; val wanted=expected.getValue(target)
            assertEquals(target,block.position); assertTrue(block.discontinuity)
            assertArrayEquals(wanted,block.samples.copyOf(wanted.size))
            var remaining=block.frames.toLong()
            while (true) { val next=stream.decodeBlock() ?: break; assertFalse(next.discontinuity); remaining+=next.frames }
            assertEquals(stream.info.totalSamples-target,remaining)
        }
        stream.seekSample(stream.info.totalSamples); assertNull(stream.decodeBlock())
        stream.seekSample(stream.info.totalSamples+1); assertNull(stream.decodeBlock())
    }
    @Test fun fileSourceAndDirectBufferRegion() {
        TestInputStreamSource(fixture.source.inputStream()).use { source ->
            val stream=open(source); val digest=MessageDigest.getInstance("SHA-256")
            while (true) { val block=stream.decodeBlock() ?: break; digest.update(pcmBytes(block)) }
            assertEquals(fixture.value("pcm_sha256"),digest.digest().hex())
            stream.seekSample(1); assertEquals(1L,stream.decodeBlock()!!.position)
        }
        val bytes=fixture.source.readBytes(); val data=ByteBuffer.allocateDirect(bytes.size+19)
        data.position(7); data.put(bytes); data.limit(7+bytes.size); data.position(7)
        val stream=open(data); assertEquals(7,data.position()); val digest=MessageDigest.getInstance("SHA-256")
        while (true) { val block=stream.decodeBlock() ?: break; digest.update(pcmBytes(block)) }
        assertEquals(fixture.value("pcm_sha256"),digest.digest().hex())
    }
}

// Test-only WAV metadata reader: production G.711 consumes extractor metadata.
internal data class G711Metadata(val law:me.misa198.airmedy.codecs.codec.g711.Law,val rate:Int,val channels:Int,val offset:Long,val frames:Long)
internal fun g711Metadata(bytes:ByteArray):G711Metadata {
 val b=ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);var pos=12;var tag=0;var rate=0;var channels=0
 while(pos+8<=bytes.size){val name=String(bytes,pos,4,Charsets.US_ASCII);val size=b.getInt(pos+4);check(size>=0&&pos+8L+size<=bytes.size)
  if(name=="fmt "){tag=b.getShort(pos+8).toInt();channels=b.getShort(pos+10).toInt();rate=b.getInt(pos+12)}
  if(name=="data"){check(tag==6||tag==7);return G711Metadata(if(tag==6)me.misa198.airmedy.codecs.codec.g711.Law.ALaw else me.misa198.airmedy.codecs.codec.g711.Law.MuLaw,rate,channels,(pos+8).toLong(),size.toLong()/channels)}
  pos+=8+size+(size and 1)
 };error("missing WAV data")
}
