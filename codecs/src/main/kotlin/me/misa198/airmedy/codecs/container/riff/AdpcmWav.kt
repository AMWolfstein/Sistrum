// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow container/riff/demux.go and container/internal/waveformat/waveformat.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.container.riff

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import me.misa198.airmedy.codecs.codec.adpcm.*
import me.misa198.airmedy.codecs.container.*
import me.misa198.airmedy.codecs.container.adpcm.*

/** RIFF branch for MS ADPCM; WAV IMA and G.711 remain owned by Media3. */
class AdpcmWav private constructor(private val source:RandomAccessSource,private val strict:Boolean) {
 val warnings=ArrayList<String>();lateinit var stream:BlockStream;private set
 val info get()=stream.info
 private fun bad(s:String):Nothing=throw IOException("wav: $s")
 private fun warn(off:Long,s:String){if(strict)bad("$s (at offset $off)");if(warnings.size<64&&!warnings.contains(s))warnings.add(s)}
 private fun read(off:Long,n:Int):ByteBuffer {val b=ByteBuffer.allocate(n).order(ByteOrder.LITTLE_ENDIAN);var pos=off;while(b.hasRemaining()){val before=b.position();val got=source.read(pos,b);if(got<=0||b.position()-before!=got)bad("reading header: unexpected EOF");pos+=got};b.flip();return b}
 private fun id(b:ByteBuffer,p:Int)=String(byteArrayOf(b.get(p),b.get(p+1),b.get(p+2),b.get(p+3)),Charsets.ISO_8859_1)
 private fun u16(b:ByteBuffer,p:Int)=b.getShort(p).toInt() and 65535
 private fun u32(b:ByteBuffer,p:Int)=b.getInt(p).toLong() and 0xffffffffL
 init{parse()}
 private fun parse(){val size=source.length;val h=read(0,12);val magic=id(h,0);if(magic !in listOf("RIFF","RF64","BW64")||id(h,8)!="WAVE")bad("not a RIFF/WAVE file");val rf64=magic!="RIFF"
 var cfg:Config?=null;var rate=0;var dataOff= -1L;var dataBytes= -1L;var fact= -1L;var factSeen=false;var haveDS64=false;var dsData=0L;var dsSamples=0L;var off=12L;var chunks=0
 while(off+8<=size){if(chunks++>=1024)bad("more than 1024 chunks");val ch=read(off,8);val key=id(ch,0);var n=u32(ch,4);var streaming=false
 when(key){
 "ds64"->{if(!rf64)warn(off,"ds64 chunk in a plain RIFF file ignored") else {if(n<28||off+36>size)bad("ds64 chunk truncated");val p=read(off+8,28);dsData=p.getLong(8);dsSamples=p.getLong(16);haveDS64=true}}
 "fmt "->{if(cfg!=null)warn(off,"duplicate fmt chunk ignored") else {if(n<16)bad("fmt chunk of $n bytes, want at least 16");val use=minOf(n,4096).toInt();if(n>4096)warn(off,"fmt chunk of $n bytes truncated to 4096");if(off+8+use>size)bad("fmt chunk extends past end of file");val p=read(off+8,use);val tag=u16(p,0)
 if(tag!=2)throw IOException("wav: this Kotlin entry point only ports MS ADPCM; format tag $tag belongs to Media3")
 val channels=u16(p,2);rate=u32(p,4).toInt();val align=u16(p,12);val bits=u16(p,14);if(bits!=4)warn(off,"MS ADPCM declares $bits bits per sample, which is 4 by definition")
 val extra=if(use>=18)minOf(u16(p,16),use-18) else 0;if(extra<2)bad("MS ADPCM header states no samples per block");val declared=u16(p,18);if(extra<4)bad("MS ADPCM header states no coefficient count");val count=u16(p,20);if(count!=7)bad("MS ADPCM with $count predictor coefficient pairs (this build reads 7)");if(extra<4+4*count)bad("MS ADPCM header holds ${maxOf(extra-4,0)/4} of its $count coefficient pairs")
 val coefs=Array(7){i->intArrayOf(p.getShort(22+4*i).toInt(),p.getShort(24+4*i).toInt())};if(!coefs.indices.all{coefs[it].contentEquals(defaultCoefs[it])})warnings.add("MS ADPCM coefficient table is not the conventional one; readers that ignore it decode this file differently")
 if(channels<1)bad("MS ADPCM header declares $channels channels");if(channels>8)bad("$channels channels (supported: 1..8)");if(channels>2)bad("$channels channels of MS ADPCM, whose nibbles alternate between at most 2");if(align<7*channels)bad("MS ADPCM declares $align-byte blocks, too small for the ${7*channels} bytes its $channels channels of header need")
 val frames=(align-7*channels)*2/channels+2;cfg=Config(Layout.MS,channels,align,frames,coefs);cfg.validate();if(declared!=frames)warn(off,"MS ADPCM declares $declared samples per block, its $align-byte blocks hold $frames; using $frames")}}
 "fact"->{if(!factSeen&&n>=4&&off+12<=size){val p=read(off+8,4);factSeen=true;val count=u32(p,0);if(count!=0xffffffffL)fact=count}}
 "data"->{if(dataBytes>=0)warn(off,"extra data chunk ignored") else {dataOff=off+8;dataBytes=when {n==0xffffffffL&&haveDS64->{if(dsData<0||dsData>size-dataOff){warn(off,"ds64 data size ${java.lang.Long.toUnsignedString(dsData)} exceeds file, clamped");size-dataOff}else dsData};n==0xffffffffL->{warn(off,"streaming data size, clamped to end of file");streaming=true;size-dataOff};dataOff+n>size->{warn(off,"data chunk size $n exceeds file, clamped");size-dataOff};else->n};n=dataBytes}}
 };if(streaming)break;val next=off+8+n+(n and 1);if(next<=off)bad("chunk size overflow");if(next>size&&key!="data"){warn(off,"\"$key\" chunk extends past end of file");break};off=next}
 val config=cfg?:bad("no fmt chunk");if(dataBytes<0)bad("no data chunk");val rem=dataBytes%config.blockAlign;if(rem!=0L){warn(dataOff,"$rem trailing bytes are not a whole block, ignored");dataBytes-=rem};val blocks=dataBytes/config.blockAlign;val capacity=blocks*config.samplesPerBlock;var samples=capacity
 if(fact>=0){when {fact>capacity->warn(0,"fact declares $fact samples, the data holds $capacity; clamped");capacity-fact>=config.samplesPerBlock->warn(0,"fact declares $fact samples, ${capacity-fact} short of the $capacity the data holds; ignored");else->samples=fact}}
 if(haveDS64&&dsSamples!=0L&&dsSamples!=capacity)warn(0,"ds64 sample count $dsSamples disagrees with data size ($capacity frames)")
 stream=BlockStream(config,source,ContiguousBlocks(dataOff,blocks,config.blockAlign),rate,samples)
 }
 fun decodeBlock()=stream.decodeBlock()
 fun seekSample(sample:Long)=stream.seekSample(sample)
 companion object {fun open(source:RandomAccessSource,strict:Boolean=false)=AdpcmWav(source,strict);fun open(source:ByteBuffer,strict:Boolean=false)=open(ByteBufferSource(source),strict)}
}
