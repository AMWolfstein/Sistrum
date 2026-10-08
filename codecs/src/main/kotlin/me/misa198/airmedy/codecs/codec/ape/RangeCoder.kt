// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/ape/rangecoder.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.ape

import java.nio.ByteBuffer

private val TOTAL_OLD=longArrayOf(0,14824,28224,39348,47855,53994,58171,60926,62682,63786,64463,64878,65126,65276,65365,65419,65450,65469,65480,65487,65491,65493,65494,65495,65496,65497,65498,65499,65500,65501,65502,65503,65504,65505,65506,65507,65508,65509,65510,65511,65512,65513,65514,65515,65516,65517,65518,65519,65520,65521,65522,65523,65524,65525,65526,65527,65528,65529,65530,65531,65532,65533,65534,65535,65536)
private val TOTAL_CURRENT=longArrayOf(0,19578,36160,48417,56323,60899,63265,64435,64971,65232,65351,65416,65447,65466,65476,65482,65485,65488,65490,65491,65492,65493,65494,65495,65496,65497,65498,65499,65500,65501,65502,65503,65504,65505,65506,65507,65508,65509,65510,65511,65512,65513,65514,65515,65516,65517,65518,65519,65520,65521,65522,65523,65524,65525,65526,65527,65528,65529,65530,65531,65532,65533,65534,65535,65536)
private val K_BOUNDARY=longArrayOf(0,32,64,128,256,512,1024,2048,4096,8192,16384,32768,65536,131072,262144,524288,1048576,2097152,4194304,8388608,16777216,33554432,67108864,134217728,268435456,536870912,1073741824,2147483648,0,0,0,0)

private class RangeModel(val total: LongArray) {
    val width=LongArray(64) { total[it+1]-total[it] }; val tab=ByteArray(65536)
    init { var sym=0; var i=0; while (i<tab.size) { if (i.toLong()>=total[sym+1]) sym++; tab[i]=sym.toByte(); i++ } }
}
private object Models { val current=RangeModel(TOTAL_CURRENT); val old=RangeModel(TOTAL_OLD) }
internal class ApeBitReader {
    private lateinit var data: ByteBuffer; private var base=0; private var length=0; private var bit=0; private var over=false
    fun reset(data: ByteBuffer,base: Int,skip: Int) { this.data=data; this.base=base; length=data.limit()-base; bit=skip*8; over=false }
    private fun byteAt(i: Int): Long {
        val p=(i and 3.inv())+(3-(i and 3))
        if (p<length) return u8(data,base+p).toLong()
        over=true; return 0
    }
    fun exhausted()=over && (bit ushr 3)>length+8
    fun bits(count: Int): Long {
        var n=count; var v=0L
        while (n>0) {
            val available=8-(bit and 7); val take=minOf(n,available)
            v=((v shl take) or ((byteAt(bit ushr 3) ushr (available-take)) and ((1L shl take)-1))) and 0xffffffffL
            bit+=take; n-=take
        }
        return v
    }
    fun readByte(): Long { val b=byteAt(bit ushr 3); bit+=8; return b }
    fun alignByte() { bit=(bit+7) and 7.inv() }
}
internal class EntropyState {
    var k=10; var kSum=16384L
    fun flush() { k=10; kSum=16384 }
    fun update(value: Long) {
        kSum=(kSum+(((value+1)/2) and 0xffffffffL)-(((kSum+16) and 0xffffffffL) ushr 5)) and 0xffffffffL
        if (kSum<K_BOUNDARY[k]) k-- else if (K_BOUNDARY[k+1]!=0L && kSum>=K_BOUNDARY[k+1]) k++
    }
}
internal class RangeDecoder(fileVersion: Int) {
    val br=ApeBitReader(); private var low=0L; private var rng=0L; private var buf=0L
    private val old=fileVersion<3990; private val model=if (old) Models.old else Models.current
    private var pivot=0L; private var live=true
    fun start() { br.alignByte(); br.readByte(); buf=br.readByte(); low=buf ushr 1; rng=128 }
    private fun normalize(): Boolean {
        while (rng<=8388608) {
            buf=((buf shl 8) or br.readByte()) and 0xffffffffL
            low=((low shl 8) or ((buf ushr 1) and 255)) and 0xffffffffL
            rng=(rng shl 8) and 0xffffffffL
            if (rng==0L) return false
        }
        return true
    }
    private fun decodeFast(shift: Int): Long { if (!normalize()) return 0; rng=rng ushr shift; return low/rng }
    private fun decodeDirect(shift: Int): Long {
        while (rng<=8388608) {
            if (rng==0L) malformed("range coder collapsed")
            buf=((buf shl 8) or br.readByte()) and 0xffffffffL
            low=((low shl 8) or ((buf ushr 1) and 255)) and 0xffffffffL
            rng=(rng shl 8) and 0xffffffffL
        }
        rng=rng ushr shift
        if (rng==0L) malformed("range coder collapsed")
        val v=low/rng; low%=rng; return v
    }
    private fun decodeOverflow(): Long {
        var attempt=0
        while (attempt++<2) {
            val total=decodeFast(16)
            if (total>=65536) malformed("range coder frequency $total out of model")
            val symbol=model.tab[total.toInt()].toInt() and 255
            low=(low-rng*model.total[symbol]) and 0xffffffffL
            rng=(rng*model.width[symbol]) and 0xffffffffL
            if (symbol!=63 || old) return symbol.toLong()
            val hi=decodeDirect(16); val lo=decodeDirect(16); val overflow=(hi shl 16) or lo
            if (overflow!=1L) return overflow
            pivot=32768
        }
        malformed("range coder repeats its overflow signal")
    }
    fun decodeValue(s: EntropyState): Int {
        if (br.exhausted()) malformed("frame ends mid-value: its coded data ran out")
        live=true
        val value=if (old) decodeValueOld(s) else decodeValueCurrent(s)
        if (!live) return 0
        s.update(value)
        return if (value and 1!=0L) ((value shr 1)+1).toInt() else (-(value shr 1)).toInt()
    }
    private fun decodeValueCurrent(s: EntropyState): Long {
        pivot=maxOf(s.kSum/32,1L); val overflow=decodeOverflow()
        val base: Long
        if (pivot>=65536) {
            var bits=0; while (pivot ushr bits>0) bits++
            val shift=maxOf(bits-16,0); val split=1L shl shift
            val hi=decodeDivided(pivot/split+1); val lo=decodeDivided(split)
            base=(hi*split+lo) and 0xffffffffL
        } else {
            if (!normalize()) { live=false; return 0 }
            rng/=pivot; base=low/rng; low%=rng
        }
        return base+overflow*pivot
    }
    private fun decodeDivided(n: Long): Long {
        if (!normalize()) malformed("range coder collapsed")
        rng/=n; if (rng==0L) malformed("range coder collapsed")
        val v=low/rng; low%=rng; return v
    }
    private fun decodeValueOld(s: EntropyState): Long {
        pivot=0; var overflow=decodeOverflow()
        val k: Int
        if (overflow==63L) { k=decodeDirect(5).toInt(); overflow=0 } else k=maxOf(s.k-1,0)
        val value=if (k<=16) decodeDirect(k) else {
            val lo=decodeDirect(16); val hi=decodeDirect(k-16); lo or (hi shl 16)
        }
        return value+(overflow shl k)
    }
}
