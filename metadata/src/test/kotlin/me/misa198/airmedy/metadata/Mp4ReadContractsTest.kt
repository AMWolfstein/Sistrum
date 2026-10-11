// SPDX-License-Identifier: GPL-3.0-or-later
// Sistrum contracts for WaxLabel cfbb45d13135a76f5df6781fdeec75f5cfe63bf9.
// Copyright (c) 2026 Cole Springer, MIT; see metadata/THIRD-PARTY-NOTICES.
package me.misa198.airmedy.metadata

import me.misa198.airmedy.codecs.container.RandomAccessSource
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import kotlin.random.Random

class Mp4ReadContractsTest {
    private fun bytes(name: String)=Files.readAllBytes(cache.resolve("owned/mp4-$name"))
    private class Observed(val data: ByteArray,val allowed: (Long,Int)->Boolean={_,_->true},val short: Int=Int.MAX_VALUE): RandomAccessSource {
        override val length=data.size.toLong()
        var largest=0; var total=0L; var forbidden=false
        override fun read(position: Long,buffer: ByteBuffer): Int {
            val n=buffer.remaining()
            if(!allowed(position,n)) { forbidden=true; throw IOException("forbidden payload read") }
            largest=maxOf(largest,n)
            if(position>=length) return -1
            val count=minOf(n,short,(length-position).toInt()); buffer.put(data,position.toInt(),count); total+=count; return count
        }
    }
    private fun mdats(data: ByteArray): List<Pair<Long,Long>> {
        val out=mutableListOf<Pair<Long,Long>>(); var p=0
        while(data.size-p>=8) {
            var size=data.be(p,4); var header=8
            if(size==1L) { size=data.be(p+8,8); header=16 }; if(size==0L) size=(data.size-p).toLong()
            check(size>=header && size<=data.size-p)
            if(data.latin(p+4,4)=="mdat") out+=(p+header).toLong() to p+size
            p+=size.toInt()
        }; return out
    }
    @Test fun audioPayloadIsNeverRead() {
        for(name in listOf("base.m4a","mutagen.m4a","alac.m4a","flac.mp4","pcm_s24be.mov","pcm_f64le.mov","fragmented.m4a")) {
            val data=bytes(name); val ranges=mdats(data)
            val source=Observed(data,allowed={p,n -> ranges.none { p<it.second && p+n>it.first } })
            val doc=Metadata.read(source)
            assertNull("$name: ${doc.error}",doc.error); assertFalse(source.forbidden)
            assertEquals("MP4",doc.format)
        }
    }
    @Test fun onlyChapterTitlesAreReadInsideMdat() {
        val data=bytes("chapters.m4b"); val ranges=mdats(data)
        val titles=listOf("First","Second").map { text ->
            val needle=byteArrayOf(0,text.length.toByte())+text.toByteArray()
            val start=(0..data.size-needle.size).single { p -> data.copyOfRange(p,p+needle.size).contentEquals(needle) }
            start.toLong() to (start+needle.size).toLong()
        }
        val source=Observed(data,allowed={p,n -> ranges.none { p<it.second && p+n>it.first } || titles.any { p>=it.first && p+n<=it.second } })
        val doc=Metadata.read(source)
        assertNull(doc.error); assertFalse(source.forbidden)
        assertEquals(listOf("First","Second"),doc.chapters.map { it.title })
        assertEquals(listOf(25L,100L),doc.chapters.map { it.startMs })
    }
    @Test fun chapterOffsetsCannotAliasAudioOrStructuralBytes() {
        for(name in listOf("chapter-audio-alias.m4a","stco-structural.m4a","stco-outside.m4a")) {
            val data=bytes(name); val ranges=mdats(data)
            val source=Observed(data,allowed={p,n -> ranges.none { p<it.second && p+n>it.first } })
            val doc=Metadata.read(source)
            assertNull("$name: ${doc.error}",doc.error); assertFalse(source.forbidden)
            assertTrue(doc.chapters.isNotEmpty()); assertTrue(doc.chapters.all { it.title.isEmpty() })
        }
    }
    @Test fun scanStreamsLargeCoversAndNoneSkipsAllImageBytes() {
        val data=bytes("large-cover.m4a")
        val image=(0..data.size-8).first { data.latin(it,4)=="\u0089PNG" }
        val scan=Observed(data); val doc=Metadata.read(scan)
        assertNull(doc.error); assertEquals(1,doc.pictures.size); assertNull(doc.pictures[0].bytes())
        assertTrue(scan.largest<=8192)
        val none=Observed(data,allowed={p,n -> p+n<=image })
        val without=Metadata.read(none,ReadOptions(pictures=Pictures.NONE))
        assertNull(without.error); assertFalse(none.forbidden); assertTrue(without.pictures.isEmpty())
        assertEquals(doc.native,without.native); assertEquals(doc.tags,without.tags)
        assertTrue(none.total<512)
    }
    @Test fun fullLoadsAreLazyAndHaveIndependentReadBudgets() {
        val source=Observed(bytes("large-cover.m4a"))
        val doc=Metadata.read(source,ReadOptions(pictures=Pictures.FULL,maxReadBytes=1_200_000))
        assertNull(doc.error); assertTrue(source.largest<=8192)
        val cover=doc.pictures.single(); val first=cover.bytes()!!; val again=cover.bytes()!!
        assertArrayEquals(first,again); assertNotSame(first,again)
        first[0]=0; assertNotEquals(first[0],cover.bytes()!![0])
    }
    @Test fun shortReadsAndSourceFailuresHaveTypedOutcomes() {
        val data=bytes("mutagen.m4a"); val expected=Metadata.read(Observed(data))
        val short=Metadata.read(Observed(data,short=3)); assertNull(short.error); assertEquals(jsonProjection(expected),jsonProjection(short))
        val failing=object: RandomAccessSource {
            override val length=data.size.toLong()
            override fun read(position: Long,buffer: ByteBuffer): Int=throw IOException("owned failure")
        }
        assertEquals(ErrorClass.IO,Metadata.read(failing).error?.errorClass)
    }
    @Test fun containerHintsRecognizeAllMp4ExtensionsWithoutFtyp() {
        val original=bytes("all-text.m4a")
        val data=original.copyOfRange(original.be(0,4).toInt(),original.size)
        val tags=Metadata.read(Observed(original)).tags
        for(hint in listOf("mp4","m4a","m4b","m4r","mov","alac")) {
            val doc=Metadata.read(Observed(data),ReadOptions(formatHint=hint))
            assertNull(doc.error); assertEquals("MP4",doc.format); assertEquals(tags,doc.tags)
        }
    }
    @Test fun metadataAndElementBudgetsAreEnforced() {
        assertEquals(ErrorClass.SIZE_TOO_LARGE,Metadata.read(Observed(bytes("large-cover.m4a")),ReadOptions(maxTagBytes=1024)).error?.errorClass)
        assertEquals(ErrorClass.SIZE_TOO_LARGE,Metadata.read(Observed(bytes("all-text.m4a")),ReadOptions(maxElements=8)).error?.errorClass)
        assertEquals(ErrorClass.SIZE_TOO_LARGE,Metadata.read(Observed(bytes("mutagen.m4a")),ReadOptions(maxReadBytes=64)).error?.errorClass)
    }
    @Test fun truncationsAndSeededMutationsNeverEscapeRead() {
        for(name in listOf("mutagen.m4a","chapters.m4b","indexed.m4a")) {
            val data=bytes(name)
            for(n in 0..minOf(2048,data.size)) {
                val doc=Metadata.read(Observed(data.copyOf(n)),ReadOptions(formatHint="mp4"))
                assertTrue(doc.error==null || doc.error.errorClass in ErrorClass.entries)
            }
            val random=Random(3303)
            repeat(1000) {
                val changed=data.clone(); repeat(1+random.nextInt(8)) { changed[random.nextInt(changed.size)]=random.nextInt(256).toByte() }
                val doc=Metadata.read(Observed(changed),ReadOptions(maxElements=10000,maxTagBytes=4*1024*1024,maxReadBytes=8*1024*1024))
                assertTrue(doc.error==null || doc.error.errorClass in ErrorClass.entries)
            }
        }
    }
}
