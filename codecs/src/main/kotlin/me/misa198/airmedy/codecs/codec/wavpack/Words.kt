// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/wavpack/words.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
// Hybrid entropy port: libwavpack 5.8.1 src/read_words.c and src/entropy_utils.c.
// Copyright (c) 1998-2013 Conifer Software; 1998-2025 David Bryant.
// BSD-3-Clause; conditions and disclaimer in THIRD-PARTY-NOTICES.
package me.misa198.airmedy.codecs.codec.wavpack

import me.misa198.airmedy.codecs.bitstream.BitReader

internal class EntropyChan {
    var slowLevel=0; var errorLimit=0; var bitrateAcc=0; var bitrateDelta=0
    val median=IntArray(3)
    fun med(i: Int) = (median[i] ushr 4)+1
    fun inc(i: Int) { val div=128 ushr i; median[i]+=Integer.divideUnsigned(median[i]+div,div)*5 }
    fun dec(i: Int) { val div=128 ushr i; median[i]-=Integer.divideUnsigned(median[i]+div-2,div)*2 }
}

internal class WordCoder {
    val c=Array(2) { EntropyChan() }
    var holdingOne=false
    var holdingZero=false
    var zerosAcc=0
    fun reset() { for (ch in c) { ch.median.fill(0); ch.slowLevel=0; ch.errorLimit=0; ch.bitrateAcc=0; ch.bitrateDelta=0 }; holdingOne=false; holdingZero=false; zerosAcc=0 }
    fun readHybridProfile(m: Metadata, flags: Int) {
        val channels=if (flags and MONO_DATA != 0) 1 else 2
        var at=0
        if (flags and 0x200 != 0) {
            if (m.size<channels*2) malformed("truncated hybrid profile")
            var i=0; while (i<channels) { c[i++].slowLevel=exp2s(m.short(at)); at+=2 }
        }
        if (m.size-at<channels*2) malformed("truncated hybrid bitrate")
        var i=0; while (i<channels) { c[i++].bitrateAcc=m.short(at) shl 16; at+=2 }
        if (at<m.size) {
            if (m.size-at!=channels*2) malformed("invalid hybrid delta length")
            i=0; while (i<channels) { c[i++].bitrateDelta=exp2s(m.short(at).toShort().toInt()); at+=2 }
        }
    }
    private fun updateErrorLimit(flags: Int) {
        val mono=flags and MONO_DATA != 0
        c[0].bitrateAcc+=c[0].bitrateDelta
        var b0=c[0].bitrateAcc ushr 16
        var b1=0
        if (!mono) { c[1].bitrateAcc+=c[1].bitrateDelta; b1=c[1].bitrateAcc ushr 16 }
        if (flags and 0x200 != 0) {
            val s0=(c[0].slowLevel+128) ushr 8
            val s1=(c[1].slowLevel+128) ushr 8
            if (!mono && flags and 0x400 != 0) {
                val balance=(s1-s0+b1+1) shr 1
                when {
                    balance>b0 -> { b1=b0*2; b0=0 }
                    -balance>b0 -> { b0*=2; b1=0 }
                    else -> { b1=b0+balance; b0-=balance }
                }
            }
            c[0].errorLimit=if (s0-b0> -256) exp2s(s0-b0+256) else 0
            if (!mono) c[1].errorLimit=if (s1-b1> -256) exp2s(s1-b1+256) else 0
        } else {
            c[0].errorLimit=exp2s(b0); if (!mono) c[1].errorLimit=exp2s(b1)
        }
    }
    fun getWordsHybrid(bs: BitReader, buf: IntArray, n: Int, flags: Int, wvc: BitReader?=null, corrections: IntArray?=null): Int {
        val mono=flags and MONO_DATA != 0
        val total=if (mono) n else n*2
        var i=0
        while (i<total) {
            val channel=if (mono) 0 else i and 1
            val ch=c[channel]
            if (corrections!=null) corrections[i]=0
            if (c[0].median[0] and -2==0 && !holdingZero && !holdingOne && c[1].median[0] and -2==0) {
                if (zerosAcc!=0) {
                    zerosAcc--
                    if (zerosAcc!=0) { ch.slowLevel-=(ch.slowLevel+128) ushr 8; buf[i++]=0; continue }
                } else {
                    val run=bs.readElias(); if (run<0) break
                    zerosAcc=run.toInt()
                    if (zerosAcc!=0) { ch.slowLevel-=(ch.slowLevel+128) ushr 8; c[0].median.fill(0); c[1].median.fill(0); buf[i++]=0; continue }
                }
            }
            var ones=0
            if (holdingZero) holdingZero=false else {
                while (ones<17 && bs.getBit()!=0) ones++
                if (ones==17) break
                if (ones==16) { val esc=bs.readElias(); if (esc<0) break; ones=esc.toInt()+16 }
                val carry=if (holdingOne) 1 else 0
                holdingOne=ones and 1 != 0; holdingZero=!holdingOne; ones=(ones ushr 1)+carry
            }
            if (channel==0) updateErrorLimit(flags)
            var low=0
            var high: Int
            when (ones) {
                0 -> { high=ch.med(0)-1; ch.dec(0) }
                1 -> { low=ch.med(0); ch.inc(0); high=low+ch.med(1)-1; ch.dec(1) }
                2 -> { low=ch.med(0); ch.inc(0); low+=ch.med(1); ch.inc(1); high=low+ch.med(2)-1; ch.dec(2) }
                else -> { low=ch.med(0); ch.inc(0); low+=ch.med(1); ch.inc(1); low+=(ones-2)*ch.med(2); high=low+ch.med(2)-1; ch.inc(2) }
            }
            low=low and Int.MAX_VALUE; high=high and Int.MAX_VALUE
            if (low>high) high=low
            var mid=(high+low+1) ushr 1
            if (ch.errorLimit==0) mid=low+bs.readCode(high-low) else {
                while (Integer.compareUnsigned(high-low,ch.errorLimit)>0) {
                    if (bs.getBit()!=0) low=mid else high=mid-1
                    mid=(high+low+1) ushr 1
                }
            }
            val sign=bs.getBit()
            if (wvc!=null && wvc.open() && ch.errorLimit!=0) {
                val exact=low+wvc.readCode(high-low)
                corrections!![i]=if (sign!=0) mid-exact else exact-mid
            }
            if (flags and 0x200 != 0) { ch.slowLevel-=(ch.slowLevel+128) ushr 8; ch.slowLevel+=wpLog2(mid) }
            buf[i++]=signed(sign,mid)
        }
        return if (mono) i else i/2
    }
    fun getWordsLossless(bs: BitReader, buf: IntArray, n: Int, mono: Boolean): Int {
        val total=if (mono) n else n*2
        var i=0
        while (i<total) {
            var ch=c[if (mono) 0 else i and 1]
            if (holdingZero) {
                holdingZero=false
                val low=bs.readCode(ch.med(0)-1); ch.dec(0)
                buf[i++]=signed(bs.getBit(),low)
                if (i==total) break
                ch=c[if (mono) 0 else i and 1]
            }
            if (Integer.compareUnsigned(c[0].median[0],2)<0 && !holdingOne && Integer.compareUnsigned(c[1].median[0],2)<0) {
                if (zerosAcc!=0) {
                    zerosAcc--
                    if (zerosAcc!=0) { buf[i++]=0; continue }
                } else {
                    val run=bs.readElias()
                    if (run<0) break
                    zerosAcc=run.toInt()
                    if (run!=0L) { c[0].median.fill(0); c[1].median.fill(0); buf[i++]=0; continue }
                }
            }
            var ones=0
            while (ones<17 && bs.getBit()!=0) ones++
            if (ones>=16) {
                if (ones==17) break
                val esc=bs.readElias(); if (esc<0) break
                ones=esc.toInt()+16
            }
            val carry=if (holdingOne) 1 else 0
            holdingOne=ones and 1 != 0; holdingZero=!holdingOne
            ones=(ones ushr 1)+carry
            var low=0
            val high: Int
            when (ones) {
                0 -> { high=ch.med(0)-1; ch.dec(0) }
                1 -> { low=ch.med(0); ch.inc(0); high=low+ch.med(1)-1; ch.dec(1) }
                2 -> { low=ch.med(0); ch.inc(0); low+=ch.med(1); ch.inc(1); high=low+ch.med(2)-1; ch.dec(2) }
                else -> { low=ch.med(0); ch.inc(0); low+=ch.med(1); ch.inc(1); low+=(ones-2)*ch.med(2); high=low+ch.med(2)-1; ch.inc(2) }
            }
            low+=bs.readCode(high-low)
            buf[i++]=signed(bs.getBit(),low)
        }
        return if (mono) i else i/2
    }
}
internal fun signed(sign: Int, mag: Int) = if (sign!=0) mag.inv() else mag
private val exp2Table=intArrayOf(

	0x00, 0x01, 0x01, 0x02, 0x03, 0x03, 0x04, 0x05, 0x06, 0x06, 0x07, 0x08, 0x08, 0x09, 0x0a, 0x0b,
	0x0b, 0x0c, 0x0d, 0x0e, 0x0e, 0x0f, 0x10, 0x10, 0x11, 0x12, 0x13, 0x13, 0x14, 0x15, 0x16, 0x16,
	0x17, 0x18, 0x19, 0x19, 0x1a, 0x1b, 0x1c, 0x1d, 0x1d, 0x1e, 0x1f, 0x20, 0x20, 0x21, 0x22, 0x23,
	0x24, 0x24, 0x25, 0x26, 0x27, 0x28, 0x28, 0x29, 0x2a, 0x2b, 0x2c, 0x2c, 0x2d, 0x2e, 0x2f, 0x30,
	0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3a, 0x3a, 0x3b, 0x3c, 0x3d,
	0x3e, 0x3f, 0x40, 0x41, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x48, 0x49, 0x4a, 0x4b,
	0x4c, 0x4d, 0x4e, 0x4f, 0x50, 0x51, 0x51, 0x52, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59, 0x5a,
	0x5b, 0x5c, 0x5d, 0x5e, 0x5e, 0x5f, 0x60, 0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69,
	0x6a, 0x6b, 0x6c, 0x6d, 0x6e, 0x6f, 0x70, 0x71, 0x72, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79,
	0x7a, 0x7b, 0x7c, 0x7d, 0x7e, 0x7f, 0x80, 0x81, 0x82, 0x83, 0x84, 0x85, 0x87, 0x88, 0x89, 0x8a,
	0x8b, 0x8c, 0x8d, 0x8e, 0x8f, 0x90, 0x91, 0x92, 0x93, 0x95, 0x96, 0x97, 0x98, 0x99, 0x9a, 0x9b,
	0x9c, 0x9d, 0x9f, 0xa0, 0xa1, 0xa2, 0xa3, 0xa4, 0xa5, 0xa6, 0xa8, 0xa9, 0xaa, 0xab, 0xac, 0xad,
	0xaf, 0xb0, 0xb1, 0xb2, 0xb3, 0xb4, 0xb6, 0xb7, 0xb8, 0xb9, 0xba, 0xbc, 0xbd, 0xbe, 0xbf, 0xc0,
	0xc2, 0xc3, 0xc4, 0xc5, 0xc6, 0xc8, 0xc9, 0xca, 0xcb, 0xcd, 0xce, 0xcf, 0xd0, 0xd2, 0xd3, 0xd4,
	0xd6, 0xd7, 0xd8, 0xd9, 0xdb, 0xdc, 0xdd, 0xde, 0xe0, 0xe1, 0xe2, 0xe4, 0xe5, 0xe6, 0xe8, 0xe9,
	0xea, 0xec, 0xed, 0xee, 0xf0, 0xf1, 0xf2, 0xf4, 0xf5, 0xf6, 0xf8, 0xf9, 0xfa, 0xfc, 0xfd, 0xff,
)
internal fun exp2s(log: Int): Int {
    if (log<0) return -exp2s(-log)
    val value=exp2Table[log and 255] or 256
    val exponent=log shr 8
    return if (exponent<=9) value ushr (9-exponent) else value shl ((exponent-9) and 31)
}
internal fun restoreWeight(w: Int): Int {
    var r=w*8
    if (r>0) r+=(r+64) shr 7
    return r
}

