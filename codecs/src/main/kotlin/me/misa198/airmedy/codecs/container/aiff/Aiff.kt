// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow container/aiff/demux.go and container/aiff/ext80.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.container.aiff

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import me.misa198.airmedy.codecs.codec.adpcm.*
import me.misa198.airmedy.codecs.codec.g711.*
import me.misa198.airmedy.codecs.codec.pcm.Pcm
import me.misa198.airmedy.codecs.audio.Buffer
import me.misa198.airmedy.codecs.audio.FloatBuffer
import me.misa198.airmedy.codecs.container.*
import me.misa198.airmedy.codecs.container.adpcm.*

/** AIFF and fixed-unit AIFF-C audio; float sources use decodeFloatBlock. */
class Aiff private constructor(private val source:RandomAccessSource,private val strict:Boolean) {
 val warnings=ArrayList<String>();internal var adpcm:BlockStream?=null;private var g711:G711?=null;private var pcm:Pcm?=null
 data class StreamInfo(val sampleRate:Int,val channels:Int,val bitsPerSample:Int,val totalSamples:Long,val floating:Boolean,val sourceBits:Int,val samplesExact:Boolean=true)
 lateinit var info:StreamInfo;private set
 var compression="";private set
 private fun bad(s:String):Nothing=throw IOException("aiff: $s")
 private fun warn(off:Long,s:String){if(strict)bad("$s (at offset $off)");if(warnings.size<64&&!warnings.contains(s))warnings.add(s)}
 private fun read(off:Long,n:Int):ByteBuffer {val b=ByteBuffer.allocate(n).order(ByteOrder.BIG_ENDIAN);var pos=off;while(b.hasRemaining()){val before=b.position();val got=source.read(pos,b);if(got<=0||b.position()-before!=got)bad("reading header: unexpected EOF");pos+=got};b.flip();return b}
 private fun id(b:ByteBuffer,p:Int)=String(byteArrayOf(b.get(p),b.get(p+1),b.get(p+2),b.get(p+3)),Charsets.ISO_8859_1)
 private fun u32(b:ByteBuffer,p:Int)=b.getInt(p).toLong() and 0xffffffffL
 init{parse()}
 private fun parse(){val size=source.length;val head=read(0,12);if(id(head,0)!="FORM"||id(head,8) !in listOf("AIFF","AIFC"))bad("not an AIFF/AIFF-C file");val aifc=id(head,8)=="AIFC";var seen=false;var channels=0;var depth=0;var wireBits=0;var floating=false;var cfg:Config?=null;var rate=0;var units=0L;var dataOff= -1L;var dataBytes= -1L;var off=12L;var chunks=0
 while(off+8<=size){if(chunks++>=1024)bad("more than 1024 chunks");val hdr=read(off,8);val key=id(hdr,0);val n=u32(hdr,4)
 when(key){"COMM"->{if(seen)warn(off,"duplicate COMM chunk ignored") else {val want=if(aifc)22 else 18;if(n<want)bad("COMM chunk of $n bytes, want at least $want");val use=minOf(n,512).toInt();if(n>512)warn(off,"COMM chunk of $n bytes truncated to 512");if(off+8+use>size)bad("COMM chunk extends past end of file");val p=read(off+8,use);val ch=p.getShort(0).toInt();units=u32(p,2);val bits=p.getShort(6).toInt();val exponent=((p.get(8).toInt() and 127) shl 8) or (p.get(9).toInt() and 255);val mantissa=p.getLong(10);val sign=if(p.get(8).toInt() and 128!=0)-1 else 1
 val rateF=if(exponent==0x7fff)Double.NaN else sign*Math.scalb(java.lang.Long.toUnsignedString(mantissa).toDouble(),exponent-16383-63)
 if(ch<1)bad("$ch channels");if(ch>8)bad("$ch channels (supported: 1..8)");if(rateF.isNaN()||rateF<=0||rateF>Int.MAX_VALUE)bad("sample rate $rateF");rate=kotlin.math.floor(rateF+0.5).toInt();if(rate.toDouble()!=rateF)warnings.add("non-integer sample rate $rateF rounded to $rate")
 val raw=if(aifc)id(p,18)else "NONE";compression=raw.lowercase(java.util.Locale.ROOT);channels=ch;seen=true
 when(compression){
 "ima4"->{cfg=Config(Layout.IMAQuickTime,ch,34*ch,64);cfg.validate();depth=16;wireBits=4;if(bits!=4&&bits!=16)warn(off,"ima4 declares $bits bits per sample, want 4 or 16")}
 "alaw","ulaw"->{depth=16;wireBits=8;if(bits!=8&&bits!=16)warn(off,"${if(compression=="alaw")"A-law" else "mu-law"} declares $bits bits per sample, want 8 or 16")}
 "none","twos","sowt"->{if(bits !in 1..32)bad("$bits bits per sample");depth=bits;wireBits=(bits+7)/8*8}
 "raw "->{if(bits!=8)bad("raw compression with $bits bits");depth=8;wireBits=8}
 "in24"->{depth=24;wireBits=24};"in32"->{depth=32;wireBits=32}
 "fl32","fl64"->{floating=true;depth=32;wireBits=if(compression=="fl32")32 else 64}
 else->{val name=when(raw){"MAC3"->"MAC3";"MAC6"->"MAC6";else->""};if(name.isEmpty())bad("this build does not read compression type \"$raw\" from an AIFF-C")else bad("this build does not read $name (compression type \"$raw\") from an AIFF-C")}
 }}}
 "SSND"->{if(dataBytes>=0)warn(off,"extra SSND chunk ignored") else {if(n<8)bad("SSND chunk of $n bytes, want at least 8");val p=read(off+8,8);val start=u32(p,0);dataOff=off+16+start;dataBytes=n-8-start;if(dataBytes<0)bad("SSND offset $start exceeds chunk");if(dataOff+dataBytes>size){warn(off,"SSND data of $dataBytes bytes exceeds file, clamped");dataBytes=maxOf(size-dataOff,0)}}}}
 val next=off+8+n+(n and 1);if(next<=off)bad("chunk size overflow");if(next>size&&key!="SSND"){warn(off,"\"$key\" chunk extends past end of file");break};off=next}
 if(!seen)bad("no COMM chunk");if(dataBytes<0)bad("no SSND chunk")
 val unitBytes=cfg?.blockAlign ?: (channels*(wireBits/8));val unitFrames=cfg?.samplesPerBlock ?: 1;val unit=if(cfg!=null)"packet" else "frame"
 val rem=dataBytes%unitBytes;if(rem!=0L){warn(dataOff,"$rem trailing bytes are not a whole $unit, ignored");dataBytes-=rem};val available=dataBytes/unitBytes
 if(available<units){warn(dataOff,"COMM declares $units ${unit}s, SSND holds $available; clamped");units=available}else if(available>units)warn(dataOff,"SSND holds ${available-units} ${unit}s beyond the $units COMM declares; extra ignored")
 val samples=units*unitFrames;info=StreamInfo(rate,channels,depth,samples,floating,wireBits)
 when(compression){
 "ima4"->adpcm=BlockStream(cfg!!,source,ContiguousBlocks(dataOff,units,unitBytes),rate)
 "alaw","ulaw"->g711=G711.open(if(compression=="alaw")Law.ALaw else Law.MuLaw,rate,channels,source,dataOff,samples)
 else->pcm=Pcm(source,dataOff,samples,channels,wireBits,if(floating)wireBits else depth,floating,compression=="raw ",compression!="sowt")
 }
 }
 fun decodeBlock():Buffer? {check(!info.floating){"Use decodeFloatBlock for floating-point AIFF"};return if(adpcm!=null)adpcm!!.decodeBlock()else if(g711!=null)g711!!.decodeBlock()else pcm!!.decodeBlock()}
 fun decodeFloatBlock():FloatBuffer? {check(info.floating);return pcm!!.decodeFloatBlock()}
 fun seekSample(sample:Long){if(sample<0)bad("negative seek target");adpcm?.seekSample(sample);g711?.seekSample(sample);pcm?.seekSample(sample)}
 companion object {fun open(source:RandomAccessSource,strict:Boolean=false)=Aiff(source,strict);fun open(source:ByteBuffer,strict:Boolean=false)=open(ByteBufferSource(source),strict)}
}
