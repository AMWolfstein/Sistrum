// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/wma/wma.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.wma

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

class WmaException(val unsupported:Boolean,message:String):IOException("wma: $message")
internal fun malformed(s:String):Nothing=throw WmaException(false,s)
internal fun unsupported(s:String):Nothing=throw WmaException(true,s)
internal fun bitsOf(value:Int):Int {var n=0;var v=value;while(v>1){v=v ushr 1;n++};return n}
class Config(val v2:Boolean,val rate:Int,val channels:Int,val bitRate:Int,val blockAlign:Int,var flags2:Int) {
 val expVLC get()=flags2 and 1!=0
 val reservoir get()=flags2 and 2!=0
 val varBlockLen get()=flags2 and 4!=0
 val frameLenBits get()=when{rate<=16000->9;rate<=22050||rate<=32000&&!v2->10;else->11}
 val frameLen get()=1 shl frameLenBits
 fun validate(){when{channels<1->malformed("$channels channels");channels>2->unsupported("$channels channels: Windows Media Audio 1 and 2 are mono or stereo")
 rate<=0->malformed("sample rate $rate");rate>50000->unsupported("sample rate $rate above the 50000 Hz this decoder covers")
 bitRate<=0->malformed("nAvgBytesPerSec is ${bitRate/8}; the frame layout is derived from it");blockAlign<=0->malformed("nBlockAlign is $blockAlign; it is the superframe size")
 !v2&&varBlockLen->unsupported("Windows Media Audio 1 with variable block lengths: no encoder writes it and no layout is defined for it")
 !v2&&channels==2&&reservoir->unsupported("stereo Windows Media Audio 1 with a bit reservoir: its per-channel byte alignment has no defined meaning once a frame can move")};offsetBits()}
 fun bps()=bitRate.toDouble()/(channels*rate)
 fun bps1()=bps()*(if(channels==2)1.6 else 1.0)
 fun offsetBits():Int {val v=maxOf(1,(bps()*frameLen/8+0.5).toInt());val n=bitsOf(v)+2;if(n+3>24)malformed("bit rate $bitRate needs a ${n+3}-bit superframe offset field");return n}
 fun nbBlockSizes():Int {if(!varBlockLen)return 1;var n=((flags2 and 0x18) ushr 3)+1;if(bitRate/channels>=32000)n+=2;return minOf(n,frameLenBits-7)+1}
 fun coefBookPair():Int {if(rate>=32000){if(bps1()<0.72)return 0;if(bps1()<1.16)return 1};return 2}
 fun normRate():Int {if(!v2)return rate;for(r in intArrayOf(44100,22050,16000,11025,8000))if(rate>=r)return r;return rate}
 fun highFreqMult():Double {val b=bps();val b1=bps1();return when(normRate()){
 44100->if(b1>=0.61)1.0 else 0.4
 22050->if(b1>=1.16)1.0 else if(b1>=0.72)0.7 else 0.6
 16000->if(b>0.5)0.5 else 0.3
 11025->0.7
 8000->if(b<=0.625)0.5 else if(b>0.75)1.0 else 0.65
 else->if(b>=0.8)0.75 else if(b>=0.6)0.6 else 0.5}}
 fun coefsStart()=if(v2)0 else 3
 fun coefsEnd(k:Int)=(frameLen-frameLen*9/100) ushr k
 fun noiseMult()=if(expVLC)0.02 else 0.04
 companion object {
  fun parse(bytes:ByteBuffer):Config {val b=bytes.slice().order(ByteOrder.LITTLE_ENDIAN);if(b.remaining()<18)malformed("codec config of ${b.remaining()} bytes, want at least the 18-byte WAVEFORMATEX")
   val tag=b.getShort(0).toInt() and 65535;val rate=b.getInt(4).toLong() and 0xffffffffL;val byteRate=b.getInt(8).toLong() and 0xffffffffL
   if(rate>1 shl 27)malformed("WAVEFORMATEX states $rate Hz, which is not a rate a file has");if(byteRate>1 shl 27)malformed("WAVEFORMATEX states $byteRate bytes/s, which is not a rate a file has")
   if(tag!=0x160&&tag!=0x161)unsupported("audio format 0x${tag.toString(16).padStart(4,'0')} is not Windows Media Audio 1 or 2")
   val v2=tag==0x161;val extra=minOf(b.remaining()-18,b.getShort(16).toInt() and 65535);val at=if(v2)4 else 2;var flags=if(extra>=at+2)b.getShort(18+at).toInt() and 65535 else 0
   if(v2&&flags==0x000d)flags=flags and 4.inv()
   return Config(v2,rate.toInt(),b.getShort(2).toInt() and 65535,byteRate.toInt()*8,b.getShort(12).toInt() and 65535,flags).also{it.validate()}
  }
 }
}
internal fun mdctNorm(blockLen:Int,v2:Boolean):Double {val n4=blockLen/2.0;var norm=1/n4;if(!v2)norm*=sqrt(n4);return norm}
