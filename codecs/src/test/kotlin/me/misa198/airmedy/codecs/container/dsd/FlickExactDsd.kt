// SPDX-License-Identifier: GPL-3.0-or-later
// Adapter for dsf-meta 0.3.0 / dff-meta 0.2.0 (MIT OR Apache-2.0) and Flick
// rust/src/audio/dsd_engine/dsd at 79da4ed76557c8ddf534e898480dde66bcc90334 (MIT).
// Original source notices are retained in THIRD-PARTY-NOTICES.
package me.misa198.airmedy.codecs.container.dsd

import java.io.IOException
import java.nio.ByteBuffer
import me.misa198.airmedy.codecs.audio.Buffer
import me.misa198.airmedy.codecs.audio.FloatBuffer
import me.misa198.airmedy.codecs.container.*
import me.misa198.airmedy.codecs.container.dsf.Dsf
import me.misa198.airmedy.codecs.container.dff.Dff
import me.misa198.airmedy.codecs.codec.dsd.*

/** Borrowed output blocks. PCM and DoP have independent sample cursors. */
internal class FlickExactDsd private constructor(private val source:RandomAccessSource,private val header:Header,targetRate:Int=176400){
    data class Info(val sampleRate:Int,val channels:Int,val bitsPerSample:Int,val totalSamples:Long,val dsdSampleRate:Int,val totalDsdSamples:Long,val channelLayout:String,val dopSampleRate:Int)
    private val pipeline=FlickExactDecimationPipeline(header.rate,targetRate,header.channels)
    private val packer=DopPacker(header.rate,header.channels)
    val info=Info(targetRate,header.channels,32,header.bytesPerChannel*8/(header.rate/targetRate),header.rate,header.bytesPerChannel*8,header.layout,packer.carrierRate)
    private val planar=ByteArray(4096*header.channels)
    private val interleaved=ByteArray(planar.size)
    private val input=ByteBuffer.wrap(interleaved)
    private val offsets=IntArray(header.channels)
    private val pcm=FloatBuffer(header.channels,4096*8/(header.rate/targetRate))
    private val dop=Buffer(header.channels,32,4096/packer.bytesPerChannelPerFrame)
    private var bytePosition=0L;private var pcmPosition=0L;private var dopBytePosition=0L;private var dopPosition=0L
    private var pending=false;private var discontinuity=false;private var dopDiscontinuity=false
    companion object {
        fun open(buffer:ByteBuffer,targetRate:Int=176400)=open(ByteBufferSource(buffer),targetRate)
        fun open(source:RandomAccessSource,targetRate:Int=176400):FlickExactDsd {
            val h=headerBytes(source,0,4);val parsed=when(tag(h,0)){"DSD "->Dsf.parse(source);"FRM8"->Dff.parse(source);else->throw IOException("dsd: unsupported container")}
            if(parsed.rate !in intArrayOf(2822400,5644800,11289600))throw IOException("dsd: unsupported sample rate")
            val targets=when(parsed.rate){2822400->intArrayOf(176400,88200,44100);5644800->intArrayOf(352800,176400,88200,44100);else->intArrayOf(705600,352800,176400,88200)}
            if(targetRate !in targets)throw IOException("dsd: unsupported PCM target")
            return FlickExactDsd(source,parsed,targetRate)
        }
    }
    private fun readPlanar(position:Long,n:Int){
        for(ch in 0 until header.channels)offsets[ch]=ch*n
        if(header.blocked){
            for(ch in 0 until header.channels){var done=0;while(done<n){val p=position+done;val count=minOf(n-done,4096-(p%4096).toInt());input.clear();input.limit(count);readExact(source,header.dataOffset+(p/4096)*(4096L*header.channels)+ch*4096+p%4096,input);System.arraycopy(interleaved,0,planar,ch*n+done,count);done+=count}}
        }else{input.clear();input.limit(n*header.channels);readExact(source,header.dataOffset+position*header.channels,input);for(ch in 0 until header.channels)for(i in 0 until n)planar[ch*n+i]=interleaved[i*header.channels+ch]}
        if(header.reverse)for(i in 0 until n*header.channels)planar[i]=(Integer.reverse(planar[i].toInt() and 255) ushr 24).toByte()
    }
    fun decodeBlock():FloatBuffer? {
        if(pending){pending=false;pcm.discontinuity=discontinuity;discontinuity=false;return pcm}
        while(bytePosition<header.bytesPerChannel){val n=minOf(4096L,header.bytesPerChannel-bytePosition).toInt();readPlanar(bytePosition,n);bytePosition+=n
            pcm.frames=pipeline.processBytes(planar,offsets,n,pcm.samples);pcm.position=pcmPosition;pcmPosition+=pcm.frames;pcm.discontinuity=discontinuity;discontinuity=false;if(pcm.frames>0)return pcm
        };return null
    }
    /** Replay source state to the exact sample; seek is outside the decode-loop timing. */
    fun seekSample(sample:Long){
        if(sample<0||sample>info.totalSamples)throw IOException("dsd: sample out of range")
        pipeline.reset();bytePosition=0;pcmPosition=0;pending=false;discontinuity=false
        if(sample==0L){discontinuity=true;return}
        if(sample==info.totalSamples){bytePosition=header.bytesPerChannel;pcmPosition=sample;discontinuity=true;return}
        while(pcmPosition<=sample){val b=decodeBlock()?:break;if(pcmPosition>sample){val skip=(sample-b.position).toInt();System.arraycopy(b.samples,skip*header.channels,b.samples,0,(b.frames-skip)*header.channels);b.frames-=skip;b.position=sample;pending=true;break}}
        discontinuity=true
    }
    fun decodeDopBlock():Buffer? {
        val n=minOf(4096L,header.bytesPerChannel-dopBytePosition).toInt()
        if(n<packer.bytesPerChannelPerFrame)return null
        readPlanar(dopBytePosition,n);dopBytePosition+=n;dop.frames=packer.packToI32(planar,offsets,n,dop.samples);dop.position=dopPosition;dopPosition+=dop.frames;dop.discontinuity=dopDiscontinuity;dopDiscontinuity=false;return dop
    }
    fun seekDopSample(sample:Long){val total=header.bytesPerChannel/packer.bytesPerChannelPerFrame;if(sample<0||sample>total)throw IOException("dsd: DoP sample out of range");dopBytePosition=sample*packer.bytesPerChannelPerFrame;dopPosition=sample;packer.seekFrame(sample);dopDiscontinuity=true}
}
