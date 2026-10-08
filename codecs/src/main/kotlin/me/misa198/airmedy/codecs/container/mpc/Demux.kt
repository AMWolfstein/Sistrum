// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow container/mpc/demux.go, container/mpc/sv7.go and container/mpc/sv8.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.container.mpc

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import me.misa198.airmedy.codecs.container.RandomAccessSource
import me.misa198.airmedy.codecs.container.internal.srcwin.Window
import me.misa198.airmedy.codecs.container.wv.stripTrailers
import me.misa198.airmedy.codecs.container.wv.id3Size
import me.misa198.airmedy.codecs.codec.musepack.*

internal class Demuxer(val source:RandomAccessSource,val strict:Boolean=false) {
 val w=Window(source){cause->java.io.IOException("musepack: reading stream data",cause)}
 var base=0L;lateinit var cfg:Config;var totalSamples=0L;var delay=481L;val warnings=ArrayList<String>()
 private val positions=ArrayList<Long>();private var endBits=0L;private var maxPayload=5120;private var packetCount=0;private var stride=0;private var lastFrames=0;private var cursor = -1;private var cursorPos=0L
 var next=0;val total get()=packetCount;lateinit var packet:ByteArray;private lateinit var readBuffer:ByteBuffer
 var packetFrames=0;var packetBits=0;private var sv7Frames=0L
 private val blockHeader=Block()
 init {parse();packet=ByteArray(maxPayload);readBuffer=ByteBuffer.wrap(packet)}
 private fun warn(off:Long,msg:String){if(strict)throw malformed("$msg (at offset $off)");if(warnings.size<64&&!warnings.contains(msg))warnings.add(msg)}
 private fun bytes(pos:Long,n:Int):ByteBuffer {w.ensure(pos,n);return w.data}
 private fun u8(pos:Long):Int {bytes(pos,1);return w.data.get(w.index(pos)).toInt() and 255}
 private fun u32(pos:Long):Long {bytes(pos,4);return w.data.order(ByteOrder.LITTLE_ENDIAN).getInt(w.index(pos)).toLong() and 0xffffffffL}
 private fun parse(){w.ensure(0,10);val from=id3Size(w.data,0,false).toLong();val limit=minOf(from+(1 shl 20),w.dataEnd);var off=from;var found=false
 while(off+4<=limit){w.ensure(off,4,true);val i=w.index(off);val a=w.data.get(i);val b=w.data.get(i+1);val c=w.data.get(i+2);val v=w.data.get(i+3).toInt() and 255
 if(a==77.toByte()&&b==80.toByte()&&((c==67.toByte()&&v==75)||(c==43.toByte()&&(v and 15) in 4..7))){base=off;found=true;break};off++;w.trim(off)}
 if(!found)throw malformed("not a Musepack stream")
 if(u8(base+2)==67)parseSV8() else {val version=u8(base+3) and 15;if(version!=7)throw unsupported("Musepack SV$version is not supported: only SV7 and SV8 are");parseSV7()};cfg.validate()
 }
 private fun parseSV7(){if(w.dataEnd-base<28)throw malformed("SV7 header truncated");sv7Frames=u32(base+4);val w2=u32(base+8);val w5=u32(base+20);val maxBand=((w2 ushr 24) and 63).toInt();val last=((w5 ushr 20) and 2047).toInt()
 if(maxBand !in 1..31)throw malformed("max band $maxBand outside 1..31");if(last>1152)throw malformed("header states $last samples in the last frame, more than a frame holds")
 cfg=Config(7,rates[((w2 ushr 16) and 3).toInt()],2,maxBand,w2 ushr 30 and 1!=0L,pns=if(u8(base+3) ushr 4!=0)1 else 0,trueGapless=w5 ushr 31!=0L,lastFrameSamples=last);cfg.validate()
 stripTrailers(w,base+28);endBits=((w.dataEnd-base) and -4L)*8;var pos=200L;var present=0L
 while(present<sv7Frames){val n=sv7Bits(pos,20);if(n<0){warn(base+(pos ushr 3),"the header counts $sv7Frames frames but the stream ends after $present");break}
 if(n !in 8..40960){warn(base+(pos ushr 3),"frame $present declares $n bits, which no frame can hold; the stream ends there");break}
 if(pos+20+n>endBits){warn(base+(pos ushr 3),"frame $present runs past the end of the stream");break}
 if(present%32==0L)positions.add(pos);packetCount++;pos+=20+n;present++;w.trim(base+(pos ushr 3))}
 var tail=if(last==0)1152 else last
 if(present==sv7Frames&&sv7Frames>0){val count=sv7Bits(pos,11);if(count>=0){pos+=11;val inStream=if(count==0)1152 else count;if(inStream>1152)warn(base+(pos ushr 3),"the stream states $inStream samples in its last frame, more than a frame holds; the header's $tail stands") else if(inStream!=tail){warn(base+(pos ushr 3),"the header says the last frame holds $tail samples, the stream says $inStream");tail=inStream}} else warn(base+(pos ushr 3),"the stream ends before its last-frame sample count")
 if(cfg.trueGapless&&tail>671){val n=sv7Bits(pos,20);if(n !in 8..40960||pos+20+n>endBits)warn(base+(pos ushr 3),"the last frame's $tail samples need a decay frame the stream does not hold") else {if(present%32==0L)positions.add(pos);packetCount++}}}
 val declared=if(sv7Frames==0L)0 else if(cfg.trueGapless)(sv7Frames-1)*1152+tail else sv7Frames*1152-481;val deliverable=maxOf(total*1152L-481,0)
 if(deliverable<declared)warn(w.dataEnd,"the header declares $declared samples but the frames present deliver $deliverable");totalSamples=minOf(declared,deliverable)
 }
 private fun sv7Bits(pos:Long,n:Int):Int {if(pos<0||pos+n>endBits)return -1;val m0=pos ushr 3;val w0=m0 and -4L;val wEnd=((m0+4) and -4L)+4;val got=w.ensure(base+w0,(wEnd-w0).toInt(),true);val start=w.index(base+w0);var acc=0L
 for(j in 0L..4L){acc=acc shl 8;val i=((m0+j-w0) xor 3).toInt();if(i<got)acc=acc or (w.data.get(start+i).toLong() and 255)};return ((acc shl (24+(pos and 7).toInt())) ushr (64-n)).toInt()}
 private class Block(var key:Int=0,var head:Int=0,var size:Int=0)
 private fun keyName(key:Int)="${(key ushr 8).toChar()}${(key and 255).toChar()}"
 private fun sv8Header(off:Long):Block? {val got=w.ensure(off,11,true);if(got<3)return null;val at=w.index(off);val a=w.data.get(at).toInt() and 255;val b=w.data.get(at+1).toInt() and 255;if(a !in 65..90||b !in 65..90)return null
 var size=0L;for(n in 1..9){if(n+2>got)return null;val c=w.data.get(at+1+n).toInt() and 255;size=(size shl 7) or (c.toLong() and 127);if(c and 128==0){if(size<2+n||size>w.dataEnd-off||size>Int.MAX_VALUE)return null;blockHeader.key=(a shl 8) or b;blockHeader.head=2+n;blockHeader.size=(size-2-n).toInt();return blockHeader}};return null}
 private fun payload(off:Long,b:Block):ByteArray {w.ensure(off+b.head,b.size);val a=ByteArray(b.size);val at=w.index(off+b.head);System.arraycopy(w.data.array(),at,a,0,b.size);return a}
 private fun parseSH(p:ByteArray):LongArray {if(p.size<4)throw malformed("stream header of ${p.size} bytes");val r=BitReader();r.reset(p);val crc=r.bits(32).toLong() and 0xffffffffL;val c=CRC32();c.update(p,4,p.size-4);if(crc!=c.value)throw malformed("stream header fails its CRC");val v=r.bits(8);if(v!=8)throw malformed("stream header version $v, want 8")
 val count=r.varint();val silence=r.varint();val freq=r.bits(3);val band=r.bits(5)+1;val channels=r.bits(4)+1;val ms=r.bits(1)!=0;val power=r.bits(3)*2;if(r.over)throw malformed("stream header truncated");if(rates[freq]==0)throw malformed("sample rate index $freq is reserved");if(count>1L shl 40||silence>count)throw malformed("stream header declares $count samples with $silence of beginning silence")
 cfg=Config(8,rates[freq],channels,band,ms,power,255,true);cfg.validate();return longArrayOf(count,silence)}
 private fun parseSV8(){var pos=base+4;var shEnd=0L;var count=0L;var silence=0L;var haveSH=false
 while(true){val b=sv8Header(pos)?:throw malformed("no whole packet at offset $pos before the first audio packet");if(b.key==16720)break;if(b.size>61173)throw malformed("${keyName(b.key)} packet of ${b.size} bytes exceeds the 61173-byte bound");val p=payload(pos,b)
 when(b.key){21320->{val values=parseSH(p);count=values[0];silence=values[1];haveSH=true;shEnd=pos+b.head+b.size};17737->if(p.isNotEmpty()&&haveSH)cfg.pns=p[0].toInt() and 1};pos+=b.head+b.size}
 if(!haveSH)throw malformed("no stream header before the first audio packet");stripTrailers(w,shEnd);val fpb=cfg.framesPerBlock;var se=false
 while(pos<w.dataEnd){val b=sv8Header(pos);if(b==null){warn(pos,"${w.dataEnd-pos} trailing bytes are not a whole packet, dropped");break};if(b.key==21317){se=true;break};if(b.key==16720){if(b.size>83886080)throw malformed("audio block of ${b.size} bytes exceeds the 83886080-byte bound");if(packetCount and ((1 shl stride)-1)==0)positions.add(pos);if(positions.size>65536){var j=0;for(i in positions.indices step 2)positions[j++]=positions[i];while(positions.size>j)positions.removeAt(positions.size-1);stride++};packetCount++;maxPayload=maxOf(maxPayload,b.size)};pos+=b.head+b.size;w.trim(pos)}
 if(!se)warn(w.dataEnd,"the stream has no end marker");delay=481+silence;lastFrames=fpb
 if(count==0L){if(total==0){totalSamples=0;return};val last=total-1;val off=sv8BlockAt(last);val block=sv8Header(off)!!;val p=ByteArray(block.size);val view=ByteBuffer.wrap(p);readFull(off+block.head,view);val r=BitReader();r.reset(p);val st=FrameState();var frames=0
 try{while(r.end-r.pos>=8&&frames<fpb){st.readSV8(r,cfg,frames==0);frames++};if(r.end-r.pos>=8)throw malformed("block has ${r.end-r.pos} bits past its last frame, more than padding");lastFrames=frames}catch(e:MusepackException){warn(w.dataEnd,"the stream states no length and its last block does not parse (${e.message}); reading it as a full block")};totalSamples=maxOf((total-1)*fpb.toLong()*1152+lastFrames*1152-delay,0);return}
 val frames=(count+481+1151)/1152;val blocks=(frames+fpb-1)/fpb;var samples=count-silence
 if(total<blocks){val deliverable=maxOf(total*fpb.toLong()*1152-delay,0);warn(w.dataEnd,"the header declares $samples samples in $blocks blocks but only $total blocks are present, delivering $deliverable");samples=deliverable} else {packetCount=blocks.toInt();if(total>0)lastFrames=(frames-(blocks-1)*fpb).toInt()};totalSamples=samples
 }
 private fun readFull(off:Long,b:ByteBuffer){var at=off;while(b.hasRemaining()){val before=b.position();val n=source.read(at,b);if(n<=0||b.position()-before!=n)throw java.io.IOException("musepack: reading stream data");at+=n}}

