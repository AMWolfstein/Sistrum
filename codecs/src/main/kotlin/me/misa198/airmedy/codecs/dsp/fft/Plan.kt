// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow dsp/fft/fft.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.dsp.fft

/** WaxFlow FFT's radix-4/2 path; these are the only factors WMA's powers of two use. */
class Plan(val n:Int) {
 private class Level(val f:Int,val m:Int,val bl:Int,val re:Array<FloatArray>,val im:Array<FloatArray>)
 private val perm=IntArray(n);private val levels:Array<Level>
 init{require(n>0&&n and(n-1)==0);val factors=ArrayList<Int>();var remaining=n
 for(f in intArrayOf(4,2))while(remaining%f==0&&remaining>1){factors.add(f);remaining/=f}
 fun build(out:Int,input:Int,stride:Int,depth:Int){val f=factors[depth];var m=n;for(i in 0..depth)m/=factors[i]
 if(m==1){for(q in 0 until f)perm[out+q]=input+q*stride;return};for(q in 0 until f)build(out+q*m,input+q*stride,stride*f,depth+1)}
 if(n==1)perm[0]=0 else build(0,0,1,0)
 val list=ArrayList<Level>();var bl=n;var stride=1
 for(f in factors){val m=bl/f;val re=Array(f-1){FloatArray(m)};val im=Array(f-1){FloatArray(m)}
 for(q in 1 until f)for(k in 0 until m){val angle= -2*Math.PI*((k*q*stride)%n)/n;re[q-1][k]=kotlin.math.cos(angle).toFloat();im[q-1][k]=kotlin.math.sin(angle).toFloat()}
 list.add(0,Level(f,m,bl,re,im));bl=m;stride*=f};levels=list.toTypedArray()}
 fun transform(re:FloatArray,im:FloatArray,srcRe:FloatArray,srcIm:FloatArray){require(re.size>=n&&im.size>=n&&srcRe.size>=n&&srcIm.size>=n);require(re!==srcRe&&re!==srcIm&&im!==srcRe&&im!==srcIm)
 for(i in 0 until n){re[i]=srcRe[perm[i]];im[i]=srcIm[perm[i]]}
 for(lv in levels){val m=lv.m;val w0r=lv.re[0];val w0i=lv.im[0];var base=0
 while(base<n){for(k in 0 until m){val i0=base+k;val i1=i0+m
 val t1r=re[i1]*w0r[k]-im[i1]*w0i[k];val t1i=re[i1]*w0i[k]+im[i1]*w0r[k]
 if(lv.f==2){re[i1]=re[i0]-t1r;im[i1]=im[i0]-t1i;re[i0]+=t1r;im[i0]+=t1i}else{
 val i2=i1+m;val i3=i2+m;val w1r=lv.re[1];val w1i=lv.im[1];val w2r=lv.re[2];val w2i=lv.im[2]
 val t2r=re[i2]*w1r[k]-im[i2]*w1i[k];val t2i=re[i2]*w1i[k]+im[i2]*w1r[k]
 val t3r=re[i3]*w2r[k]-im[i3]*w2i[k];val t3i=re[i3]*w2i[k]+im[i3]*w2r[k]
 val ar=re[i0]+t2r;val ai=im[i0]+t2i;val br=re[i0]-t2r;val bi=im[i0]-t2i
 val cr=t1r+t3r;val ci=t1i+t3i;val dr=t1r-t3r;val di=t1i-t3i
 re[i0]=ar+cr;im[i0]=ai+ci;re[i1]=br+di;im[i1]=bi-dr;re[i2]=ar-cr;im[i2]=ai-ci;re[i3]=br-di;im[i3]=bi+dr
 }};base+=lv.bl}}
 }
}
