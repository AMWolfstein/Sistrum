// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/musepack/decode_test.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs

import java.security.MessageDigest
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import me.misa198.airmedy.codecs.container.mpc.Musepack
import me.misa198.airmedy.codecs.audio.FloatBuffer

internal fun pcmBytes(b:FloatBuffer):ByteArray {
 val out=ByteBuffer.allocate(b.frames*b.channels*4).order(ByteOrder.LITTLE_ENDIAN)
 for(i in 0 until b.frames*b.channels)out.putInt(b.samples[i].toRawBits());return out.array()
}
@RunWith(Parameterized::class)
internal class MusepackOracleTest(private val f:Fixture) {
 companion object {@JvmStatic @Parameterized.Parameters(name="{0}") fun corpus():Collection<Array<Any>> = OracleCorpus.allRows.filter{it.name.endsWith(".mpc")}.also{check(it.size>=32)}.map{arrayOf<Any>(it)}}
 @Test fun matchesOracleAndReusesBuffers(){val s=Musepack.open(f.byteBuffer());assertEquals("ok",f.status);assertEquals(f.value("rate").toInt(),s.info.sampleRate);assertEquals(f.value("channels").toInt(),s.info.channels);assertEquals(f.value("frames").toLong(),s.info.totalSamples)
 val d=MessageDigest.getInstance("SHA-256");var frames=0L;var old:FloatBuffer?=null
 while(true){val b=s.decodeBlock()?:break;if(old!=null){assertSame(old,b);assertSame(old.samples,b.samples)};old=b;assertEquals(frames,b.position);frames+=b.frames;d.update(pcmBytes(b))};assertEquals(s.info.totalSamples,frames);assertEquals(f.value("pcm_sha256"),d.digest().hex())}
 @Test fun exactSeekingAgainstOracleVerifiedLinearPcm(){val s=Musepack.open(f.byteBuffer());val d=MessageDigest.getInstance("SHA-256");val targets=longArrayOf(0,1,1152,4000,s.info.totalSamples/2,s.info.totalSamples-1).filter{it<s.info.totalSamples}.distinct();val expected=HashMap<Long,IntArray>()
 while(true){val b=s.decodeBlock()?:break;d.update(pcmBytes(b));for(t in targets)if(t in b.position until b.position+b.frames){val start=(t-b.position).toInt()*b.channels;expected[t]=IntArray(minOf(16*b.channels,b.frames*b.channels-start)){b.samples[start+it].toRawBits()}}};assertEquals(f.value("pcm_sha256"),d.digest().hex())
 for(t in targets.reversed()){s.seekSample(t);val b=s.decodeBlock()!!;assertEquals(t,b.position);assertTrue(b.discontinuity);val want=expected.getValue(t);assertArrayEquals(want,IntArray(want.size){b.samples[it].toRawBits()});var remaining=b.frames.toLong();while(true){val n=s.decodeBlock()?:break;remaining+=n.frames};assertEquals(s.info.totalSamples-t,remaining)};s.seekSample(s.info.totalSamples);assertNull(s.decodeBlock())}
 @Test fun partialSourceAndDirectBufferRegion(){val bytes=f.source.readBytes();val direct=ByteBuffer.allocateDirect(bytes.size+19);direct.position(7);direct.put(bytes);direct.limit(7+bytes.size);direct.position(7);val s=Musepack.open(direct);assertEquals(7,direct.position());val d=MessageDigest.getInstance("SHA-256");while(true){val b=s.decodeBlock()?:break;d.update(pcmBytes(b))};assertEquals(f.value("pcm_sha256"),d.digest().hex())
 val source=object:me.misa198.airmedy.codecs.container.RandomAccessSource {override val length=bytes.size.toLong();override fun read(position:Long,buffer:ByteBuffer):Int {if(position>=length)return -1;val n=minOf(7,buffer.remaining(),(length-position).toInt());buffer.put(bytes,position.toInt(),n);return n}}
 val other=Musepack.open(source);val hash=MessageDigest.getInstance("SHA-256");while(true){val b=other.decodeBlock()?:break;hash.update(pcmBytes(b))};assertEquals(f.value("pcm_sha256"),hash.digest().hex());other.seekSample(1);assertEquals(1L,other.decodeBlock()!!.position)
 }

}
