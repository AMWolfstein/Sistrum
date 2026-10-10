// SPDX-License-Identifier: GPL-3.0-or-later
// Oracle tests for pinned Flick 79da4ed76557c8ddf534e898480dde66bcc90334 (MIT),
// dsf-meta 0.3.0 and dff-meta 0.2.0 (MIT OR Apache-2.0); see docs/dsd/ORACLE.md.
package me.misa198.airmedy.codecs

import java.io.*
import java.nio.ByteBuffer
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import me.misa198.airmedy.codecs.container.*
import me.misa198.airmedy.codecs.container.dsd.Dsd
import me.misa198.airmedy.codecs.container.dsd.FlickExactDsd

internal data class DsdFixture(val fields:Map<String,String>){
 val name get()=fields.getValue("file");val status get()=fields.getValue("status")
 val source get()=File(System.getProperty("dsd.corpus"),name)
 fun value(key:String)=fields.getValue(key)
 fun open()=Dsd.open(ByteBuffer.wrap(source.readBytes()))
 fun openFlick()=FlickExactDsd.open(ByteBuffer.wrap(source.readBytes()))
 override fun toString()=name
}
internal object DsdCorpus {
 val rows:List<DsdFixture> by lazy {
  val lines=File(System.getProperty("dsd.fixtures")).readLines();val header=lines.first().split('\t')
  lines.drop(1).filter{it.isNotBlank()}.map{line->DsdFixture(header.zip(line.split('\t')).toMap()).also{f->check(MessageDigest.getInstance("SHA-256").digest(f.source.readBytes()).hex()==f.value("file_sha256")){"DSD corpus mismatch: bash scripts/dsd-oracle.sh --fetch-generate"}}}.also{check(it.size==20)}
 }
}
private fun floats(b:me.misa198.airmedy.codecs.audio.FloatBuffer):ByteArray {val bytes=ByteBuffer.allocate(b.frames*b.channels*4).order(java.nio.ByteOrder.LITTLE_ENDIAN);for(i in 0 until b.frames*b.channels)bytes.putInt(b.samples[i].toRawBits());return bytes.array()}
private fun words(b:me.misa198.airmedy.codecs.audio.Buffer):ByteArray {val bytes=ByteBuffer.allocate(b.frames*b.channels*4).order(java.nio.ByteOrder.LITTLE_ENDIAN);for(i in 0 until b.frames*b.channels)bytes.putInt(b.samples[i]);return bytes.array()}
@RunWith(Parameterized::class)
internal class DsdOracleTest(private val f:DsdFixture){
 companion object{@JvmStatic @Parameterized.Parameters(name="{0}")fun corpus():Collection<Array<Any>> = DsdCorpus.rows.map{arrayOf<Any>(it)}}
 private fun refused():Boolean{if(f.status=="ok")return false;assertEquals(f.status.removePrefix("refused: "),assertThrows(IOException::class.java){f.openFlick()}.message);return true}
 @Test fun pcmMatchesRustAndReusesBuffers(){if(refused())return;val s=f.openFlick();assertEquals(f.value("dsd_rate").toInt(),s.info.dsdSampleRate);assertEquals(f.value("pcm_rate").toInt(),s.info.sampleRate);assertEquals(f.value("channels").toInt(),s.info.channels);assertEquals(f.value("layout"),s.info.channelLayout);assertEquals(f.value("frames").toLong(),s.info.totalSamples);val d=MessageDigest.getInstance("SHA-256");var previous:Any?=null;var frames=0L
 while(true){val b=s.decodeBlock()?:break;if(previous!=null)assertSame(previous,b);previous=b;assertEquals(frames,b.position);frames+=b.frames;d.update(floats(b))};assertEquals(s.info.totalSamples,frames);assertEquals(f.value("pcm_sha256"),d.digest().hex())}
 @Test fun exactPcmSeeks(){if(refused())return;val s=f.openFlick();val out=ByteArrayOutputStream();while(true){val b=s.decodeBlock()?:break;out.write(floats(b))};val all=out.toByteArray();assertEquals(f.value("pcm_sha256"),MessageDigest.getInstance("SHA-256").digest(all).hex())
 for(t in longArrayOf(0,1,63,2047,2048,4095,4096,s.info.totalSamples/2,s.info.totalSamples-1).filter{it in 0 until s.info.totalSamples}.reversed()){s.seekSample(t);val tail=ByteArrayOutputStream();var first=true;while(true){val b=s.decodeBlock()?:break;if(first){assertEquals(t,b.position);assertTrue(b.discontinuity);first=false};tail.write(floats(b))};assertArrayEquals(all.copyOfRange((t*s.info.channels*4).toInt(),all.size),tail.toByteArray())}
 s.seekSample(s.info.totalSamples);assertNull(s.decodeBlock());assertThrows(IOException::class.java){s.seekSample(-1)};assertThrows(IOException::class.java){s.seekSample(s.info.totalSamples+1)}}
 @Test fun dopMatchesRustAndExactSeeks(){if(refused())return;val s=f.open();assertEquals(f.value("dop_rate").toInt(),s.info.dopSampleRate);val out=ByteArrayOutputStream();var prior:Any?=null;var frames=0L
 while(true){val b=s.decodeDopBlock()?:break;if(prior!=null)assertSame(prior,b);prior=b;assertEquals(frames,b.position);for(i in 0 until b.frames)for(ch in 0 until b.channels){val word=b.samples[i*b.channels+ch];assertEquals(if((frames+i)and 1L==0L)5 else 250,word ushr 24);assertEquals(0,word and 255)};frames+=b.frames;out.write(words(b))}
 val all=out.toByteArray();assertEquals(f.value("dop_first8_le32"),all.copyOfRange(0,8*s.info.channels*4).hex())
 for(t in longArrayOf(0,1,7,2047,2048,4095,frames/2,frames-1).filter{it in 0 until frames}.reversed()){s.seekDopSample(t);val tail=ByteArrayOutputStream();while(true){val b=s.decodeDopBlock()?:break;tail.write(words(b))};assertArrayEquals(all.copyOfRange((t*s.info.channels*4).toInt(),all.size),tail.toByteArray())};s.seekDopSample(frames);assertNull(s.decodeDopBlock());assertThrows(IOException::class.java){s.seekDopSample(-1)}}
 @Test fun shortReadsAndDirectBuffer(){if(refused())return;val bytes=f.source.readBytes();val direct=ByteBuffer.allocateDirect(bytes.size+13);direct.position(5);direct.put(bytes);direct.limit(bytes.size+5);direct.position(5)
 val short=object:RandomAccessSource{override val length=bytes.size.toLong();override fun read(position:Long,buffer:ByteBuffer):Int{if(!buffer.hasRemaining())return 0;if(position>=length)return -1;val n=minOf(7,buffer.remaining(),(length-position).toInt());buffer.put(bytes,position.toInt(),n);return n}}
 for(s in listOf(FlickExactDsd.open(short),FlickExactDsd.open(direct))){val d=MessageDigest.getInstance("SHA-256");while(true){val b=s.decodeBlock()?:break;d.update(floats(b))};assertEquals(f.value("pcm_sha256"),d.digest().hex());val b=s.decodeDopBlock()!!;assertEquals(f.value("dop_first8_le32"),words(b).copyOfRange(0,8*s.info.channels*4).hex())};assertEquals(5,direct.position())}
}
