// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/wma/mdct.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.wma

import me.misa198.airmedy.codecs.dsp.fft.Plan
internal class ImdctScratch(maxN:Int){val cr=FloatArray(maxN/4);val ci=FloatArray(maxN/4);val dr=FloatArray(maxN/4);val di=FloatArray(maxN/4)}
internal class ImdctPlan(val blockLen:Int) {
 private val m=blockLen/2;private val fp=Plan(m)
 private val twRe=DoubleArray(m){kotlin.math.cos(2*Math.PI*(it+0.125)/(2*blockLen))}
 private val twIm=DoubleArray(m){kotlin.math.sin(2*Math.PI*(it+0.125)/(2*blockLen))}
 val window=FloatArray(blockLen){kotlin.math.sin((it+0.5)*Math.PI/(2*blockLen)).toFloat()}
 fun imdct(spec:FloatArray,out:FloatArray,s:ImdctScratch){for(j in 0 until m){val x1=spec[2*j].toDouble();val x2=spec[blockLen-1-2*j].toDouble();val c=twRe[j];val sn=twIm[j];s.cr[j]=(x1*c+x2*sn).toFloat();s.ci[j]=(x2*c-x1*sn).toFloat()}
 fp.transform(s.dr,s.di,s.cr,s.ci)
 for(q in 0 until m){val re=s.dr[q].toDouble();val im=s.di[q].toDouble();val c=twRe[q];val sn=twIm[q];out[m+2*q]=(re*sn-im*c).toFloat();out[3*m-1-2*q]=(re*c+im*sn).toFloat()}
 for(q in 0 until m)out[m-1-q]= -out[m+q];for(r in 0 until m)out[3*m+r]=out[3*m-1-r]}
}
internal fun overlapAdd(dst:FloatArray,at:Int,block:FloatArray,len:Int,prevLen:Int,nextLen:Int,plan:ImdctPlan,prev:ImdctPlan,next:ImdctPlan){val w=plan.window
 if(len<=prevLen){for(i in 0 until len)dst[at+i]+=block[i]*w[i]}else{val m=(len-prevLen)/2;for(i in 0 until prevLen)dst[at+m+i]+=block[m+i]*prev.window[i];System.arraycopy(block,m+prevLen,dst,at+m+prevLen,len-m-prevLen)}
 if(len<=nextLen){for(i in 0 until len)dst[at+len+i]=block[len+i]*w[len-1-i]}else{val m=(len-nextLen)/2;System.arraycopy(block,len,dst,at+len,m);for(i in 0 until nextLen)dst[at+len+m+i]=block[len+m+i]*next.window[nextLen-1-i];java.util.Arrays.fill(dst,at+len+m+nextLen,at+2*len,0f)}}
