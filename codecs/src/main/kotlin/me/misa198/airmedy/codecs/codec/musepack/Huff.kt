// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/musepack/huff.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.musepack

internal data class Entry(val code:Int,val length:Int,val value:Int)
internal data class Can(val name:String,val entries:Array<Entry>,val sym:IntArray)
internal class Vlc(entries:Array<Entry>,symbols:IntArray?=null) {
 private var kid=IntArray(2);private var sym=IntArray(0);private var maxLen=0
 init {val codes=ArrayList<Int>();val lens=ArrayList<Int>();val syms=ArrayList<Int>();var prev=0x10000
 for(e in entries){if(e.length==0)continue;val shift=16-e.length;val lo=e.code ushr shift;val hi=(prev-1) ushr shift
 for(c in lo..hi){codes.add(c);lens.add(e.length);syms.add(symbols?.get((e.value-c) and 255)?:e.value)};prev=e.code}
 sym=syms.toIntArray();for(i in codes.indices){val n=lens[i];maxLen=maxOf(maxLen,n);var at=0
 for(b in n-1 downTo 0){val d=(codes[i] ushr b) and 1;if(b==0){check(kid[2*at+d]==0);kid[2*at+d]=-1-i;break};var next=kid[2*at+d];check(next>=0);if(next==0){kid=kid.copyOf(kid.size+2);next=kid.size/2-1;kid[2*at+d]=next};at=next}}}
 fun decode(r:BitReader):Int {val acc=r.peek(maxLen);var at=0;for(i in 0 until maxLen){val d=(acc ushr (maxLen-1-i)) and 1;val next=kid[2*at+d];if(next<0){if(r.pos+i+1>r.end){r.overrun();throw malformed("frame carries a codeword outside its Huffman book")};r.pos+=i+1;return sym[-1-next]};if(next==0)break;at=next};throw malformed("frame carries a codeword outside its Huffman book")}
}
internal object Books7 {
 val hdr=Vlc(Tables.tableHuffHdr);val scfi=Vlc(Tables.tableHuffSCFI);val dscf=Vlc(Tables.tableHuffDSCF)
 val q=arrayOf(Tables.tableHuffQ1,Tables.tableHuffQ2,Tables.tableHuffQ3,Tables.tableHuffQ4,Tables.tableHuffQ5,Tables.tableHuffQ6,Tables.tableHuffQ7).map{pair->Array(2){Vlc(pair[it])}}.toTypedArray()
}
internal object Books8 {
 private fun book(c:Can)=Vlc(c.entries,c.sym)
 val bands=book(Tables.canBands);val q1=book(Tables.canQ1);val q9up=book(Tables.canQ9up)
 val scfi=Array(2){book(Tables.canSCFI[it])};val dscf=Array(2){book(Tables.canDSCF[it])};val res=Array(2){book(Tables.canRes[it])}
 val q=arrayOf(Tables.canQ2,arrayOf(Tables.canQ3,Tables.canQ4),Tables.canQ5,Tables.canQ6,Tables.canQ7,Tables.canQ8).map{pair->Array(2){book(pair[it])}}.toTypedArray()
}