 private fun sv7Frame(i:Int):Long {
 var j=i/32*32;var pos=positions[i/32];if(i==cursor){j=i;pos=cursorPos}
 while(true){val n=sv7Bits(pos,20);if(n<0)throw malformed("frame $j is no longer readable");var after=pos+20+n;if(j.toLong()==sv7Frames-1)after+=11
 if(j==i){cursor=i+1;cursorPos=after;packetBits=n;return pos+20};pos=after;j++}
 }
 private fun sv8BlockAt(b:Int):Long {var i=(b ushr stride) shl stride;var off=positions[b ushr stride]
 while(i<b){var blk=sv8Header(off)?:throw malformed("audio block $i is no longer readable");off+=blk.head+blk.size
 while(true){blk=sv8Header(off)?:throw malformed("audio block ${i+1} is no longer readable");if(blk.key==16720)break;off+=blk.head+blk.size};i++};return off}
 fun readPacket():Boolean {if(next>=total)return false
 if(cfg.streamVersion==7){val off=sv7Frame(next);packetFrames=1;val count=(packetBits+7)/8
 for(j in 0 until count){val n=minOf(8,packetBits-j*8);val v=sv7Bits(off+j*8,n);if(v<0)throw malformed("frame $next runs past the end of the stream");packet[j]=(v shl (8-n)).toByte()}}
 else {val off=sv8BlockAt(next);val blk=sv8Header(off);if(blk==null||blk.key!=16720)throw malformed("audio block $next is no longer readable");packetFrames=if(next==total-1)lastFrames else cfg.framesPerBlock;packetBits=blk.size*8;readBuffer.clear();readBuffer.limit(blk.size);readFull(off+blk.head,readBuffer)};next++;return true}
}
