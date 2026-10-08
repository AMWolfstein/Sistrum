// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow container/aiff/demux.go and container/aiff/ext80.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.container.aiff

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import me.misa198.airmedy.codecs.codec.adpcm.*
import me.misa198.airmedy.codecs.container.*
import me.misa198.airmedy.codecs.container.adpcm.*

/** AIFF-C branch for Apple's ima4 blocks. */
class Ima4 private constructor(private val source:RandomAccessSource,private val strict:Boolean) {
 val warnings=ArrayList<String>();lateinit var stream:BlockStream;private set
 val info get()=stream.info
 private fun bad(s:String):Nothing=throw IOException("aiff: $s")
 private fun warn(off:Long,s:String){if(strict)bad("$s (at offset $off)");if(warnings.size<64&&!warnings.contains(s))warnings.add(s)}
 private fun read(off:Long,n:Int):ByteBuffer {val b=ByteBuffer.allocate(n).order(ByteOrder.BIG_ENDIAN);var pos=off;while(b.hasRemaining()){val before=b.position();val got=source.read(pos,b);if(got<=0||b.position()-before!=got)bad("reading header: unexpected EOF");pos+=got};b.flip();return b}
 private fun id(b:ByteBuffer,p:Int)=String(byteArrayOf(b.get(p),b.get(p+1),b.get(p+2),b.get(p+3)),Charsets.ISO_8859_1)
 private fun u32(b:ByteBuffer,p:Int)=b.getInt(p).toLong() and 0xffffffffL
 init{parse()}
 private fun parse(){val size=source.length;val head=read(0,12);if(id(head,0)!="FORM"||id(head,8)!="AIFC")bad("not an AIFF/AIFF-C file");var cfg:Config?=null;var rate=0;var units=0L;var dataOff= -1L;var dataBytes= -1L;var off=12L;var chunks=0
 while(off+8<=size){if(chunks++>=1024)bad("more than 1024 chunks");val hdr=read(off,8);val key=id(hdr,0);val n=u32(hdr,4)
 when(key){"COMM"->{if(cfg!=null)warn(off,"duplicate COMM chunk ignored") else {if(n<22)bad("COMM chunk of $n bytes, want at least 22");val use=minOf(n,512).toInt();if(n>512)warn(off,"COMM chunk of $n bytes truncated to 512");if(off+8+use>size)bad("COMM chunk extends past end of file");val p=read(off+8,use);val ch=p.getShort(0).toInt();units=u32(p,2);val bits=p.getShort(6).toInt();val exponent=((p.get(8).toInt() and 127) shl 8) or (p.get(9).toInt() and 255);val mantissa=p.getLong(10);val sign=if(p.get(8).toInt() and 128!=0)-1 else 1
 val rateF=if(exponent==0x7fff)Double.NaN else sign*Math.scalb(java.lang.Long.toUnsignedString(mantissa).toDouble(),exponent-16383-63)
 if(ch<1)bad("$ch channels");if(ch>8)bad("$ch channels (supported: 1..8)");if(rateF.isNaN()||rateF<=0||rateF>Int.MAX_VALUE)bad("sample rate $rateF");rate=kotlin.math.floor(rateF+0.5).toInt();if(rate.toDouble()!=rateF)warnings.add("non-integer sample rate $rateF rounded to $rate")
 val comp=id(p,18);if(comp.lowercase()!="ima4")throw IOException("aiff: this Kotlin entry point only ports ima4; compression $comp belongs to another decoder")
 cfg=Config(Layout.IMAQuickTime,ch,34*ch,64);cfg.validate();if(bits!=4&&bits!=16)warn(off,"ima4 declares $bits bits per sample, want 4 or 16")}}
 "SSND"->{if(dataBytes>=0)warn(off,"extra SSND chunk ignored") else {if(n<8)bad("SSND chunk of $n bytes, want at least 8");val p=read(off+8,8);val start=u32(p,0);dataOff=off+16+start;dataBytes=n-8-start;if(dataBytes<0)bad("SSND offset $start exceeds chunk");if(dataOff+dataBytes>size){warn(off,"SSND data of $dataBytes bytes exceeds file, clamped");dataBytes=maxOf(size-dataOff,0)}}}}
 val next=off+8+n+(n and 1);if(next<=off)bad("chunk size overflow");if(next>size&&key!="SSND"){warn(off,"\"$key\" chunk extends past end of file");break};off=next}
 val config=cfg?:bad("no COMM chunk");if(dataBytes<0)bad("no SSND chunk");val rem=dataBytes%config.blockAlign;if(rem!=0L){warn(dataOff,"$rem trailing bytes are not a whole packet, ignored");dataBytes-=rem};val available=dataBytes/config.blockAlign
 if(available<units){warn(dataOff,"COMM declares $units packets, SSND holds $available; clamped");units=available}else if(available>units)warn(dataOff,"SSND holds ${available-units} packets beyond the $units COMM declares; extra ignored")
 stream=BlockStream(config,source,ContiguousBlocks(dataOff,units,config.blockAlign),rate)
 }
 fun decodeBlock()=stream.decodeBlock()
 fun seekSample(sample:Long)=stream.seekSample(sample)
 companion object {fun open(source:RandomAccessSource,strict:Boolean=false)=Ima4(source,strict);fun open(source:ByteBuffer,strict:Boolean=false)=open(ByteBufferSource(source),strict)}
}
