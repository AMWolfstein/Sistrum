// SPDX-License-Identifier: GPL-3.0-or-later
package com.amwolfstein.sistrum.codecs

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.lang.management.ManagementFactory
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import com.amwolfstein.sistrum.codecs.container.dsd.Dsd
import com.amwolfstein.sistrum.codecs.container.wv.Wv

private val dffOwnedRoot get()=File(System.getProperty("oracle.owned"),"dsd-multichannel")
internal data class MultichannelDffFixture(val fields:Map<String,String>) {
    val name get()=fields.getValue("name")
    fun source(extension:String)=File(dffOwnedRoot,"$name.$extension")
    fun open(extension:String)=Dsd.open(ByteBuffer.wrap(source(extension).readBytes()))
    override fun toString()=name
}
@RunWith(Parameterized::class)
internal class DffMultichannelTest(private val fixture:MultichannelDffFixture) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name="{0}")
        fun corpus():Collection<Array<Any>> {
            val lines=File(dffOwnedRoot,"headers.tsv").readLines();val columns=lines.first().split('\t')
            return lines.drop(1).filter{it.isNotBlank()}.map{arrayOf<Any>(MultichannelDffFixture(columns.zip(it.split('\t')).toMap()))}.also{check(it.size==14)}
        }
    }
    @Test fun headerMatchesPinnedLibwavpackDffReader() {
        val fields=fixture.fields
        assertEquals("4827b9889665b937b6ed71b9c6c0123152cd7a02",fields.getValue("libwavpack_commit"))
        assertEquals("True",fields.getValue("accepted"))
        val native=Wv.open(ByteBuffer.wrap(fixture.source("wv").readBytes()))
        val parsed=fixture.open("dff").info
        assertEquals(fields.getValue("oracle_rate").toInt(),parsed.dsdSampleRate)
        assertEquals(fields.getValue("oracle_channels").toInt(),parsed.channels)
        assertEquals(fields.getValue("oracle_bits").toLong(),parsed.totalDsdSamples)
        assertEquals(fields.getValue("oracle_mask").toInt() shl 2,parsed.channelMask)
        assertEquals(native.info.dsdSampleRate,parsed.dsdSampleRate)
        assertEquals(native.info.channels,parsed.channels)
        assertEquals(native.info.channelMask.toInt() shl 2,parsed.channelMask)
        val digest=MessageDigest.getInstance("SHA-256")
        while(true){val b=native.decodeDsdBlock()?:break;digest.update(b.bytes,0,b.bytesPerChannel*b.channels)}
        assertEquals(fields.getValue("raw_sha256"),digest.digest().hex())
        val dsf=fixture.open("dsf").info
        when(fixture.name){
            "generic-6ch"->{assertEquals("c000,c001,c123,c500,c998,c999",parsed.channelLayout);assertEquals(0,parsed.channelMask);assertEquals(63,parsed.channelIndexMask)}
            "mixed-3ch"->{assertEquals("FL,FR,c123",parsed.channelLayout);assertEquals(12,parsed.channelMask);assertEquals(7,parsed.channelIndexMask)}
            else->{assertEquals(dsf.channelLayout,parsed.channelLayout);assertEquals(dsf.channelMask,parsed.channelMask);assertEquals(0,parsed.channelIndexMask)}
        }
    }
    @Test fun pcmAndDopMatchDsfBitForBitAfterMapping() {
        for(dop in listOf(false,true)) {
            val expected=decode(fixture.open("dsf"),dop)
            val stream=fixture.open("dff")
            assertArrayEquals(expected,decode(stream,dop))
        }
    }
    @Test fun randomSeeksMatchUninterruptedMappedOutput() {
        for(dop in listOf(false,true)) {
            val stream=fixture.open("dff");val all=decode(stream,dop)
            val frameBytes=stream.info.channels*4
            val total=all.size/frameBytes
            val random=Random(513)
            val positions=listOf(0,1,total/2,total-1,total)+List(24){random.nextInt(total)}
            for(position in positions) {
                if(dop)stream.seekDopSample(position.toLong())else stream.seekSample(position.toLong())
                assertArrayEquals(all.copyOfRange(position*frameBytes,all.size),decode(stream,dop,position.toLong(),true))
            }
        }
    }
    @Test fun decodeLoopsAllocateZeroBytes() {
        val allocation=ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        assertTrue(allocation.isThreadAllocatedMemorySupported)
        allocation.isThreadAllocatedMemoryEnabled=true
        val thread=Thread.currentThread().threadId()
        for(dop in listOf(false,true)) {
            val stream=fixture.open("dff")
            repeat(20){rewind(stream,dop);drain(stream,dop)}
            val bytes=LongArray(5)
            repeat(5){index->
                rewind(stream,dop)
                val before=allocation.getThreadAllocatedBytes(thread)
                val frames=drain(stream,dop)
                bytes[index]=allocation.getThreadAllocatedBytes(thread)-before
                assertTrue(frames>0)
                assertEquals("${fixture.name} ${if(dop) "DoP" else "PCM"} decode-loop allocation, trial $index",0L,bytes[index])
            }
        }
    }
    private fun rewind(stream:Dsd,dop:Boolean){if(dop)stream.seekDopSample(0)else stream.seekSample(0)}
    private fun drain(stream:Dsd,dop:Boolean):Long {
        var frames=0L
        if(dop){while(true){val b=stream.decodeDopBlock()?:break;frames+=b.frames}}
        else{while(true){val b=stream.decodeBlock()?:break;frames+=b.frames}}
        return frames
    }
    private fun decode(stream:Dsd,dop:Boolean,start:Long=0,discontinuity:Boolean=false):ByteArray {
        val out=ByteArrayOutputStream();var cursor=start;var first=true;var borrowed:Any?=null;var storage:Any?=null
        while(true) {
            if(dop) {
                val b=stream.decodeDopBlock()?:break
                assertEquals(cursor,b.position);assertEquals(first&&discontinuity,b.discontinuity)
                if(borrowed!=null){assertSame(borrowed,b);assertSame(storage,b.samples)}
                borrowed=b;storage=b.samples;cursor+=b.frames
                out.write(pcmBytes(b))
            } else {
                val b=stream.decodeBlock()?:break
                assertEquals(cursor,b.position);assertEquals(first&&discontinuity,b.discontinuity)
                if(borrowed!=null){assertSame(borrowed,b);assertSame(storage,b.samples)}
                borrowed=b;storage=b.samples;cursor+=b.frames
                out.write(pcmBytes(b))
            }
            first=false
        }
        return out.toByteArray()
    }
}

