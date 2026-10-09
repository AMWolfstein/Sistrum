// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/wmavoice/decode.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.wmavoice

import me.misa198.airmedy.codecs.audio.FloatBuffer
import java.util.Arrays
import kotlin.math.PI

/** Incremental form of the source packet callback. Consume nextFrame before accepting another packet. */
class Decoder(internal val cfg:Config) {
    init { cfg.validate() }
    internal val g=cfg.geom();internal val lsps=cfg.lsps;internal val tf=Transforms()
    private val r=BitReader();private val cr=BitReader()
    private val carry=BitAppender(cfg.blockAlign);private val joined=BitAppender(2*cfg.blockAlign)
    private var residualLSP=false;private var pendingJoined=false;private var remaining=0;private var live=false
    internal val excBase=g.history;internal val synthBase=lsps;internal val zeroBase=g.history
    internal val exc=FloatArray(excBase+480+16);internal val synth=FloatArray(lsps+480)
    internal val zero=FloatArray(zeroBase+480);internal val resynth=FloatArray(lsps+80);internal val smoothed=FloatArray(80)
    internal val prevLSF=DoubleArray(16);internal var pitchState=0;internal var prevACB=ACB_NONE
    internal val gainPredErr=FloatArray(6);internal var frameCounter=0;internal var agcMem=0f
    internal val dcMem=FloatArray(2);internal val cache=FloatArray(160);internal var cacheLen=0
    internal val lsf=Array(3){DoubleArray(16)};internal var frameIdx=0
    internal val pitch=IntArray(8);internal val pitchQ=IntArray(8)
    internal var asymBase=0;internal var curPitch=0;internal var pitchSlope=0;internal var lastPitchField=0;internal var firstBlockPitch=0
    internal var silenceGain=0f;internal val aw=AwCoords();internal var awNextOff=0
    internal val pulses=FloatArray(160);internal val awMask=IntArray(9)
    internal val lpc=FloatArray(16);internal val lspScratch=DoubleArray(16);internal val paScratch=DoubleArray(9);internal val qaScratch=DoubleArray(9)
    internal val lspIndices=IntArray(5);internal val lsfPrevWork=DoubleArray(16);internal val lsfIndep=DoubleArray(16)
    internal val lsfA1=DoubleArray(32);internal val lsfA2=DoubleArray(32)
    internal val pfWork=FloatArray(128);internal val pfSpec=FloatArray(128);internal val pfIR=FloatArray(128)
    internal val pfLPCSpec=FloatArray(130);internal val pfCoefs=FloatArray(130);internal val pfSigSpec=FloatArray(130);internal val pfIRSpec=FloatArray(130)
    internal val pfGain=FloatArray(65);internal val pfMag=FloatArray(65)
    internal val pfDct=FloatArray(64);internal val pfH=FloatArray(64);internal val pfS=FloatArray(64)
    internal var acbGain=0f;internal var fcbGain=0f
    private val out=FloatBuffer(1,480);private val plane=out.samples
    init { warmFrameTypes();warmLsp();warmExcitation();energyTable.size;reset() }

