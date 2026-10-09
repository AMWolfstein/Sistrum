// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow container/aiff/codecs_test.go and golden_test.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import me.misa198.airmedy.codecs.container.*
import me.misa198.airmedy.codecs.container.aiff.Aiff

@RunWith(Parameterized::class)
internal class AiffOracleTest(private val f:Fixture){
 companion object{@JvmStatic @Parameterized.Parameters(name="{0}")fun corpus():Collection<Array<Any>> = OracleCorpus.allRows.filter{it.name.endsWith(".aiff")||it.name.endsWith(".aifc")}.also{check(it.size>=21)}.map{arrayOf<Any>(it)}}
 private fun refused():Boolean {if(f.status=="ok")return false;val e=assertThrows(java.io.IOException::class.java){Aiff.open(f.byteBuffer())};assertEquals(f.status.removePrefix("refused: waxflow: "),e.message);return true}
 private fun next(s:Aiff):ByteArray?=if(s.info.floating)s.decodeFloatBlock()?.let{aiffPcmBytes(it)}else s.decodeBlock()?.let{aiffPcmBytes(it)}
 @Test fun pcmMatchesOracleAndBuffersAreReused(){if(refused())return;val s=Aiff.open(f.byteBuffer());assertEquals(f.value("rate").toInt(),s.info.sampleRate);assertEquals(f.value("channels").toInt(),s.info.channels);assertEquals(f.value("bits").toInt(),((s.info.bitsPerSample+7)/8)*8);assertEquals(f.value("frames").toLong(),s.info.totalSamples);val digest=MessageDigest.getInstance("SHA-256");var previous:Any?=null;var frames=0L
 while(true){if(s.info.floating){val b=s.decodeFloatBlock()?:break;if(previous!=null)assertSame(previous,b);previous=b;assertEquals(frames,b.position);frames+=b.frames;digest.update(aiffPcmBytes(b))}else{val b=s.decodeBlock()?:break;if(previous!=null)assertSame(previous,b);previous=b;assertEquals(frames,b.position);frames+=b.frames;digest.update(aiffPcmBytes(b))}}
 assertEquals(s.info.totalSamples,frames);assertEquals(f.value("pcm_sha256"),digest.digest().hex())}
 @Test fun exactSampleSeeks(){if(refused())return;val s=Aiff.open(f.byteBuffer());val output=ByteArrayOutputStream();while(true){val b=next(s)?:break;output.write(b)};val linear=output.toByteArray();assertEquals(f.value("pcm_sha256"),MessageDigest.getInstance("SHA-256").digest(linear).hex());val frameBytes=s.info.channels*((s.info.bitsPerSample+7)/8)
 for(t in longArrayOf(0,1,63,64,4095,4096,s.info.totalSamples/2,s.info.totalSamples-1).filter{it in 0 until s.info.totalSamples}.reversed()){
 s.seekSample(t);val tail=ByteArrayOutputStream();while(true){val b=next(s)?:break;tail.write(b)};assertArrayEquals(linear.copyOfRange((t*frameBytes).toInt(),linear.size),tail.toByteArray())}
 s.seekSample(s.info.totalSamples);assertNull(next(s));assertThrows(java.io.IOException::class.java){s.seekSample(-1)}}
 @Test fun boundedReadsAndDirectBufferRegion(){if(refused())return;val bytes=f.source.readBytes();val direct=ByteBuffer.allocateDirect(bytes.size+13);direct.position(5);direct.put(bytes);direct.limit(5+bytes.size);direct.position(5)
 val short=object:RandomAccessSource{override val length=bytes.size.toLong();override fun read(position:Long,buffer:ByteBuffer):Int{if(position>=length)return -1;val n=minOf(7,buffer.remaining(),(length-position).toInt());buffer.put(bytes,position.toInt(),n);return n}}
 for(s in listOf(Aiff.open(direct),Aiff.open(short))){val d=MessageDigest.getInstance("SHA-256");while(true){val b=next(s)?:break;d.update(b)};assertEquals(f.value("pcm_sha256"),d.digest().hex())};assertEquals(5,direct.position())}
}

// The oracle writes WAV: non-byte-aligned integers are left-justified in whole-byte words.
private fun aiffPcmBytes(b:me.misa198.airmedy.codecs.audio.Buffer):ByteArray {
 val width=(b.bits+7)/8;val bytes=ByteArray(b.frames*b.channels*width);var at=0
 for(i in 0 until b.frames*b.channels){val v=(b.samples[i] shl (width*8-b.bits))+if(width==1)128 else 0;for(j in 0 until width)bytes[at++]=(v ushr (j*8)).toByte()};return bytes
}
private fun aiffPcmBytes(b:me.misa198.airmedy.codecs.audio.FloatBuffer)=pcmBytes(b)