private val log2Table=intArrayOf(
    0x00, 0x01, 0x03, 0x04, 0x06, 0x07, 0x09, 0x0a, 0x0b, 0x0d, 0x0e, 0x10, 0x11, 0x12, 0x14, 0x15,
    0x16, 0x18, 0x19, 0x1a, 0x1c, 0x1d, 0x1e, 0x20, 0x21, 0x22, 0x24, 0x25, 0x26, 0x28, 0x29, 0x2a,
    0x2c, 0x2d, 0x2e, 0x2f, 0x31, 0x32, 0x33, 0x34, 0x36, 0x37, 0x38, 0x39, 0x3b, 0x3c, 0x3d, 0x3e,
    0x3f, 0x41, 0x42, 0x43, 0x44, 0x45, 0x47, 0x48, 0x49, 0x4a, 0x4b, 0x4d, 0x4e, 0x4f, 0x50, 0x51,
    0x52, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59, 0x5a, 0x5c, 0x5d, 0x5e, 0x5f, 0x60, 0x61, 0x62, 0x63,
    0x64, 0x66, 0x67, 0x68, 0x69, 0x6a, 0x6b, 0x6c, 0x6d, 0x6e, 0x6f, 0x70, 0x71, 0x72, 0x74, 0x75,
    0x76, 0x77, 0x78, 0x79, 0x7a, 0x7b, 0x7c, 0x7d, 0x7e, 0x7f, 0x80, 0x81, 0x82, 0x83, 0x84, 0x85,
    0x86, 0x87, 0x88, 0x89, 0x8a, 0x8b, 0x8c, 0x8d, 0x8e, 0x8f, 0x90, 0x91, 0x92, 0x93, 0x94, 0x95,
    0x96, 0x97, 0x98, 0x99, 0x9a, 0x9b, 0x9b, 0x9c, 0x9d, 0x9e, 0x9f, 0xa0, 0xa1, 0xa2, 0xa3, 0xa4,
    0xa5, 0xa6, 0xa7, 0xa8, 0xa9, 0xa9, 0xaa, 0xab, 0xac, 0xad, 0xae, 0xaf, 0xb0, 0xb1, 0xb2, 0xb2,
    0xb3, 0xb4, 0xb5, 0xb6, 0xb7, 0xb8, 0xb9, 0xb9, 0xba, 0xbb, 0xbc, 0xbd, 0xbe, 0xbf, 0xc0, 0xc0,
    0xc1, 0xc2, 0xc3, 0xc4, 0xc5, 0xc6, 0xc6, 0xc7, 0xc8, 0xc9, 0xca, 0xcb, 0xcb, 0xcc, 0xcd, 0xce,
    0xcf, 0xd0, 0xd0, 0xd1, 0xd2, 0xd3, 0xd4, 0xd4, 0xd5, 0xd6, 0xd7, 0xd8, 0xd8, 0xd9, 0xda, 0xdb,
    0xdc, 0xdc, 0xdd, 0xde, 0xdf, 0xe0, 0xe0, 0xe1, 0xe2, 0xe3, 0xe4, 0xe4, 0xe5, 0xe6, 0xe7, 0xe7,
    0xe8, 0xe9, 0xea, 0xea, 0xeb, 0xec, 0xed, 0xee, 0xee, 0xef, 0xf0, 0xf1, 0xf1, 0xf2, 0xf3, 0xf4,
    0xf4, 0xf5, 0xf6, 0xf7, 0xf7, 0xf8, 0xf9, 0xf9, 0xfa, 0xfb, 0xfc, 0xfc, 0xfd, 0xfe, 0xff, 0xff
)
internal fun wpLog2(value: Int): Int {
    val v=value+(value ushr 9)
    val bits=32-Integer.numberOfLeadingZeros(v)
    val index=if (bits<=9) (v shl (9-bits)) and 255 else (v ushr (bits-9)) and 255
    return (bits shl 8)+log2Table[index]
}
