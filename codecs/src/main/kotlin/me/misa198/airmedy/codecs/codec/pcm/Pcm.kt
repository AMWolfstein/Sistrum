// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/pcm/pcm.go and decode.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.pcm

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.IOException
import me.misa198.airmedy.codecs.audio.Buffer
import me.misa198.airmedy.codecs.audio.FloatBuffer
import me.misa198.airmedy.codecs.container.RandomAccessSource

/** Fixed-width PCM source region; output buffers are borrowed until the next decode. */
class Pcm(val source:RandomAccessSource,val offset:Long,val totalSamples:Long,val channels:Int,val bits:Int,val validBits:Int=bits,val floating:Boolean=false,val unsigned:Boolean=false,val bigEndian:Boolean=true) {
    init { require(channels in 1..8 && totalSamples>=0 && offset>=0)
        require(if(floating)bits==32||bits==64 else bits in intArrayOf(8,16,24,32))
        require(if(floating)validBits==bits else validBits in 1..bits)
        require(!unsigned||bits==8)
    }
    private val input=ByteBuffer.allocate(4096*channels*(bits/8)).order(if(bigEndian)ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN)
    private val integers=if(!floating)Buffer(channels,validBits,4096)else null
    private val floats=if(floating)FloatBuffer(channels,4096)else null
    private var position=0L;private var discontinuity=false
    private fun read():Int {
        if(position>=totalSamples)return 0
        val frames=minOf(4096L,totalSamples-position).toInt();input.clear();input.limit(frames*channels*(bits/8))
        var at=offset+position*channels*(bits/8)
        while(input.hasRemaining()){val before=input.position();val n=source.read(at,input);if(n<=0||input.position()-before!=n)throw IOException("pcm: reading packet data: unexpected EOF");at+=n}
        input.flip();return frames
    }
    fun decodeBlock():Buffer? {
        check(!floating);val frames=read();if(frames==0)return null;val out=integers!!;val shift=bits-validBits
        for(i in 0 until frames*channels){val v=when(bits){8->if(unsigned)(input.get().toInt() and 255)-128 else input.get().toInt();16->input.short.toInt();24->{val a=input.get().toInt() and 255;val b=input.get().toInt() and 255;val c=input.get().toInt() and 255;val v=if(bigEndian)(a shl 16) or (b shl 8) or c else (c shl 16) or (b shl 8) or a;(v shl 8) shr 8};else->input.int};out.samples[i]=v shr shift}
        out.frames=frames;out.position=position;out.discontinuity=discontinuity;position+=frames;discontinuity=false;return out
    }
    fun decodeFloatBlock():FloatBuffer? {
        check(floating);val frames=read();if(frames==0)return null;val out=floats!!
        for(i in 0 until frames*channels)out.samples[i]=if(bits==32)input.float else input.double.toFloat()
        out.frames=frames;out.position=position;out.discontinuity=discontinuity;position+=frames;discontinuity=false;return out
    }
    fun seekSample(sample:Long){if(sample<0)throw IOException("pcm: negative seek target");position=minOf(sample,totalSamples);discontinuity=true}
}
