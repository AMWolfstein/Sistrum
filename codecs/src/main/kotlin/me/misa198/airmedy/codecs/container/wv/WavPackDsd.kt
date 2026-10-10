// SPDX-License-Identifier: GPL-3.0-or-later
// Original adapter: libwavpack-derived raw output feeds the existing DSD pipeline and DoP packer.
// Raw port attribution is retained in codec/wavpack/Dsd.kt and THIRD-PARTY-NOTICES.
package me.misa198.airmedy.codecs.container.wv

import java.nio.ByteBuffer
import me.misa198.airmedy.codecs.container.RandomAccessSource
import me.misa198.airmedy.codecs.container.ByteBufferSource
import me.misa198.airmedy.codecs.audio.Buffer
import me.misa198.airmedy.codecs.audio.FloatBuffer
import me.misa198.airmedy.codecs.codec.dsd.DsdDecimationPipeline
import me.misa198.airmedy.codecs.codec.dsd.DopPacker
import me.misa198.airmedy.codecs.codec.wavpack.ErrorCode
import me.misa198.airmedy.codecs.codec.wavpack.WavPackException
import me.misa198.airmedy.codecs.container.dsd.Dsd

/** Borrowed PCM and DoP views with independent cursors. The caller owns the source. */
class WavPackDsd private constructor(source:RandomAccessSource,targetRate:Int) {
    private val pcmSource=Wv.open(source)
    private val rawInfo=pcmSource.info
    init { if (!rawInfo.isDsd) invalid("stream is not DSD") }
    private val dopSource=Wv.open(source)
    private val target=if (targetRate==0) Dsd.bestTargetRate(rawInfo.dsdSampleRate) else targetRate
    private val pipeline=DsdDecimationPipeline(rawInfo.dsdSampleRate,target,rawInfo.channels)
    private val packer=DopPacker(rawInfo.dsdSampleRate,rawInfo.channels)
    data class Info(val sampleRate:Int,val channels:Int,val totalSamples:Long,val dsdSampleRate:Int,
                    val totalDsdSamples:Long,val channelMask:Long,val dopSampleRate:Int,val totalDopSamples:Long)
    val info=Info(target,rawInfo.channels,rawInfo.totalDsdSamples/(rawInfo.dsdSampleRate/target),
        rawInfo.dsdSampleRate,rawInfo.totalDsdSamples,rawInfo.channelMask,packer.carrierRate,
        rawInfo.totalSamples/packer.bytesPerChannelPerFrame)
    private val pcmCapacity=pcmSource.demuxer.capacityFrames
    private val pcmPlanar=ByteArray(pcmCapacity*info.channels)
    private val pcmOffsets=IntArray(info.channels) { it*pcmCapacity }
    private val pcm=FloatBuffer(info.channels,(pcmCapacity*8/(info.dsdSampleRate/target))+1)
    private val dopStride=dopSource.demuxer.capacityFrames+packer.bytesPerChannelPerFrame
    private val dopPlanar=ByteArray(dopStride*info.channels)
    private val dopOffsets=IntArray(info.channels) { it*dopStride }
    private val dop=Buffer(info.channels,32,dopStride/packer.bytesPerChannelPerFrame+1)
    private var pcmPosition=0L;private var dopPosition=0L;private var carry=0
    private var pending=false;private var pcmDiscontinuity=false;private var dopDiscontinuity=false
    companion object {
        fun open(source:RandomAccessSource,targetRate:Int=0)=WavPackDsd(source,targetRate)
        fun open(source:ByteBuffer,targetRate:Int=0)=open(ByteBufferSource(source),targetRate)
        private fun invalid(message:String):Nothing=throw WavPackException(ErrorCode.INVALID_REQUEST,message)
    }
    fun decodeBlock():FloatBuffer? {
        if (pending) { pending=false;pcm.discontinuity=pcmDiscontinuity;pcmDiscontinuity=false;return pcm }
        while (true) {
            val raw=pcmSource.decodeDsdBlock() ?: return null
            var ch=0
            while (ch<info.channels) {
                var i=0;while (i<raw.bytesPerChannel) { pcmPlanar[pcmOffsets[ch]+i]=raw.bytes[i*info.channels+ch];i++ };ch++
            }
            pcm.frames=pipeline.processBytes(pcmPlanar,pcmOffsets,raw.bytesPerChannel,pcm.samples)
            pcm.position=pcmPosition;pcmPosition+=pcm.frames
            if (pcm.frames==0) continue
            pcm.discontinuity=pcmDiscontinuity;pcmDiscontinuity=false;return pcm
        }
    }
    /** Replay decimation history, then trim to the exact requested PCM sample. */
    fun seekSample(sample:Long) {
        if (sample<0 || sample>info.totalSamples) invalid("PCM sample out of range")
        pcmSource.seekSample(0);pipeline.reset();pcmPosition=0;pending=false;pcmDiscontinuity=false
        if (sample==info.totalSamples) { pcmSource.seekSample(rawInfo.totalSamples);pcmPosition=sample }
        else if (sample>0) {
            while (true) {
                val b=decodeBlock() ?: break
                if (pcmPosition>sample) {
                    val skip=(sample-b.position).toInt()
                    System.arraycopy(b.samples,skip*info.channels,b.samples,0,(b.frames-skip)*info.channels)
                    b.frames-=skip;b.position=sample;pending=true;break
                }
            }
        }
        pcmDiscontinuity=true
    }
    fun decodeDopBlock():Buffer? {
        while (true) {
            val raw=dopSource.decodeDsdBlock() ?: return null
            var ch=0
            while (ch<info.channels) {
                var i=0;while (i<raw.bytesPerChannel) { dopPlanar[dopOffsets[ch]+carry+i]=raw.bytes[i*info.channels+ch];i++ };ch++
            }
            val total=raw.bytesPerChannel+carry
            val complete=total-total%packer.bytesPerChannelPerFrame
            dop.frames=packer.packToI32(dopPlanar,dopOffsets,complete,dop.samples)
            carry=total-complete
            if (carry>0) {
                ch=0;while (ch<info.channels) { System.arraycopy(dopPlanar,dopOffsets[ch]+complete,dopPlanar,dopOffsets[ch],carry);ch++ }
            }
            if (dop.frames==0) continue
            dop.position=dopPosition;dopPosition+=dop.frames
            dop.discontinuity=dopDiscontinuity;dopDiscontinuity=false;return dop
        }
    }
    fun seekDopSample(sample:Long) {
        if (sample<0 || sample>info.totalDopSamples) invalid("DoP sample out of range")
        dopSource.seekSample(sample*packer.bytesPerChannelPerFrame)
        packer.seekFrame(sample);carry=0;dopPosition=sample;dopDiscontinuity=true
    }
}
