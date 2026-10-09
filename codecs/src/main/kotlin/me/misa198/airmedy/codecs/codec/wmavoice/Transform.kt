// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/wmavoice/transform.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.wmavoice

import kotlin.math.*
import java.util.Arrays
import me.misa198.airmedy.codecs.dsp.fft.Plan
internal class Transforms {
    private val plan=Plan(128)
    private val re=FloatArray(128);private val im=FloatArray(128);private val outRe=FloatArray(128);private val outIm=FloatArray(128);private val ext=DoubleArray(130)
    private val dct=Array(64){k->DoubleArray(64){t->when(t){0->1.0/64;63->if(k and 1!=0)-1.0/64 else 1.0/64;else->2*cos(Math.PI*t*k/63)/64}}}
    private val dst=Array(64){k->DoubleArray(64){t->2*sin(Math.PI*(t+1)*(k+1)/65)/64}}
    private val trig=Array(65){n->doubleArrayOf(sin(2*Math.PI*n/65),cos(2*Math.PI*n/65))}
    val phase=Array(511){idx->val m=idx-255;val a=(abs(m.toDouble())+0.5)*Math.PI/512;val s=sin(a).toFloat();floatArrayOf(cos(a).toFloat(),if(m<0)-s else s)}
    fun forwardDFT(dst:FloatArray,src:FloatArray){System.arraycopy(src,0,re,0,128);Arrays.fill(im,0f);plan.transform(outRe,outIm,re,im);for(k in 0..64){dst[2*k]=outRe[k];dst[2*k+1]=outIm[k]};dst[1]=0f;dst[129]=0f}
    fun inverseDFT(dst:FloatArray,src:FloatArray){for(k in 0..64){re[k]=src[2*k];im[k]= -src[2*k+1]};for(k in 65 until 128){re[k]=src[2*(128-k)];im[k]=src[2*(128-k)+1]};plan.transform(outRe,outIm,re,im);System.arraycopy(outRe,0,dst,0,128)}
    fun dctI(out:FloatArray,src:FloatArray){for(k in 0 until 64){var acc=0.0;for(t in 0 until 64)acc+=dct[k][t]*src[t].toDouble();out[k]=acc.toFloat()}}
    fun dstI(out:FloatArray,src:FloatArray){for(k in 0 until 64){var acc=0.0;for(t in 0 until 64)acc+=dst[k][t]*src[t].toDouble();out[k]=acc.toFloat()}}
    fun phaseRef65(src:FloatArray):Float {Arrays.fill(ext,0.0);for(i in 1..64){ext[i]= -src[i-1].toDouble();ext[130-i]=src[i-1].toDouble()};var acc=0.0;for(n in 0..64)acc+=ext[2*n]*trig[n][0]+ext[2*n+1]*trig[n][1];return acc.toFloat()}
}
internal fun phaseIndex(v:Float)=if(v.isNaN())255 else 255+clip(v.toInt(),-255,255)
