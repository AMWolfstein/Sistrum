// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow container/mp4/demux_test.go, codec/alac/encode_test.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import me.misa198.airmedy.codecs.codec.alac.Alac
import me.misa198.airmedy.codecs.audio.Buffer

@RunWith(Parameterized::class)
internal class AlacOracleTest(private val fixture: Fixture) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name="{0}")
        fun corpus(): Collection<Array<Any>> = OracleCorpus.allRows.filter { it.status=="ok" && (it.name.startsWith("waxflow-alac-tests/") || it.name=="waxflow-testdata/chapters.m4b") }
            .also { check(it.size>=8) { "ALAC oracle corpus missing" } }.map { arrayOf<Any>(it) }
    }
    @Test fun matchesOracleAndReusesBuffers() {
        val data=AlacPackets(fixture); val stream=Alac.open(data.cookie,data.bytes,data.index)
        assertEquals("ok",fixture.status)
        assertEquals(fixture.value("rate").toInt(),stream.info.sampleRate)
        assertEquals(fixture.value("channels").toInt(),stream.info.channels)
        assertEquals(fixture.value("bits").toInt(),stream.info.bitDepth)
        assertEquals(fixture.value("frames").toLong(),stream.totalSamples)
        val digest=MessageDigest.getInstance("SHA-256"); var frames=0L; var borrowed: Buffer?=null
        while (true) {
            val block=stream.decodeBlock() ?: break
            if (borrowed!=null) { assertSame(borrowed,block); assertSame(borrowed.samples,block.samples) }
            borrowed=block; assertEquals(frames,block.position); digest.update(pcmBytes(block)); frames+=block.frames
        }
        assertEquals(stream.totalSamples,frames); assertEquals(fixture.value("pcm_sha256"),digest.digest().hex()); assertNull(stream.decodeBlock())
    }
    @Test fun exactSampleSeekingMatchesOracleVerifiedLinearPcm() {
        val data=AlacPackets(fixture); val stream=Alac.open(data.cookie,data.bytes,data.index)
        val targets=longArrayOf(0,1,stream.totalSamples/3,stream.totalSamples/2,stream.info.frameLength.toLong(),stream.totalSamples-1)
            .filter { it<stream.totalSamples }.distinct()
        val expected=HashMap<Long,IntArray>(); val digest=MessageDigest.getInstance("SHA-256")
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
            assertEquals(target,block.position); assertTrue(block.discontinuity); assertArrayEquals(wanted,block.samples.copyOf(wanted.size))
        }
        stream.seekSample(stream.totalSamples); assertNull(stream.decodeBlock())
    }
    @Test fun boundedFileAndLongOffsetPartialReads() {
        val data=AlacPackets(fixture)
        val backing=me.misa198.airmedy.codecs.container.ByteBufferSource(data.bytes)
        val origin=(1L shl 31)+321
        val source=object: me.misa198.airmedy.codecs.container.RandomAccessSource {
            override val length=origin+backing.length
            override fun read(position: Long,buffer: java.nio.ByteBuffer): Int {
                val limit=buffer.limit(); buffer.limit(minOf(limit,buffer.position()+7))
                return try { backing.read(position-origin,buffer) } finally { buffer.limit(limit) }
            }
        }
        val shifted=me.misa198.airmedy.codecs.codec.alac.PacketIndex(data.index.offsets.map { it+origin }.toLongArray(),data.index.sizes,data.index.sampleStarts,data.index.totalSamples)
        val stream=Alac.open(data.cookie,source,shifted); val digest=MessageDigest.getInstance("SHA-256")
        while (true) { val block=stream.decodeBlock() ?: break; digest.update(pcmBytes(block)) }
        assertEquals(fixture.value("pcm_sha256"),digest.digest().hex())
        val root=java.io.File(checkNotNull(System.getProperty("waxflow.alacPackets")) { "Run bash scripts/waxflow-oracle.sh --fetch-generate, then run tests through Gradle" })
        java.nio.channels.FileChannel.open(java.io.File(root,fixture.name+".packets").toPath(),java.nio.file.StandardOpenOption.READ).use { channel ->
            val fileStream=Alac.open(data.cookie,me.misa198.airmedy.codecs.container.FileChannelSource(channel),data.index)
            val fileDigest=MessageDigest.getInstance("SHA-256")
            while (true) { val block=fileStream.decodeBlock() ?: break; fileDigest.update(pcmBytes(block)) }
            assertEquals(fixture.value("pcm_sha256"),fileDigest.digest().hex())
            fileStream.seekSample(1); assertEquals(1L,fileStream.decodeBlock()!!.position)
        }
    }

}
