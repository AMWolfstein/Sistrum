// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow container/apen/demux.go, codec/ape/decode.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.container.apen

import java.nio.ByteBuffer
import me.misa198.airmedy.codecs.audio.Buffer
import me.misa198.airmedy.codecs.codec.ape.Decoder
import me.misa198.airmedy.codecs.container.ByteBufferSource
import me.misa198.airmedy.codecs.container.RandomAccessSource

/** Borrowed 4096-frame interleaved chunks; caller owns the immutable seekable source. */
class Ape private constructor(source: RandomAccessSource,strict: Boolean) {
    val demuxer=Demuxer(source,strict)
    val info=demuxer.header
    private val decoder=Decoder(info)
    private val buffer=Buffer(info.channels,info.bitsPerSample,4096)
    private var offset=0; private var discard=0L; private var discontinuity=false
    private var position=0L
    fun decodeBlock(): Buffer? {
        while (true) {
            if (offset>=decoder.blocks) {
                if (!demuxer.readPacket()) return null
                decoder.decode(demuxer.packet); offset=0; position=demuxer.packetPosition
            }
            if (discard>0) { val n=minOf(discard,(decoder.blocks-offset).toLong()).toInt(); offset+=n; discard-=n; if (offset>=decoder.blocks) continue }
            val n=minOf(4096,decoder.blocks-offset)
            System.arraycopy(decoder.samples,offset*info.channels,buffer.samples,0,n*info.channels)
            buffer.frames=n; buffer.position=position+offset; buffer.discontinuity=discontinuity
            offset+=n; discontinuity=false; return buffer
        }
    }
    fun seekSample(sample: Long) {
        val landed=demuxer.seekSample(sample); discard=maxOf(0,sample-landed); offset=decoder.blocks; discontinuity=true
    }
    companion object {
        fun open(source: RandomAccessSource,strict: Boolean=false)=Ape(source,strict)
        fun open(source: ByteBuffer,strict: Boolean=false)=open(ByteBufferSource(source),strict)
    }
}
