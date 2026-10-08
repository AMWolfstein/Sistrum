// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow container/mpc/demux.go, container/mpc/sv7.go, container/mpc/sv8.go and format/media.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.container.mpc

import java.nio.ByteBuffer
import me.misa198.airmedy.codecs.audio.FloatBuffer
import me.misa198.airmedy.codecs.codec.musepack.*
import me.misa198.airmedy.codecs.container.RandomAccessSource
import me.misa198.airmedy.codecs.container.ByteBufferSource

class Musepack private constructor(source:RandomAccessSource,strict:Boolean) {
 private val demux=Demuxer(source,strict)
 val info=StreamInfo(demux.cfg.rate,demux.cfg.channels,32,demux.totalSamples,demux.cfg.streamVersion)
 val warnings:List<String> get()=demux.warnings
 private val decoder=Decoder(demux.cfg);private val buffer=FloatBuffer(info.channels,1152)
 private var remaining=0;private var rawPosition=0L;private var discard=demux.delay;private var position=0L;private var discontinuity=false
 private val scanner=Scanner(demux.cfg);private val checkpoints=ArrayList<State>().also{it.add(State())};private val scanState=State();private var scanned=0
 fun decodeBlock():FloatBuffer? {while(position<info.totalSamples){if(remaining==0){if(!demux.readPacket())return null;remaining=demux.packetFrames;decoder.startPayload(demux.packet,0,demux.packetBits,remaining)}
 val pcm=decoder.decodeFrame();remaining--;rawPosition+=1152;val skip=minOf(discard,1152L).toInt();discard-=skip;if(skip==1152)continue
 val n=minOf((1152-skip).toLong(),info.totalSamples-position).toInt();System.arraycopy(pcm,skip*info.channels,buffer.samples,0,n*info.channels);buffer.frames=n;buffer.position=position;buffer.discontinuity=discontinuity;position+=n;discontinuity=false;return buffer};return null}
 fun seekSample(sample:Long){if(sample<0)throw java.io.IOException("musepack: negative seek target");val target=minOf(sample,info.totalSamples);val raw=target+demux.delay;val frame=maxOf(raw-512,0)/1152
 val block=minOf((frame/demux.cfg.framesPerBlock).toInt(),maxOf(demux.total-1,0));val stride=if(demux.cfg.streamVersion==7)32 else 1
 if(demux.cfg.streamVersion==8&&demux.cfg.pns==0){decoder.reset();demux.next=block;remaining=0;rawPosition=block*demux.cfg.framesPerBlock.toLong()*1152;discard=maxOf(raw-rawPosition,0);position=target;discontinuity=true;return}
 if(block<scanned){val k=block/stride;scanner.load(checkpoints[k]);scanned=k*stride}
 while(scanned<block){val end=minOf(block,(scanned/stride+1)*stride);while(scanned<end){demux.next=scanned;check(demux.readPacket());scanner.scan(demux.packet,demux.packetBits,demux.packetFrames);scanned++};if(scanned%stride==0&&checkpoints.size==scanned/stride){val state=State();scanner.capture(state);checkpoints.add(state)}}
 scanner.capture(scanState);decoder.reset();scanState.load(decoder.st);demux.next=block;remaining=0;rawPosition=block*demux.cfg.framesPerBlock.toLong()*1152;discard=maxOf(raw-rawPosition,0);position=target;discontinuity=true
 }
 data class StreamInfo(val sampleRate:Int,val channels:Int,val bitsPerSample:Int,val totalSamples:Long,val streamVersion:Int)
 companion object {fun open(source:RandomAccessSource,strict:Boolean=false)=Musepack(source,strict);fun open(source:ByteBuffer,strict:Boolean=false)=open(ByteBufferSource(source),strict)}
}
