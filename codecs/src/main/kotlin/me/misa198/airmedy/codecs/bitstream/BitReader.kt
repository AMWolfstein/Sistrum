// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/wavpack/words.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.bitstream

import java.nio.ByteBuffer

/** WavPack LSB-first reader, reused for each metadata payload. */
internal class BitReader {
    private var data: ByteBuffer? = null
    private var pos=0
    private var end=0
    private var sr=0L
    private var bc=0
    var over=false
        private set
    fun clear() { data=null; pos=0; end=0; sr=0; bc=0; over=false }
    fun reset(data: ByteBuffer, offset: Int, length: Int) {
        this.data=data; pos=offset; end=offset+length; sr=0; bc=0; over=false
    }
    fun open() = data!=null
    private fun fill(n: Int) {
        val b=data!!
        while (bc<n) {
            if (bc<=31 && pos+4<=end) {
                val v=(b.get(pos).toLong() and 255) or ((b.get(pos+1).toLong() and 255) shl 8) or
                    ((b.get(pos+2).toLong() and 255) shl 16) or ((b.get(pos+3).toLong() and 255) shl 24)
                sr=sr or (v shl bc); pos+=4; bc+=32; continue
            }
            var v=0L
            if (pos<end) { v=b.get(pos++).toLong() and 255 } else { over=true }
            sr=sr or (v shl bc); bc+=8
        }
    }
    fun getBit(): Int { fill(1); val bit=sr.toInt() and 1; sr=sr ushr 1; bc--; return bit }
    fun getBits(n: Int): Int {
        if (n<=0) return 0
        fill(n); val v=(sr and ((1L shl n)-1)).toInt(); sr=sr ushr n; bc-=n; return v
    }
    fun readCode(maxcode: Int): Int {
        if (Integer.compareUnsigned(maxcode,2)<0) return if (maxcode==0) 0 else getBit()
        val n=32-Integer.numberOfLeadingZeros(maxcode)
        val extras=((1L shl n)-(maxcode.toLong() and 0xffffffffL)-1).toInt()
        fill(n)
        var code=(sr and ((1L shl (n-1))-1)).toInt()
        if (Integer.compareUnsigned(code,extras)>=0) {
            code=(code shl 1)-extras+((sr ushr (n-1)).toInt() and 1)
            sr=sr ushr n; bc-=n
        } else { sr=sr ushr (n-1); bc-=n-1 }
        return code
    }
    /** Unsigned 32-bit value in a Long, or -1 for the invalid escape. */
    fun readElias(): Long {
        var cbits=0
        while (cbits<33 && getBit()!=0) cbits++
        if (cbits==33) return -1
        if (cbits<2) return cbits.toLong()
        var v=0; var mask=1; var k=1
        while (k<cbits) { if (getBit()!=0) v=v or mask; mask=mask shl 1; k++ }
        return (v or mask).toLong() and 0xffffffffL
    }
}
