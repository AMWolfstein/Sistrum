// SPDX-License-Identifier: GPL-3.0-or-later
// Sistrum contracts for WaxLabel cfbb45d13135a76f5df6781fdeec75f5cfe63bf9.
// Copyright (c) 2026 Cole Springer, MIT; see metadata/THIRD-PARTY-NOTICES.
package com.amwolfstein.sistrum.metadata

import com.amwolfstein.sistrum.codecs.container.RandomAccessSource
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.file.Files
import kotlin.random.Random

class AsfReadContractsTest {
    private fun bytes(name: String)=Files.readAllBytes(cache.resolve("owned/asf-$name.wma"))
    private class Observed(val data: ByteArray,val allowed: (Long,Int)->Boolean={_,_->true},val short: Int=Int.MAX_VALUE): RandomAccessSource {
        override val length=data.size.toLong();var largest=0;var total=0L
        override fun read(position: Long,buffer: ByteBuffer): Int {
            val n=buffer.remaining();check(allowed(position,n)) {"forbidden payload read $position + $n"};largest=maxOf(largest,n)
            if(position>=length) return -1
            val count=minOf(n,short,(length-position).toInt());buffer.put(data,position.toInt(),count);total+=count;return count
        }
    }
    private fun ByteArray.indexOfSlice(needle: ByteArray): Int=(0..size-needle.size).first {p -> needle.indices.all {this[p+it]==needle[it]}}
    @Test fun noDataPacketsAreReadIncludingCorruptHeaderAliases() {
        for(name in listOf("base","structured","metadata","library","markers","opaque","header-size-4294967295","header-count-0","header-in-packets","data-audio-alias","ffmpeg-wmav1","ffmpeg-wmav2","mutagen-wmav2","data-trailing-index")) {
            val data=bytes(name);val actual=data.indexOfSlice(AsfGuid.Data.toByteArray(Charsets.ISO_8859_1))
            val start=actual+50L;val end=actual+data.le(actual+16,8)
            val source=Observed(data,{p,n -> p+n<=start || p>=end})
            val doc=Metadata.read(source);assertNull(name,doc.error)
            if(name=="data-audio-alias") {assertEquals(0,doc.native.last().size);assertTrue(doc.warnings.any {it.code=="no-audio"})}
            if(name=="header-in-packets") assertTrue(doc.warnings.any {it.code=="no-audio"})
        }
    }
    @Test fun opaqueHeaderObjectsAndBinaryDescriptorBodiesAreSkipped() {
        val data=bytes("opaque");val input=Input(Observed(data),ReadOptions());val reader=AsfReader(input,Builder())
        val objs=reader.walk(30,data.le(16,8));val forbidden=objs.filter {objectName(it.id) in listOf("Codec List","Stream Bitrate Properties","Content Encryption","Extended Content Encryption","header object")}.map {it.body to it.end}
        assertEquals(5,forbidden.size)
        val source=Observed(data,{p,n -> forbidden.none {p<it.second && p+n>it.first}})
        assertNull(Metadata.read(source).error);assertTrue(source.total<2048)
        val binary=Observed(bytes("large-binary"));assertNull(Metadata.read(binary).error);assertTrue(binary.total<2048);assertTrue(binary.largest<2048)
    }
    @Test fun scanStreamsArtNoneSkipsTheWholePictureValueAndFullLoadsLazily() {
        val data=bytes("large-picture");val scan=Observed(data);val doc=Metadata.read(scan)
        assertNull(doc.error);assertEquals(1,doc.pictures.size);assertNull(doc.pictures.single().bytes());assertTrue(scan.largest<=8192)
        val image=data.indexOfSlice(byteArrayOf(0x89.toByte(),80,78,71));val start=image-37L;val end=image+doc.pictures.single().size.toLong()
        val none=Observed(data,{p,n -> p+n<=start || p>=end});val skipped=Metadata.read(none,ReadOptions(pictures=Pictures.NONE))
        assertNull(skipped.error);assertTrue(skipped.pictures.isEmpty());assertTrue(skipped.native.none {it.kind=="WM/Picture"});assertTrue(none.total<2048)
        val full=Observed(data);val loaded=Metadata.read(full,ReadOptions(pictures=Pictures.FULL,maxReadBytes=2_000_000));assertNull(loaded.error)
        val before=full.total
        repeat(3) {val payload=loaded.pictures.single().bytes()!!;assertEquals(doc.pictures.single().sha256,java.security.MessageDigest.getInstance("SHA-256").digest(payload).hex());payload[0]=0}
        assertTrue(full.total>before)
        for(name in listOf("picture-short","picture-no-mime","picture-no-desc","picture-empty")) {
            val absent=Metadata.read(Observed(bytes(name)),ReadOptions(pictures=Pictures.NONE));assertNull(absent.error);assertFalse(absent.warnings.any {it.code=="invalid-picture"})
        }
    }
    @Test fun deduplicationLosslessDepthAndMarkerOrderingFollowPinnedReader() {
        val dedup=Metadata.read(Observed(bytes("dedup")));assertEquals(listOf("Same"),dedup.tags["TITLE"])
        val conflicts=Metadata.read(Observed(bytes("conflicts")));assertEquals(listOf("One","Two"),conflicts.tags["TITLE"]);assertTrue(conflicts.families.first {it.key=="TITLE"}.selected);assertFalse(conflicts.families.first {it.key=="ORIGINALDATE"}.selected)
        assertEquals(24,Metadata.read(Observed(bytes("lossless-24-18"))).properties.bitsPerSample)
        assertEquals(16,Metadata.read(Observed(bytes("lossless-32-18"))).properties.bitsPerSample)
        assertEquals(listOf("Earlier","Later"),Metadata.read(Observed(bytes("marker-subms"))).chapters.map {it.title})
        val capped=Metadata.read(Observed(bytes("rate-over-int")));assertEquals(Int.MAX_VALUE,capped.properties.sampleRate);assertEquals(4294967295L,capped.properties.totalSamples)
    }
    @Test fun oneByteReadsPreserveAllFields() {
        for(name in listOf("structured","library","markers","mutagen-wmav2","picture-wrong-mime","data-audio-alias")) {
            val data=bytes(name);assertEquals(jsonProjection(Metadata.read(Observed(data))),jsonProjection(Metadata.read(Observed(data,short=1))))
        }
    }
    @Test fun sourceFailuresBecomeTypedIo() {
        for(name in listOf("structured","library","markers")) {
            val data=bytes(name)
            for(stop in listOf(0,16,30,80,200,data.le(16,8).toInt())) {
                val source=object: RandomAccessSource {
                    override val length=data.size.toLong()
                    override fun read(position: Long,buffer: ByteBuffer): Int {if(position+buffer.remaining()>stop) throw java.io.IOException("owned failure");val n=buffer.remaining();buffer.put(data,position.toInt(),n);return n}
                }
                assertEquals(ErrorClass.IO,Metadata.read(source).error?.errorClass)
            }
        }
    }
    @Test fun objectReadAndAllocationLimitsAreEnforced() {
        assertEquals(ErrorClass.SIZE_TOO_LARGE,Metadata.read(Observed(bytes("structured")),ReadOptions(maxElements=2)).error?.errorClass)
        assertEquals(ErrorClass.SIZE_TOO_LARGE,Metadata.read(Observed(bytes("structured")),ReadOptions(maxReadBytes=64)).error?.errorClass)
        // A header window cap, matching upstream, may leave oversized objects unparsed.
        val limited=Metadata.read(Observed(bytes("large-picture")),ReadOptions(maxTagBytes=4096))
        assertNull(limited.error);assertTrue(limited.pictures.isEmpty())
        val markers=Metadata.read(Observed(bytes("markers")),ReadOptions(maxElements=3))
        assertNull(markers.error);assertEquals(3,markers.chapters.size);assertTrue(markers.warnings.any {it.code=="element-cap"})
    }
    @Test fun malformedTruncatedAndMutatedInputsNeverEscapeRead() {
        val random=Random(20261011)
        for(name in listOf("structured","library","markers","data-audio-alias","mutagen-wmav2")) {
            val seed=bytes(name)
            for(cut in 0..minOf(2048,seed.size)) Metadata.read(Observed(seed.copyOf(cut)),ReadOptions(maxTagBytes=4096,maxElements=64))
            repeat(250) {val copy=seed.copyOf(minOf(seed.size,4096));repeat(8) {copy[random.nextInt(copy.size)]=random.nextInt(256).toByte()};Metadata.read(Observed(copy),ReadOptions(maxTagBytes=4096,maxElements=64,maxReadBytes=32768))}
        }
    }
}
