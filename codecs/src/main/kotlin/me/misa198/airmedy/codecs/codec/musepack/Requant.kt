// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/musepack/requant.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.musepack
internal val cc = floatArrayOf(
	111.285962475327f, 
	65536.000000000000f, 21845.333333333332f, 13107.200000000001f, 9362.285714285713f,
	7281.777777777777f, 4369.066666666666f, 2114.064516129032f, 1040.253968253968f,
	516.031496062992f, 257.003921568627f, 128.250489236790f, 64.062561094819f,
	32.015632633121f, 16.003907203907f, 8.000976681723f, 4.000244155527f,
	2.000061037018f, 1.000015259021f,
)
internal val dc = intArrayOf(
	2,
	0, 1, 2, 3, 4, 7, 15, 31, 63,
	127, 255, 511, 1023, 2047, 4095, 8191, 16383, 32767,
)
internal val resBit = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16)

internal val scfTable=FloatArray(256).also{t->val ratio=0.83298066476582673961;val factor=1.0/32768;t[1]=factor.toFloat();var f1=factor*ratio;var f2=factor*(1/ratio);for(n in 1..128){t[(1+n) and 255]=f1.toFloat();t[(1-n) and 255]=f2.toFloat();f1*=ratio;f2*=1/ratio}}
internal fun requantize(st:FrameState,cfg:Config,y:Array<Array<FloatArray>>){
 for(band in 0..cfg.maxBand){val resL=st.res[0][band];val resR=st.res[1][band];val qL=st.q[band][0];val qR=st.q[band][1];val yL=y[0];val yR=y[1]
 for(part in 0..2){val facL=if(resL!=0)cc[resL+1]*scfTable[st.scf[0][band][part] and 255] else 0f;val facR=if(resR!=0)cc[resR+1]*scfTable[st.scf[1][band][part] and 255] else 0f
 for(n in part*12 until part*12+12){when {
 st.ms[band]&&resL!=0&&resR!=0->{val l=mul(facL,qL[n].toFloat());val r=mul(facR,qR[n].toFloat());yL[n][band]=l+r;yR[n][band]=l-r}
 st.ms[band]&&resL!=0->{val v=facL*qL[n].toFloat();yL[n][band]=v;yR[n][band]=v}
 st.ms[band]&&resR!=0->{val v=facR*qR[n].toFloat();yL[n][band]=v;yR[n][band]=-v}
 else->{yL[n][band]=if(resL!=0)facL*qL[n].toFloat() else 0f;yR[n][band]=if(resR!=0)facR*qR[n].toFloat() else 0f}
 }}}
 }
}