class DffChannelContractTest {
    @Test fun missingInvalidOrUnsupportedChnlHasClearRefusal() {
        val reasons=mapOf(
            "missing-chnl" to "Missing CHNL chunk.",
            "invalid-id" to "Invalid CHNL channel ID 'NOPE'.",
            "invalid-generic" to "Invalid CHNL channel ID 'c00x'.",
            "duplicate-id" to "Duplicate CHNL channel identity.",
            "duplicate-alias" to "Duplicate CHNL channel identity.",
            "missing-id" to "CHNL chunk size does not match channel data.",
            "extra-id" to "CHNL chunk size does not match channel data.",
            "zero-channels" to "CHNL channel count must be between 1 and 6.",
            "seven-channels" to "CHNL channel count must be between 1 and 6.",
            "dst" to "Compression type must be 'DSD '. DST not supported."
        )
        for((name,reason) in reasons) assertEquals("dff: $reason",assertThrows(IOException::class.java){Dsd.open(ByteBuffer.wrap(File(dffOwnedRoot,"$name.dff").readBytes()))}.message)
    }
    @Test fun everyGenericIdIsAcceptedWithoutInventingSpeakerPositions() {
        val source=File(dffOwnedRoot,"generic-6ch.dff").readBytes()
        val at=source.indices.first{it+4<=source.size && String(source,it,4,Charsets.US_ASCII)=="CHNL"}
        for(index in 0..999) {
            // Preserve six distinct IDs while exercising every value at the first position.
            val bytes=source.clone()
            for(ch in 0..5){val id="c"+((index+ch)%1000).toString().padStart(3,'0');id.toByteArray().copyInto(bytes,at+14+ch*4)}
            val parsed=Dsd.open(ByteBuffer.wrap(bytes)).info
            assertEquals("c"+index.toString().padStart(3,'0'),parsed.channelLayout.substringBefore(','))
            assertEquals(0,parsed.channelMask);assertEquals(63,parsed.channelIndexMask)
        }
    }
    @Test fun duplicateChnlAndTruncatedPropertiesAreRefused() {
        val source=File(dffOwnedRoot,"dsd64-3ch.dff").readBytes()
        fun find(id:String)=source.indices.first{it+4<=source.size && String(source,it,4,Charsets.US_ASCII)==id}
        val chnl=find("CHNL");val prop=find("PROP");val length=26
        val duplicate=source.copyOfRange(0,chnl+length)+source.copyOfRange(chnl,chnl+length)+source.copyOfRange(chnl+length,source.size)
        ByteBuffer.wrap(duplicate).order(ByteOrder.BIG_ENDIAN).apply{putLong(4,getLong(4)+length);putLong(prop+4,getLong(prop+4)+length)}
        assertEquals("dff: Duplicate CHNL chunk.",assertThrows(IOException::class.java){Dsd.open(ByteBuffer.wrap(duplicate))}.message)
        val truncated=source.clone()
        ByteBuffer.wrap(truncated).order(ByteOrder.BIG_ENDIAN).apply{putLong(prop+4,15)}
        assertEquals("dff: Truncated property chunk header.",assertThrows(IOException::class.java){Dsd.open(ByteBuffer.wrap(truncated))}.message)
    }
}
