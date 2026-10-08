// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/alac/decode.go, format/media.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.alac

import java.io.IOException
import java.nio.ByteBuffer
import me.misa198.airmedy.codecs.audio.Buffer
import me.misa198.airmedy.codecs.container.RandomAccessSource
import me.misa198.airmedy.codecs.container.ByteBufferSource

/** Packet locations and sample timestamps supplied by the existing extractor.
 * The caller keeps these arrays immutable for the life of the decoder.
 */
class PacketIndex(val offsets: LongArray,val sizes: IntArray,val sampleStarts: LongArray,val totalSamples: Long) {
    init {
        require(offsets.size==sizes.size && sizes.size==sampleStarts.size)
        require(totalSamples>=0)
        var i=0; while (i<sizes.size) {
            require(offsets[i]>=0 && sizes[i]>0 && sampleStarts[i]>=0)
            require(i==0 || sampleStarts[i]>=sampleStarts[i-1]); i++
        }
    }
}
/** Decoder only: Media3 owns MP4 extraction and provides cookie, packet index and source. */
class Alac private constructor(cookie: ByteBuffer,private val source: RandomAccessSource,private val index: PacketIndex) {
    val info=Config(cookie)
    val totalSamples=index.totalSamples
    private val decoder=Decoder(info)
    private val packet=ByteBuffer.allocate(index.sizes.maxOrNull() ?: 0)
    private var next=0; private var discard=0L; private var discontinuity=false
    init {
        var i=0; while (i<index.sizes.size) { require(index.offsets[i]<=source.length-index.sizes[i]); i++ }
    }
    fun decodeBlock(): Buffer? {
        while (next<index.sizes.size) {
            val position=index.sampleStarts[next]; packet.clear(); packet.limit(index.sizes[next])
            try {
                while (packet.hasRemaining()) {
                    val before=packet.position(); val n=source.read(index.offsets[next]+before,packet)
                    if (n<=0 || n!=packet.position()-before) throw IOException("source ended before its declared length")
                }
            } catch (e: IOException) { throw AlacException(AlacException.Reason.SOURCE_UNREADABLE,"reading packet data").also { it.initCause(e) } }
            next++; packet.flip(); val out=decoder.decode(packet)
            out.frames=minOf(out.frames.toLong(),maxOf(0,totalSamples-position)).toInt()
            if (discard>=out.frames) { discard-=out.frames; continue }
            out.position=position; out.discontinuity=discontinuity
            if (discard>0) {
                val skip=discard.toInt(); val remaining=out.frames-skip
                System.arraycopy(out.samples,skip*out.channels,out.samples,0,remaining*out.channels)
                out.frames=remaining; out.position+=skip; discard=0
            }
            discontinuity=false; return out
        }
        return null
    }
    fun seekSample(sample: Long) {
        if (sample<0) throw AlacException(AlacException.Reason.INVALID_REQUEST,"negative seek target")
        if (sample>=totalSamples) { next=index.sizes.size; discard=0; discontinuity=true; return }
        var lo=0; var hi=index.sampleStarts.size
        while (lo<hi) { val mid=lo+(hi-lo)/2; if (index.sampleStarts[mid]<=sample) lo=mid+1 else hi=mid }
        next=maxOf(0,lo-1); discard=sample-index.sampleStarts[next]; discontinuity=true
    }
    companion object {
        fun open(cookie: ByteBuffer,source: RandomAccessSource,index: PacketIndex)=Alac(cookie,source,index)
        fun open(cookie: ByteBuffer,source: ByteBuffer,index: PacketIndex)=open(cookie,ByteBufferSource(source),index)
    }
}
