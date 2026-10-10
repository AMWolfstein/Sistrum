// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from Flick rust/src/audio/dsd_engine/dsd/{mod.rs,coefficients.rs},
// github.com/moss-apps/Flick at 79da4ed76557c8ddf534e898480dde66bcc90334.
// Copyright (c) 2026 Flick Player Contributors, MIT (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.dsd

import kotlin.math.*

/** Third-order CIC followed by the source's 512-tap Kaiser FIR. Scratch is retained. */
class FlickExactDecimationPipeline(val dsdRate:Int,val targetPcmRate:Int,val channels:Int) {
    private val total=dsdRate/targetPcmRate
    private val cicDecimation=(4 downTo 1).first { total%it==0 }
    private val stage2Decimation=total/cicDecimation
    private val cicIntC=LongArray(channels*3)
    private val cicIntF=DoubleArray(channels*3)
    private val cicCombC=LongArray(channels*3)
    private val cicCombF=DoubleArray(channels*3)
    // Both halves mirror each other, so newest-to-oldest history is contiguous.
    private val firState=Array(channels){DoubleArray(1024)}
    private val firHead=IntArray(channels)
    private val cicBuffer=DoubleArray(4096*8/cicDecimation)
    private val coefficients=generateFlickSincFilter(512,18000.0/(dsdRate/cicDecimation))
    private val normalization=1.0/(cicDecimation*cicDecimation*cicDecimation)

    fun processBytes(bytes:ByteArray,offsets:IntArray,bytesPerChannel:Int,output:FloatArray):Int {
        val cicCount=bytesPerChannel*8/cicDecimation
        val frames=cicCount/stage2Decimation
        for(ch in 0 until channels){
            runCicStage(ch,bytes,offsets[ch],bytesPerChannel)
            val state=firState[ch]
            var head=firHead[ch]
            for(i in 0 until frames){
                val base=i*stage2Decimation
                for(j in 0 until stage2Decimation){
                    head=(head-1)and 511
                    val value=cicBuffer[base+j]
                    state[head]=value
                    state[head+512]=value
                }
                var sample=0.0
                for(tap in 0 until 512)sample+=state[head+tap]*coefficients[tap]
                output[i*channels+ch]=(sample*normalization).toFloat()
            }
            firHead[ch]=head
        }
        return frames
    }
    private fun runCicStage(ch:Int,bytes:ByteArray,offset:Int,count:Int){
        val base=ch*3;var bitIndex=0
        for(i in 0 until count)for(shift in 7 downTo 0){
            val full=cicIntF[base]+if((bytes[offset+i].toInt() ushr shift)and 1==1)1.0 else -1.0
            val carry=full.toLong();cicIntC[base]+=carry;cicIntF[base]=full-carry.toDouble()
            for(stage in 1..2){val p=base+stage;val f=cicIntF[p]+cicIntF[p-1];val c=f.toLong();cicIntC[p]+=cicIntC[p-1]+c;cicIntF[p]=f-c.toDouble()}
            bitIndex++
            if(bitIndex%cicDecimation==0){
                var vc=cicIntC[base+2];var vf=cicIntF[base+2]
                for(stage in 0..2){val p=base+stage;val oldC=cicCombC[p];val oldF=cicCombF[p];cicCombC[p]=vc;cicCombF[p]=vf;vc-=oldC;val f=vf-oldF;val c=f.toLong();vc+=c;vf=f-c.toDouble()}
                cicBuffer[bitIndex/cicDecimation-1]=vc.toDouble()+vf
            }
        }
    }
    fun reset(){cicIntC.fill(0);cicIntF.fill(0.0);cicCombC.fill(0);cicCombF.fill(0.0);for(s in firState)s.fill(0.0);firHead.fill(0)}
}

internal fun generateFlickSincFilter(taps:Int,cutoff:Double):DoubleArray {
    val result=DoubleArray(taps);val center=(taps-1)/2.0;var sum=0.0
    for(i in 0 until taps){val x=i-center;val sinc=if(abs(x)<1e-10)2.0*cutoff else sin(2.0*PI*cutoff*x)/(PI*x)
        val wx=(i-center)/center;val w=besselI0(10.0*sqrt(1.0-wx*wx))/besselI0(10.0)
        result[i]=sinc*w;sum+=result[i]
    }
    if(abs(sum)>1e-10)for(i in result.indices)result[i]/=sum
    return result
}
private fun besselI0(x:Double):Double {var sum=1.0;var term=1.0;for(k in 1..24){val v=x/(2.0*k);term*=v*v;sum+=term;if(term<1e-12)break};return sum}
