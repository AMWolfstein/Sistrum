// SPDX-License-Identifier: GPL-3.0-or-later
// Sistrum contracts for WaxLabel cfbb45d13135a76f5df6781fdeec75f5cfe63bf9.
// Copyright (c) 2026 Cole Springer (MIT); see metadata/THIRD-PARTY-NOTICES.
package me.misa198.airmedy.metadata

import me.misa198.airmedy.codecs.container.RandomAccessSource
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.file.Files
import kotlin.random.Random

class VorbisReadContractsTest {
    private fun bytes(name: String)=Files.readAllBytes(cache.resolve("owned/$name"))
    private class Observed(val data: ByteArray,val allowed: (Long,Int)->Boolean={_,_->true},val short: Int=Int.MAX_VALUE) : RandomAccessSource {
        override val length=data.size.toLong()
        var largest=0; var total=0L
        override fun read(position: Long,buffer: ByteBuffer): Int {
            val n=buffer.remaining()
            check(allowed(position,n)) { "forbidden audio/image payload read $position + $n" }
            largest=maxOf(largest,n)
            if(position>=length) return -1
            val count=minOf(n,short,(length-position).toInt())
            buffer.put(data,position.toInt(),count); total+=count
            return count
        }
    }
    private fun flacMetadataEnd(data: ByteArray): Int {
        var p=4
        while(true) { val header=data.u(p); val size=data.be(p+1,3).toInt(); p+=size+4; if(header and 128!=0) return p }
    }
    @Test fun flacReadsNoEncodedFramesEvenForTruncatedOrJunkAudio() {
        for(name in listOf("vorbis-structured.flac","vorbis-tail-junk.flac","vorbis-truncated-audio.flac","vorbis-large-comment-picture.flac")) {
            val data=bytes(name); val end=flacMetadataEnd(data)
            val observed=Observed(data,allowed={p,n -> p+n<=end || p==data.size-128L && n==3 })
            val doc=Metadata.read(observed)
            assertNull("$name: ${doc.error}",doc.error)
            assertEquals(48000,doc.properties.sampleRate)
        }
    }
    @Test fun oggReadsPageHeadersAndTagPacketsWithoutAudioBodies() {
        for(name in listOf("vorbis-mutagen.ogg","vorbis-mutagen.opus","vorbis-mutagen.oga","vorbis-ogg-native-picture.oga","vorbis-shared-page.opus")) {
            val data=bytes(name); val allowed=mutableListOf<Pair<Long,Long>>(); var p=0; var packets=0; var done=false
            val need=if(name.endsWith("ogg")) 3 else if(name.endsWith("opus")) 2 else 0
            var packetFirst=0
            while(p+27<=data.size && data.latin(p,4)=="OggS") {
                val count=data.u(p+26); val body=p+27+count
                allowed+=p.toLong() to body.toLong()
                var offset=body
                for(i in 0 until count) {
                    val n=data.u(p+27+i)
                    if(!done) {
                        if(packetFirst==0 && n>0) packetFirst=data.u(offset)
                        allowed+=offset.toLong() to (offset+n).toLong()
                        if(n<255) { packets++; if(need>0 && packets==need || need==0 && packets>=2 && packetFirst and 128!=0) done=true; packetFirst=0 }
                    }
                    offset+=n
                }
                p=offset
            }
            val observed=Observed(data,allowed={position,n -> allowed.any { position>=it.first && position+n<=it.second } })
            assertNull("$name",Metadata.read(observed).error)
        }
    }
    @Test fun metadataOnlyStreamsNativeAndBase64ArtInBoundedBuffers() {
        for(name in listOf("vorbis-large-native-picture.flac","vorbis-large-comment-picture.flac","vorbis-ogg-large-packet.ogg","vorbis-ogg-large-packet.opus","vorbis-ogg-large-packet.oga")) {
            val observed=Observed(bytes(name)); val doc=Metadata.read(observed)
            assertNull("$name: ${doc.error}",doc.error)
            assertEquals(1,doc.pictures.size); assertNull(doc.pictures.single().bytes())
            assertTrue("$name: ${observed.largest}",observed.largest<=8192)
        }
    }
    @Test fun noneSkipsNativeAndBase64PicturePayloads() {
        for(name in listOf("vorbis-large-native-picture.flac","vorbis-large-comment-picture.flac")) {
            val data=bytes(name); var p=4; var forbiddenStart=0; var forbiddenEnd=0
            while(p<data.size) {
                val code=data.u(p) and 127; val n=data.be(p+1,3).toInt()
                if(code==6) { forbiddenStart=p+4; forbiddenEnd=forbiddenStart+n }
                if(code==4) {
                    val body=p+4; val vendor=data.le(body,4).toInt(); val entry=body+4+vendor+4+4
                    val eq=(entry until p+4+n).first { data[it]=='='.code.toByte() }
                    forbiddenStart=eq+1; forbiddenEnd=p+4+n
                }
                if(data.u(p) and 128!=0) break
                p+=4+n
            }
            val observed=Observed(data,allowed={position,n -> position+n<=forbiddenStart || position>=forbiddenEnd })
            val doc=Metadata.read(observed,ReadOptions(pictures=Pictures.NONE))
            assertNull("$name: ${doc.error}",doc.error); assertTrue(doc.pictures.isEmpty()); assertTrue(observed.total<512)
        }
        for(name in listOf("vorbis-bad-base64.flac","vorbis-bad-picture.flac","vorbis-ogg-bad-picture.opus")) {
            val doc=Metadata.read(Observed(bytes(name)),ReadOptions(pictures=Pictures.NONE))
            assertNull(doc.error); assertFalse(doc.warnings.any { it.code=="invalid-picture" })
        }
    }
    @Test fun fullBase64ReadsHaveFreshBudgetsAndDetachedBytes() {
        val source=Observed(bytes("vorbis-large-comment-picture.flac"))
        val doc=Metadata.read(source,ReadOptions(pictures=Pictures.FULL,maxReadBytes=6_000_000))
        assertNull(doc.error)
        assertTrue(source.largest<=8192)
        val before=source.total
        val picture=doc.pictures.single()
        repeat(10) {
            val payload=picture.bytes()!!
            assertEquals(picture.sha256,java.security.MessageDigest.getInstance("SHA-256").digest(payload).hex())
            payload[0]=0
        }
        assertTrue(source.total>before)
    }
    @Test fun positiveOneByteReadsPreserveAllFields() {
        for(name in listOf("vorbis-structured.flac","vorbis-mutagen.ogg","vorbis-mutagen.opus","vorbis-mutagen.oga")) {
            val data=bytes(name)
            assertEquals(jsonProjection(Metadata.read(Observed(data))),jsonProjection(Metadata.read(Observed(data,short=1))))
        }
    }
    @Test fun malformedTruncatedAndMutatedVorbisNeverThrow() {
        val random=Random(20261011)
        for(name in listOf("vorbis-structured.flac","vorbis-mutagen.ogg","vorbis-mutagen.opus","vorbis-mutagen.oga")) {
            val seed=bytes(name)
            for(cut in 0..minOf(2048,seed.size)) Metadata.read(Observed(seed.copyOf(cut)),ReadOptions(maxTagBytes=4096,maxElements=64))
            repeat(250) {
                val copy=seed.copyOf(minOf(seed.size,4096))
                repeat(8) { copy[random.nextInt(copy.size)]=random.nextInt(256).toByte() }
                Metadata.read(Observed(copy),ReadOptions(maxTagBytes=4096,maxElements=64,maxReadBytes=32768))
            }
        }
    }
    @Test fun vorbisLimitsRejectOversizedBlocksAndCommentCounts() {
        for(name in listOf("vorbis-large-native-picture.flac","vorbis-large-comment-picture.flac","vorbis-ogg-large-packet.opus"))
            assertEquals(ErrorClass.SIZE_TOO_LARGE,Metadata.read(Observed(bytes(name)),ReadOptions(maxTagBytes=4096)).error?.errorClass)
        assertEquals(ErrorClass.SIZE_TOO_LARGE,Metadata.read(Observed(bytes("vorbis-structured.flac")),ReadOptions(maxElements=2)).error?.errorClass)
        val longName=ByteArray(65540) { 'A'.code.toByte() }
        val body=ByteBuffer.allocate(12+longName.size).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .putInt(0).putInt(1).putInt(longName.size).put(longName).array()
        val input=Input(Observed(body),ReadOptions())
        try { Vorbis.comments(input,0,body.size); fail("unbounded Vorbis name accepted") }
        catch(e: ReadFailure) { assertEquals(ErrorClass.SIZE_TOO_LARGE,e.kind) }
    }
}
