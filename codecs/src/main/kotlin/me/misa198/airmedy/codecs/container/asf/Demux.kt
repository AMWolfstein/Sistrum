// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow container/asf/asf.go, demux.go, codecs.go and container/internal/codecname/codecname.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.container.asf

import java.io.IOException
import java.nio.ByteBuffer
import me.misa198.airmedy.codecs.container.RandomAccessSource
import me.misa198.airmedy.codecs.codec.wma.Config
import me.misa198.airmedy.codecs.codec.wma.WmaException

class AsfException(val unsupported:Boolean,message:String):IOException("wma: $message")
private fun bad(s:String):Nothing=throw AsfException(false,s)
private fun unsupported(s:String):Nothing=throw AsfException(true,s)
internal fun u16(b:ByteArray,p:Int)=(b[p].toInt() and 255) or ((b[p+1].toInt() and 255) shl 8)
internal fun u32(b:ByteArray,p:Int):Long {var v=0L;for(i in 0..3)v=v or ((b[p+i].toLong() and 255) shl (i*8));return v}
internal fun u64(b:ByteArray,p:Int):Long=u32(b,p) or (u32(b,p+4) shl 32)
private fun unsigned(v:Long)=java.lang.Long.toUnsignedString(v)
internal fun msToSamples(ms:Long,rate:Int):Long=if(ms<=0||rate<=0)0 else ms/1000*rate+(ms%1000*rate+500)/1000

data class AsfFormat(val rate:Int,val channels:Int,val bits:Int,val floating:Boolean)
/** One borrowed reassembled media object. */
class MediaObject internal constructor(){val data=ByteArray(1 shl 22);var size=0;var pts=0L;var duration=0L;var gap=false}
private class Stream(val number:Int,val type:String,val encrypted:Boolean,val config:ByteArray?)
private class Payload {var stream=0;var number=0L;var offset=0L;var size=0L;var ms=0L;var at=0;var length=0}
private class Cursor {
 lateinit var b:ByteArray;var p=0;var end=0;var ok=true
 fun reset(bytes:ByteArray,from:Int=0,limit:Int=bytes.size){b=bytes;p=from;end=limit;ok=true}
 fun take(n:Int):Int{if(n<0||!ok||n>end-p){ok=false;return -1};val at=p;p+=n;return at}
 fun u8():Int{val at=take(1);return if(at<0)0 else b[at].toInt() and 255}
 fun u16():Int{val at=take(2);return if(at<0)0 else u16(b,at)}
 fun u32():Long{val at=take(4);return if(at<0)0 else u32(b,at)}
 fun vlen(type:Int):Long=when(type){1->u8().toLong();2->u16().toLong();3->u32();else->0L}
}
private class PacketInfo {var start=0;var end=0;var sendMS=0L;var multi=false;var count=1;var lengthType=0;var repType=0;var offsetType=0;var numberType=0}

