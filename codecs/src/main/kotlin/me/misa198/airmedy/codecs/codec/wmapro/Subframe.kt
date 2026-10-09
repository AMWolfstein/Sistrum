// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/wmapro/subframe.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.wmapro

import java.util.Arrays
import kotlin.math.*
internal object Gains {
    val table=FloatArray(657){10.0.pow((it-256)/20.0).toFloat()}
    val sin=FloatArray(33){sin(it*Math.PI/64).toFloat()}
    fun gain(exp:Int)=if(exp in -256..400)table[exp+256] else 10.0.pow(exp/20.0).toFloat()
}
internal fun Decoder.tiling(r:BitReader){
    val nc=cfg.channels
    for(c in ch){c.subCount=0;c.placed=0;c.decoded=0;c.subIdx=0;c.reuse=false}
    var atFrontier=nc;var frontier=0;val uniform=maxSubframes==1||r.bit()!=0;var step=0
    while(frontier<frameLen){if(step++>=nc*32)malformed("the tiling walk does not terminate")
        val last=frontier==frameLen-minSubframeLen
        for(c in 0 until nc)takes[c]=when{ch[c].placed!=frontier->false;uniform||atFrontier==1||last->true;else->r.bit()!=0}
        val length=subframeLen(r,frontier);frontier+=length
        for(c in 0 until nc){val channel=ch[c]
            if(takes[c]){if(channel.subCount>=32)malformed("channel $c takes more than 32 subframes");channel.subLens[channel.subCount++]=length;channel.placed+=length;if(channel.placed>frameLen)malformed("channel $c overruns the frame at ${channel.placed} samples");continue}
            if(channel.placed<=frontier){if(channel.placed<frontier){atFrontier=0;frontier=channel.placed};atFrontier++}
        };r.check()
    }
    for(c in ch.indices)if(ch[c].placed!=frameLen)malformed("channel $c tiles ${ch[c].placed} of $frameLen samples")
}
private fun Decoder.subframeLen(r:BitReader,frontier:Int):Int {
    if(frontier==frameLen-minSubframeLen)return minSubframeLen
    val depth=cfg.subframeDepth;val w=floorLog2(depth)+1
    val shift=if(maxSubframes==4||maxSubframes==16){if(r.bit()!=0)1+r.bits(w-1)else 0}else r.bits(w)
    if(shift>depth)malformed("a subframe length shift of $shift, want at most $depth")
    val length=frameLen ushr shift
    if(length<minSubframeLen||frontier+length>frameLen)malformed("a subframe of $length samples at frontier $frontier of $frameLen")
    return length
}
internal fun Decoder.subframes(r:BitReader){var step=0
    while(true){if(step++>=cfg.channels*32+1)malformed("the subframe walk does not terminate")
        var offset=frameLen;for(c in ch)offset=minOf(offset,c.decoded);if(offset>=frameLen)return
        var length=0
        for(c in ch.indices){val channel=ch[c];if(channel.decoded==offset){if(channel.subIdx>=channel.subCount)malformed("channel $c has no subframe at offset $offset");length=channel.subLens[channel.subIdx];break}}
        partN=0;for(c in ch.indices){val channel=ch[c];if(channel.decoded==offset&&channel.subIdx<channel.subCount&&channel.subLens[channel.subIdx]==length)part[partN++]=c}
        subframe(r,offset,length)
        for(p in 0 until partN){ch[part[p]].decoded+=length;ch[part[p]].subIdx++}
    }
}
internal fun Decoder.sizeIndex(length:Int)=floorLog2(frameLen/length)
private fun Decoder.subframe(r:BitReader,off:Int,length:Int){
    val k=sizeIndex(length);if(k>=sizes)malformed("a subframe of $length samples has no band layout")
    val edges=lay.edges[k];val nb=lay.numBands(k)
    if(r.bit()!=0)unsupported("a subframe carries the low-bit-rate tool's payload")
    if(r.bit()!=0)malformed("a reserved subframe bit is set")
    if(cfg.channels>1)channelGroups(r,k)else nGroups=0
    var any=false
    for(p in 0 until partN){val c=ch[part[p]];c.transmits=r.bit()!=0;any=any||c.transmits}
    if(any)quantisation(r,k,length)
    for(p in 0 until partN){val c=ch[part[p]];c.active=c.transmits;if(!c.transmits){Arrays.fill(c.coefs,0,length,0f);continue};coefficients(r,c,length)}
    r.check()
    for(gi in 0 until nGroups){val g=groups[gi];if(!g.on||g.count<2)continue;var live=false;for(j in 0 until g.count)live=live||ch[g.chans[j]].transmits;if(live)for(j in 0 until g.count)ch[g.chans[j]].active=true}
    applyTransforms(length,edges,nb)
    for(p in 0 until partN)reconstruct(part[p],off,length,edges,nb)
}
private fun Decoder.channelGroups(r:BitReader,k:Int){
    if(r.bit()!=0)unsupported("an unknown channel transform")
    nGroups=0;System.arraycopy(part,0,ungrouped,0,partN);var left=partN
    while(left>0&&nGroups<partN){val g=groups[nGroups];g.count=0;g.on=false;g.allBands=false
        if(left>2){var restN=0;for(j in 0 until left){val c=ungrouped[j];if(r.bit()!=0)g.chans[g.count++]=c else rest[restN++]=c};System.arraycopy(rest,0,ungrouped,0,restN);left=restN}
        else{System.arraycopy(ungrouped,0,g.chans,0,left);g.count=left;left=0}
        groupTransform(r,g)
        if(g.on){if(r.bit()!=0)g.allBands=true else for(b in 0 until lay.numBands(k))g.bands[b]=r.bit()!=0}
        nGroups++
    };r.check()
}
private fun Decoder.groupTransform(r:BitReader,g:Group){val n=g.count
    when {
        n<2->return
        n==2->{if(r.bit()==0){g.on=true;val s=if(cfg.channels==2)1f else (181.0/256.0).toFloat();g.matrix[0]=s;g.matrix[1]= -s;g.matrix[2]=s;g.matrix[3]=s;return};if(r.bit()!=0)unsupported("an unknown two-channel channel transform")}
        else->{if(r.bit()==0)return;g.on=true;if(r.bit()!=0){explicitMatrix(r,g);return};if(n>=defaultDecorrelation.size)unsupported("a built-in decorrelation matrix for a group of $n channels");System.arraycopy(defaultDecorrelation[n],0,g.matrix,0,n*n)}
    }
}
private fun Decoder.explicitMatrix(r:BitReader,g:Group){
    val n=g.count;for(i in 0 until n*(n-1)/2)angles[i]=r.bits(6);r.check()
    val m=g.matrix;Arrays.fill(m,0,n*n,0f);for(i in 0 until n)m[i*n+i]=if(r.bit()!=0)1f else -1f
    var p=0
    for(i in 1 until n){for(x in 0 until i){val a=angles[p+x];val s:Float;val c:Float
        if(a<32){s=Gains.sin[a];c=Gains.sin[32-a]}else{s=Gains.sin[64-a];c= -Gains.sin[a-32]}
        for(y in 0..i){val v1=m[x*n+y];val v2=m[i*n+y];m[x*n+y]=v1*s-v2*c;m[i*n+y]=v1*c+v2*s}
    };p+=i};r.check()
}
private fun Decoder.applyTransforms(length:Int,edges:IntArray,nb:Int){
    for(gi in 0 until nGroups){val g=groups[gi];val n=g.count;if(!g.on||n<2)continue
        for(b in 0 until nb){val lo=edges[b];val hi=minOf(edges[b+1],length)
            if(!g.allBands&&!g.bands[b]){if(cfg.channels==2)for(j in 0 until n){val co=ch[g.chans[j]].coefs;for(y in lo until hi)co[y]*=(181.0/128.0).toFloat()};continue}
            for(y in lo until hi){for(j in 0 until n)vec[j]=ch[g.chans[j]].coefs[y];for(m in 0 until n){var acc=0f;for(j in 0 until n)acc+=vec[j]*g.matrix[m*n+j];ch[g.chans[m]].coefs[y]=acc}}
        }
    }
}
private fun Decoder.reconstruct(c:Int,off:Int,length:Int,edges:IntArray,nb:Int){
    val channel=ch[c];val base=frameLen/2+off
    if(channel.active){for(b in 0 until nb){val lo=edges[b];val hi=minOf(edges[b+1],length);val exp=channel.quantStep-(channel.maxScale-channel.scale[b])*channel.sfRes
        if(exp>400)malformed("a band exponent of $exp, past the 400 this decoder holds finite")
        val gain=Gains.gain(exp);for(y in lo until hi)spec[y]=channel.coefs[y]*gain
    }
        val scale=2.0/length*Math.scalb(1.0,-(cfg.bitsPerSample-1));plans[sizeIndex(length)].imdct(spec,channel.buf,base,scale,scratch)
    }else Arrays.fill(channel.buf,base,base+length,0f)
    val ov=minOf(channel.prevLen,length);overlapButterfly(channel.buf,base-ov/2,ov,plans[sizeIndex(ov)].window);channel.prevLen=length
}