    fun acceptPacket(pkt:ByteArray,size:Int=pkt.size) {
        require(size in 0..pkt.size);check(!live&&!pendingJoined){"Consume the preceding packet first"}
        if(size==0){finish();return}
        if(size>cfg.blockAlign){carry.reset();malformed("packet of $size bytes, longer than nBlockAlign ${cfg.blockAlign}")}
        try {
            r.reset(pkt,size*8);r.skip(4);residualLSP=r.bit()!=0
            var count=0
            while(true){if(r.n-r.pos<6+g.spilloverBits)malformed("the packet header runs past the packet");val v=r.bits(6);count+=v;if(v!=63)break}
            val spill=r.bits(g.spilloverBits);r.check()
            if(count==0)malformed("a superframe count of zero, which no packet has")
            val payload=r.pos
            if(spill>r.n-r.pos)malformed("the packet declares $spill spillover bits with ${r.n-r.pos} left")
            if(carry.bits>0){joined.reset();joined.appendFrom(carry.buf,0,carry.bits);joined.appendFrom(pkt,payload,spill);cr.reset(joined.buf,joined.bits);carry.reset();pendingJoined=true}
            r.seek(payload+spill);remaining=count-1;live=true
        } catch(e:VoiceException){carry.reset();pendingJoined=false;live=false;throw e}
    }
    /** Returns a borrowed 480-sample buffer, or null after this packet is consumed. */
    fun nextFrame():FloatBuffer? {
        try {
            while(true){
                if(pendingJoined){pendingJoined=false;superframe(cr);if(out.frames>0)return out}
                else if(live&&remaining>0){remaining--;superframe(r);if(out.frames>0)return out}
                else {if(live){carry.reset();carry.appendFrom(r.buf,r.pos,r.n-r.pos);live=false};return null}
            }
        } catch(e:VoiceException){carry.reset();pendingJoined=false;live=false;throw e}
    }
    fun finish(){check(!live&&!pendingJoined){"Consume the preceding packet first"};if(carry.bits>0){cr.reset(carry.buf,carry.bits);carry.reset();pendingJoined=true}}
    private fun superframe(r:BitReader){
        val speech=r.bit();r.check()
        if(speech==0)unsupported("the superframe carries a WMA Pro payload rather than speech, unless a count overstated the packet and this is its padding")
        var samples=480
        if(r.bit()!=0){samples=r.bits(12);r.check();if(samples>480)malformed("the superframe declares $samples samples, want at most 480")};r.check()
        if(residualLSP){residualLSFs(r);r.check();for(f in 0..2)stabilise(lsf[f],lsps)}
        for(f in 0..2){if(!residualLSP){independentFrameLSFs(r,lsf[f]);r.check();stabilise(lsf[f],lsps)};frame(r,f)}
        if(r.bit()!=0){val k=r.bits(4);r.skip(10*(k+1))};r.check()
        for(x in plane)if(!(absF(x)<=1048576f)){roll();clearHistory();malformed("the synthesis diverged past a magnitude of 1048576")}
        out.frames=samples;roll()
    }
    private fun frame(r:BitReader,idx:Int){
        frameIdx=idx;val desc=frameTypes[readFrameType(r)]
        if(desc.acb==ACB_ASYM)framePitch(r,desc)
        silenceGain=0f;if(desc.fcb==FCB_SILENCE)silenceGain=gainSilence[r.bits(8)]
        if(desc.fcb==FCB_WINDOW)awReadCoords(r);r.check()
        val prev=if(idx==0)prevLSF else lsf[idx-1];val current=lsf[idx];val base=idx*160;val size=160/desc.blocks
        for(b in 0 until desc.blocks){
            val at=base+b*size
            val lag=when(desc.acb){ACB_ASYM->pitch[b];ACB_HAMMING->{pitchQ[b]=blockPitch(r,b==0);pitchQ[b] shr 2};else->0}
            if(b==0)firstBlockPitch=lag
            block(r,at,size,b,lag,desc);lsfToLPC(prev,current,(b+0.5)/desc.blocks);synthesise(at,size)
        }
        if(cfg.postfilter){lsfToLPC(prev,current,0.5);postfilter(plane,base,base,desc.fcb,firstBlockPitch);lsfToLPC(prev,current,1.0);postfilter(plane,base+80,base+80,desc.fcb,firstBlockPitch)}
        else System.arraycopy(synth,synthBase+base,plane,base,160)
        pitchState=when(desc.acb){ACB_ASYM->curPitch;ACB_HAMMING->pitchQ[desc.blocks-1] shr 2;else->0};prevACB=desc.acb;r.check()
    }
    private fun block(r:BitReader,at:Int,size:Int,idx:Int,pitchLag:Int,desc:FrameDesc){
        val eAt=excBase+at
        when(desc.fcb){
            FCB_SILENCE->{comfortNoise(eAt,size,idx,silenceGain);gainPredErr.fill(0f);r.check();return}
            FCB_HARDCODED->{val off=r.bits(8);val gain=gainUniversal[r.bits(6)];r.check();for(m in 0 until size)exc[eAt+m]=stdCodebook[off+m]*gain;gainPredErr.fill(0f);return}
        }
        Arrays.fill(pulses,0,size,0f)
        if(desc.fcb==FCB_WINDOW){awFirstSet(r,idx,pitchLag);r.check();if(!awSecondSet(r,idx,pitchLag)){r.skip(8);comfortNoise(eAt,size,idx,silenceGain);r.check();return}}
        else innovationPulses(r,desc)
        r.check()
        when(desc.acb){ACB_ASYM->acbAsymmetric(excBase+at-idx*size,idx,size);ACB_HAMMING->acbHammingBlock(eAt,size,pitchQ[idx])}
        blockGains(r,desc.log2);r.check()
        for(m in 0 until size)exc[eAt+m]=acbGain*exc[eAt+m]+fcbGain*pulses[m]
    }
    private fun roll(){
        System.arraycopy(exc,480,exc,0,excBase);System.arraycopy(synth,480,synth,0,synthBase);System.arraycopy(zero,480,zero,0,zeroBase)
        System.arraycopy(lsf[2],0,prevLSF,0,16);frameCounter+=3;if(frameCounter>=65535)frameCounter-=65535
    }
    private fun clearHistory(){exc.fill(0f);synth.fill(0f);zero.fill(0f);resynth.fill(0f);smoothed.fill(0f);cache.fill(0f);cacheLen=0;agcMem=0f;dcMem.fill(0f)}
    fun reset(){carry.reset();pendingJoined=false;remaining=0;live=false;clearHistory();gainPredErr.fill(0f);frameCounter=0;pitchState=40;prevACB=ACB_NONE;awNextOff=0;for(n in 0 until lsps)prevLSF[n]=(n+1)*PI/(lsps+1)}
    fun setPosition(sample:Long){frameCounter=(3*((maxOf(0,sample)+240)/480)%65535).toInt()}
}
