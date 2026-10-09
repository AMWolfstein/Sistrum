// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/wma/bits.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.wma

internal class BitReader {
 var buf=ByteArray(0);var bytes=0;var n=0;var pos=0;var failed=false
 fun reset(b:ByteArray,length:Int= b.size,bits:Int=length*8){buf=b;bytes=length;n=bits;pos=0;failed=false}
 fun check(){if(failed)malformed("the frame reads past the end of its $bytes-byte packet")}
 fun bits(count:Int):Int {if(count==0)return 0;if(pos+count>n){pos=n;failed=true;return 0};val v=peek(count);pos+=count;return v}
 fun bit():Int=bits(1)
 fun peek(count:Int):Int {var acc=0;val i=pos ushr 3;for(j in 0..3){acc=acc shl 8;if(i+j<bytes)acc=acc or (buf[i+j].toInt() and 255)};return (acc shl (pos and 7)) ushr (32-count)}
 fun skip(count:Int){if(pos+count>n){pos=n;failed=true}else pos+=count}
 fun align(){val off=pos and 7;if(off!=0)skip(8-off)}
}
internal class BitAppender {
 val buf=ByteArray(32768);var bits=0
 fun reset(){bits=0}
 fun appendFrom(src:ByteArray,atStart:Int,n:Int){if((bits+n+7)/8>buf.size)malformed("the bit reservoir carry passes 32768 bytes");var at=atStart;var left=n
 while(left>0){if(bits and 7==0)buf[bits ushr 3]=0;if(at ushr 3<src.size&&(src[at ushr 3].toInt() ushr (7-(at and 7))) and 1!=0)buf[bits ushr 3]=(buf[bits ushr 3].toInt() or (1 shl (7-(bits and 7)))).toByte();at++;bits++;left--}}
}
