// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow container/asf/demux.go and codec/wma/decode.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package com.amwolfstein.sistrum.codecs.container.asf

import java.nio.ByteBuffer
import java.io.IOException
import com.amwolfstein.sistrum.codecs.audio.FloatBuffer
import com.amwolfstein.sistrum.codecs.codec.wma.Config
import com.amwolfstein.sistrum.codecs.codec.wma.Decoder
import com.amwolfstein.sistrum.codecs.codec.wma.FloatSink
import com.amwolfstein.sistrum.codecs.container.RandomAccessSource
import com.amwolfstein.sistrum.codecs.container.ByteBufferSource

/** ASF WMA v1/v2 stream. ASF duration is advisory, never a PCM truncation limit. */
class Wma private constructor(source:RandomAccessSource,strict:Boolean) {
 val demux=Demux(source,strict)
 private val cfg=Config.parse(ByteBuffer.wrap(demux.codecConfig));private val decoder=Decoder(cfg)
 data class StreamInfo(val sampleRate:Int,val channels:Int,val bitsPerSample:Int,val totalSamples:Long,val samplesExact:Boolean=false)
 val info=StreamInfo(cfg.rate,cfg.channels,32,demux.totalSamples)
 private val output=FloatBuffer(cfg.channels,16*cfg.frameLen)
 private val sink=FloatSink { b ->
  check(output.frames+b.frames<=16*cfg.frameLen) { "WMA superframe exceeds its frame-count field" }
  System.arraycopy(b.samples,0,output.samples,output.frames*cfg.channels,b.frames*cfg.channels);output.frames+=b.frames
 }
 // Checkpoints are populated only during seek preparation, without per-block playback allocation.
 private val noisePhases=HashMap<Long,Int>()
 private val usesNoise=cfg.highFreqMult()!=1.0
 private var position=0L;private var discard=0L;private var drained=false;private var discontinuity=false
 fun decodeBlock():FloatBuffer? {while(true){output.frames=0
  val packet=demux.next();if(packet!=null){if(packet.gap){decoder.reset();discontinuity=true};decoder.decode(packet.data,packet.size,sink)}else{if(drained)return null;decoder.drain(sink);drained=true}
  if(output.frames==0)continue
  if(discard>0){val n=minOf(discard,output.frames.toLong()).toInt();position+=n;discard-=n;output.frames-=n;if(output.frames==0)continue;System.arraycopy(output.samples,n*cfg.channels,output.samples,0,output.frames*cfg.channels)}
  output.position=position;output.discontinuity=discontinuity;position+=output.frames;discontinuity=false;return output
 }}
 /** Restart at an ASF packet and warm the codec before the exact target. */
 fun seekSample(sample:Long){
  if(sample<0)throw IOException("wma: negative seek target")
  val target=if(sample==0L)0L else sample+cfg.frameLen
  val pts=demux.seek(target,2)
  val offset=demux.restartOffset
  var phase=0
  if(usesNoise&&pts>0&&offset>=0){
   phase=noisePhases[offset]?:run {
    // Uncoded channels consume no noise. Parse the missing history rather than guessing
    // from timestamps; cache the phase after any carried frame that a restart drops.
    demux.rewind();decoder.reset(fromStart=true)
    while(true){
     val packet=demux.next()?:throw IOException("wma: seek object disappeared during noise scan")
     if(packet.gap)decoder.reset()
     noisePhases[packet.sourceOffset]=decoder.scanNoise(packet.data,packet.size)
     if(packet.sourceOffset==offset)break
    }
    demux.seek(target,2)
    noisePhases.getValue(offset)
   }
  }
  decoder.reset(fromStart=true,noisePhase=phase)
  position=(pts+cfg.frameLen/2)/cfg.frameLen*cfg.frameLen;discard=maxOf(0,sample-position);drained=false;discontinuity=true
 }

 companion object {
  fun open(source:RandomAccessSource,strict:Boolean=false)=Wma(source,strict)
  fun open(source:ByteBuffer,strict:Boolean=false)=open(ByteBufferSource(source),strict)
 }
}
