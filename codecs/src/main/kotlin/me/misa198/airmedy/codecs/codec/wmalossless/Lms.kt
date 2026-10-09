// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/wmalossless/lms.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.wmalossless

import java.util.Arrays
internal fun sign(x:Int)=when{ x>0->1;x<0->-1;else->0 }
internal class Cdlms {
    var order=0;var scaling=0
    private val coeff=IntArray(256);private val history=IntArray(512);private val update=IntArray(512)
    private var recent=0
    fun resize(order:Int,scaling:Int){this.order=order;this.scaling=scaling;Arrays.fill(coeff,0,order,0);Arrays.fill(history,0,2*order,0);Arrays.fill(update,0,2*order,0);recent=order}
    private fun advance(sample:Int,speed:Int,lo:Int,hi:Int){
        if(recent>0)recent-- else {System.arraycopy(history,0,history,order,order);System.arraycopy(update,0,update,order,order);recent=order-1}
        history[recent]=sample.coerceIn(lo,hi);update[recent]=sign(sample)*speed
        update[recent+(order ushr 4)]=update[recent+(order ushr 4)] shr 2
        update[recent+(order ushr 3)]=update[recent+(order ushr 3)] shr 1
    }
    fun run(x:IntArray,off:Int,n:Int,speed:Int,lo:Int,hi:Int){
        val bias=(1 shl scaling) shr 1
        for(i in off until off+n){val residue=x[i];var dot=0
            for(h in 0 until order){val c=coeff[h];dot+=c*history[recent+h];when{residue>0->coeff[h]=c+update[recent+h];residue<0->coeff[h]=c-update[recent+h]}}
            val sample=residue+((bias+dot) shr scaling);x[i]=sample;advance(sample,speed,lo,hi)
        }
    }
    fun rescaleUpdates(faster:Boolean,liveWindow:Boolean){val at=if(liveWindow)recent else 0;for(i in at until at+order)update[i]=if(faster)update[i]*2 else update[i]/2}
}
internal class Mclms(private val channels:Int) {
    var order=0;var scaling=0;private var recent=0
    val coeff=IntArray(32*channels*channels);val curCoeff=IntArray(channels*channels)
    private val history=IntArray(64*channels);private val update=IntArray(64*channels);private val pred=IntArray(channels)
    fun resize(order:Int,scaling:Int){this.order=order;this.scaling=scaling;val span=order*channels;Arrays.fill(coeff,0,span*channels,0);Arrays.fill(curCoeff,0);Arrays.fill(history,0,2*span,0);Arrays.fill(update,0,2*span,0);Arrays.fill(pred,0);recent=span}
    fun run(x:Array<IntArray>,coded:BooleanArray,off:Int,n:Int,lo:Int,hi:Int){
        val span=order*channels;val bias=(1 shl scaling) shr 1
        for(i in off until off+n){
            for(c in 0 until channels){if(!coded[c]){pred[c]=0;continue};var p=0
                for(h in 0 until span)p+=history[recent+h]*coeff[c*span+h]
                for(j in 0 until c)p+=x[j][i]*curCoeff[c*channels+j]
                p=(p+bias) shr scaling;pred[c]=p;x[c][i]+=p
            }
            for(c in 0 until channels){val error=x[c][i]-pred[c];if(error==0)continue
                if(error>0){for(h in 0 until span)coeff[c*span+h]+=update[recent+h];for(j in 0 until c)curCoeff[c*channels+j]+=sign(x[j][i])}
                else {for(h in 0 until span)coeff[c*span+h]-=update[recent+h];for(j in 0 until c)curCoeff[c*channels+j]-=sign(x[j][i])}
            }
            for(c in channels-1 downTo 0){if(recent>0)recent-- else {System.arraycopy(history,0,history,span,span);System.arraycopy(update,0,update,span,span);recent=span-1}
                history[recent]=x[c][i].coerceIn(lo,hi);update[recent]=sign(x[c][i])
            }
        }
    }
}
internal class AcFilter {
    var order=0;var scaling=0;val coeff=IntArray(16)
    fun run(x:IntArray,off:Int,n:Int,prev:IntArray){
        for(i in 0 until n){var p=0;for(j in 0 until order){val k=i-j-1;p+=coeff[j]*(if(k>=0)x[off+k] else prev[-k-1])};x[off+i]+=p shr scaling}
        for(j in order-1 downTo 0)prev[j]=if(j<n)x[off+n-1-j] else prev[j-n]
    }
}
