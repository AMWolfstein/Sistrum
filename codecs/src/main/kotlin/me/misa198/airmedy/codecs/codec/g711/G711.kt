// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/g711/g711.go, decode.go and tables.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.g711

import java.io.IOException
import java.nio.ByteBuffer
import me.misa198.airmedy.codecs.audio.Buffer
import me.misa198.airmedy.codecs.container.RandomAccessSource
import me.misa198.airmedy.codecs.container.ByteBufferSource

enum class Law { ALaw, MuLaw }

/** Decoder-only source region. Container metadata comes from the existing extractor. */
class G711 private constructor(val info: StreamInfo, private val source: RandomAccessSource, private val dataOffset: Long, law: Law) {
 data class StreamInfo(val sampleRate:Int,val channels:Int,val bitsPerSample:Int,val totalSamples:Long)
 private val table=IntArray(256) { b ->
  if(law==Law.ALaw){val v=b xor 0x55;var t=(v and 15) shl 4;val seg=(v and 0x70) shr 4
   t=when(seg){0->t+8;1->t+0x108;else->(t+0x108) shl (seg-1)};if(v and 0x80!=0)t else -t
  }else{val v=b.inv() and 255;val t=(((v and 15) shl 3)+0x84) shl ((v and 0x70) shr 4);if(v and 0x80!=0)0x84-t else t-0x84}
 }
 private val encoded=ByteBuffer.allocate(4096*info.channels)
 private val output=Buffer(info.channels,16,4096)
 private var position=0L;private var discontinuity=false
 fun decodeBlock():Buffer? {
  if(position>=info.totalSamples)return null
  val frames=minOf(4096L,info.totalSamples-position).toInt();encoded.clear();encoded.limit(frames*info.channels)
  var at=dataOffset+position*info.channels
  while(encoded.hasRemaining()){val before=encoded.position();val n=source.read(at,encoded);if(n<=0||encoded.position()-before!=n)throw IOException("g711: reading packet data");at+=n}
  encoded.flip();var i=0;while(encoded.hasRemaining()){output.samples[i++]=table[encoded.get().toInt() and 255]}
  output.frames=frames;output.position=position;output.discontinuity=discontinuity;discontinuity=false;position+=frames;return output
 }
 fun seekSample(sample:Long){if(sample<0)throw IOException("g711: negative seek target");position=minOf(sample,info.totalSamples);discontinuity=true}
 companion object {
  fun open(law:Law,rate:Int,channels:Int,source:RandomAccessSource,dataOffset:Long,totalSamples:Long):G711 {
   if(rate<=0)throw IOException("audio: rate $rate must be positive")
   if(channels !in 1..8)throw IOException("audio: $channels channels outside 1..8")
   require(dataOffset>=0&&totalSamples>=0&&dataOffset<=source.length&&totalSamples<=(source.length-dataOffset)/channels)
   return G711(StreamInfo(rate,channels,16,totalSamples),source,dataOffset,law)
  }
  fun open(law:Law,rate:Int,channels:Int,source:ByteBuffer,dataOffset:Long,totalSamples:Long)=open(law,rate,channels,ByteBufferSource(source),dataOffset,totalSamples)
 }
}
