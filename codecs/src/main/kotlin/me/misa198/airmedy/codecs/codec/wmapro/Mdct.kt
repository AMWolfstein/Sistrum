// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/wmapro/mdct.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.wmapro

import me.misa198.airmedy.codecs.dsp.fft.Plan
import kotlin.math.*
internal class ImdctScratch(n:Int){val cr=FloatArray(n/2);val ci=FloatArray(n/2);val dr=FloatArray(n/2);val di=FloatArray(n/2)}
internal class ImdctPlan(val length:Int) {
    private val m=length/2;private val fp=Plan(m)
    private val twRe=DoubleArray(m){cos(2*Math.PI*(it+0.125)/(2*length))}
    private val twIm=DoubleArray(m){sin(2*Math.PI*(it+0.125)/(2*length))}
    val window=FloatArray(length){sin((it+0.5)*Math.PI/(2*length)).toFloat()}
    fun imdct(spec:FloatArray,out:FloatArray,at:Int,scale:Double,s:ImdctScratch){
        for(j in 0 until m){val x1=spec[2*j].toDouble();val x2=spec[length-1-2*j].toDouble();val c=twRe[j];val sn=twIm[j];s.cr[j]=(x1*c+x2*sn).toFloat();s.ci[j]=(x2*c-x1*sn).toFloat()}
        fp.transform(s.dr,s.di,s.cr,s.ci)
        for(q in 0 until m){val re=s.dr[q].toDouble();val im=s.di[q].toDouble();val c=twRe[q];val sn=twIm[q];out[at+2*q]=(scale*(re*sn-im*c)).toFloat();out[at+length-1-2*q]=(scale*(re*c+im*sn)).toFloat()}
    }
}
internal fun overlapButterfly(region:FloatArray,at:Int,n:Int,w:FloatArray){for(r in 0 until n/2){val a=region[at+r];val b=region[at+n-1-r];val wa=w[r];val wb=w[n-1-r];region[at+r]=a*wb-b*wa;region[at+n-1-r]=a*wa+b*wb}}
