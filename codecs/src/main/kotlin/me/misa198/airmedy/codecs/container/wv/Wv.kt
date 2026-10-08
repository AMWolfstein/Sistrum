// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow container/wv/demux.go, format/media.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.container.wv

import java.nio.ByteBuffer
import me.misa198.airmedy.codecs.container.RandomAccessSource
import me.misa198.airmedy.codecs.container.ByteBufferSource
import me.misa198.airmedy.codecs.audio.Buffer
import me.misa198.airmedy.codecs.codec.wavpack.Decoder

/** Open native WavPack and decode borrowed interleaved PCM blocks.
 * ByteBuffer uses its remaining region without copying; keep its storage alive and immutable.
 * RandomAccessSource supplies bounded positional reads; the caller owns its lifetime.
 * InputStream adaptation belongs in tests, not the production decoder.
 */
class Wv private constructor(source: RandomAccessSource, strict: Boolean) {
    val demuxer=Demuxer(source,strict)
    val info get() = demuxer.info
    private val decoder=Decoder(demuxer.config)
    private var discard=0L
    private var discontinuity=false
    fun decodeBlock(): Buffer? {
        while (demuxer.readPacket()) {
            val out=decoder.decode(demuxer.packetData) ?: continue
            out.position=demuxer.packetPosition
            out.discontinuity=discontinuity
            if (discard>=out.frames) { discard-=out.frames; continue }
            if (discard>0) {
                val skip=discard.toInt(); val remaining=out.frames-skip
                System.arraycopy(out.samples,skip*out.channels,out.samples,0,remaining*out.channels)
                out.frames=remaining; out.position+=skip; discard=0
            }
            discontinuity=false
            return out
        }
        return null
    }
    fun seekSample(sample: Long) {
        val landed=demuxer.seekSample(sample)
        decoder.reset(); discard=maxOf(0,sample-landed); discontinuity=true
    }
    companion object {
        fun open(source: ByteBuffer, strict: Boolean=false): Wv = open(ByteBufferSource(source),strict)
        fun open(source: RandomAccessSource, strict: Boolean=false): Wv = Wv(source,strict)
    }
}
