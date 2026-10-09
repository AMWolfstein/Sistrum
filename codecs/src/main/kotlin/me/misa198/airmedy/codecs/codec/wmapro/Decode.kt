// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/wmapro/decode.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.wmapro

import java.io.IOException
import java.util.Arrays
import me.misa198.airmedy.codecs.audio.FloatBuffer

fun interface FloatSink {fun emit(buffer:FloatBuffer)}
internal class Channel(frameLen:Int,maxBands:Int) {
    val buf=FloatArray(frameLen+frameLen/2);var prevLen=frameLen
    val subLens=IntArray(32);var subCount=0;var placed=0;var decoded=0;var subIdx=0
    val saved=IntArray(maxBands);var savedSize=0;var reuse=false;var sfRes=0
    val scale=IntArray(maxBands);var maxScale=0;var quantStep=0;var vecLimit=0;var transmits=false;var active=false
    val coefs=FloatArray(frameLen)
}
internal class Group(channels:Int,maxBands:Int){val chans=IntArray(channels);var count=0;var on=false;val matrix=FloatArray(channels*channels);var allBands=false;val bands=BooleanArray(maxBands)}

class Decoder(val cfg:Config) {
    init{cfg.validate()}
    internal val lay=Layout(cfg);internal val frameLen=cfg.samplesPerFrame;internal val frameLenBits=cfg.frameLenBits
    internal val maxSubframes=cfg.maxSubframes;internal val minSubframeLen=cfg.minSubframeLen;internal val frameSizeBits=cfg.frameSizeBits
    internal val sizes=cfg.sizeCount;private val maxBands=lay.edges.maxOf{it.size-1}
    internal val ch=Array(cfg.channels){Channel(frameLen,maxBands)}
    internal val scratch=ImdctScratch(frameLen);internal val spec=FloatArray(frameLen)
    internal val takes=BooleanArray(cfg.channels);internal val part=IntArray(cfg.channels);internal var partN=0
    internal val ungrouped=IntArray(cfg.channels);internal val rest=IntArray(cfg.channels)
    internal val angles=IntArray(cfg.channels*cfg.channels);internal val vec=FloatArray(cfg.channels)
    internal val groups=Array(cfg.channels){Group(cfg.channels,maxBands)};internal var nGroups=0;internal var explicitLimit=false
    internal val plans=Array(sizes){ImdctPlan(frameLen ushr it)};internal val mags=LongArray(4)
    private val r=BitReader();private val cr=BitReader();private val carry=BitAppender()
    private var seq= -1;private var dropNext=true
    private val out=FloatBuffer(cfg.channels,frameLen)
    private val undoBuf=Array(cfg.channels){FloatArray(frameLen+frameLen/2)};private val undoLen=IntArray(cfg.channels)
    init {Books.coef;Books.scaleDelta;Gains.table}
    fun decode(pkt:ByteArray,length:Int,sink:FloatSink){
        if(length>cfg.blockAlign)malformed("packet of $length bytes, longer than nBlockAlign ${cfg.blockAlign}")
        try {decodePacket(pkt,length,sink)}catch(e:IOException){carry.reset();dropNext=true;throw e}
    }
    private fun decodePacket(pkt:ByteArray,length:Int,sink:FloatSink){
        if(length==0)return
        r.reset(pkt,length*8);val nextSeq=r.bits(4);r.skip(2);val cont=r.bits(frameSizeBits);r.check()
        val gap=seq>=0&&nextSeq!=((seq+1) and 15);seq=nextSeq;val payload=r.n-r.pos;var continues=false
        when {
            gap->{carry.reset();dropNext=true;r.skip(minOf(cont,payload))}
            cont==0->carry.reset()
            carry.bits==0->{r.skip(minOf(cont,payload));dropNext=true}
            else->{val n=minOf(cont,payload);carry.appendFrom(pkt,r.pos,n);r.skip(n)
                val done=carriedFrame(sink)
                if(!done){if(cont>payload)continues=true else {carry.reset();dropNext=true}}
            }
        }
        if(!continues)framesIn(sink)
        if(length<cfg.blockAlign&&carry.bits>0){carry.reset();dropNext=true}
    }
    private fun carriedFrame(sink:FloatSink):Boolean {
        if(carry.bits<=frameSizeBits)return false
        cr.reset(carry.buf,carry.bits);val n=cr.bits(frameSizeBits)
        if(n<=frameSizeBits)malformed("a carried frame of $n bits cannot hold its own length prefix")
        if(n==cfg.blockAlign*8)return longFrame(sink)
        if(n>carry.bits)return false
        frame(cr,0,n,sink);carry.reset();return true
    }
    private fun longFrame(sink:FloatSink):Boolean {
        if(carry.bits>16*cfg.blockAlign*8)malformed("a frame spanning more than 16 packets")
        for(c in ch.indices){System.arraycopy(ch[c].buf,0,undoBuf[c],0,ch[c].buf.size);undoLen[c]=ch[c].prevLen}
        try {frame(cr,0,0,sink)}catch(e:IOException){
            if(cr.error!=null){for(c in ch.indices){System.arraycopy(undoBuf[c],0,ch[c].buf,0,ch[c].buf.size);ch[c].prevLen=undoLen[c]};return false};throw e
        }
        carry.reset();return true
    }
    private fun framesIn(sink:FloatSink){
        var i=0
        while(true){if(i++>=256)malformed("more than 256 frames in one packet")
            if(r.n-r.pos<=frameSizeBits)break
            val at=r.pos;val n=r.bits(frameSizeBits)
            if(n==0||n>r.n-at){r.seek(at);break}
            if(n<=frameSizeBits)malformed("a frame of $n bits cannot hold its own length prefix")
            val more=frame(r,at,n,sink);r.seek(at+n);if(!more)break
        }
        carry.reset();val left=r.n-r.pos;if(left>0)carry.appendFrom(r.buf,r.pos,left)
    }
    private fun frame(r:BitReader,at:Int,n:Int,sink:FloatSink):Boolean {
        tiling(r)
        if(cfg.channels>1&&r.bit()!=0&&r.bit()!=0)r.skip(4*cfg.channels*cfg.channels)
        if(cfg.hasDRC)r.skip(8)
        var start=0;var end=0
        if(r.bit()!=0){val w=frameLenBits+1;if(r.bit()!=0)start=r.bits(w);if(r.bit()!=0)end=r.bits(w)}
        subframes(r)
        when{n==0->r.skip(1);r.pos>at+n-1->malformed("the frame reads ${r.pos-(at+n-1)} bits past its declared length of $n");else->r.seek(at+n-1)}
        val more=r.bit()!=0;r.check();emitFrame(start,end,sink);return more
    }
    private fun emitFrame(start:Int,end:Int,sink:FloatSink){try {
        if(dropNext){dropNext=false;return}
        val lo=maxOf(start,0);val hi=minOf(frameLen-end,frameLen);if(lo>=hi)return
        out.frames=hi-lo;for(c in ch.indices)for(i in lo until hi)out.samples[(i-lo)*cfg.channels+c]=ch[c].buf[i]
        sink.emit(out)
    }finally{val half=frameLen/2;for(c in ch)System.arraycopy(c.buf,frameLen,c.buf,0,half)}}
    fun reset(){carry.reset();seq= -1;dropNext=true;for(c in ch){Arrays.fill(c.buf,0f);c.prevLen=frameLen;c.reuse=false;c.decoded=0;c.subIdx=0;c.placed=0;c.subCount=0}}
}
