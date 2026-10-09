// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/wma/huff.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.wma

internal class VLC(codes:IntArray,bits:IntArray) {
 private val kid:IntArray;private val maxLen:Int
 init{val k=ArrayList<Int>();k.add(0);k.add(0);var longest=0
 for(i in codes.indices){val n=bits[i];longest=maxOf(longest,n);var at=0
 for(b in n-1 downTo 0){val d=(codes[i] ushr b) and 1
 if(b==0){check(k[2*at+d]==0);k[2*at+d]= -1-i;break}
 var next=k[2*at+d];check(next>=0);if(next==0){k.add(0);k.add(0);next=k.size/2-1;k[2*at+d]=next};at=next}}
 kid=k.toIntArray();maxLen=longest}
 fun decode(r:BitReader):Int{val acc=r.peek(maxLen);var at=0;for(i in 0 until maxLen){val d=(acc ushr (maxLen-1-i)) and 1;val next=kid[2*at+d]
 if(next<0){if(r.pos+i+1>r.n){r.pos=r.n;r.failed=true;return -1};r.pos+=i+1;return -1-next};if(next==0)return -1;at=next};return -1}
}
internal class CoefBook(i:Int){val vlc=VLC(coefCodes[i],coefBits[i]);val run=IntArray(coefBits[i].size);val level=IntArray(run.size)
 init{var at=2;for(lv in coefLevels[i].indices){var r=0;while(r<coefLevels[i][lv]&&at<run.size){run[at]=r;level[at]=lv+1;at++;r++}}}}
internal object Books {
 val coefs=Array(6){CoefBook(it)}
 val exponent=VLC(expScaleCodes,expScaleBits)
 val gain:VLC
 init{val maxLen=hgainHuff.maxOf{it[1]};val codes=IntArray(hgainHuff.size);val bits=IntArray(codes.size);var acc=0
 for(i in codes.indices){codes[i]=acc ushr(maxLen-hgainHuff[i][1]);bits[i]=hgainHuff[i][1];acc+=1 shl(maxLen-bits[i])};gain=VLC(codes,bits)}
}
