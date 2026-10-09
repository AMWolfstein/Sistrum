// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/wmavoice/wmavoice.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.wmavoice

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VoiceException(val unsupported:Boolean,message:String):IOException("wmavoice: $message")
internal fun malformed(message:String):Nothing=throw VoiceException(false,message)
internal fun unsupported(message:String):Nothing=throw VoiceException(true,message)
internal fun ceilLog2(x:Int)=if(x<=1)0 else 32-Integer.numberOfLeadingZeros(x-1)
internal fun floorLog2(x:Int)=31-Integer.numberOfLeadingZeros(x)
internal fun clip(x:Int,lo:Int,hi:Int)=maxOf(lo,minOf(hi,x))
class Config(val rate:Int,val blockAlign:Int,val flags:Int,val tree:IntArray) {
    val lsps get()=if(flags and 4096!=0)16 else 10
    val postfilter get()=flags and 1!=0
    val denoiseStrength get()=(flags and 60) ushr 2
    val denoiseTilt get()=flags and 64!=0
    val dcLevel get()=(flags and 1920) ushr 7
    val lspQuantiserB get()=flags and 8192!=0
    val meanIndex get()=if(flags and 16384!=0)1 else 0
    internal fun geom()=Geometry(this)
    fun validate(){
        when{rate<=0||rate>1 shl 27->malformed("sample rate $rate")
            rate<322||rate>22097->unsupported("sample rate $rate (this format admits 322 to 22097)")
            blockAlign<=0->malformed("nBlockAlign $blockAlign")
            blockAlign>1 shl 22->unsupported("nBlockAlign $blockAlign (this build decodes up to 4194304)")
            denoiseStrength>=12->unsupported("denoise strength $denoiseStrength (the table has 12 rows)")}
        val g=geom()
        if(g.pitchRange<=0)unsupported("sample rate $rate leaves a pitch range of ${g.pitchRange}")
        if(g.deltaPitchHalf<=0)unsupported("sample rate $rate leaves a delta-pitch half-range of ${g.deltaPitchHalf}, so the per-block pitch field has no width")
        if(g.history>416)unsupported("sample rate $rate needs ${g.history} samples of excitation history, want at most 416")
    }
    companion object {
        fun parse(input:ByteBuffer):Config {
            val b=input.slice().order(ByteOrder.LITTLE_ENDIAN)
            fun u16(at:Int)=b.getShort(at).toInt() and 65535
            if(b.remaining()<18)malformed("WAVEFORMATEX of ${b.remaining()} bytes, want at least 18")
            val tag=u16(0)
            if(tag==11)unsupported("wFormatTag 0x000B is Windows Media Audio Voice 10, which no reference decoder reads")
            if(tag!=10)malformed("wFormatTag 0x${tag.toString(16).padStart(4,'0')} is not WMA Voice")
            val cb=u16(16);if(cb!=46)malformed("cbSize declares $cb codec extra bytes, want exactly 46")
            if(b.remaining()!=64)malformed("${b.remaining()-18} codec extra bytes, want exactly 46")
            val ch=u16(2);if(ch!=1)unsupported("$ch channels; this format is mono")
            val rate=b.getInt(4).toLong() and 0xffffffffL;if(rate<=0||rate>1 shl 27)malformed("sample rate $rate")
            val bytes=ByteArray(24);for(i in bytes.indices)bytes[i]=b.get(40+i)
            val tree=IntArray(24){-1};val count=IntArray(8);val r=BitReader();r.reset(bytes,192)
            for(n in 0 until 17){val cls=r.bits(3);if(count[cls]>=3)unsupported("variable bit mode class $cls holds more than 3 frame types");tree[3*cls+count[cls]]=n;count[cls]++};r.check()
            return Config(rate.toInt(),u16(12),b.getInt(36),tree).also{it.validate()}
        }
    }
}
internal class Geometry(c:Config){
    val minPitch=(((c.rate.toLong() shl 8)/400+50) shr 8).toInt()
    val maxPitch=(((c.rate.toLong() shl 8)*37/2000+50) shr 8).toInt()
    val pitchRange=maxPitch-minPitch;val pitchBits=ceilLog2(pitchRange);val history=maxPitch+8
    val conv=intArrayOf(minPitch,(pitchRange*25) shr 6,(pitchRange*44) shr 6,maxPitch-1)
    val deltaPitchHalf=(pitchRange shr 3) and 15.inv();val deltaPitchBits=1+ceilLog2(deltaPitchHalf)
    val blockPitchRange=conv[2]+conv[3]+1+2*(conv[1]-2*minPitch)
    val blockPitchBits=ceilLog2(blockPitchRange);val spilloverBits=3+ceilLog2(c.blockAlign)
}
