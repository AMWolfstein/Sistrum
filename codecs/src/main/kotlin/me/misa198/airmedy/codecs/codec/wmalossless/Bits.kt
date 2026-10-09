// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/wmalossless/bits.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.wmalossless

internal class BitReader {
    var buf=ByteArray(0);var n=0;var pos=0;var error:LosslessException?=null
    fun reset(bytes:ByteArray,bits:Int){buf=bytes;n=bits;pos=0;error=null}
    fun check(){error?.let{throw it}}
    private fun overrun():Int {if(error==null)error=LosslessException(false,"the frame reads past the end of its $n-bit run");return 0}
    fun bits(width:Int):Int {
        if(width==0)return 0
        if(width<0||width>32||pos+width>n){if(width in 0..32)pos=n;return overrun()}
        val at=pos ushr 3;val off=pos and 7;var acc=0L
        for(j in 0..7){acc=acc shl 8;if(at+j<buf.size)acc=acc or (buf[at+j].toLong() and 255)}
        pos+=width;return ((acc shl off) ushr (64-width)).toInt()
    }
    fun signed(width:Int):Int {
        if(width<=0||width>32){if(width!=0)overrun();return 0}
        val v=bits(width);return if(width==32)v else (v shl (32-width)) shr (32-width)
    }
    fun bit():Int {if(pos>=n){pos=n;return overrun()};val v=(buf[pos ushr 3].toInt() ushr (7-(pos and 7))) and 1;pos++;return v}
    fun unary(limit:Int):Int {for(i in 0..limit){if(pos>=n){overrun();return -1};if(bit()==0)return i};return -1}
    fun skip(count:Int){if(count<0||pos+count>n){pos=n;overrun()}else pos+=count}
}
internal class BitAppender {
    val buf=ByteArray(1 shl 20);var bits=0
    fun reset(){bits=0}
    fun appendFrom(src:ByteArray,atStart:Int,count:Int){
        if(bits.toLong()+count>buf.size*8L)malformed("a frame spanning more than 1048576 bytes of packets")
        var at=atStart;var n=count
        var off=bits and 7
        if(off!=0){val take=minOf(8-off,n);buf[bits ushr 3]=(buf[bits ushr 3].toInt() or (peek(src,at,take) shl (8-off-take))).toByte();bits+=take;at+=take;n-=take}
        if(n==0)return
        if(at and 7==0){val whole=minOf(n ushr 3,src.size-(at ushr 3));System.arraycopy(src,at ushr 3,buf,bits ushr 3,whole);bits+=whole*8;at+=whole*8;n-=whole*8}
        val sh=at and 7
        if(sh!=0){var i=at ushr 3;while(n>=8&&i+1<src.size){buf[bits ushr 3]=((src[i].toInt() shl sh) or ((src[i+1].toInt() and 255) ushr (8-sh))).toByte();bits+=8;i++;at+=8;n-=8}}
        while(n>0){off=bits and 7;if(off==0)buf[bits ushr 3]=0;val free=8-off;val take=minOf(free,n);buf[bits ushr 3]=(buf[bits ushr 3].toInt() or (peek(src,at,take) shl (free-take))).toByte();bits+=take;at+=take;n-=take}
    }
    private fun peek(src:ByteArray,at:Int,n:Int):Int {val i=at ushr 3;var acc=if(i<src.size)(src[i].toInt() and 255) shl 8 else 0;if(i+1<src.size)acc=acc or (src[i+1].toInt() and 255);return (acc ushr (16-(at and 7)-n)) and ((1 shl n)-1)}
}
