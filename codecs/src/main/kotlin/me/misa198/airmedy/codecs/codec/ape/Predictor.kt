// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/ape/predictor.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.ape

internal class RollBuf32 {
    val data=IntArray(264); var cur=8
    fun flush() { data.fill(0); cur=8 }
    fun increment() { cur++; if (cur==data.size) { System.arraycopy(data,cur-8,data,0,8); cur=8 } }
}
internal class ScaledFilter {
    var last=0
    fun compress(value: Int): Int { val out=value-((last*31) shr 5); last=value; return out }
    fun decompress(value: Int): Int { last=value+((last*31) shr 5); return last }
}
internal class Predictor(h: Header) {
    private val nn=when(h.compressionLevel) {
        2000 -> arrayOf(NnFilter(16,11,h.fileVersion))
        3000 -> arrayOf(NnFilter(64,11,h.fileVersion))
        4000 -> arrayOf(NnFilter(256,13,h.fileVersion),NnFilter(32,10,h.fileVersion))
        5000 -> arrayOf(NnFilter(1280,15,h.fileVersion),NnFilter(256,13,h.fileVersion),NnFilter(16,11,h.fileVersion))
        else -> emptyArray()
    }
    private val predA=RollBuf32(); private val predB=RollBuf32(); private val adaptA=RollBuf32(); private val adaptB=RollBuf32()
    private val stage1A=ScaledFilter(); private val stage1B=ScaledFilter()
    private val mA=IntArray(4); private val mB=IntArray(5); private var lastA=0; private var wide=false
    fun setInterim(on: Boolean) { wide=on; for (f in nn) f.interim=on }
    fun flush() {
        for (f in nn) f.flush()
        predA.flush(); predB.flush(); adaptA.flush(); adaptB.flush(); stage1A.last=0; stage1B.last=0
        mA[0]=360; mA[1]=317; mA[2]=-109; mA[3]=98; mB.fill(0); lastA=0
    }
    fun decompress(value: Int,b: Int): Int {
        var a=value; var i=nn.size-1
        while (i>=0) { a=nn[i].decompress(a); i-- }
        val pa=predA.data; val pb=predB.data; val ca=predA.cur; val cb=predB.cur
        pa[ca]=lastA; pa[ca-1]=pa[ca]-pa[ca-1]
        pb[cb]=stage1B.compress(b); pb[cb-1]=pb[cb]-pb[cb-1]
        val current: Int
        if (wide) {
            var ap=0L; var bp=0L; i=0
            while (i<4) { ap+=pa[ca-i].toLong()*mA[i]; i++ }
            i=0; while (i<5) { bp+=pb[cb-i].toLong()*mB[i]; i++ }
            current=a+((ap+(bp shr 1)) shr 10).toInt()
        } else {
            var ap=0; var bp=0; i=0
            while (i<4) { ap+=pa[ca-i]*mA[i]; i++ }
            i=0; while (i<5) { bp+=pb[cb-i]*mB[i]; i++ }
            current=a+((ap+(bp shr 1)) shr 10)
        }
        val aa=adaptA.data; val ab=adaptB.data; val da=adaptA.cur; val db=adaptB.cur
        aa[da]=adaptStep(pa[ca]); aa[da-1]=adaptStep(pa[ca-1]); ab[db]=adaptStep(pb[cb]); ab[db-1]=adaptStep(pb[cb-1])
        val dir=if (a<0) 1 else if (a>0) -1 else 0
        i=0; while (i<4) { mA[i]+=aa[da-i]*dir; i++ }
        i=0; while (i<5) { mB[i]+=ab[db-i]*dir; i++ }
        val out=stage1A.decompress(current); lastA=current
        predA.increment(); predB.increment(); adaptA.increment(); adaptB.increment(); return out
    }
    private fun adaptStep(v: Int)=if (v==0) 0 else ((v shr 30) and 2)-1
}
