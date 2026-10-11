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

class IffReadContractsTest {
    private fun bytes(name: String)=Files.readAllBytes(cache.resolve("owned/iff-$name"))
    private class Observed(val data: ByteArray,val allowed: (Long,Int)->Boolean={_,_->true},val short: Int=Int.MAX_VALUE): RandomAccessSource {
        override val length=data.size.toLong();var total=0L;var largest=0
        override fun read(position: Long,buffer: ByteBuffer): Int {
            val n=buffer.remaining();check(allowed(position,n)) {"forbidden payload read $position + $n"}
            largest=maxOf(largest,n);if(position>=length) return -1
            val count=minOf(n,short,(length-position).toInt());buffer.put(data,position.toInt(),count);total+=count;return count
        }
    }
    @Test fun readsOnlyStructuralFieldsAndTagRegions() {
        for(name in listOf("base.wav","native.wav","id3.wav","rf64.wav","bw64.wav","rf64-table.wav","rf64-fact-normal.wav","opaque.wav","large-opaque.wav","base.aiff","id3.aiff","opaque.aiff","aiff-offset.aiff","aifc-696d6134.aifc","ffmpeg-adpcm_ima_wav.wav","ffmpeg-adpcm_ima_qt.aifc")) {
            val data=bytes(name);val input=Input(Observed(data),ReadOptions());val aiff=data.latin(0,4)=="FORM"
            val ds=if(data.latin(0,4) in listOf("RF64","BW64")) IffWalker.ds64(input) else null
            val walk=IffWalker.recovering(input,data.size.toLong(),aiff,ds).first
            val allowed=mutableListOf(0L to 12L)
            for(ch in walk.chunks) {
                allowed+=ch.offset-8 to ch.offset
                val n=when(ch.id) {
                    "fmt " -> minOf(16,ch.size);"COMM" -> minOf(22,ch.size);"SSND","fact" -> minOf(4,ch.size)
                    "ds64","id3 ","ID3 ","NAME","AUTH","(c) ","ANNO" -> ch.size
                    "LIST" -> if(input.bytes(ch.offset,4).latin()=="INFO") ch.size else 4
                    else -> 0
                }
                if(n>0) allowed+=ch.offset to ch.offset+n
            }
            val source=Observed(data,{p,n -> allowed.any {p>=it.first && p+n<=it.second}})
            assertNull("$name",Metadata.read(source).error)
            assertEquals(jsonProjection(Metadata.read(Observed(data))),jsonProjection(Metadata.read(source)))
        }
    }
    @Test fun factControlsCompressedDurationButNotPcmAndRejectsImplausibleCounts() {
        val compressed=Metadata.read(Observed(bytes("fact-accepted.wav")))
        assertEquals(160,compressed.properties.totalSamples);assertEquals(3,compressed.properties.durationMs)
        assertEquals(160,Metadata.read(Observed(bytes("rf64-fact-accepted.wav"))).properties.totalSamples)
        assertEquals(80,Metadata.read(Observed(bytes("fmt-1-fact-160.wav"))).properties.totalSamples)
        assertEquals(0,Metadata.read(Observed(bytes("fmt-17-fact-160.wav"))).properties.totalSamples)
        assertEquals(0,Metadata.read(Observed(bytes("fmt-17-fact-4294967295.wav"))).properties.totalSamples)
    }
    @Test fun emptyNativeValuesAndDuplicateNumbersRemainDistinct() {
        val empty=Metadata.read(Observed(bytes("info-empty.wav")));assertEquals(listOf(""),empty.tags["TITLE"])
        val duplicate=Metadata.read(Observed(bytes("info-duplicate.wav")))
        assertEquals(listOf("One","Two"),duplicate.tags["TITLE"]);assertEquals(listOf("03"),duplicate.tags["TRACKNUMBER"]);assertEquals(listOf("12"),duplicate.tags["TRACKTOTAL"])
        val first=Metadata.read(Observed(bytes("info-duplicates.wav")));assertEquals(listOf("One"),first.tags["TITLE"]);assertTrue(first.warnings.any {it.code=="duplicate-tag-block"})
        val native=Metadata.read(Observed(bytes("info-latin1.wav")));assertEquals(listOf("café"),native.tags["TITLE"])
    }
    @Test fun scanStreamsImagesNoneSkipsThemAndFullLoadsLazily() {
        for(suffix in listOf("wav","aiff")) {
            val data=bytes("large-art.$suffix");val scan=Observed(data);val doc=Metadata.read(scan)
            assertNull(doc.error);assertEquals(1,doc.pictures.size);assertNull(doc.pictures.single().bytes());assertTrue(scan.largest<=8192)
            val start=data.indexOfSlice(byteArrayOf(0x89.toByte(),0x50,0x4e,0x47));val end=start+doc.pictures.single().size
            val none=Observed(data,{p,n -> p+n<=start || p>=end})
            val skipped=Metadata.read(none,ReadOptions(pictures=Pictures.NONE));assertNull(skipped.error);assertTrue(skipped.pictures.isEmpty());assertTrue(none.total<1024)
            val full=Observed(data);val loaded=Metadata.read(full,ReadOptions(pictures=Pictures.FULL,maxReadBytes=2_000_000));assertNull(loaded.error)
            val before=full.total
            repeat(3) {val payload=loaded.pictures.single().bytes()!!;assertEquals(doc.pictures.single().sha256,java.security.MessageDigest.getInstance("SHA-256").digest(payload).hex());payload[0]=0}
            assertTrue(full.total>before)
        }
    }
    private fun ByteArray.indexOfSlice(needle: ByteArray): Int=(0..size-needle.size).first {p -> needle.indices.all {this[p+it]==needle[it]}}
    @Test fun oneByteReadsPreserveProjection() {
        for(name in listOf("id3.wav","id3.aiff","rf64.wav","bw64.wav","rf64-table.wav","info-missing-pad.wav","aifc-696d6134.aifc")) {
            val data=bytes(name);assertEquals(jsonProjection(Metadata.read(Observed(data))),jsonProjection(Metadata.read(Observed(data,short=1))))
        }
    }
    @Test fun sourceFailuresBecomeTypedIo() {
        for(name in listOf("id3.wav","id3.aiff","rf64.wav")) {
            val data=bytes(name)
            for(stop in listOf(0,12,20,40,100)) {
                val source=object: RandomAccessSource {
                    override val length=data.size.toLong()
                    override fun read(position: Long,buffer: ByteBuffer): Int {if(position+buffer.remaining()>stop) throw java.io.IOException("owned failure");val n=buffer.remaining();buffer.put(data,position.toInt(),n);return n}
                }
                assertEquals(ErrorClass.IO,Metadata.read(source).error?.errorClass)
            }
        }
    }
    @Test fun resourceLimitsBoundChunksInfoDs64AndPictures() {
        for(name in listOf("large-art.wav","large-art.aiff")) assertEquals(ErrorClass.SIZE_TOO_LARGE,Metadata.read(Observed(bytes(name)),ReadOptions(maxTagBytes=4096)).error?.errorClass)
        assertEquals(ErrorClass.SIZE_TOO_LARGE,Metadata.read(Observed(bytes("native.wav")),ReadOptions(maxElements=2)).error?.errorClass)
        assertEquals(ErrorClass.SIZE_TOO_LARGE,Metadata.read(Observed(bytes("id3.aiff")),ReadOptions(maxElements=2)).error?.errorClass)
        val data=bytes("rf64.wav");ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(44,65537)
        assertEquals(ErrorClass.SIZE_TOO_LARGE,Metadata.read(Observed(data)).error?.errorClass)
        assertEquals(ErrorClass.SIZE_TOO_LARGE,Metadata.read(Observed(bytes("id3.wav")),ReadOptions(maxReadBytes=16)).error?.errorClass)
    }
    @Test fun nativeInfoFloodRetainsEveryValueAndSelectedFamily() {
        val out=java.io.ByteArrayOutputStream();out.write("INFO".toByteArray())
        repeat(2000) {i ->
            val value="Value $i\u0000".toByteArray();out.write("INAM".toByteArray())
            out.write(ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(value.size).array());out.write(value)
            if(value.size%2!=0) out.write(0)
        }
        val body=out.toByteArray();val base=bytes("base.wav")
        val data=base.copyOfRange(0,36)+"LIST".toByteArray()+ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(body.size).array()+body+base.copyOfRange(36,base.size)
        ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(4,data.size-8)
        val doc=Metadata.read(Observed(data));assertNull(doc.error)
        assertEquals(2000,doc.tags["TITLE"]!!.size);assertEquals(2000,doc.families.size);assertTrue(doc.families.all {it.selected})
    }
    @Test fun malformedTruncatedAndMutatedInputsNeverThrow() {
        val random=Random(20261011)
        for(name in listOf("id3.wav","id3.aiff","rf64.wav","bw64.wav","rf64-table.wav","aifc-696d6134.aifc")) {
            val seed=bytes(name)
            for(cut in 0..minOf(2048,seed.size)) Metadata.read(Observed(seed.copyOf(cut)),ReadOptions(maxTagBytes=4096,maxElements=64))
            repeat(250) {val copy=seed.copyOf();repeat(8) {copy[random.nextInt(copy.size)]=random.nextInt(256).toByte()};Metadata.read(Observed(copy),ReadOptions(maxTagBytes=4096,maxElements=64,maxReadBytes=32768))}
        }
    }
}
