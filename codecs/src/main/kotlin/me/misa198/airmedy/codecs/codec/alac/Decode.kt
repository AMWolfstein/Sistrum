// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/alac/decode.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.alac

import java.nio.ByteBuffer
import me.misa198.airmedy.codecs.audio.Buffer

/** Independent ALAC access-unit decoder. Initialization is the canonical Media3 ALAC cookie. */
class Decoder(val config: Config) {
    private val ac=config
    private val buffer=Buffer(ac.channels,ac.bitDepth,ac.frameLength)
    private val predictor=IntArray(ac.frameLength); private val mixU=IntArray(ac.frameLength); private val mixV=IntArray(ac.frameLength)
    private val shiftBuf=IntArray(2*ac.frameLength); private val coefsU=ShortArray(32); private val coefsV=ShortArray(32)
    private val reader=BitReader(); private val shiftReader=BitReader()
    private var numSamples=0; private var bytesShifted=0; private var escape=0; private var chanBits=0
    fun decode(packet: ByteBuffer): Buffer {
        val r=reader; r.reset(packet); var channelIndex=0; var frameSamples=0
        while (channelIndex<ac.channels) {
            if (r.overrun()) malformed("packet ends mid-frame")
            when (val tag=r.read(3)) {
                0,3 -> { decodeMono(r,channelIndex); frameSamples=numSamples; channelIndex++ }
                1 -> { if (channelIndex+2>ac.channels) { channelIndex=ac.channels; continue }; decodeStereo(r,channelIndex); frameSamples=numSamples; channelIndex+=2 }
                4 -> skipDse(r)
                6 -> { var count=r.read(4); if (count==15) count+=r.read(8)-1; r.pos+=count*8 }
                7 -> { r.byteAlign(); channelIndex=ac.channels }
                else -> unsupported("unsupported element type $tag")
            }
        }
        if (r.overrun()) malformed("frame overruns packet")
        buffer.frames=frameSamples; return buffer
    }
    private fun elementHeader(r: BitReader,sideBit: Int) {
        r.read(4); if (r.read(12)!=0) malformed("element header reserved bits set")
        val hdr=r.read(4); val partial=hdr ushr 3; bytesShifted=(hdr ushr 1) and 3
        if (bytesShifted==3) malformed("invalid shift-off value")
        escape=hdr and 1; chanBits=ac.bitDepth-bytesShifted*8+sideBit; numSamples=ac.frameLength
        if (partial!=0) numSamples=(r.read(16) shl 16) or r.read(16)
        if (numSamples<=0 || numSamples>ac.frameLength) malformed("partial frame length $numSamples outside 1..${ac.frameLength}")
    }
    private fun decodeMono(r: BitReader,ci: Int) {
        elementHeader(r,0); val shift=bytesShifted*8
        if (escape==0) {
            r.read(8); r.read(8); var hb=r.read(8); val mode=hb ushr 4; val den=hb and 15
            hb=r.read(8); val pb=hb ushr 5; val num=hb and 31
            var i=0; while (i<num) { coefsU[i]=r.read(16).toShort(); i++ }
            val start=r.pos; if (bytesShifted!=0) r.pos+=shift*numSamples
            dynDecomp(r,chanBits,ac.pb*pb/4); predict(mode,num,chanBits,den,coefsU,mixU)
            if (bytesShifted!=0) readShift(r,start,shift,1)
        } else { readUncompressed(r,chanBits,1); bytesShifted=0 }
        var i=0
        while (i<numSamples) { var v=mixU[i]; if (bytesShifted!=0) v=(v shl shift) or shiftBuf[i]; buffer.samples[i*ac.channels+ci]=v; i++ }
    }
    private fun decodeStereo(r: BitReader,ci: Int) {
        elementHeader(r,1); val shift=bytesShifted*8; var mixBits=0; var mixRes=0
        if (escape==0) {
            mixBits=r.read(8); mixRes=r.read(8).toByte().toInt()
            if (mixRes!=0 && mixBits>=32) malformed("mix shift $mixBits out of range")
            var hb=r.read(8); val modeU=hb ushr 4; val denU=hb and 15
            hb=r.read(8); val pbU=hb ushr 5; val numU=hb and 31
            var i=0; while (i<numU) { coefsU[i]=r.read(16).toShort(); i++ }
            hb=r.read(8); val modeV=hb ushr 4; val denV=hb and 15
            hb=r.read(8); val pbV=hb ushr 5; val numV=hb and 31
            i=0; while (i<numV) { coefsV[i]=r.read(16).toShort(); i++ }
            val start=r.pos; if (bytesShifted!=0) r.pos+=shift*2*numSamples
            dynDecomp(r,chanBits,ac.pb*pbU/4); predict(modeU,numU,chanBits,denU,coefsU,mixU)
            dynDecomp(r,chanBits,ac.pb*pbV/4); predict(modeV,numV,chanBits,denV,coefsV,mixV)
            if (bytesShifted!=0) readShift(r,start,shift,2)
        } else { chanBits=ac.bitDepth; readUncompressed(r,chanBits,2); bytesShifted=0 }
        var i=0
        while (i<numSamples) {
            var left: Int; var right: Int
            if (mixRes!=0) { left=mixU[i]+mixV[i]-((mixRes*mixV[i]) shr mixBits); right=left-mixV[i] } else { left=mixU[i]; right=mixV[i] }
            if (bytesShifted!=0) { left=(left shl shift) or shiftBuf[2*i]; right=(right shl shift) or shiftBuf[2*i+1] }
            buffer.samples[i*ac.channels+ci]=left; buffer.samples[i*ac.channels+ci+1]=right; i++
        }
    }
    private fun predict(mode: Int,num: Int,bits: Int,den: Int,coefs: ShortArray,out: IntArray) {
        if (mode!=0) unpcBlock(predictor,predictor,numSamples,coefs,31,bits,0)
        unpcBlock(predictor,out,numSamples,coefs,num,bits,den)
    }
    private fun readUncompressed(r: BitReader,bits: Int,count: Int) {
        val shift=32-bits; var i=0
        while (i<numSamples) { mixU[i]=(r.read(bits) shl shift) shr shift; if (count==2) mixV[i]=(r.read(bits) shl shift) shr shift; i++ }
    }
    private fun readShift(r: BitReader,start: Int,shift: Int,count: Int) {
        shiftReader.reset(r.data,start); var i=0
        while (i<numSamples*count) { shiftBuf[i]=shiftReader.read(shift) and 65535; i++ }
    }
    private fun dynDecomp(r: BitReader,bits: Int,pb: Int) {
        var mb=ac.mb; val kb=ac.kb; val wb=if (kb>=32) -1 else (1 shl kb)-1
        var zmode=0; var c=0
        while (c<numSamples) {
            if (r.pos>r.validBits) malformed("adaptive-Golomb data overruns packet")
            val mean=mb ushr 9; var k=31-Integer.numberOfLeadingZeros(mean+3); if (k>kb) k=kb
            val m=if (k>=32) -1 else (1 shl k)-1; var n=r.dynGet32(m,k,bits)
            val decoded=n+zmode; var delta=(decoded+1) ushr 1; if (decoded and 1!=0) delta=-delta
            predictor[c++]=delta
            mb=pb*(n+zmode)+mb-((pb*mb) ushr 9); if (uint(n)>65535) mb=65535
            zmode=0
            if (uint(mb shl 2)<512 && c<numSamples) {
                zmode=1; k=Integer.numberOfLeadingZeros(mb)-24+((mb+16) ushr 6)
                val mz=(if (k>=32) -1 else (1 shl k)-1) and wb
                n=r.dynGet16(mz,k)
                if (c.toLong()+uint(n)>numSamples) malformed("zero run overruns frame")
                var j=0; while (j<n) { predictor[c++]=0; j++ }
                if (uint(n)>=65535) zmode=0
                mb=0
            }
        }
    }
    private fun skipDse(r: BitReader) {
        r.read(4); val align=r.read(1); var count=r.read(8); if (count==255) count+=r.read(8)
        if (align!=0) r.byteAlign(); r.pos+=count*8
    }
}
internal fun signOf(v: Int)=if (v>0) 1 else if (v<0) -1 else 0
internal fun unpcBlock(pc: IntArray,out: IntArray,num: Int,coefs: ShortArray,active: Int,bits: Int,den: Int) {
    if (num<=0) return
    val shift=maxOf(0,32-bits); val half=if (den>0) 1 shl (den-1) else 0
    out[0]=pc[0]
    if (active==0) { System.arraycopy(pc,1,out,1,num-1); return }
    if (active==31) { var prev=out[0]; var j=1; while (j<num) { prev=((pc[j]+prev) shl shift) shr shift; out[j]=prev; j++ }; return }
    var j=1
    while (j<=minOf(active,num-1)) { out[j]=((pc[j]+out[j-1]) shl shift) shr shift; j++ }
    val limit=active+1; j=limit
    while (j<num) {
        val top=out[j-limit]; var sum=0; var k=0
        while (k<active) { sum+=coefs[k].toInt()*(out[j-1-k]-top); k++ }
        var delta=pc[j]; var original=delta; val sg=signOf(delta)
        delta+=top+((sum+half) shr den); out[j]=(delta shl shift) shr shift
        if (sg>0) { k=active-1; while (k>=0) { val dd=top-out[j-1-k]; val sign=signOf(dd); coefs[k]=(coefs[k]-sign).toShort(); original-=(active-k)*((sign*dd) shr den); if (original<=0) break; k-- } }
        else if (sg<0) { k=active-1; while (k>=0) { val dd=top-out[j-1-k]; val sign=signOf(dd); coefs[k]=(coefs[k]+sign).toShort(); original-=(active-k)*((-sign*dd) shr den); if (original>=0) break; k-- } }
        j++
    }
}
