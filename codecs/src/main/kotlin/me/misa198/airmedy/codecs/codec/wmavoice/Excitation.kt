// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/wmavoice/excitation.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.wmavoice

import kotlin.math.*
private val gainCoeff=floatArrayOf(0.8169f,-0.06545f,0.1726f,0.0185f,-0.0359f,0.0458f)
internal fun warmExcitation(){gainCoeff.size;gainACB.size;stdCodebook.size;interpAsym.size}
internal fun Decoder.framePitch(r:BitReader,desc:FrameDesc){
    var cur=g.minPitch+r.bits(g.pitchBits);if(cur>=g.maxPitch)cur=g.maxPitch-1
    var last=pitchState;val jumped=20*abs(cur-last)>cur+last;if(prevACB==ACB_NONE||jumped)last=cur
    val b=desc.blocks;for(n in 0 until b){val w=2*n+1;pitch[n]=(w*cur+(2*b-w)*last+b) shr (desc.log2+1)}
    asymBase=last;curPitch=cur;pitchSlope=(cur-last)*65536/160
}
internal fun Decoder.blockPitch(r:BitReader,first:Boolean):Int {
    var field=if(first)r.bits(g.blockPitchBits)else lastPitchField-g.deltaPitchHalf+r.bits(g.deltaPitchBits)
    lastPitchField=clip(field,g.deltaPitchHalf,g.blockPitchRange-g.deltaPitchHalf)
    val t1=(g.conv[1]-g.conv[0]) shl 2;val t2=(g.conv[2]-g.conv[1]) shl 1;val t3=g.conv[3]-g.conv[2]+1
    if(field<t1)return (g.conv[0] shl 2)+field
    field-=t1;if(field<t2)return (g.conv[1] shl 2)+(field shl 1)
    field-=t2;if(field<t3)return (g.conv[2]+field) shl 2
    return g.conv[3] shl 2
}
internal fun Decoder.blockGains(r:BitReader,log2Blocks:Int){
    val idx=r.bits(7);acbGain=gainACB[idx];var pred=0f
    for(i in gainCoeff.indices)pred+=gainPredErr[i]*gainCoeff[i]
    fcbGain=exp(pred.toDouble()-5.2409161640+gainFCB[idx].toDouble()).toFloat()
    val err=gainFCB[idx].coerceIn(-2.9957322736f,1.6094379124f);val weight=minOf(8 ushr log2Blocks,6)
    System.arraycopy(gainPredErr,0,gainPredErr,weight,6-weight);for(i in 0 until weight)gainPredErr[i]=err
}
internal fun Decoder.innovationPulses(r:BitReader,desc:FrameDesc){
    val w=5-desc.log2
    for(n in 0 until 5){val sign=if(r.bit()!=0)1f else -1f;val p1=r.bits(w);pulses[5*p1+n]+=sign
        if(n<desc.double){val p2=r.bits(w);pulses[5*p2+n]+=if(p1<p2)-sign else sign}}
}
internal fun Decoder.comfortNoise(at:Int,size:Int,blockIndex:Int,gain:Float){
    var x=1877*blockIndex+frameCounter+frameIdx;if(x>=65535)x-=65535
    val y=x%9;val z=(x.toLong()*49995/(5*y+6) and 65535).toInt();val off=z%(1000-size)
    for(m in 0 until size)exc[at+m]=stdCodebook[off+m]*gain
}
internal fun Decoder.acbAsymmetric(base:Int,block:Int,size:Int){
    var n=0
    while(n<size){val at=block*size+n;val pSh16=(asymBase shl 16)+pitchSlope*at;val p=(pSh16+0x6fff) shr 16
        val iSh16=((p shl 16)-pSh16)*8+0x58000;val frac=iSh16 shr 16;var run=size
        if(pitchSlope!=0){val next=if(pitchSlope<0)(iSh16+0x10000) and 65535.inv()else iSh16 and 65535.inv();run=clip((iSh16-next)/pitchSlope/8,1,size-n)}
        interpAsymmetric(base+at,p,frac,run);n+=run
    }
}
private fun Decoder.interpAsymmetric(at:Int,p:Int,frac:Int,run:Int){for(m in 0 until run){val src=at+m-p;var acc=0f;for(k in 0 until 9)acc+=exc[src+k]*interpAsym[17*k+frac]+exc[src-k-1]*interpAsym[17*(k+1)-frac];exc[at+m]=acc}}
internal fun Decoder.acbHammingBlock(at:Int,size:Int,pitchQ:Int){
    val p=pitchQ shr 2;val frac=pitchQ and 3
    if(frac==0){for(m in 0 until size)exc[at+m]=exc[at+m-p];return}
    for(m in 0 until size){val src=at+m-p;var acc=0f;for(k in 0 until 8)acc+=exc[src+k]*interpHamming[4*k+frac]+exc[src-k-1]*interpHamming[4*(k+1)-frac];exc[at+m]=acc}
}
internal fun Decoder.synthesise(at:Int,size:Int){val sAt=synthBase+at;val eAt=excBase+at;for(m in 0 until size){var acc=exc[eAt+m];for(j in 0 until lsps)acc-=lpc[j]*synth[sAt+m-j-1];synth[sAt+m]=acc}}
internal fun allPole(dst:FloatArray,at:Int,size:Int,lpc:FloatArray,l:Int){for(m in 0 until size){var acc=dst[at+m];for(j in 0 until l)acc-=lpc[j]*dst[at+m-j-1];dst[at+m]=acc}}
internal fun allZero(dst:FloatArray,src:FloatArray,dstAt:Int,srcAt:Int,size:Int,lpc:FloatArray,l:Int){for(m in 0 until size){var acc=src[srcAt+m];for(j in 0 until l)acc+=lpc[j]*src[srcAt+m-j-1];dst[dstAt+m]=acc}}
