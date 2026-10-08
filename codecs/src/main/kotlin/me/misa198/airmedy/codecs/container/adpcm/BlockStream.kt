// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow container/riff/demux.go and container/aiff/demux.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.container.adpcm

import java.io.IOException
import java.nio.ByteBuffer
import me.misa198.airmedy.codecs.audio.Buffer
import me.misa198.airmedy.codecs.codec.adpcm.Config
import me.misa198.airmedy.codecs.codec.adpcm.Layout
import me.misa198.airmedy.codecs.codec.adpcm.Decoder
import me.misa198.airmedy.codecs.container.RandomAccessSource

/** Immutable compressed block addresses, all offsets are source bytes. */
interface BlockIndex {val blocks:Long;fun offset(block:Long):Long}
class ContiguousBlocks(private val start:Long,override val blocks:Long,private val blockBytes:Int):BlockIndex {
 override fun offset(block:Long)=start+block*blockBytes
}
class BlockStream(val cfg:Config,val source:RandomAccessSource,val index:BlockIndex,rate:Int,totalSamples:Long=index.blocks*cfg.samplesPerBlock,private val frontDelay:Long=0) {
 init {cfg.validate();require(totalSamples>=0&&totalSamples<=index.blocks*cfg.samplesPerBlock)}
 val info=StreamInfo(rate,cfg.channels,16,totalSamples,cfg.layout)
 private val decoder=Decoder(cfg);private val encoded=ByteBuffer.allocate(cfg.blockAlign);private val output=Buffer(cfg.channels,16,4096)
 private var next=0L;private var offset=cfg.samplesPerBlock;private var discard=frontDelay;private var position=0L;private var discontinuity=false
 fun decodeBlock():Buffer? {while(position<info.totalSamples){if(offset>=cfg.samplesPerBlock){if(next>=index.blocks)return null;encoded.clear();val at=index.offset(next);var pos=at
 while(encoded.hasRemaining()){val before=encoded.position();val n=source.read(pos,encoded);if(n<=0||encoded.position()-before!=n)throw IOException("adpcm: reading block data");pos+=n};encoded.flip();decoder.decodeBlock(encoded);next++;offset=0}
 if(discard>0){val n=minOf(discard,(cfg.samplesPerBlock-offset).toLong()).toInt();offset+=n;discard-=n;if(offset>=cfg.samplesPerBlock)continue}
 val n=minOf(4096L,(cfg.samplesPerBlock-offset).toLong(),info.totalSamples-position).toInt();System.arraycopy(decoder.samples,offset*cfg.channels,output.samples,0,n*cfg.channels);output.frames=n;output.position=position;output.discontinuity=discontinuity;offset+=n;position+=n;discontinuity=false;return output};return null}
 fun seekSample(sample:Long){if(sample<0)throw IOException("adpcm: negative seek target");val target=minOf(sample,info.totalSamples);decoder.reset();val raw=target+frontDelay;next=if(cfg.layout==Layout.IMAQuickTime)0 else raw/cfg.samplesPerBlock;offset=cfg.samplesPerBlock;discard=raw-next*cfg.samplesPerBlock;position=target;discontinuity=true}
 data class StreamInfo(val sampleRate:Int,val channels:Int,val bitsPerSample:Int,val totalSamples:Long,val layout:Layout)
}
