// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from libwavpack 5.8.1 src/unpack_floats.c and src/open_utils.c,
// commit 4827b9889665b937b6ed71b9c6c0123152cd7a02.
// Copyright (c) 1998-2013 Conifer Software; 1998-2025 David Bryant.
// BSD-3-Clause; copyright, conditions and disclaimer in THIRD-PARTY-NOTICES.
package me.misa198.airmedy.codecs.codec.wavpack

import me.misa198.airmedy.codecs.bitstream.BitReader

/** Restore IEEE-754 bit patterns using integer operations, without normalization. */
internal class FloatFixup {
    private var flags=0;private var shift=0;private var maxExp=0
    private var minZeros=0;private var maxOnes=0;private var present=false
    fun reset() { flags=0;shift=0;maxExp=0;minZeros=0;maxOnes=0;present=false }
    fun readInfo(m:Metadata) {
        if (m.size!=4) malformed("float info of ${m.size} bytes, want 4")
        flags=m.byte(0);shift=m.byte(1);maxExp=m.byte(2);present=true
        // float_norm_exp is advisory unless OPEN_NORMALIZE is requested; it is not.
    }
    fun readWidths(bs:BitReader) { minZeros=bs.getBits(5) and 31;maxOnes=bs.getBits(5) and 31 }
    fun restore(buf:IntArray,length:Int,bs:BitReader,expectedCrc:Int,position:Long) {
        if (!present) malformed("block has no float info")
        val extra=bs.open();var crc= -1;var i=0
        while (i<length) {
            var v=buf[i];var exp=maxExp;var sign=0;var mantissa=0;var count=0
            if (v==0) {
                exp=0
                if (extra && flags and 8!=0) {
                    if (bs.getBit()!=0) {
                        mantissa=bs.getBits(23)
                        if (maxExp>=25) exp=bs.getBits(8)
                        sign=bs.getBit()
                    } else if (flags and 16!=0) sign=bs.getBit()
                }
            } else {
                v=v shl (shift and 31)
                if (v<0) { v= -v;sign=1 }
                if (extra && v==0x1000000) {
                    if (bs.getBit()!=0) mantissa=bs.getBits(23)
                    exp=255
                } else {
                    if (!extra && v>=0x1000000) {
                        while (v and 0xf000000!=0) { v=v shr 1;exp++ }
                    } else if (exp!=0) {
                        while (v and 0x800000==0) {
                            exp--;if (exp==0) break
                            count++;v=v shl 1
                        }
                        count=count and 31
                        if (count!=0) {
                            if (flags and 1!=0 || (extra && flags and 2!=0 && bs.getBit()!=0)) v=v or ((1 shl count)-1)
                            else if (extra && flags and 4!=0) {
                                val mask=(1 shl count)-1
                                var zeros=if (maxOnes!=0 && count>maxOnes) count-maxOnes else 0
                                if (minZeros>zeros) zeros=minOf(minZeros,count)
                                val read=count-zeros
                                if (read>0) v=v or ((bs.getBits(read) shl zeros) and mask)
                            }
                        }
                    }
                    mantissa=v and 0x7fffff
                }
            }
            exp=exp and 255;mantissa=mantissa and 0x7fffff
            buf[i++]=(sign shl 31) or (exp shl 23) or mantissa
            if (extra) crc=crc*27+mantissa*9+exp*3+sign
        }
        if (extra && (bs.over || crc!=expectedCrc)) malformed("block at sample $position fails its extension CRC")
    }
}
