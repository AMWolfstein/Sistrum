// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/alac/bits.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.alac

import java.nio.ByteBuffer

internal class BitReader {
    lateinit var data: ByteBuffer; private var base=0; private var length=0
    var pos=0; var validBits=0
    fun reset(data: ByteBuffer,position: Int=0) { this.data=data; base=data.position(); length=data.remaining(); pos=position; validBits=length*8 }
    private fun byteAt(i: Int)=if (i>=0 && i<length) u8(data,base+i) else 0
    fun read(n: Int): Int {
        if (n==0) return 0
        val byteOff=pos ushr 3; val bitOff=pos and 7
        var acc=0L; var i=0
        while (i<8) { acc=(acc shl 8) or byteAt(byteOff+i).toLong(); i++ }
        pos+=n
        return ((acc ushr (64-bitOff-n)) and (if (n>=32) 0xffffffffL else (1L shl n)-1)).toInt()
    }
    fun byteAlign() { pos=(pos+7) and 7.inv() }
    fun overrun()=pos>validBits
    private fun read32(i: Int)=(byteAt(i) shl 24) or (byteAt(i+1) shl 16) or (byteAt(i+2) shl 8) or byteAt(i+3)
    private fun peek32(p: Int)=read32(p ushr 3) shl (p and 7)
    private fun streamBits(p: Int,n: Int): Int {
        val load=read32(p ushr 3); val bo=p and 7
        val result=if (n+bo>32) ((load shl bo) ushr (32-n)) or (byteAt((p ushr 3)+4) ushr (8-(n+bo-32))) else load ushr (32-n-bo)
        return if (n==32) result else result and ((1L shl n)-1).toInt()
    }
    fun dynGet32(m: Int,k: Int,maxBits: Int): Int {
        var stream=peek32(pos); var result=Integer.numberOfLeadingZeros(stream.inv())
        if (result>=9) { result=streamBits(pos+9,maxBits); pos+=9+maxBits; return result }
        pos+=result+1
        if (k!=1) {
            stream=if (result+1>=32) 0 else stream shl (result+1)
            val v=if (k==0) 0 else stream ushr (32-k)
            pos+=k-1; result*=m
            if (uint(v)>=2) { result+=v-1; pos++ }
        }
        return result
    }
    fun dynGet16(m: Int,k: Int): Int {
        var stream=peek32(pos); val pre=Integer.numberOfLeadingZeros(stream.inv())
        if (pre>=9) { pos+=9; stream=stream shl 9; val result=stream ushr 16; pos+=16; return result }
        pos+=pre+1; stream=if (pre+1>=32) 0 else stream shl (pre+1)
        val v=if (k==0) 0 else stream ushr (32-k)
        pos+=k; var result=pre*m+v-1
        if (uint(v)<2) { result-=v-1; pos-- }
        return result
    }
}
