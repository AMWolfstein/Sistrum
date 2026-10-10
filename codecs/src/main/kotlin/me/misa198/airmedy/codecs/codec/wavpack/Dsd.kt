// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from libwavpack 5.8.1 src/unpack_dsd.c,
// commit 4827b9889665b937b6ed71b9c6c0123152cd7a02.
// Copyright (c) 2013-2024 David Bryant. All rights reserved.
// BSD-3-Clause; copyright, conditions and disclaimer in THIRD-PARTY-NOTICES.
package me.misa198.airmedy.codecs.codec.wavpack

import java.nio.ByteBuffer

private const val U32=0xffffffffL
/** Raw, fast byte-range and high bit-range DSD decoding; tables are reserved at open. */
internal class DsdState {
    private lateinit var data:ByteBuffer
    private var at=0;private var end=0;private var mode=0;private var ready=false
    private var bins=0;private var p0=0;private var p1=0
    private var low=0L;private var high=U32;private var value=0L
    private val probabilities=IntArray(32*256)
    private val summed=IntArray(32*256)
    private val lookup=ByteArray(32*1280)
    private val lookupOffsets=IntArray(32)
    private val ptable=IntArray(256)
    private val filters=Array(2) { IntArray(10) }
    fun reset() { ready=false }
    private fun byte():Int { if (at>=end) malformed("truncated DSD bitstream");return u8(data,at++) }
    private fun readValue() { var i=0;value=0;while (i++<4) value=(value shl 8) or byte().toLong() }
    fun readInfo(m:Metadata,h:BlockHeader) {
        if (m.size<2) malformed("truncated DSD metadata")
        data=m.block;at=m.offset;end=at+m.size
        if (byte()>31) malformed("invalid DSD multiplier")
        mode=byte()
        when (mode) {
            0 -> if (end-at!=h.blockSamples*(if (h.mono()) 1 else 2)) malformed("invalid raw DSD block length")
            1 -> initFast()
            3 -> initHigh(h.mono())
            else -> malformed("invalid DSD coding mode $mode")
        }
        ready=true
    }
    private fun initFast() {
        val history=byte();if (history>5) malformed("invalid DSD history bits")
        bins=1 shl history
        val maxProbability=byte();val count=bins*256
        if (maxProbability<255) {
            var out=0
            while (out<count && at<end) {
                val code=byte()
                if (code>maxProbability) {
                    val n=minOf(code-maxProbability,count-out);probabilities.fill(0,out,out+n);out+=n
                } else if (code!=0) probabilities[out++]=code else break
            }
            if (out<count || (at<end && byte()!=0)) malformed("invalid DSD probabilities")
        } else {
            if (end-at<=count) malformed("truncated DSD probability table")
            var i=0;while (i<count) probabilities[i++]=byte()
        }
        var total=0;var bin=0
        while (bin<bins) {
            val base=bin*256;var sum=0;var i=0
            lookupOffsets[bin]=total
            while (i<256) { sum+=probabilities[base+i];summed[base+i]=sum;i++ }
            if (total+sum>lookup.size) malformed("DSD probability table exceeds limit")
            i=0
            while (i<256) { var c=probabilities[base+i];while (c-->0) lookup[total++]=i.toByte();i++ }
            bin++
        }
        readValue();p0=0;p1=0;low=0;high=U32
    }
    private fun initHigh(mono:Boolean) {
        if (end-at<(if (mono) 13 else 20)) malformed("truncated high DSD block")
        val initial=byte();val rateS=byte();if (rateS!=20) malformed("invalid DSD probability rate")
        var v=0x808000;var rate=initial shl 8;var c=(rate+128) shr 8
        while (c-->0) v+=(0x10000-v) shr 8
        var i=0
        while (i<128) {
            ptable[i]=v;ptable[255-i]=0x100ffff-v
            if (v>0x10000) {
                rate+=(rate*rateS+128) shr 8;c=(rate+64) shr 7
                while (c-->0) v+=(0x10000-v) shr 8
            }
            i++
        }
        var ch=0
        while (ch<(if (mono) 1 else 2)) {
            val f=filters[ch++];f.fill(0);i=1
            while (i<=5) { f[i++]=byte() shl 12 }
            f[7]=(byte() or (byte() shl 8)).toShort().toInt()
        }
        low=0;high=U32;readValue()
    }
    private fun renormalize() {
        while ((high xor low) and 0xff000000L==0L && at<end) {
            value=((value shl 8) or byte().toLong()) and U32
            high=((high shl 8) or 255) and U32;low=(low shl 8) and U32
        }
    }
    fun decode(h:BlockHeader,out:IntArray):Int {
        if (!ready) malformed("block has no DSD bitstream")
        val mono=h.mono();val span=h.blockSamples*(if (mono) 1 else 2)
        var crc= -1;var i=0
        if (mode==0) {
            while (i<span) { val b=byte();out[i++]=b;crc=crcMono(crc,b) }
        } else if (mode==1) {
            while (i<span) {
                val base=p0*256;val sum=summed[base+255]
                if (sum==0) malformed("empty DSD history bin")
                var mult=((high-low) and U32)/sum
                if (mult==0L) {
                    if (end-at>=4) readValue()
                    low=0;high=U32;mult=high/sum
                    if (mult==0L) malformed("invalid DSD range")
                }
                val index=((value-low) and U32)/mult
                if (index>=sum) malformed("invalid DSD range index")
                val code=lookup[lookupOffsets[p0]+index.toInt()].toInt() and 255
                if (code!=0) low=(low+summed[base+code-1]*mult) and U32
                high=(low+probabilities[base+code]*mult-1) and U32
                out[i++]=code;crc=crcMono(crc,code)
                if (mono) p0=code and (bins-1) else { p0=p1;p1=code and (bins-1) }
                renormalize()
            }
        } else {
            val channels=if (mono) 1 else 2;var frame=0
            while (frame<h.blockSamples) {
                var ch=0
                while (ch<channels) { val f=filters[ch++];f[8]=f[1]-f[5]+((f[6]*f[7]) shr 2) }
                var bits=0
                while (bits++<8) {
                    ch=0
                    while (ch<channels) {
                        val f=filters[ch++];val p=(f[8] shr 8) and 255
                        val split=(low+(((high-low) and U32) ushr 8)*(ptable[p] shr 16)) and U32
                        if (value<=split) { high=split;ptable[p]+=(0x010000fe-ptable[p]) shr 8;f[0]= -1 }
                        else { low=(split+1) and U32;ptable[p]+=(0x10000-ptable[p]) shr 8;f[0]=0 }
                        renormalize()
                        f[8]+=f[6]*8;f[9]=(f[9] shl 1) or (f[0] and 1)
                        f[7]+=(((f[8] xor f[0]) shr 31) or 1) and ((f[8] xor (f[8]-f[6]*16)) shr 31)
                        f[1]+=((f[0] and 0x100000)-f[1]) shr 6
                        f[2]+=((f[0] and 0x100000)-f[2]) shr 4
                        f[3]+=(f[2]-f[3]) shr 4;f[4]+=(f[3]-f[4]) shr 4
                        f[8]=(f[4]-f[5]) shr 4;f[5]+=f[8];f[6]+=(f[8]-f[6]) shr 3
                        f[8]=f[1]-f[5]+((f[6]*f[7]) shr 2)
                    }
                }
                ch=0
                while (ch<channels) { val f=filters[ch++];val b=f[9] and 255;out[i++]=b;crc=crcMono(crc,b);f[7]-=(f[7]+512) shr 10 }
                frame++
            }
        }
        if (crc!=h.crc) malformed("block at sample ${h.blockIndex} fails its DSD CRC")
        if (h.flags and FALSE_STEREO!=0) { i=h.blockSamples-1;while (i>=0) { val b=out[i];out[i*2]=b;out[i*2+1]=b;i-- } }
        return h.blockSamples
    }
}
