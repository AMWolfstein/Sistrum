// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow container/mp4/box.go, container/mp4/stsd.go, container/mp4/stsdcodecs.go, container/mp4/stbl.go, container/mp4/fragdemux.go and container/mp4/meta.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.container.mp4

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.math.BigInteger
import me.misa198.airmedy.codecs.codec.adpcm.*
import me.misa198.airmedy.codecs.container.*
import me.misa198.airmedy.codecs.container.adpcm.*

/** Only the ima4 sample-entry branch missing from Media3's MP4 extractor. */
class Ima4 private constructor(private val source:RandomAccessSource,private val strict:Boolean) {
 val warnings=ArrayList<String>();lateinit var stream:BlockStream;private set
 val info get()=stream.info
 private data class Box(val type:String,val off:Long,val head:Int,val size:Long) {val payload get()=off+head;val end get()=off+size}
 private class Index(val byteSize:Int):BlockIndex {val offsets=ArrayList<Long>();val first=ArrayList<Long>();override var blocks=0L
 fun add(off:Long,n:Long){if(n==0L)return;offsets.add(off);first.add(blocks);blocks+=n}
 override fun offset(block:Long):Long {var lo=0;var hi=first.size;while(lo<hi){val mid=(lo+hi) ushr 1;if(first[mid]<=block)lo=mid+1 else hi=mid};val k=maxOf(lo-1,0);return offsets[k]+(block-first[k])*byteSize}}
 private fun bad(s:String):Nothing=throw IOException("mp4: $s")
 private fun warn(off:Long,s:String){if(strict)bad("$s (at offset $off)");if(warnings.size<64&&!warnings.contains(s))warnings.add(s)}
 private fun read(off:Long,n:Int):ByteBuffer {val b=ByteBuffer.allocate(n).order(ByteOrder.BIG_ENDIAN);var pos=off;while(b.hasRemaining()){val before=b.position();val got=source.read(pos,b);if(got<=0||b.position()-before!=got)bad("reading box: unexpected EOF");pos+=got};b.flip();return b}
 private fun id(b:ByteBuffer,p:Int)=String(byteArrayOf(b.get(p),b.get(p+1),b.get(p+2),b.get(p+3)),Charsets.ISO_8859_1)
 private fun u32(b:ByteBuffer,p:Int)=b.getInt(p).toLong() and 0xffffffffL
 private fun box(off:Long,end:Long):Box {if(off+8>end)bad("box header at $off runs past end");val b=read(off,8);val type=id(b,4);var size=u32(b,0);var head=8
 if(size==1L){if(off+16>end)bad("64-bit box size at $off runs past end");size=read(off+8,8).getLong(0);head=16}else if(size==0L)size=end-off
 if(size<head)bad("box \"$type\" size $size smaller than its header");if(size>end-off)bad("box \"$type\" at $off (size $size) runs past end $end");return Box(type,off,head,size)}
 private fun children(b:Box):List<Box>{val list=ArrayList<Box>();var off=b.payload;while(off+8<=b.end){val child=box(off,b.end);list.add(child);off=child.end};return list}
 private fun child(b:Box,type:String)=children(b).firstOrNull{it.type==type}
 private fun body(b:Box):ByteBuffer {val size=b.size-b.head;if(size>64L shl 20)bad("moov box of $size bytes exceeds the 67108864 cap");return read(b.payload,size.toInt())}
 private fun rescale(a:Long,b:Long,c:Long):Long {if(a<=0||b<=0||c<=0)return 0;return BigInteger.valueOf(a).multiply(BigInteger.valueOf(b)).divide(BigInteger.valueOf(c)).min(BigInteger.valueOf(Long.MAX_VALUE)).toLong()}
 init{parse()}
 private fun parse(){val top=ArrayList<Box>();var pos=0L;while(pos+8<=source.length){val b=box(pos,source.length);top.add(b);pos=b.end}
 val moov=top.firstOrNull{it.type=="moov"}?:bad("no moov box");if(moov.size-moov.head>64L shl 20)bad("moov box of ${moov.size-moov.head} bytes exceeds the 67108864 cap")
 var movieScale=0L;child(moov,"mvhd")?.let{val p=body(it);val off=if(p.get(0).toInt()==1)20 else 12;if(p.limit()>=off+4)movieScale=u32(p,off)}
 var track:Box?=null;var stbl:Box?=null;var cfg:Config?=null;var rate=0;var scale=0L;var trackID=0;var entry:ByteBuffer?=null
 for(trak in children(moov).filter{it.type=="trak"}){val mdia=child(trak,"mdia")?:continue;val hdlr=child(mdia,"hdlr")?:continue;val hd=body(hdlr);if(hd.limit()<12||id(hd,8)!="soun")continue;val minf=child(mdia,"minf")?:continue;val table=child(minf,"stbl")?:continue;val stsd=child(table,"stsd")?:continue;val sd=body(stsd);if(sd.limit()<16)bad("stsd truncated");if(u32(sd,4)==0L)bad("stsd has no sample entries");val sample=box(stsd.payload+8,stsd.end);if(sample.type.lowercase()!="ima4")continue;val p=body(sample);if(p.limit()<28)bad("audio sample entry \"ima4\" truncated")
 val version=p.getShort(8).toInt() and 65535;var ch=p.getShort(16).toInt() and 65535;rate=(u32(p,24) ushr 16).toInt();if(version==2){if(p.limit()<64)bad("version 2 audio sample entry \"ima4\" truncated (${p.limit()} bytes)");val hz=p.getDouble(32);if(!hz.isFinite()||hz<1||hz>(1 shl 24))bad("version 2 audio sample entry \"ima4\" declares a $hz Hz rate");rate=kotlin.math.floor(hz+0.5).toInt();val rawCh=u32(p,40);if(rawCh==0L||rawCh>255)bad("version 2 audio sample entry \"ima4\" declares $rawCh channels");ch=rawCh.toInt();val depth=u32(p,48);if(depth>64)bad("version 2 audio sample entry \"ima4\" declares $depth bits per channel")}
 val mdhd=child(mdia,"mdhd")?:bad("audio track missing mdhd");val md=body(mdhd);val mdOff=if(md.get(0).toInt()==1)20 else 12;scale=u32(md,mdOff);if(rate<=0){if(scale !in 1..Int.MAX_VALUE.toLong())bad("sample entry states no sample rate and the media timescale is $scale");rate=scale.toInt();warnings.add("sample entry states no sample rate; taking the media timescale's $scale Hz")}
 cfg=Config(Layout.IMAQuickTime,ch,34*ch,64);cfg.validate();entry=p;track=trak;stbl=table;child(trak,"tkhd")?.let{val tk=body(it);trackID=u32(tk,if(tk.get(0).toInt()==1)20 else 12).toInt()};break}
 val config=cfg?:bad("no supported audio track");val table=stbl!!;val index=Index(config.blockAlign);var ticks=0L;val stts=child(table,"stts")?.let{body(it)};var timeline=0L
 if(stts!=null){if(stts.limit()<8)bad("stts truncated");val n=u32(stts,4);if(n>(stts.limit()-8)/8)bad("stts declares $n entries for ${stts.limit()-8} bytes");for(i in 0 until n.toInt()){val count=u32(stts,8+8*i);val delta=u32(stts,12+8*i);ticks+=count*delta;timeline+=count*rescale(delta,rate.toLong(),scale)}}
 val sz=child(table,"stsz")?.let{body(it)};val sc=child(table,"stsc")?.let{body(it)};val offsets=child(table,"co64")?:child(table,"stco")
 if(sz!=null&&sc!=null&&offsets!=null){if(sz.limit()<12)bad("stsz truncated");val constSize=u32(sz,4);var sampleN=u32(sz,8);if(constSize>0)sampleN=minOf(sampleN,source.length/constSize+1) else if(sampleN>(sz.limit()-12)/4)bad("stsz declares $sampleN samples for ${sz.limit()-12} bytes")
 val chunk=body(offsets);if(chunk.limit()<8)bad("stco truncated");val count=u32(chunk,4).toInt();val width=if(offsets.type=="co64")8 else 4;if(count<0||count>(chunk.limit()-8)/width)bad("${offsets.type} truncated")
 val runs=u32(sc,4).toInt();if(runs<0||runs>(sc.limit()-8)/12)bad("stsc truncated");var prev=0L;for(i in 0 until runs){val first=u32(sc,8+12*i);if(first<=prev)bad("stsc first_chunk $first not increasing");prev=first}
 var sample=0L;for(k in 0 until runs){val first=u32(sc,8+12*k);val per=u32(sc,12+12*k);if(first<1||first>count+1)bad("stsc first_chunk $first outside 1..${count+1}");val last=if(k+1<runs)minOf(u32(sc,20+12*k)-1,count.toLong()) else count.toLong()
 for(c in first..last){if(sample>=sampleN)break;var off=if(width==8)chunk.getLong(8+(c-1).toInt()*width) else u32(chunk,8+(c-1).toInt()*width);val n=minOf(per,sampleN-sample)
 if(constSize==config.blockAlign.toLong()){val fit=if(off<0||off>source.length)0 else minOf(n,(source.length-off)/config.blockAlign);if(fit<n)warn(off,"sample table truncated at sample $sample: $sampleN samples declared, ${sample+fit} readable");index.add(off,fit);sample+=n}
 else {repeat(n.toInt()){val size=if(constSize>0)constSize else u32(sz,12+sample.toInt()*4);val fit=if(off<0||off>source.length)0 else minOf(size,source.length-off);if(fit%config.blockAlign!=0L)warn(off,"$fit trailing bytes are not a whole block, ignored");index.add(off,fit/config.blockAlign);off+=size;sample++}}}}
 }
 val mvex=child(moov,"mvex");var defaultDur=0L;var defaultSize=0L
 if(mvex!=null){val trexes=children(mvex).filter{it.type=="trex"};val trex=if(trexes.size==1)trexes[0] else trexes.firstOrNull{u32(body(it),4)==trackID.toLong()};trex?.let{val p=body(it);if(p.limit()>=24){defaultDur=u32(p,12);defaultSize=u32(p,16)}}}
 for(moof in top.filter{it.type=="moof"}){if(moof.size-moof.head>8L shl 20)bad("moof box of ${moof.size-moof.head} bytes exceeds the 8388608 cap")
 for(traf in children(moof).filter{it.type=="traf"}){val tfhd=child(traf,"tfhd")?:continue;val p=body(tfhd);if(p.limit()<8)continue;if(u32(p,4)!=trackID.toLong())continue;val flags=p.getInt(0) and 0xffffff;var at=8;var base=moof.off;var dur=defaultDur;var size=defaultSize
 if(flags and 1!=0){base=p.getLong(at);at+=8};if(flags and 2!=0)at+=4;if(flags and 8!=0){dur=u32(p,at);at+=4};if(flags and 16!=0){size=u32(p,at);at+=4}
 val run=child(traf,"trun")?:bad("traf has no trun");val r=body(run);if(r.limit()<8)bad("trun truncated");val rf=r.getInt(0) and 0xffffff;val count=u32(r,4).toInt();if(count<0||count>1 shl 20)bad("trun declares ${u32(r,4)} samples");at=8;var off=top.firstOrNull{it.off>=moof.end&&it.type=="mdat"}?.payload?:moof.end
 if(rf and 1!=0){if(r.limit()<at+4)bad("trun data offset truncated");off=base+r.getInt(at);at+=4};if(rf and 4!=0)at+=4;val each=Integer.bitCount(rf and 0xf00)*4;if(count.toLong()*each>r.limit()-at)bad("trun declares $count samples for ${r.limit()-at} bytes")
 for(i in 0 until count){var duration=dur;var bytes=size;if(rf and 0x100!=0){duration=u32(r,at);at+=4};if(rf and 0x200!=0){bytes=u32(r,at);at+=4};if(rf and 0x400!=0)at+=4;if(rf and 0x800!=0)at+=4
 if(off<0||bytes>source.length-off)bad("fragment sample runs past end of file");index.add(off,bytes/config.blockAlign);off+=bytes;ticks+=duration;timeline+=rescale(duration,rate.toLong(),scale)}
 }}
 var delay=0L;var samples=if(mvex!=null)index.blocks*64 else timeline;val edts=child(track!!,"edts");val elst=edts?.let{child(it,"elst")};if(elst!=null){val p=body(elst);if(p.limit()>=8){val version=p.get(0).toInt();val width=if(version==1)20 else 12;val n=minOf(u32(p,4),(p.limit()-8).toLong()/width);var media= -1L;var seg=0L
 for(i in 0 until n.toInt()){val at=8+i*width;val mt=if(version==1)p.getLong(at+8) else p.getInt(at+4).toLong();val duration=if(version==1)p.getLong(at) else u32(p,at);if(mt<0)continue;if(media<0)media=mt;seg+=duration}
 if(media>=0&&(mvex!=null||media>0)){delay=rescale(media,rate.toLong(),scale);if(seg>0&&movieScale>0)samples=rescale(seg,rate.toLong(),movieScale) else samples=timeline-delay}}}
 val capacity=index.blocks*64;delay=delay.coerceIn(0,capacity);samples=samples.coerceIn(0,capacity-delay)
 stream=BlockStream(config,source,index,rate,samples,delay)
 }
 fun decodeBlock()=stream.decodeBlock()
 fun seekSample(sample:Long)=stream.seekSample(sample)
 companion object {fun open(source:RandomAccessSource,strict:Boolean=false)=Ima4(source,strict);fun open(source:ByteBuffer,strict:Boolean=false)=open(ByteBufferSource(source),strict)}
}
