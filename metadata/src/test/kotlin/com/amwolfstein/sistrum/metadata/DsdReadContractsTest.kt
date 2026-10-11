// SPDX-License-Identifier: GPL-3.0-or-later
// Sistrum DSD read contracts; pinned dsf-meta/dff-meta and WaxLabel ID3 references.
// See THIRD-PARTY-NOTICES for source attribution.
package com.amwolfstein.sistrum.metadata

import com.amwolfstein.sistrum.codecs.container.RandomAccessSource
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.file.Files
import kotlin.random.Random

class DsdReadContractsTest {
    private fun bytes(name: String,suffix: String)=Files.readAllBytes(cache.resolve("owned/dsd-$name.$suffix"))
    private class Observed(val data: ByteArray,val forbidden: List<Pair<Long,Long>> = emptyList(),val short: Int=Int.MAX_VALUE): RandomAccessSource {
        override val length=data.size.toLong();var largest=0;var total=0L
        override fun read(position: Long,buffer: ByteBuffer): Int {
            val n=buffer.remaining();check(forbidden.none {position<it.second && position+n>it.first}) {"sample payload read $position + $n"};largest=maxOf(largest,n)
            if(position>=length) return -1
            val count=minOf(n,short,(length-position).toInt());buffer.put(data,position.toInt(),count);total+=count;return count
        }
    }
    private fun chunks(data: ByteArray,start: Int,end: Int): List<Triple<String,Int,Int>> {
        val out=mutableListOf<Triple<String,Int,Int>>();var p=start
        while(p+12<=end) {val n=data.be(p+4,8).toInt();if(n<0 || n>end-p-12) break;out+=Triple(data.latin(p,4),p+12,n);p+=12+n+n%2}
        return out
    }
    private fun audio(data: ByteArray,suffix: String): Pair<Long,Long> = if(suffix=="dsf") 92L to (if(data.le(84,8)<0) data.size.toLong() else minOf(data.size.toLong(),80+data.le(84,8))) else chunks(data,32,data.size).first {it.first=="DSD "}.let {it.second.toLong() to (it.second+it.third).toLong()}
    @Test fun metadataNeverReadsDsdSamplesOrOpaqueDffBodies() {
        for(suffix in listOf("dsf","dff")) for(name in listOf("tags","picture","chapters-lyrics","large-picture")) {
            val data=bytes(name,suffix);val source=Observed(data,listOf(audio(data,suffix)))
            assertNull("$name.$suffix",Metadata.read(source).error)
        }
        val data=bytes("opaque","dff");val junk=chunks(data,32,data.size).first {it.first=="JUNK"}
        val source=Observed(data,listOf(audio(data,"dff"),junk.second.toLong() to (junk.second+junk.third).toLong()))
        assertNull(Metadata.read(source).error);assertTrue(source.total<4096)
        val diin=bytes("diin-unknown","dff");val parent=chunks(diin,32,diin.size).first {it.first=="DIIN"};val unknown=chunks(diin,parent.second,parent.second+parent.third).first {it.first=="EMID"}
        assertNull(Metadata.read(Observed(diin,listOf(unknown.second.toLong() to (unknown.second+unknown.third).toLong()))).error)
    }
    @Test fun dsfMetadataOffsetsCannotAliasHeadersOrSampleBytes() {
        for(name in listOf("pointer-1","pointer-28","pointer-80","pointer-92","pointer-100","pointer-100000","data_size-100000","data_size-18446744073709551615")) {
            val data=bytes(name,"dsf");val doc=Metadata.read(Observed(data,listOf(audio(data,"dsf"))))
            assertNull(name,doc.error);assertTrue(doc.tags.isEmpty());assertTrue(doc.warnings.any {it.code=="malformed-tag-entry"})
        }
    }
    @Test fun picturesStreamByDefaultAndLoadLazilyWithFreshFullBudgets() {
        for(suffix in listOf("dsf","dff")) {
            val data=bytes("large-picture",suffix);val scan=Observed(data,listOf(audio(data,suffix)));val doc=Metadata.read(scan)
            assertNull(doc.error);assertNull(doc.pictures.single().bytes());assertTrue(scan.largest<=8192)
            val start=data.indices.first {it+4<data.size && data.u(it)==137 && data.latin(it+1,3)=="PNG"}.toLong()
            val none=Observed(data,listOf(start to start+doc.pictures.single().size,audio(data,suffix)));val skipped=Metadata.read(none,ReadOptions(pictures=Pictures.NONE))
            assertNull(skipped.error);assertTrue(skipped.pictures.isEmpty());assertTrue(none.total<4096)
            val full=Observed(data,listOf(audio(data,suffix)));val loaded=Metadata.read(full,ReadOptions(pictures=Pictures.FULL,maxReadBytes=2_000_000));assertNull(loaded.error)
            val before=full.total
            repeat(3) {val payload=loaded.pictures.single().bytes()!!;assertEquals(doc.pictures.single().sha256,java.security.MessageDigest.getInstance("SHA-256").digest(payload).hex());payload[0]=0}
            assertTrue(full.total>before)
        }
    }
    @Test fun diinValuesFillMissingKeysAndKeepId3ConflictsInProvenance() {
        val native=Metadata.read(Observed(bytes("diin-tags","dff")));assertEquals(listOf("Owned Artist"),native.tags["ARTIST"]);assertEquals(listOf("Owned Title"),native.tags["TITLE"])
        val conflict=Metadata.read(Observed(bytes("diin-conflict","dff")));assertEquals(listOf("One","Two"),conflict.tags["ARTIST"]);assertEquals(listOf("Owned"),conflict.tags["TITLE"])
        assertTrue(conflict.families.filter {it.family=="dff"}.all {!it.selected})
        val early=Metadata.read(Observed(bytes("id3-before-audio","dff")));assertEquals(listOf("Owned"),early.tags["TITLE"]);assertEquals(1,early.pictures.size)
        val echo=Metadata.read(Observed(bytes("diin-echo","dff")));assertTrue(echo.families.all {it.selected})
        assertEquals(listOf("One","Two"),Metadata.read(Observed(bytes("diin-duplicates","dff"))).tags["ARTIST"])
        assertEquals(32771L,Metadata.read(Observed(bytes("samples-32771","dsf"))).properties.totalSamples)
        val rate=Metadata.read(Observed(bytes("rate-4294967295","dsf"))).properties;assertEquals(Int.MAX_VALUE,rate.sampleRate);assertEquals(Int.MAX_VALUE,rate.bitrate);assertEquals(7629L,rate.durationNs)
        assertEquals(ErrorClass.INVALID_DATA,Metadata.read(Observed(bytes("dst-refusal","dff"))).error?.errorClass)
        assertEquals(ErrorClass.INVALID_DATA,Metadata.read(Observed(bytes("dst-name-refusal","dff"))).error?.errorClass)
        assertEquals(ErrorClass.INVALID_DATA,Metadata.read(Observed(bytes("channels-6","dff"))).error?.errorClass)
    }
    @Test fun shortReadsAndSourceFaultsAreHandledWithoutEscaping() {
        for(suffix in listOf("dsf","dff")) {
            val data=bytes("tags",suffix);assertEquals(jsonProjection(Metadata.read(Observed(data))),jsonProjection(Metadata.read(Observed(data,short=1))))
            for(stop in listOf(0,20,80,100)) {
                val source=object: RandomAccessSource {
                    override val length=data.size.toLong()
                    override fun read(position: Long,buffer: ByteBuffer): Int {if(position+buffer.remaining()>stop) throw java.io.IOException("owned failure");val n=buffer.remaining();buffer.put(data,position.toInt(),n);return n}
                }
                assertEquals(ErrorClass.IO,Metadata.read(source).error?.errorClass)
            }
        }
    }
    @Test fun byteAndElementLimitsBoundDsdMetadataReads() {
        for(suffix in listOf("dsf","dff")) {
            assertEquals(ErrorClass.SIZE_TOO_LARGE,Metadata.read(Observed(bytes("tags",suffix)),ReadOptions(maxElements=2)).error?.errorClass)
            assertEquals(ErrorClass.SIZE_TOO_LARGE,Metadata.read(Observed(bytes("tags",suffix)),ReadOptions(maxReadBytes=64)).error?.errorClass)
            assertEquals(ErrorClass.SIZE_TOO_LARGE,Metadata.read(Observed(bytes("large-picture",suffix)),ReadOptions(maxTagBytes=4096)).error?.errorClass)
        }
    }
    @Test fun unsignedCountsAndOverflowingDurationsReturnTypedLimits() {
        val unsigned=bytes("base","dsf");for(i in 64..71) unsigned[i]=0xff.toByte()
        assertEquals(ErrorClass.SIZE_TOO_LARGE,Metadata.read(Observed(unsigned)).error?.errorClass)
        val overflow=bytes("base","dsf");for(i in 64..70) overflow[i]=0xff.toByte();overflow[71]=0x7f
        overflow[56]=1;for(i in 57..59) overflow[i]=0
        assertEquals(ErrorClass.SIZE_TOO_LARGE,Metadata.read(Observed(overflow)).error?.errorClass)
    }
    @Test fun malformedAndTruncatedDsdMetadataNeverThrows() {
        val random=Random(20261012)
        for((name,suffix) in listOf("tags" to "dsf","tags" to "dff","diin-tags" to "dff","chapters-lyrics" to "dsf","picture" to "dff")) {
            val seed=bytes(name,suffix)
            for(cut in 0..minOf(2048,seed.size)) Metadata.read(Observed(seed.copyOf(cut)),ReadOptions(maxTagBytes=4096,maxElements=64))
            repeat(300) {val copy=seed.copyOf();repeat(8) {copy[random.nextInt(copy.size)]=random.nextInt(256).toByte()};Metadata.read(Observed(copy),ReadOptions(maxTagBytes=4096,maxElements=64,maxReadBytes=32768))}
        }
    }
}