class Demux(val source:RandomAccessSource,private val strict:Boolean=false,private val resolver:(Int,ByteArray)->AsfFormat=::resolveWma) {
 val warnings=ArrayList<String>();var format:AsfFormat=AsfFormat(0,0,0,false);private set
 lateinit var codecConfig:ByteArray;private set
 var tag=0;private set
 var totalSamples= -1L;private set
 val samplesExact=false
 private val streams=ArrayList<Stream>();private var selected=0;private var encrypted=false
 private var prerollMS=0L;private var playHNS=0L;private var declaredPackets=0L
 private var packetLen=0;private var dataOff=0L;private var packets=0L;private var cursor=0L
 private lateinit var packet:ByteBuffer
 private val payloads=Array(4096){Payload()};private var payCount=0;private var payIdx=0
 private val reader=Cursor();private val info=PacketInfo()
 private val assembly=ByteArray(1 shl 22);private var asmNumber=0L;private var asmSize=0L;private var asmMS=0L;private var asmHave=0;private var asmOpen=false
 private var gap=false;private var cur=MediaObject();private var nxt=MediaObject();private var haveCur=false;private var haveNext=false;private var filled=false;private var emitted=false
 private var lastDur=0L;private var lastPTS=0L;private var error:IOException?=null
 init {parse();packet=ByteBuffer.allocate(packetLen)}
 private fun warn(off:Long,s:String){if(strict)bad("$s (at offset $off)");if(warnings.size<64&&!warnings.contains(s))warnings.add(s)}
 private fun read(off:Long,n:Int):ByteArray {if(n<0||n>16 shl 20)bad("object of $n bytes exceeds the 16777216 cap");val b=ByteBuffer.allocate(n);readFull(off,b,"wma: reading the header");return b.array()}
 private fun readFull(off:Long,b:ByteBuffer,message:String){var pos=off;try{while(b.hasRemaining()){val before=b.position();val got=source.read(pos,b);if(got<=0||b.position()-before!=got)throw IOException("unexpected EOF");pos+=got}}catch(e:IOException){throw IOException("$message: ${e.message}",e)}}
 private fun parse(){if(source.length<30)bad("not an ASF file");val head=read(0,30);if(guidAt(head,0)!=guidHeader)bad("not an ASF file");val size=u64(head,16)
 if(size<30||size>source.length)bad("header object of ${unsigned(size)} bytes in a ${source.length}-byte file")
 if(head[28].toInt()!=1||head[29].toInt()!=2)warn(28,"header object reserved bytes are ${head[28].toInt() and 255} and ${head[29].toInt() and 255}, not 1 and 2")
 if(size-30>16 shl 20)bad("object of ${size-30} bytes exceeds the 16777216 cap");walkHeader(read(30,(size-30).toInt()));selectStream();parseData(size)}
 private fun walkHeader(b:ByteArray){var at=0
 while(at+24<=b.size){val id=guidAt(b,at);val size=u64(b,at+16);if(size<24||size>b.size-at){warn(30L+at,"header object ${guidLabel(id)} declares ${unsigned(size)} bytes with ${b.size-at} left");return};val p=at+24;val n=size.toInt()-24;val off=30L+p
 when(id){guidFileProperties->parseFileProperties(b,p,n,off);guidStreamProperties->parseStreamProperties(b,p,n,off);guidContentEncryption,guidExtendedContentEncryption->encrypted=true;guidHeaderExtension->walkExtension(b,p,n)};at+=size.toInt()}}
 private fun parseFileProperties(b:ByteArray,p:Int,n:Int,off:Long){if(n<80){warn(off,"File Properties Object is $n bytes, not 80");return};playHNS=u64(b,p+40);val pre=u64(b,p+56)
 if(pre>=0&&pre<=60000)prerollMS=pre else warn(off+56,"pre-roll of ${unsigned(pre)} ms is implausible and was ignored")
 declaredPackets=u64(b,p+32);val min=u32(b,p+68);val max=u32(b,p+72)
 if(min!=max)unsupported("variable-size data packets ($min to $max bytes) are not a file this build reads");if(min==0L||min>1 shl 20)bad("data packet size of $min bytes");packetLen=min.toInt()}
 private fun parseStreamProperties(b:ByteArray,p:Int,n:Int,off:Long){if(n<54){warn(off,"Stream Properties Object is $n bytes, not 54");return};if(streams.size>=128){warn(off,"more than 128 streams");return}
 val flags=u16(b,p+48);val number=flags and 127;val type=guidAt(b,p);var ts=u32(b,p+40);val avail=n-54
 if(ts>avail){warn(off+40,"stream $number declares $ts bytes of type-specific data with $avail present");ts=avail.toLong()}
 var config:ByteArray?=null;if(type==guidAudioMedia){if(ts>=18){val len=18+minOf(u16(b,p+54+16),ts.toInt()-18);config=b.copyOfRange(p+54,p+54+len)}
 val ec=guidAt(b,p+16);if(ec!=guidNoErrorCorrection&&ec!="00000000000000000000000000000000"){
 val ecLen=u32(b,p+44);val ecAt=p+54+ts.toInt();if(ec==guidAudioSpread){if(ecLen<1||ecAt>=p+n)warn(off+44,"stream $number declares Audio Spread with $ecLen bytes of correction data")else{val span=b[ecAt].toInt() and 255;if(span>1)unsupported("stream $number interleaves its payload across $span packets (ASF Audio Spread), which this build does not undo")}}
 else if(warnings.size<64)warnings.add("stream $number uses error correction ${guidLabel(ec)}, which this build does not interpret")}}
 streams.add(Stream(number,type,flags and 0x8000!=0,config))}
 private fun walkExtension(b:ByteArray,p:Int,n:Int){if(n<22)return;val end=p+22+minOf(u32(b,p+18),(n-22).toLong()).toInt();var at=p+22
 while(at+24<=end){val size=u64(b,at+16);if(size<24||size>end-at)return;if(guidAt(b,at)==guidAdvancedContentEncryption){encrypted=true;return};at+=size.toInt()}}
 private fun selectStream(){if(packetLen==0)bad("no File Properties Object");if(streams.isEmpty())bad("no Stream Properties Object");val audio=streams.filter{it.type==guidAudioMedia}
 if(audio.isEmpty())unsupported("no audio stream (the file carries ${streams.map{typeName(it.type)}.distinct().joinToString(", ")})")
 if(encrypted)unsupported("the file is encrypted (DRM), which this build cannot read");var named=""
 for(s in audio){val c=s.config?:continue;val t=u16(c,0);val name=codecName(t)
 if(t !in intArrayOf(0x160,0x161,0x162,0x163,0x0a)){if(name.isNotEmpty()&&named.isEmpty())named=name;continue}
 if(s.encrypted)unsupported("$name stream ${s.number} is encrypted (DRM), which this build cannot read")
 format=resolver(t,c);tag=t;codecConfig=c;selected=s.number;totalSamples=declaredSamples(format.rate)
 if(streams.size>1&&warnings.size<64)warnings.add("the file carries ${streams.size} streams; $name stream ${s.number} was selected");return}
 if(named.isNotEmpty())unsupported("$named is not a codec this build decodes");val s=audio[0];val c=s.config?:bad("audio stream ${s.number} carries no WAVEFORMATEX");val t=u16(c,0);val name=waveName(t);val hex="0x"+t.toString(16).padStart(4,'0')
 if(name.isNotEmpty())unsupported("this build does not read $name (audio format $hex) from an ASF");unsupported("this build does not read audio format $hex from an ASF")}
 private fun declaredSamples(rate:Int):Long{if(playHNS<=0)return -1;val dur=playHNS-prerollMS*10000;if(dur<0)return -1;val sec=dur/10000000;val rem=dur%10000000;if(sec>(Long.MAX_VALUE-rate)/rate)return -1;return sec*rate+(rem*rate+5000000)/10000000}
 private fun parseData(off:Long){if(source.length-off<50)bad("no Data Object after the header");val b=read(off,50);val id=guidAt(b,0);if(id!=guidData)bad("no Data Object after the header (found ${guidLabel(id)})");dataOff=off+50;var end=source.length;val size=u64(b,16)
 if(size>=50&&size<=source.length-off)end=off+size else if(size!=0L)warn(off+16,"Data Object declares ${unsigned(size)} bytes with ${source.length-off} in the file")
 val avail=end-dataOff;if(avail>0)packets=avail/packetLen;var want=u64(b,40);if(want==0L)want=declaredPackets;if(want!=0L&&want!=packets)warn(off+40,"Data Object declares ${unsigned(want)} packets and holds $packets")}
 fun next():MediaObject?{error?.let{throw it};try{if(emitted){val old=cur;cur=nxt;nxt=old;haveCur=haveNext;haveNext=false;emitted=false;if(!haveCur)return null;haveNext=fetch(nxt)}else if(!filled){filled=true;haveCur=fetch(cur);if(!haveCur)return null;haveNext=fetch(nxt)}
 if(!haveCur)return null;val duration=if(haveNext)maxOf(1,nxt.pts-cur.pts)else if(lastDur>0)lastDur else if(totalSamples>cur.pts)minOf(totalSamples-cur.pts,1L shl 20)else 1
 cur.duration=duration;lastDur=duration;emitted=true;return cur
 }catch(e:IOException){error=e;throw e}}
 private fun fetch(dst:MediaObject):Boolean{while(true){if(payIdx<payCount){val p=payloads[payIdx++];if(!absorb(p,dst))continue;if(dst.pts<lastPTS){warn(packetOff(cursor-1),"presentation times run backwards");dst.pts=lastPTS};lastPTS=dst.pts;dst.gap=gap;gap=false;return true}
 if(cursor>=packets){dropOpen(packetOff(cursor-1));return false};val index=cursor++;loadPacket(index)}}
 private fun packetOff(index:Long)=if(index<0)dataOff else dataOff+index*packetLen
 private fun copy(dst:MediaObject,b:ByteArray,at:Int,n:Int,ms:Long){System.arraycopy(b,at,dst.data,0,n);dst.size=n;dst.pts=msToSamples(ms-prerollMS,format.rate)}
 private fun absorb(p:Payload,dst:MediaObject):Boolean{if(p.stream!=selected)return false;val off=packetOff(cursor-1)
 if(p.size==0L||p.size>1 shl 22){dropAssembly();gap=true;warn(off,"media object of ${p.size} bytes");return false}
 if(p.offset==0L&&p.size==p.length.toLong()){dropOpen(off);copy(dst,packet.array(),p.at,p.length,p.ms);return true}
 if(p.offset==0L){dropOpen(off);if(p.length>assembly.size){gap=true;warn(off,"media object ${p.number} overruns its declared ${p.size} bytes");return false};System.arraycopy(packet.array(),p.at,assembly,0,p.length);asmNumber=p.number;asmSize=p.size;asmMS=p.ms;asmHave=p.length;asmOpen=true}
 else{if(!asmOpen||p.number!=asmNumber||p.size!=asmSize||p.offset!=asmHave.toLong()){dropAssembly();gap=true;warn(off,"media object ${p.number} fragment at offset ${p.offset} does not continue the one in hand");return false};if(p.length>assembly.size-asmHave){dropAssembly();gap=true;warn(off,"media object ${p.number} overruns its declared ${p.size} bytes");return false};System.arraycopy(packet.array(),p.at,assembly,asmHave,p.length);asmHave+=p.length}
 if(asmHave>asmSize){dropAssembly();gap=true;warn(off,"media object ${p.number} overruns its declared ${p.size} bytes");return false};if(asmHave<asmSize)return false;copy(dst,assembly,0,asmHave,asmMS);dropAssembly();return true}
 private fun dropOpen(off:Long){if(!asmOpen)return;val missing=asmSize-asmHave;dropAssembly();gap=true;warn(off,"media object $asmNumber is missing $missing bytes and was dropped")}
 private fun dropAssembly(){asmOpen=false;asmHave=0}
 private fun prologue(b:ByteArray,total:Int):Boolean{if(b.size<2)return false;var p=0;val first=b[0].toInt() and 255;if(first and 128!=0){if(first and 16!=0||((first ushr 5) and 3)!=0)return false;p=1+(first and 15)}
 val r=reader;r.reset(b,p,total);val lenFlags=r.u8();val prop=r.u8();val pktLen=r.vlen((lenFlags ushr 5) and 3);r.vlen((lenFlags ushr 1) and 3);val pad=r.vlen((lenFlags ushr 3) and 3);info.sendMS=r.u32();r.u16();info.multi=lenFlags and 1!=0;info.count=1;info.repType=prop and 3;info.offsetType=(prop ushr 2) and 3;info.numberType=(prop ushr 4) and 3
 if((prop ushr 6) and 3!=1)return false;if(info.multi){val pf=r.u8();info.count=pf and 63;info.lengthType=(pf ushr 6) and 3};if(!r.ok)return false;val used=if(pktLen!=0L&&pktLen<total)pktLen else total.toLong();val end=used-pad;if(end<r.p)return false;info.start=r.p;info.end=end.toInt();return true}
 private fun loadPacket(index:Long){payCount=0;payIdx=0;val off=packetOff(index);packet.clear();readFull(off,packet,"wma: reading packet data");val b=packet.array()
 if(!prologue(b,packetLen)||info.end>b.size){warn(off,"data packet $index has an unreadable header");return};val r=reader;r.reset(b,info.start,info.end)
 var k=0;while(k<info.count&&r.p<info.end&&payCount<4096){val sn=r.u8();val number=r.vlen(info.numberType);val offset=r.vlen(info.offsetType);val repLen=r.vlen(info.repType).toInt();val rep=r.take(repLen);val n=if(info.multi)r.vlen(info.lengthType).toInt()else info.end-r.p;val data=r.take(n)
 if(!r.ok){warn(off,"data packet $index payload $k runs past the packet");return};addPayload(off,sn and 127,number,offset,rep,repLen,data,n);k++}}
 private fun addPayload(off:Long,sn:Int,number:Long,offset:Long,rep:Int,repLen:Int,data:Int,length:Int){val b=packet.array()
 when{repLen==1->{val delta=b[rep].toInt() and 255;var at=data;val end=data+length;var j=0;while(at<end){if(payCount>=4096){warn(off,"compressed payload holds more than 4096 sub-objects");return};val n=b[at++].toInt() and 255;if(n==0||n>end-at){warn(off,"compressed payload sub-object of $n bytes with ${end-at} left");return};val p=payloads[payCount++];p.stream=sn;p.number=0;p.offset=0;p.size=n.toLong();p.ms=offset+j.toLong()*delta;p.at=at;p.length=n;at+=n;j++}}
 repLen>=8->{val p=payloads[payCount++];p.stream=sn;p.number=number;p.offset=offset;p.size=u32(b,rep);p.ms=u32(b,rep+4);p.at=data;p.length=length}
 else->warn(off,"payload carries $repLen bytes of replicated data")}}
 fun rewind(){cursor=0;payCount=0;payIdx=0;dropAssembly();haveCur=false;haveNext=false;filled=false;emitted=false;gap=false;lastPTS=0;error=null}
}
private fun typeName(g:String)=when(g){guidAudioMedia->"audio";guidVideoMedia->"video";guidCommandMedia->"command";guidBinaryMedia->"binary";else->"stream type "+guidLabel(g)}
private fun codecName(tag:Int)=when(tag){0x160->"Windows Media Audio 1";0x161->"Windows Media Audio 2";0x162->"Windows Media Audio Pro";0x163->"Windows Media Audio Lossless";0x164->"Windows Media Audio Pro over S/PDIF";0xa->"Windows Media Audio Voice";0xb->"Windows Media Audio Voice 10";else->""}
private fun waveName(tag:Int)=when(tag){1->"PCM";2->"ADPCM";3->"IEEE float";6->"A-law";7->"mu-law";0xa->"WMA Voice";0x11->"IMA ADPCM";0x50->"MP2";0x55->"MP3";0xff->"AAC";0x160->"WMA v1";0x161->"WMA v2";0x162->"WMA Pro";0x163->"WMA Lossless";0xfffe->"PCM (extensible)";else->""}
private fun resolveWma(tag:Int,bytes:ByteArray):AsfFormat {try{val c=Config.parse(ByteBuffer.wrap(bytes));return AsfFormat(c.rate,c.channels,32,true)}catch(e:WmaException){throw AsfException(e.unsupported,e.message!!)}}
