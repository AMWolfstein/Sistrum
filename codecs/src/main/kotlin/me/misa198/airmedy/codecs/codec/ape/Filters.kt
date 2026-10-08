// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/ape/filters.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.ape

internal class RollBuf16(val hist: Int) {
    val data=ShortArray(512+hist); var cur=hist
    fun flush() { data.fill(0); cur=hist }
    fun increment() { cur++; if (cur==data.size) { System.arraycopy(data,cur-hist,data,0,hist); cur=hist } }
}
internal class NnFilter(val order: Int,val shift: Int,fileVersion: Int) {
    private val round=1 shl (shift-1); private val oldDelta=fileVersion<3980
    var interim=false
    private val m=ShortArray(order); private val input=RollBuf16(order); private val deltaM=RollBuf16(order)
    private var avg=0
    fun flush() { m.fill(0); input.flush(); deltaM.flush(); avg=0 }
    fun decompress(value: Int): Int {
        var dot=0; var j=0
        while (j<order) { dot+=input.data[input.cur-order+j].toInt()*m[j].toInt(); j++ }
        val out=value+if (interim) ((dot.toLong()+round) shr shift).toInt() else ((dot+round) shr shift)
        j=0
        while (j<order) {
            val delta=deltaM.data[deltaM.cur-order+j].toInt()
            if (value<0) m[j]=(m[j]+delta).toShort() else if (value>0) m[j]=(m[j]-delta).toShort()
            j++
        }
        updateDelta(out)
        input.data[input.cur]=if (out.toShort().toInt()==out) out.toShort() else ((out shr 31) xor 0x7fff).toShort()
        input.increment(); deltaM.increment(); return out
    }
    private fun updateDelta(v: Int) {
        val s=deltaM.data; val now=deltaM.cur
        if (oldDelta) {
            s[now]=if (v==0) 0 else (((v shr 28) and 8)-4).toShort()
            s[now-4]=(s[now-4].toInt() shr 1).toShort(); s[now-8]=(s[now-8].toInt() shr 1).toShort(); return
        }
        val abs=if (v<0) -v else v
        s[now]=when { abs>avg*3 -> (((v shr 25) and 64)-32).toShort()
            abs>avg*4/3 -> (((v shr 26) and 32)-16).toShort()
            abs>0 -> (((v shr 27) and 16)-8).toShort()
            else -> 0 }
        avg+=(abs-avg)/16
        s[now-1]=(s[now-1].toInt() shr 1).toShort(); s[now-2]=(s[now-2].toInt() shr 1).toShort(); s[now-8]=(s[now-8].toInt() shr 1).toShort()
    }
}
