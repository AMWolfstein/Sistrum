// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/wmavoice/corpus_test.go and container/asf/demux_test.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package com.amwolfstein.sistrum.codecs

import java.security.MessageDigest
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import com.amwolfstein.sistrum.codecs.container.asf.WmaVoice
import com.amwolfstein.sistrum.codecs.audio.FloatBuffer

@RunWith(Parameterized::class)
internal class WmaVoiceOracleTest(private val f:Fixture) {
 companion object {@JvmStatic @Parameterized.Parameters(name="{0}") fun corpus():Collection<Array<Any>> = OracleCorpus.allRows.filter{it.name.startsWith("waxflow-wmavoice-tests/")}.also{check(it.size>=12)}.map{arrayOf<Any>(it)}}
 private fun refusal():Boolean {
  if(f.status=="ok")return false
  val e=assertThrows(java.io.IOException::class.java){WmaVoice.open(f.byteBuffer())}
  assertEquals(f.status.removePrefix("refused: waxflow: "),e.message)
  return true
 }
 @Test fun matchesOracleAndReusesBuffers(){if(refusal())return;val s=WmaVoice.open(f.byteBuffer());assertEquals("ok",f.status);assertEquals(f.value("rate").toInt(),s.info.sampleRate);assertEquals(f.value("channels").toInt(),s.info.channels);assertFalse(s.info.samplesExact)
 val d=MessageDigest.getInstance("SHA-256");var frames=0L;var old:FloatBuffer?=null
 while(true){val b=s.decodeBlock()?:break;if(old!=null){assertSame(old,b);assertSame(old.samples,b.samples)};old=b;assertEquals(frames,b.position);frames+=b.frames;d.update(pcmBytes(b))};assertEquals(f.value("frames").toLong(),frames);assertEquals(f.value("pcm_sha256"),d.digest().hex())}
 @Test fun exactSeekingAgainstOracleVerifiedLinearPcm() {
  if(refusal())return
  val s=WmaVoice.open(f.byteBuffer())
  val d=MessageDigest.getInstance("SHA-256")
  val total=f.value("frames").toLong()
  val targets=longArrayOf(0,1,1152,4000,total/2,total-1).filter{it<total}.distinct()
  val expected=HashMap<Long,IntArray>()
  val linear=ArrayList<Float>()
  while(true){
   val b=s.decodeBlock()?:break
   d.update(pcmBytes(b))
   for(i in 0 until b.frames*b.channels)linear+=b.samples[i]
   for(t in targets)if(t in b.position until b.position+b.frames){
    val start=(t-b.position).toInt()*b.channels
    expected[t]=IntArray(minOf(16*b.channels,b.frames*b.channels-start)){b.samples[start+it].toRawBits()}
   }
  }
  assertEquals(f.value("pcm_sha256"),d.digest().hex())
  // D-30: these three cold Voice restarts retain position/length, with ±1 dB RMS
  // over the next 0.5 s; recursive LPC/gain/postfilter history cannot be bit exact.
  val rmsSeek=f.name.substringAfterLast('/') in setOf("voice-8000-4k.wma","voice-8000-5k.wma","voice-8000-8k.wma")
  for(t in targets.reversed()){
   s.seekSample(t)
   val b=s.decodeBlock()!!
   assertEquals(t,b.position);assertTrue(b.discontinuity)
   val want=expected.getValue(t)
   if(!rmsSeek||t==0L)assertArrayEquals(want,IntArray(want.size){b.samples[it].toRawBits()})
   val window=minOf(s.info.sampleRate/2L,total-t).toInt()
   var actualPower=0.0;var measured=0;var remaining=0L
   var next:FloatBuffer?=b
   while(next!=null){
    val n=next
    assertEquals(t+remaining,n.position)
    for(i in 0 until n.frames){
     if(rmsSeek)assertTrue("seek PCM must be finite",n.samples[i].isFinite())
     if(measured<window){actualPower+=n.samples[i].toDouble()*n.samples[i];measured++}
    }
    remaining+=n.frames;next=s.decodeBlock()
   }
   assertEquals(total-t,remaining)
   if(rmsSeek){
    assertEquals(window,measured)
    val referencePower=(t.toInt() until t.toInt()+window).sumOf{linear[it].toDouble()*linear[it]}
    if(referencePower==0.0)assertEquals(0.0,actualPower,0.0) else {
     assertTrue("seek speech must be non-silent",actualPower>0.0)
     val db=10*kotlin.math.log10(actualPower/referencePower)
     assertTrue("Voice RMS difference $db dB at $t",kotlin.math.abs(db)<=1.0)
    }
   }
  }
  s.seekSample(total);assertNull(s.decodeBlock())
 }
 @Test fun partialSourceAndDirectBufferRegion(){if(refusal())return;val bytes=f.source.readBytes();val direct=ByteBuffer.allocateDirect(bytes.size+19);direct.position(7);direct.put(bytes);direct.limit(7+bytes.size);direct.position(7);val s=WmaVoice.open(direct);assertEquals(7,direct.position());val d=MessageDigest.getInstance("SHA-256");while(true){val b=s.decodeBlock()?:break;d.update(pcmBytes(b))};assertEquals(f.value("pcm_sha256"),d.digest().hex())
 val source=object:com.amwolfstein.sistrum.codecs.container.RandomAccessSource {override val length=bytes.size.toLong();override fun read(position:Long,buffer:ByteBuffer):Int {if(position>=length)return -1;val n=minOf(7,buffer.remaining(),(length-position).toInt());buffer.put(bytes,position.toInt(),n);return n}}
 val other=WmaVoice.open(source);val hash=MessageDigest.getInstance("SHA-256");while(true){val b=other.decodeBlock()?:break;hash.update(pcmBytes(b))};assertEquals(f.value("pcm_sha256"),hash.digest().hex());other.seekSample(1);assertEquals(1L,other.decodeBlock()!!.position)
 }

}
