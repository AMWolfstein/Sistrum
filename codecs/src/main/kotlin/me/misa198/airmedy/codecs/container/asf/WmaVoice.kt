// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow container/asf/codecs.go and codec/wmavoice/decode.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.container.asf

import java.nio.ByteBuffer
import java.io.IOException
import me.misa198.airmedy.codecs.audio.FloatBuffer
import me.misa198.airmedy.codecs.container.RandomAccessSource
import me.misa198.airmedy.codecs.container.ByteBufferSource
import me.misa198.airmedy.codecs.codec.wmavoice.Config
import me.misa198.airmedy.codecs.codec.wmavoice.Decoder
import me.misa198.airmedy.codecs.codec.wmavoice.VoiceException

class WmaVoice private constructor(source:RandomAccessSource,strict:Boolean) {
    val demux=Demux(source,strict){_,bytes->
        try {val cfg=Config.parse(ByteBuffer.wrap(bytes));AsfFormat(cfg.rate,1,32,true)}
        catch(e:VoiceException){throw AsfException(e.unsupported,e.message!!)}
    }
    private val cfg=Config.parse(ByteBuffer.wrap(demux.codecConfig));private val decoder=Decoder(cfg)
    data class StreamInfo(val sampleRate:Int,val channels:Int,val bitsPerSample:Int,val totalSamples:Long,val samplesExact:Boolean=false)
    val info=StreamInfo(cfg.rate,1,32,demux.totalSamples)
    private var position=0L;private var discard=0L;private var drained=false;private var discontinuity=false
    fun decodeBlock():FloatBuffer? {
        while(true){val b=decoder.nextFrame()
            if(b==null){if(drained)return null;val p=demux.next();if(p==null){decoder.finish();drained=true}else{if(p.gap){decoder.reset();discontinuity=true};decoder.acceptPacket(p.data,p.size)};continue}
            if(discard>0){val n=minOf(discard,b.frames.toLong()).toInt();position+=n;discard-=n;b.frames-=n;if(b.frames==0)continue;System.arraycopy(b.samples,n,b.samples,0,b.frames)}
            b.position=position;b.discontinuity=discontinuity;position+=b.frames;discontinuity=false;return b
        }
    }
    fun seekSample(sample:Long){if(sample<0)throw IOException("wmavoice: negative seek target");demux.rewind();decoder.reset();position=0;discard=sample;drained=false;discontinuity=true}
    companion object {
        fun open(source:RandomAccessSource,strict:Boolean=false)=WmaVoice(source,strict)
        fun open(source:ByteBuffer,strict:Boolean=false)=open(ByteBufferSource(source),strict)
    }
}
