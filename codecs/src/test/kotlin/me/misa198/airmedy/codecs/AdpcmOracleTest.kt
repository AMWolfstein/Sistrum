// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/adpcm/adpcm_test.go, container/riff/demux.go, container/aiff/demux.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
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
internal class AdpcmOracleTest(private val fixture: Fixture) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name="{0}")
        fun corpus(): Collection<Array<Any>> = OracleCorpus.allRows.filter { it.name.contains("sine-msadpcm") || it.name.contains("ima4") }.also { check(it.size>=6) { "ADPCM test corpus missing" } }.map { arrayOf<Any>(it) }
    }
    private fun open(source: RandomAccessSource): BlockStream = when {fixture.name.endsWith(".aifc")->Ima4.open(source).stream;fixture.name.endsWith(".mov")->me.misa198.airmedy.codecs.container.mp4.Ima4.open(source).stream;else->AdpcmWav.open(source).stream}
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
        val targets=longArrayOf(0,1,stream.info.totalSamples/3,stream.info.totalSamples/2,stream.cfg.samplesPerBlock.toLong(),stream.info.totalSamples-1)
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
