// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/wmapro/wmapro.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.wmapro

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ProException(val unsupported:Boolean,message:String):IOException("wmapro: $message")
internal fun malformed(message:String):Nothing=throw ProException(false,message)
internal fun unsupported(message:String):Nothing=throw ProException(true,message)
internal fun floorLog2(x:Int)=if(x<=0)0 else 31-Integer.numberOfLeadingZeros(x)
internal fun ceilLog2(x:Int)=if(x<=1)0 else 32-Integer.numberOfLeadingZeros(x-1)

class Config(val rate:Int,val channels:Int,val bitsPerSample:Int,val blockAlign:Int,val decodeFlags:Int,val layout:Int=0) {
    val frameLenBits:Int get() {
        var n=when {rate<=16000->9;rate<=22050->10;rate<=48000->11;rate<=96000->12;else->13}
        when((decodeFlags and 6) ushr 1){1->n++;2->n--;3->n-=2}
        return n
    }
    val samplesPerFrame get()=1 shl frameLenBits
    val maxSubframes get()=1 shl ((decodeFlags and 56) ushr 3)
    val minSubframeLen get()=samplesPerFrame/maxSubframes
    val frameSizeBits get()=floorLog2(blockAlign)+4
    val subframeDepth get()=(decodeFlags and 56) ushr 3
    val sizeCount get()=subframeDepth+1
    val hasLengthPrefix get()=decodeFlags and 64!=0
    val hasDRC get()=decodeFlags and 128!=0
    fun validate() {
        when {
            rate<=0||rate>1 shl 27->malformed("sample rate $rate")
            rate>384000->unsupported("sample rate $rate (this build decodes up to 384000)")
            !hasLengthPrefix->unsupported("frames with no length prefix")
            blockAlign<=0->malformed("nBlockAlign $blockAlign")
            bitsPerSample!=16&&bitsPerSample!=24->unsupported("$bitsPerSample bits per sample (this build decodes 16 and 24)")
            channels<1->malformed("$channels channels")
            channels>8->unsupported("$channels channels (this build decodes up to 8)")
        }
        if(frameSizeBits>25)malformed("nBlockAlign $blockAlign gives a $frameSizeBits-bit frame size field, want at most 25")
        if(subframeDepth>5)unsupported("subframe depth $subframeDepth, so ${1 shl subframeDepth} subframes a frame")
        if(frameLenBits<1||samplesPerFrame>8192)unsupported("the frame-length rule gives 2^$frameLenBits samples, want at most 8192")
        if(minSubframeLen<64)malformed("$maxSubframes subframes of a $samplesPerFrame-sample frame leaves $minSubframeLen samples each, want at least 64")
    }
    companion object {
        fun parse(input:ByteBuffer):Config {
            val b=input.slice().order(ByteOrder.LITTLE_ENDIAN)
            fun u16(at:Int)=b.getShort(at).toInt() and 65535
            if(b.remaining()<18)malformed("WAVEFORMATEX of ${b.remaining()} bytes, want at least 18")
            val tag=u16(0)
            if(tag!=0x162)malformed("wFormatTag 0x${tag.toString(16).padStart(4,'0')} is not WMA Pro")
            if(b.remaining()<36)malformed("${b.remaining()-18} codec extra bytes, want at least 18")
            val rate=b.getInt(4).toLong() and 0xffffffffL
            if(rate<=0||rate>1 shl 27)malformed("sample rate $rate")
            val word=u16(34)
            if(word!=0)unsupported("the stream sets the low-bit-rate tool (extra word 0x${word.toString(16).padStart(4,'0')}); no decoder outside Windows implements it")
            val ch=u16(2);val mask=b.getInt(20)
            val layout=if(mask!=0&&mask and ((1 shl 18)-1).inv()==0&&Integer.bitCount(mask)==ch)mask else when(ch){1->4;2->3;3->7;4->0x33;5->0x37;6->0x3f;7->0x70f;8->0x63f;else->0}
            return Config(rate.toInt(),ch,u16(18),u16(12),u16(32),layout).also{it.validate()}
        }
    }
}
