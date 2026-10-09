// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/wmavoice/postfilter.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.wmavoice

import java.util.Arrays
import kotlin.math.*

internal fun Decoder.postfilter(out:FloatArray,outAt:Int,at:Int,fcb:Int,pitch:Int){
    val zAt=zeroBase+at;allZero(zero,synth,zAt,synthBase+at,80,lpc,lsps)
    var src=zero;var srcAt=zAt
    if((fcb==FCB_WINDOW||fcb==FCB_INNOVATION)&&smooth(zAt,80,pitch)){src=smoothed;srcAt=0}
    System.arraycopy(src,srcAt,resynth,lsps,80);allPole(resynth,lsps,80,lpc,lsps)
    System.arraycopy(resynth,80,resynth,0,lsps)
    if(fcb!=FCB_SILENCE)denoise(fcb==FCB_HARDCODED,80)
    else {pfWork.fill(0f);System.arraycopy(resynth,lsps,pfWork,0,80)}
    mergeCache(80,fcb==FCB_SILENCE)
    var speech=0f;var post=0f
    for(m in 0 until 80){speech+=absF(synth[synthBase+at+m]);post+=absF(pfWork[m])}
    val gain=if(post!=0f)((1f-0.99f).toDouble()*speech.toDouble()/post.toDouble()).toFloat()else 0f
    for(m in 0 until 80){agcMem=0.99f*agcMem+gain;out[outAt+m]=pfWork[m]*agcMem}
    if(cfg.dcLevel>8)removeDC(out,outAt,80)
}
private fun Decoder.smooth(at:Int,size:Int,pitch:Int):Boolean {
    val lo=max(g.minPitch,pitch-3);val hi=min(g.maxPitch,pitch+3);var best= -1;var bestDot=0f
    for(lag in lo..hi){var acc=0f;for(m in 0 until size)acc+=zero[at+m]*zero[at-lag+m];if(acc>bestDot){bestDot=acc;best=lag}}
    if(best<0||bestDot<=0f)return false
    var den=0f;for(m in 0 until size)den+=zero[at-best+m]*zero[at-best+m]
    if(den<=0f)return false
    val f=if(den>bestDot)den/(den+0.6f*bestDot)else 0.625f
    for(m in 0 until size)smoothed[m]=zero[at-best+m]+f*(zero[at+m]-zero[at-best+m])
    return true
}
private fun Decoder.denoise(hardcoded:Boolean,size:Int){
    val v=pfSpec;v.fill(0f);v[0]=1f;System.arraycopy(lpc,0,v,1,lsps);tiltFilter(0.7f*tiltFactor(lpc,lsps),v,lsps+2)
    val spec=pfLPCSpec;tf.forwardDFT(spec,v);val gain=pfGain
    gain[0]=log10f(spec[0]*spec[0]);for(n in 1 until 64)gain[n]=log10f(spec[2*n]*spec[2*n]+spec[2*n+1]*spec[2*n+1])
    // These indices deliberately retain the pinned source's packing conventions.
    gain[64]=log10f(spec[64]*spec[64]);var lo=gain[0];var hi=gain[0]
    for(x in gain){lo=min(lo,x);hi=max(hi,x)}
    val range=hi-lo;val irange=if(range>0f)64f/range else 0f
    val weight=if(hardcoded)(5.0/13.0).toFloat()else (5.0/14.7).toFloat();val gainMul=range*weight
    val angleMul=(gainMul.toDouble()*5.863484791035422).toFloat()
    val mag=pfMag;val pwrRow=denoisePower[cfg.denoiseStrength]
    for(n in 0..64){
        val i=clip(Math.rint(((hi-gain[n])*irange-1f).toDouble()).toInt(),0,63);val pwr=pwrRow[i];gain[n]=angleMul*pwr
        val x=max(((pwr*gainMul).toDouble()-0.0295)*70.570526123,0.0);val j=max(x.toInt(),0)
        mag[n]=if(j>127)energyTable[127]*1.0331663.pow((j-127).toDouble()).toFloat()else energyTable[j]
    }
    System.arraycopy(gain,0,pfDct,0,64);tf.dctI(pfS,pfDct);tf.dstI(pfH,pfS);val h64=tf.phaseRef65(pfS)
    val pt=tf.phase;val c=pfCoefs;val last=mag[64]*pt[phaseIndex(h64-2f*pfH[63])][0]
    for(n in 63 downTo 1){val p=(if(n and 1!=0)-h64 else h64)-2f*pfH[n-1];val a=phaseIndex(p);c[2*n+1]=mag[n]*pt[a][1];c[2*n]=mag[n]*pt[a][0]}
    c[0]=mag[0]*pt[phaseIndex(h64)][0];c[64]=last;c[1]=0f;c[128]=0f;c[129]=0f
    val ir=pfIR;tf.inverseDFT(ir,c);val rem=min(127-size,size-1);Arrays.fill(ir,rem,128,0f)
    if(cfg.denoiseTilt){ir[rem-1]=0f;tiltFilter(-1.8f*tiltFactor(ir,rem-1),ir,rem)}
    var energy=0f;for(i in 0 until rem)energy+=ir[i]*ir[i]
    if(energy>0f){val sq=(1.0/64*sqrt(1.0/energy.toDouble())).toFloat();for(i in 0 until rem)ir[i]*=sq}
    v.fill(0f);System.arraycopy(resynth,lsps,v,0,size);val sig=pfSigSpec;val impulse=pfIRSpec
    tf.forwardDFT(sig,v);tf.forwardDFT(impulse,ir);sig[0]*=impulse[0];sig[1]=0f
    for(k in 1..64){val ar=sig[2*k];val ai=sig[2*k+1];val br=impulse[2*k];val bi=impulse[2*k+1];sig[2*k]=ar*br-ai*bi;sig[2*k+1]=ar*bi+ai*br}
    tf.inverseDFT(pfWork,sig)
}
private fun Decoder.mergeCache(size:Int,silence:Boolean){
    if(cacheLen>0){val lim=min(cacheLen,size);for(m in 0 until lim)pfWork[m]+=cache[m];cacheLen-=lim;System.arraycopy(cache,size,cache,0,160-size);Arrays.fill(cache,160-size,160,0f)}
    if(silence)return
    val rem=min(127-size,size-1);val lim=min(rem,cacheLen)
    for(m in 0 until lim)cache[m]+=pfWork[size+m]
    if(lim<rem){System.arraycopy(pfWork,size+lim,cache,lim,rem-lim);cacheLen=rem}
}
private fun Decoder.removeDC(v:FloatArray,at:Int,size:Int){for(m in 0 until size){val t=0.93980580475f*v[at+m]-(-1.9330735188f)*dcMem[0]-0.93589198496f*dcMem[1];v[at+m]=t+(-1.99997f)*dcMem[0]+dcMem[1];dcMem[1]=dcMem[0];dcMem[0]=t}}
private fun tiltFactor(a:FloatArray,n:Int):Float {var num=a[0];for(i in 0 until n-1)num+=a[i]*a[i+1];var den=1f;for(i in 0 until n)den+=a[i]*a[i];return num/den}
private fun tiltFilter(t:Float,s:FloatArray,n:Int){for(i in n-1 downTo 1)s[i]-=t*s[i-1]}
private fun log10f(v:Float)=log10(max(v.toDouble(),1e-30)).toFloat()
internal fun absF(x:Float)=if(x<0f)-x else x
