// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/wmavoice/window.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.wmavoice

import java.util.Arrays
internal class AwCoords {var range=16;var extended=false;val nPulses=IntArray(2);val firstOff=IntArray(2)}
internal fun Decoder.awReadCoords(r:BitReader){
    var b=r.bits(6);aw.extended=b>=54;if(aw.extended)b+=(b-54)*3+r.bits(2);r.check()
    val start=awStartOffsets[b];val p0=pitch[0];val p1=pitch[1]
    if(p0<1||p1<1)malformed("window pulse coder reached with pitch $p0, $p1")
    aw.range=if(minOf(p0,p1)>32)24 else 16;var offset=start;while(offset<0)offset+=p0
    aw.nPulses[0]=(p0-1+80-offset)/p0;aw.firstOff[0]=offset-aw.range/2;offset+=aw.nPulses[0]*p0
    aw.nPulses[1]=(p1-1+160-offset)/p1;aw.firstOff[1]=offset-(160+aw.range)/2
    if(start<80){while(aw.firstOff[1]-p1+aw.range>0)aw.firstOff[1]-=p1;if(start<0)while(aw.firstOff[0]-p0+aw.range>0)aw.firstOff[0]-=p0}
}
internal fun Decoder.awFirstSet(r:BitReader,block:Int,pitchLag:Int){
    val width=if(aw.extended&&block==0)10 else 12;var field=r.bits(width)
    if(aw.nPulses[block]>0){val count=if(aw.range==24)3 else 4;val signMask=if(aw.range==24)8 else 4;val idxMask=if(aw.range==24)7 else 3;val sh=if(aw.range==24)4 else 3
        for(n in count-1 downTo 0){val sign=if(field and signMask!=0)-1f else 1f;var pos=(field and idxMask)*count+n+aw.firstOff[block]
            while(pos<0)pos+=pitchLag;if(pos<80)scatter(pulses,pos,sign,pitchLag,true);field=field shr sh};return
    }
    val v=(field and 511) ushr 1;val sign=if(field and 512!=0)-1f else 1f
    val gap:Int;val i:Int
    when{v<79->{gap=1;i=v+1};v<156->{gap=3;i=v+1-77};v<231->{gap=5;i=v+1-152};else->{gap=7;i=v+1-225}}
    if(i-gap>=0&&i<80){scatter(pulses,i-gap,sign,pitchLag,false);scatter(pulses,i,if(field and 1!=0)-sign else sign,pitchLag,false)}
}
internal fun Decoder.awSecondSet(r:BitReader,block:Int,pitchLag:Int):Boolean {
    val mask=awMask;Arrays.fill(mask,0);for(i in 0 until 80)mask[(i+32) ushr 4]=mask[(i+32) ushr 4] or (1 shl ((15-(i+32)) and 15))
    var pulseOff=aw.firstOff[block]
    if(aw.nPulses[block]>0)while(pulseOff+aw.range<1)pulseOff+=pitchLag
    var span=16
    if(aw.nPulses[0]>0){if(block==0)span=32 else {span=8;if(aw.nPulses[1]>0)pulseOff=awNextOff}}
    var pulseStart=0
    if(aw.nPulses[block]>0){pulseStart=pulseOff-span/2;var idx=pulseOff
        while(idx<80){for(i in 0 until aw.range){val at=idx+i+32;mask[at ushr 4]=mask[at ushr 4] and (1 shl ((15-at) and 15)).inv()};idx+=pitchLag}
    }
    val width=if(aw.nPulses[0]>0)5-2*block else 4;val wanted=r.bits(width)
    var n=0;var start=0;var p=pulseStart
    while(n<=wanted){var idx=p;while(idx<0)idx+=pitchLag
        if(idx>=80){idx= -1;for(w in 0 until 5)if(mask[w+2]!=0){idx=16*w+15-floorLog2(mask[w+2]);break};if(idx<0)return false}
        val at=idx+32;val bit=1 shl ((15-at) and 15)
        if(mask[at ushr 4] and bit!=0){mask[at ushr 4]=mask[at ushr 4] and bit.inv();n++;start=idx};p++
    }
    val sign=if(r.bit()!=0)-1f else 1f;scatter(pulses,start,sign,pitchLag,true)
    val rem=(80-start)%pitchLag;awNextOff=if(rem!=0)pitchLag-rem else 0;return true
}
private fun scatter(pulses:FloatArray,pos:Int,sign:Float,pitchLag:Int,repeat:Boolean){var x=pos;while(x<80){pulses[x]+=sign;if(!repeat)return;x+=pitchLag}}
