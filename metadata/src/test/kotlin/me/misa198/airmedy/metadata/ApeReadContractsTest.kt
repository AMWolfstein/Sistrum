// SPDX-License-Identifier: GPL-3.0-or-later
// Sistrum contracts for WaxLabel cfbb45d13135a76f5df6781fdeec75f5cfe63bf9.
// Copyright (c) 2026 Cole Springer, MIT; see metadata/THIRD-PARTY-NOTICES.
package me.misa198.airmedy.metadata

import me.misa198.airmedy.codecs.container.RandomAccessSource
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.file.Files
import kotlin.random.Random

class ApeReadContractsTest {
    private fun bytes(name: String)=Files.readAllBytes(cache.resolve("owned/ape-$name"))
    private class Observed(val data: ByteArray,val allowed: (Long,Int)->Boolean={_,_->true},val short: Int=Int.MAX_VALUE): RandomAccessSource {
        override val length=data.size.toLong()
        var largest=0; var total=0L
        override fun read(position: Long,buffer: ByteBuffer): Int {
            val n=buffer.remaining()
            check(allowed(position,n)) { "forbidden payload read $position + $n" }
            largest=maxOf(largest,n)
            if(position>=length) return -1
            val count=minOf(n,short,(length-position).toInt())
            buffer.put(data,position.toInt(),count);total+=count;return count
        }
    }
    @Test fun monkeysAndWavpackReadOnlyFixedHeadersAndTags() {
        for(name in listOf("structured.ape","mac-3800-0.ape","mac-3950-8.ape","structured.wv")) {
            val suffix=name.substringAfterLast('.')
            val data=bytes(name)
            val tagStart=data.size-data.le(data.size-20,4).toInt()-32
            val end=if(suffix=="ape" && data.le(4,2)>=3980) 76 else 32
            val source=Observed(data,{p,n -> p+n<=end || p>=tagStart || p==data.size-128L && n==3})
            assertNull("$suffix",Metadata.read(source).error)
        }
    }
    @Test fun musepackSkipsEveryAudioPacketBody() {
        for(name in listOf("structured.mpc","chapters.mpc","chapters-seek.mpc","chapters-faults.mpc","chapter-audio-alias.mpc")) {
            val data=bytes(name);val input=Input(Observed(data),ReadOptions());var p=4L
            val forbidden=mutableListOf<Pair<Long,Long>>()
            while(p<data.size) { val packet=Musepack.packet(input,p,data.size.toLong()) ?: break
                if(packet.key=="SE") break
                if(packet.key=="AP") forbidden+=packet.payload to packet.end
                p=packet.end
            }
            assertTrue(forbidden.isNotEmpty())
            val source=Observed(data,{off,n -> forbidden.none { off<it.second && off+n>it.first } || off==data.size-128L && n==3})
            val doc=Metadata.read(source)
            assertNull("$name",doc.error)
            if(name=="chapter-audio-alias.mpc") assertTrue(doc.chapters.isEmpty())
        }
    }
    @Test fun customWavpackRateAndDsdSkipSubblockPayloads() {
        for(name in listOf("wv-custom-rate.wv","wv-dsd.wv")) {
            val data=bytes(name)
            // Rate subblock: bytes 32..33 header, 34..36 rate, 37 alignment;
            // audio subblock: bytes 38..39 header, 40..79 encoded body.
            val source=Observed(data,{p,n -> p+n<=40 || p>=80 || p==data.size-128L && n==3})
            assertNull(Metadata.read(source).error)
        }
    }
    @Test fun scanStreamsLargeArtNoneSkipsBinaryAndFullLoadsLazily() {
        val data=bytes("cover-large.ape")
        val scan=Observed(data);val doc=Metadata.read(scan)
        assertNull(doc.error);assertEquals(1,doc.pictures.size);assertNull(doc.pictures.single().bytes());assertTrue(scan.largest<=8192)
        val pic=doc.pictures.single();val payloadStart=data.size-32-pic.size
        val none=Observed(data,{p,n -> p+n<=payloadStart || p>=data.size-32L || p==data.size-128L && n==3})
        val noPictures=Metadata.read(none,ReadOptions(pictures=Pictures.NONE))
        assertNull(noPictures.error);assertTrue(noPictures.pictures.isEmpty())
        assertTrue(none.total<512)
        val full=Observed(data);val loaded=Metadata.read(full,ReadOptions(pictures=Pictures.FULL,maxReadBytes=2_000_000))
        assertNull(loaded.error);val before=full.total
        repeat(3) { val payload=loaded.pictures.single().bytes()!!;assertEquals(pic.sha256,java.security.MessageDigest.getInstance("SHA-256").digest(payload).hex());payload[0]=0 }
        assertTrue(full.total>before)
        val binary=Observed(bytes("binary-large.ape"));assertNull(Metadata.read(binary).error);assertTrue(binary.total<512)
        for(name in listOf("cover-empty.ape","cover-unterminated.ape")) {
            val skipped=Metadata.read(Observed(bytes(name)),ReadOptions(pictures=Pictures.NONE))
            assertNull(skipped.error);assertFalse(skipped.warnings.any { it.code=="invalid-picture" })
        }
    }
    @Test fun mp3ApeIsLegacyAndDoesNotExposeCover() {
        val row=fixtures(4).first { it["path"].asString=="owned/ape-mp3-id3-authority.mp3" }
        val doc=Metadata.read(Observed(fixtureBytes(row)))
        assertNull(doc.error);assertFalse(doc.tags["TITLE"].orEmpty().contains("APE title"))
        assertTrue(doc.families.any { it.family=="apev2" && it.values.contains("APE title") && !it.selected })
        assertEquals(row.getAsJsonObject("oracle"),jsonProjection(doc))
        val data=bytes("structured.mp3")
        val tag=Ape.parse(Input(Observed(data),ReadOptions()),data.size.toLong())!!
        val cover=tag.items.single { it.nonText }
        for(mode in Pictures.entries) {
            val source=Observed(data,{p,n -> p+n<=cover.offset || p>=cover.offset+cover.size})
            val legacy=Metadata.read(source,ReadOptions(pictures=mode))
            assertNull(legacy.error);assertTrue(legacy.pictures.isEmpty())
        }
    }
    @Test fun shortReadsKeepAllFields() {
        for(suffix in listOf("ape","wv","mpc","mp3")) {
            val data=bytes("structured.$suffix")
            assertEquals(jsonProjection(Metadata.read(Observed(data))),jsonProjection(Metadata.read(Observed(data,short=1))))
        }
    }
    @Test fun failingSourcesReturnTypedIo() {
        for(suffix in listOf("ape","wv","mpc","mp3")) {
            val data=bytes("structured.$suffix")
            for(stop in listOf(4,24,76,data.size-24)) {
                val source=object: RandomAccessSource {
                    override val length=data.size.toLong()
                    override fun read(position: Long,buffer: ByteBuffer): Int {
                        if(position+buffer.remaining()>stop) throw java.io.IOException("owned source failure")
                        val n=buffer.remaining();buffer.put(data,position.toInt(),n);return n
                    }
                }
                assertEquals(ErrorClass.IO,Metadata.read(source).error?.errorClass)
            }
        }
    }
    @Test fun truncationsMutationsAndResourceLimitsNeverEscapeRead() {
        val random=Random(20261011)
        for(suffix in listOf("ape","wv","mpc","mp3")) {
            val data=bytes("structured.$suffix")
            for(cut in 0..minOf(2048,data.size)) Metadata.read(Observed(data.copyOf(cut)),ReadOptions(maxTagBytes=4096,maxElements=64))
            repeat(250) { val copy=data.copyOf();repeat(8) {copy[random.nextInt(copy.size)]=random.nextInt(256).toByte()};Metadata.read(Observed(copy),ReadOptions(maxTagBytes=4096,maxElements=64,maxReadBytes=32768)) }
        }
        assertEquals(ErrorClass.SIZE_TOO_LARGE,Metadata.read(Observed(bytes("cover-large.ape")),ReadOptions(maxTagBytes=4096)).error?.errorClass)
        val limited=Metadata.read(Observed(bytes("aliases.ape")),ReadOptions(maxElements=2))
        assertNull(limited.error);assertTrue(limited.warnings.any {it.code=="element-cap"})
    }
}
