// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/wmapro/bits.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.wmapro

internal class BitReader {
    var buf=ByteArray(0);var n=0;var pos=0;var error:ProException?=null
    fun reset(bytes:ByteArray,bits:Int){buf=bytes;n=bits;pos=0;error=null}
    fun check(){error?.let{throw it}}
    fun overrun():Long {if(error==null)error=ProException(false,"the frame reads past the end of its $n-bit payload");return 0}
    private fun window():Long {val at=pos ushr 3;var acc=0L;for(j in 0..7){acc=acc shl 8;if(at+j<buf.size)acc=acc or (buf[at+j].toLong() and 255)};return acc}
    fun wide(width:Int):Long {if(width==0)return 0;require(width in 1..57);if(pos+width>n){pos=n;return overrun()};val v=(window() shl (pos and 7)) ushr (64-width);pos+=width;return v}
    fun bits(width:Int)=wide(width).toInt()
    fun bit():Int {if(pos>=n){pos=n;overrun();return 0};val v=(buf[pos ushr 3].toInt() ushr (7-(pos and 7))) and 1;pos++;return v}
    fun peek(width:Int):Long {if(width==0)return 0;require(width in 1..57);return (window() shl (pos and 7)) ushr (64-width)}
    fun skip(count:Int){if(count<0||pos+count>n){pos=n;overrun()}else pos+=count}
    fun seek(at:Int){if(at<0||at>n){pos=n;overrun()}else pos=at}
}
internal class BitAppender {
    val buf=ByteArray(1 shl 20);var bits=0
    fun reset(){bits=0}
    fun appendFrom(src:ByteArray,atStart:Int,count:Int){
        if(bits.toLong()+count>buf.size*8L)malformed("a frame spanning more than 1048576 bytes of carry")
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
