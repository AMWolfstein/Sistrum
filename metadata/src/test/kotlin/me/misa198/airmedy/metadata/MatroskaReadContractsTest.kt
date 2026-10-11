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

class MatroskaReadContractsTest {
    private fun bytes(name: String)=Files.readAllBytes(cache.resolve("owned/matroska-$name.${if(name.startsWith("ffmpeg-lib")) "webm" else "mka"}"))
    private class Observed(val data: ByteArray,val forbidden: List<Pair<Long,Long>> = emptyList(),val short: Int=Int.MAX_VALUE): RandomAccessSource {
        override val length=data.size.toLong();var largest=0;var total=0L
        override fun read(position: Long,buffer: ByteBuffer): Int {
            val n=buffer.remaining();check(forbidden.none {position<it.second && position+n>it.first}) {"forbidden payload read $position + $n"};largest=maxOf(largest,n)
            if(position>=length) return -1
            val count=minOf(n,short,(length-position).toInt());buffer.put(data,position.toInt(),count);total+=count;return count
        }
    }
    private fun ranges(data: ByteArray,ids: Set<Long>): List<Pair<Long,Long>> {
        val eb=Ebml(Input(Observed(data),ReadOptions()));val out=mutableListOf<Pair<Long,Long>>()
        val masters=setOf(0x1a45dfa3L,0x18538067L,0x1254c367L,0x7373L,0x67c8L,0x1941a469L,0x61a7L,0x1654ae6bL,0xaeL)
        fun visit(el: EbmlElement) {
            if(el.id in ids) out+=el.body to el.end
            else if(el.id in masters) eb.children(el.body,el.end,el.id==0x18538067L,::visit)
        }
        eb.children(0,data.size.toLong(),visit=::visit);return out
    }
    @Test fun clusterBodiesAndInterleavedOpaqueElementsAreNeverRead() {
        for(name in listOf("scopes","chapters","intercluster-tags","clusters-many","unknown-segment","ffmpeg-libopus","ffmpeg-libvorbis","ffmpeg-flac","ffmpeg-aac","ffmpeg-pcm_s16le","opaque")) {
            val data=bytes(name);val forbidden=ranges(data,setOf(0x1f43b675L,0x114d9b74L,0x1c53bb6bL,0xecL,0xbfL))
            assertTrue(name,forbidden.isNotEmpty())
            val source=Observed(data,forbidden);val doc=Metadata.read(source);assertNull("$name ${doc.error}",doc.error)
        }
        // Unknown-size Cluster traversal reads block headers, never block payloads.
        val streamed=bytes("streamed-trailing-tags");val cluster=ranges(streamed,setOf(0x1f43b675L)).single()
        val a3=(cluster.first.toInt() until cluster.second.toInt()).first {streamed[it]==0xa3.toByte()}
        val eb=Ebml(Input(Observed(streamed),ReadOptions()));val block=eb.element(a3.toLong(),streamed.size.toLong())!!
        val guarded=Metadata.read(Observed(streamed,listOf(block.body to block.end)));assertNull(guarded.error);assertEquals(listOf("Trailing"),guarded.tags["TITLE"])
    }
    @Test fun binaryTagsAndNonImageAttachmentsAndCodecPrivateAreSkipped() {
        for((name,ids) in listOf("binary-large" to setOf(0x4485L),"non-aac-private" to setOf(0x63a2L),"attachment-font-ttf" to setOf(0x465cL))) {
            val data=bytes(name);val forbidden=ranges(data,ids);assertEquals(1,forbidden.size)
            val source=Observed(data,forbidden);assertNull(Metadata.read(source).error);assertTrue(source.total<4096)
        }
        val data=bytes("aac-huge");val region=ranges(data,setOf(0x63a2L)).single()
        val source=Observed(data,listOf(region.first+32 to region.second));assertNull(Metadata.read(source).error);assertTrue(source.total<4096)
    }
    @Test fun defaultPicturesStreamNoneSkipsFileDataAndFullLoadsFreshBytes() {
        val data=bytes("picture-large");val scan=Observed(data);val doc=Metadata.read(scan);assertNull(doc.error)
        assertEquals(1,doc.pictures.size);assertNull(doc.pictures.single().bytes());assertTrue(scan.largest<=8192)
        val none=Observed(data,ranges(data,setOf(0x465cL)));val skipped=Metadata.read(none,ReadOptions(pictures=Pictures.NONE))
        assertNull(skipped.error);assertTrue(skipped.pictures.isEmpty());assertEquals(doc.native,skipped.native);assertTrue(none.total<4096)
        val full=Observed(data);val loaded=Metadata.read(full,ReadOptions(pictures=Pictures.FULL,maxReadBytes=2_000_000));assertNull(loaded.error)
        val before=full.total
        repeat(3) {val payload=loaded.pictures.single().bytes()!!;assertEquals(doc.pictures.single().sha256,java.security.MessageDigest.getInstance("SHA-256").digest(payload).hex());payload[0]=0}
        assertTrue(full.total>before)
    }
    @Test fun scopedValuesAndChapterFlagsLanguagesAndStableOrderingArePreserved() {
        val doc=Metadata.read(Observed(bytes("scopes")));assertNull(doc.error)
        assertEquals(listOf("Album","Track","Edition","Chapter"),doc.tags["TITLE"])
        assertEquals(listOf("album","track","edition","chapter"),doc.families.filter {it.key=="TITLE"}.map {it.scope})
        assertTrue(doc.families.filter {it.key=="TITLE"}.all {!it.selected})
        val flags=Metadata.read(Observed(bytes("chapter-flags"))).chapters.single();assertTrue(flags.hidden);assertTrue(flags.disabled);assertEquals("eng",flags.language);assertEquals("en-US",flags.languageIETF)
        assertEquals(listOf("Earlier","Stable","Later"),Metadata.read(Observed(bytes("chapter-subms"))).chapters.map {it.title})
        assertEquals("Default",Metadata.read(Observed(bytes("editions"))).chapters.single().title)
    }
    @Test fun shortReadsAndSourceFailuresRespectTheReadContract() {
        for(name in listOf("scopes","chapters","attachments-multiple","streamed-trailing-tags","aac-he")) {
            val data=bytes(name);assertEquals(jsonProjection(Metadata.read(Observed(data))),jsonProjection(Metadata.read(Observed(data,short=1))))
            for(stop in listOf(0,4,20,50,ranges(data,setOf(0x1f43b675L)).first().first.toInt()-1)) {
                val source=object: RandomAccessSource {
                    override val length=data.size.toLong()
                    override fun read(position: Long,buffer: ByteBuffer): Int {if(position+buffer.remaining()>stop) throw java.io.IOException("owned fault");val n=buffer.remaining();buffer.put(data,position.toInt(),n);return n}
                }
                assertEquals("$name fault after $stop",ErrorClass.IO,Metadata.read(source).error?.errorClass)
            }
        }
    }
    @Test fun breadthDepthAllocationAndReadLimitsAreEnforced() {
        assertEquals(ErrorClass.SIZE_TOO_LARGE,Metadata.read(Observed(bytes("scopes")),ReadOptions(maxElements=8)).error?.errorClass)
        assertEquals(ErrorClass.SIZE_TOO_LARGE,Metadata.read(Observed(bytes("scopes")),ReadOptions(maxReadBytes=64)).error?.errorClass)
        assertEquals(ErrorClass.SIZE_TOO_LARGE,Metadata.read(Observed(bytes("picture-large")),ReadOptions(maxTagBytes=4096)).error?.errorClass)
        // Clusters do not consume the metadata breadth budget.
        assertNull(Metadata.read(Observed(bytes("clusters-many")),ReadOptions(maxElements=32)).error)
        val deep=bytes("nested-deep");assertNull(Metadata.read(Observed(deep)).error)
        val eb=Ebml(Input(Observed(deep),ReadOptions()));var segment: EbmlElement?=null
        eb.children(0,deep.size.toLong()) {if(it.id==0x18538067L) segment=it}
        val prefix=deep.copyOfRange(0,segment!!.start.toInt())
        val root=byteArrayOf(0x18,0x53,0x80.toByte(),0x67,0xff.toByte(),0x12,0x54,0xc3.toByte(),0x67,0xff.toByte(),0x73,0x73,0xff.toByte())
        val nested=ByteArray(70*3) {i -> when(i%3) {0->0x67;1->0xc8.toByte();else->0xff.toByte()}}
        assertEquals(ErrorClass.SIZE_TOO_LARGE,Metadata.read(Observed(prefix+root+nested)).error?.errorClass)
    }
    @Test fun malformedAndTruncatedInputsNeverThrow() {
        val random=Random(20261012)
        for(name in listOf("scopes","chapters","attachments-multiple","streamed-trailing-tags","ffmpeg-flac")) {
            val seed=bytes(name)
            for(cut in 0..minOf(2048,seed.size)) Metadata.read(Observed(seed.copyOf(cut)),ReadOptions(maxTagBytes=4096,maxElements=64))
            repeat(300) {val copy=seed.copyOf(minOf(seed.size,4096));repeat(8) {copy[random.nextInt(copy.size)]=random.nextInt(256).toByte()};Metadata.read(Observed(copy),ReadOptions(maxTagBytes=4096,maxElements=64,maxReadBytes=32768))}
        }
    }
}
