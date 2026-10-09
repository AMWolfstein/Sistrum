// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/wmalossless/decode.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.wmalossless

import java.util.Arrays
import java.io.IOException
import me.misa198.airmedy.codecs.audio.Buffer

private class Channel {
    val g=Golomb();val filters=Array(8){Cdlms()};var filterCount=0;var updateSpeed=0;val acPrev=IntArray(16)
}
private object NotStarted:IOException("wmalossless: no seekable tile yet")

/** Incremental form of Go's packet/frame callbacks: consume ready frames before another packet. */
class Decoder(val cfg:Config) {
    init {cfg.validate()}
    private val frameLen=cfg.samplesPerFrame;private val frameLenBits=cfg.frameLenBits
    private val ch=Array(cfg.channels){Channel()};private val coded=BooleanArray(cfg.channels)
    private val ac=AcFilter();private var acOn=false;private val mc=Mclms(cfg.channels);private var mcOn=false
    private var decorr=false;private var movave=0;private var quantStep=1;private var started=false
    private var carry=BitAppender();private var spare=BitAppender();private var carryOpen=false;private var seq= -1
    private val r=BitReader();private val hdr=BitReader();private val out=Buffer(cfg.channels,cfg.bitsPerSample,frameLen)
    private val planes=Array(cfg.channels){IntArray(frameLen)}
    private val subLens=IntArray(32);private var subCount=0;private val counts=IntArray(cfg.channels)
    private val orders=IntArray(8);private val scalings=IntArray(8)
    private var error:IOException?=null;private var ready=false;private var ignoreBrokenCarry=false;private var frameCount=0
    fun acceptPacket(pkt:ByteArray,length:Int) {
        error?.let{throw it};check(!ready){"Consume ready frames before the next packet"}
        if(length>cfg.blockAlign)malformed("packet of $length bytes, longer than nBlockAlign ${cfg.blockAlign}")
        hdr.reset(pkt,length*8);val nextSeq=hdr.bits(4);hdr.bit();val spliced=hdr.bit()==1;var n=hdr.bits(cfg.frameSizeBits)
        try {hdr.check()}catch(e:IOException){error=e;throw e}
        if(spliced)unsupported("packet headerwmalossless: a spliced packet")
        try {
            if(seq>=0&&nextSeq!=((seq+1) and 15)){carry.reset();carryOpen=false;started=false};seq=nextSeq
            val off=6+cfg.frameSizeBits;val payload=length*8-off
            if(payload<=0)malformed("nBlockAlign $length leaves no payload after a $off-bit header")
            n=minOf(n,payload)
            if(carryOpen)carry.appendFrom(pkt,off,n)
            if(n>=payload)return
            if(carryOpen){r.reset(carry.buf,carry.bits);ready=true;frameCount=0;ignoreBrokenCarry=n==0;val old=carry;carry=spare;spare=old}
            carry.reset();carry.appendFrom(pkt,off+n,payload-n);carryOpen=true
        }catch(e:IOException){error=e;throw e}
    }
    fun finish(){error?.let{throw it};check(!ready);if(!carryOpen)return;r.reset(carry.buf,carry.bits);carryOpen=false;ready=true;frameCount=0;ignoreBrokenCarry=false}
    fun nextFrame():Buffer? {
        error?.let{throw it}
        while(ready){
            try {
                if(r.pos>=r.n)malformed("a packet whose last frame claims another follows it")
                if(frameCount++>=1024)malformed("more than 1024 frames in one packet")
                ready=frame();if(out.frames>0)return out
            }catch(e:IOException){
                ready=false
                if(e===NotStarted)return null
                if(ignoreBrokenCarry){started=false;return null}
                error=e;throw e
            }
        }
        return null
    }
    fun reset(){carry.reset();spare.reset();carryOpen=false;seq= -1;started=false;error=null;ready=false;r.reset(r.buf,0)}
    private fun frame():Boolean {
        val prefixed=cfg.decodeFlags and 64!=0;val start=r.pos;var frameBits=0
        if(prefixed){frameBits=r.bits(cfg.frameSizeBits);if(frameBits<2||start+frameBits>r.n)malformed("frame length prefix of $frameBits bits")}
        tiling()
        if(cfg.decodeFlags and 128!=0)r.skip(8)
        var startSkip=0;var endSkip=0
        if(r.bit()==1){if(r.bit()==1)startSkip=r.bits(frameLenBits+1);if(r.bit()==1)endSkip=r.bits(frameLenBits+1)}
        if(startSkip+endSkip>frameLen)malformed("skips of $startSkip and $endSkip in a $frameLen-sample frame")
        var at=0;for(i in 0 until subCount){subframe(at,subLens[i]);at+=subLens[i]};r.check()
        if(prefixed)r.pos=start+frameBits-1
        val more=r.bit()==1;r.check();out.frames=frameLen-startSkip-endSkip
        for(i in 0 until out.frames)for(c in ch.indices)out.samples[i*cfg.channels+c]=planes[c][startSkip+i]
        return more
    }
    private fun tiling(){
        val aligned=r.bit()==1;val fixed=cfg.maxSubframes==1||aligned;val last=frameLen-cfg.minSubframeLen
        val ratioBits=floorLog2(cfg.maxSubframes-1)+1;subCount=0;Arrays.fill(counts,0)
        while(true){var minLen=frameLen;var atMin=0
            for(v in counts){if(v<minLen){minLen=v;atMin=1}else if(v==minLen)atMin++}
            if(minLen==frameLen)break
            var takes=0;for(v in counts){if(v!=minLen)continue;if(fixed||atMin==1||minLen==last||r.bit()==1)takes++}
            r.check();if(takes==0)malformed("a subframe no channel takes")
            if(takes!=cfg.channels)unsupported("a frame whose channels are tiled differently ($takes of ${cfg.channels} channels take a subframe)")
            var length=cfg.minSubframeLen
            if(minLen!=last)length=cfg.minSubframeLen*(r.bits(ratioBits)+1)
            if(length<cfg.minSubframeLen||length>frameLen)malformed("subframe of $length samples, want ${cfg.minSubframeLen}..$frameLen")
            if(minLen+length>frameLen)malformed("a subframe of $length samples overruns a $frameLen-sample frame")
            if(subCount>=32)malformed("more than 32 subframes on one channel")
            subLens[subCount++]=length;for(i in counts.indices)counts[i]+=length
        }
    }
    private fun subframe(off:Int,n:Int){
        val seekable=r.bit()==1
        if(seekable){filterDefs();started=true}
        val raw=r.bit()==1
        if(!raw){for(c in coded.indices)coded[c]=r.bit()==1;if(cfg.decodeFlags and 256!=0&&r.bit()==1)unsupported("the LPC filter, which no available description covers")}
        val padding=if(r.bit()==1)r.bits(5)else 0;r.check()
        if(raw){val width=cfg.bitsPerSample-padding;if(width<1)malformed("$padding padding zeroes in a ${cfg.bitsPerSample}-bit raw PCM tile")
            for(c in planes.indices)for(i in off until off+n)planes[c][i]=r.signed(width)
            r.check();shiftOut(off,n,padding,false);return
        }
        if(padding>cfg.bitsPerSample)malformed("$padding padding zeroes in a ${cfg.bitsPerSample}-bit stream")
        if(!started)throw NotStarted
        val lo= -1 shl (cfg.bitsPerSample-1);val hi= -(lo+1);val extra=if(decorr)1 else 0
        for(c in planes.indices){val x=planes[c]
            if(!coded[c]){Arrays.fill(x,off,off+n,0);continue}
            if(r.bit()==1)r.skip(floorLog2(n))
            var first=0
            if(seekable){ch[c].g.seed(r.bits(cfg.bitsPerSample),movave);x[off]=r.signed(cfg.bitsPerSample+extra);first=1}
            for(i in first until n)x[off+i]=ch[c].g.next(r)
            r.check();cascade(c,x,off,n,seekable,lo,hi)
        }
        if(mcOn)mc.run(planes,coded,off,n,lo,hi)
        if(decorr&&cfg.channels==2&&(coded[0]||coded[1]))for(i in off until off+n){planes[0][i]-=planes[1][i] shr 1;planes[1][i]+=planes[0][i]}
        if(acOn)for(c in planes.indices)ac.run(planes[c],off,n,ch[c].acPrev)
        shiftOut(off,n,padding,true)
    }
    private fun cascade(c:Int,x:IntArray,off:Int,n:Int,seekable:Boolean,lo:Int,hi:Int){
        val channel=ch[c];val want=if(seekable)16 else 8
        if(channel.updateSpeed!=want){val live=cfg.decodeFlags and 256!=0;for(f in 0 until channel.filterCount)channel.filters[f].rescaleUpdates(want>channel.updateSpeed,live);channel.updateSpeed=want}
        for(f in channel.filterCount-1 downTo 0)channel.filters[f].run(x,off,n,channel.updateSpeed,lo,hi)
    }
    private fun shiftOut(off:Int,n:Int,padding:Int,quantise:Boolean){for(p in planes)for(i in off until off+n){var v=p[i];if(quantise&&quantStep!=1)v*=quantStep;p[i]=if(cfg.bitsPerSample==16)(v shl padding).toShort().toInt()else ((v shl padding) shl 8) shr 8}}
    private fun filterDefs(){
        if(r.bit()==1)unsupported("arithmetic coding")
        acOn=r.bit()==1;decorr=r.bit()==1;mcOn=r.bit()==1
        if(acOn){ac.order=r.bits(4)+1;ac.scaling=r.bits(4);for(i in 0 until ac.order)ac.coeff[i]=(r.bits(ac.scaling)+1).toShort().toInt()}else ac.order=0
        for(c in ch)Arrays.fill(c.acPrev,0,ac.order,0)
        if(mcOn){val order=(r.bits(4)+1)*2;val scaling=r.bits(4);mc.resize(order,scaling)
            if(r.bit()==1){val w=r.bits(ceilLog2(scaling+1))+2
                for(i in 0 until order*cfg.channels*cfg.channels)mc.coeff[i]=r.bits(w).toShort().toInt()
                for(c in 1 until cfg.channels)for(j in 0 until c)mc.curCoeff[c*cfg.channels+j]=r.bits(w).toShort().toInt()
            }
        }
        if(r.bit()==1)unsupported("transmitted CDLMS coefficients, whose scaling is undetermined")
        for(c in ch){val count=r.bits(3)+1
            for(f in 0 until count)orders[f]=(r.bits(7)+1)*8
            for(f in 0 until count)scalings[f]=r.bits(4)
            r.check();c.filterCount=count
            for(f in 0 until count){if(orders[f]>256)malformed("CDLMS order ${orders[f]}, want at most 256");c.filters[f].resize(orders[f],scalings[f])}
        }
        movave=r.bits(3);quantStep=r.bits(8)+1;r.check()
        for(c in ch){c.g.aveSum=0;c.g.scaling=movave}
    }
}
