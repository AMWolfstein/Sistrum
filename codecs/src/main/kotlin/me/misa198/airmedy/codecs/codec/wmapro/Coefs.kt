// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/wmapro/coefs.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.wmapro

import java.util.Arrays
internal fun Decoder.quantisation(r:BitReader,k:Int,length:Int){
    explicitLimit=r.bit()!=0
    for(p in 0 until partN){val c=part[p];val v=if(explicitLimit)r.bits(floorLog2((length+3)/4)+1) shl 2 else length
        if(v>length)malformed("a vector coefficient limit of $v in a subframe of $length");ch[c].vecLimit=v}
    var delta=r.bits(6);if(delta>=32)delta-=64
    var step=((90*cfg.bitsPerSample) shr 4)+delta
    if(delta== -32||delta==31){var acc=0;while(true){val v=r.bits(5);if(r.error!=null)malformed("the quantisation step escape runs out of bits");acc+=v;if(v!=31)break;if(acc>1 shl 20)malformed("a quantisation step escape past 1048576")};step+=if(delta==31)acc else -acc}
    if(partN==1)ch[part[0]].quantStep=step else {val w=r.bits(3);for(p in 0 until partN){var q=step;if(r.bit()!=0)q+=if(w!=0)r.bits(w)+1 else 1;ch[part[p]].quantStep=q}}
    r.check();for(p in 0 until partN)scaleFactors(r,ch[part[p]],k);r.check()
}
private fun Decoder.scaleFactors(r:BitReader,c:Channel,k:Int){
    val nb=lay.numBands(k);val work=c.scale;Arrays.fill(work,0,nb,0)
    if(c.reuse){val m=lay.resample[k][c.savedSize];for(b in 0 until nb)work[b]=c.saved[m[b]]}
    if(c.subIdx==0||r.bit()!=0){
        if(!c.reuse){c.sfRes=r.bits(2)+1;var v=45/c.sfRes;for(b in 0 until nb){val s=Books.scaleDelta.decode(r);if(s<0)malformed("a scale factor delta codeword is not in the book");v+=s-60;work[b]=v}}
        else scaleDiffs(r,work,nb)
        System.arraycopy(work,0,c.saved,0,nb);c.savedSize=k;c.reuse=true
    }
    c.maxScale=work[0];for(b in 0 until nb)c.maxScale=maxOf(c.maxScale,work[b]);r.check()
}
private fun scaleDiffs(r:BitReader,work:IntArray,nb:Int){
    var b=0
    while(b<nb){val s=Books.scaleRunLevel.decode(r);if(s<0)malformed("a scale factor difference codeword is not in the book")
        val skip:Int;val value:Int;val sign:Int
        when(s){0->{val code=r.bits(14);value=code ushr 6;sign=(code and 1)-1;skip=(code and 63) ushr 1};1->{r.check();return};else->{skip=scaleRunLevelRuns[s];value=scaleRunLevelLevels[s];sign=r.bit()-1}}
        b+=skip;if(b>=nb)malformed("a scale factor run skips past the last of $nb bands")
        work[b]+=(value xor sign)-sign;r.check();b++
    }
}
internal fun Decoder.coefficients(r:BitReader,c:Channel,length:Int){
    val book=r.bit();val coefs=c.coefs;var cur=0;var zeros=0;var runLevel=false;val threshold=length ushr 8;val limit=minOf(c.vecLimit,length)
    while((explicitLimit||!runLevel)&&cur+3<limit){fourMagnitudes(r)
        for(m in mags){if(m!=0L){coefs[cur]=if(r.bit()!=0)m.toFloat()else(-m).toFloat();zeros=0}else{coefs[cur]=0f;zeros++;if(zeros>threshold)runLevel=true};cur++};r.check()
    }
    if(cur<length){Arrays.fill(coefs,cur,length,0f);runLevelTail(r,book,coefs,cur,length)}else r.check()
}
private fun Decoder.fourMagnitudes(r:BitReader){
    val s4=Books.vec4.decode(r);if(s4<0)malformed("a four-magnitude codeword is not in the book")
    var v=s4-1
    if(v>=0){mags[0]=((v ushr 12) and 15).toLong();mags[1]=((v ushr 8) and 15).toLong();mags[2]=((v ushr 4) and 15).toLong();mags[3]=(v and 15).toLong();return}
    for(h in 0..1){val s2=Books.vec2.decode(r);if(s2<0)malformed("a two-magnitude codeword is not in the book");v=s2-1
        if(v>=0){mags[2*h]=((v ushr 4) and 15).toLong();mags[2*h+1]=(v and 15).toLong();continue}
        for(j in 0..1){val one=Books.vec1.decode(r);if(one<0)malformed("a single-magnitude codeword is not in the book");mags[2*h+j]=one.toLong()+if(one==100)largeValue(r)else 0L}
    }
}
private fun largeValue(r:BitReader):Long {var n=8;if(r.bit()!=0){n+=8;if(r.bit()!=0){n+=8;if(r.bit()!=0)n+=7}};return r.wide(n)}
private fun runLevelTail(r:BitReader,book:Int,coefs:FloatArray,from:Int,length:Int){
    val width=floorLog2(length-1)+1;val b=Books.coef[book];val runs=coefRuns[book];val levels=coefLevels[book];var i=from
    while(i<length){val s=b.decode(r);if(s<0)malformed("a run-level codeword is not in the book");val level:Float
        when{ s>1->{i+=runs[s];level=levels[s]};s==1->{r.check();return};else->{level=largeValue(r).toFloat();if(r.bit()!=0){if(r.bit()==0)i+=r.bits(2)+1 else if(r.bit()!=0)malformed("a run-level escape sets all three run bits")else i+=r.bits(width)+4}}}
        r.check();if(i>=length)malformed("a run-level run reaches $i in a subframe of $length")
        coefs[i]=if(r.bit()!=0)level else -level;i++
    };r.check()
}
