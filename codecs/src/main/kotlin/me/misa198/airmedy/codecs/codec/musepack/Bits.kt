// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/musepack/bits.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.musepack

internal class BitReader {
 var data=ByteArray(0);var start=0;var pos=0;var end=0;var over=false
 fun reset(b:ByteArray,offset:Int=0,bitLen:Int=(b.size-offset)*8){data=b;start=offset;pos=0;end=bitLen;over=false}
 fun overrun():Int {over=true;pos=end;return 0}
 fun bits(n:Int):Int {if(n==0)return 0;if(pos+n>end)return overrun();val v=peek(n);pos+=n;return v}
 fun bit():Int {if(pos>=end)return overrun();val b=(data[start+(pos ushr 3)].toInt() ushr (7-(pos and 7))) and 1;pos++;return b}
 fun peek(n:Int):Int {var acc=0L;val i=start+(pos ushr 3);for(j in 0..7){acc=acc shl 8;if(i+j<data.size)acc=acc or (data[i+j].toLong() and 255)};return ((acc shl (pos and 7)) ushr (64-n)).toInt()}
 fun varint():Long {var v=0L;repeat(9){val b=bits(8);v=(v shl 7) or (b.toLong() and 127);if(b and 128==0){if(over)throw malformed("stream header truncated");return v}};throw malformed("stream header truncated")}
 fun golomb(k:Int):Int {var q=0;while(bit()==0){if(over||++q>31-k)throw malformed("invalid Golomb code")};return (q shl k) or bits(k)}
 fun logDec(max:Int):Int=if(max<=0)0 else truncated(max+1)
 fun truncated(v:Int):Int {val l=32-Integer.numberOfLeadingZeros(v-1);val lost=(1 shl l)-v;var code=bits(l-1);if(code>=lost)code=((code shl 1) or bit())-lost;return code}
 fun enumDec(kk:Int,nn:Int):Int {var k=kk;var n=nn;var code=truncated(binomial[n][k]);var set=0;while(k>0&&n>0){n--;val c=binomial[n][k];if(code>=c){set=set or (1 shl n);code-=c;k--}};return set}
 companion object {val binomial=Array(33){IntArray(17)}.also{c->for(n in 0..32){c[n][0]=1;for(k in 1..minOf(16,n))c[n][k]=c[n-1][k-1]+c[n-1][k]}}}
}
