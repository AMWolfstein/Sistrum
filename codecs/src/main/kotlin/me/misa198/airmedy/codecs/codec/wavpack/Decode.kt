// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/wavpack/decode.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
// libwavpack 5.8.1 adaptation; Copyright (c) 1998-2025 David Bryant.
// BSD-3-Clause; copyright, conditions and disclaimer in THIRD-PARTY-NOTICES.
package me.misa198.airmedy.codecs.codec.wavpack

import java.nio.ByteBuffer
import me.misa198.airmedy.codecs.audio.Buffer

/** One independently decodable block. All decode state and PCM storage are reused. */
class Decoder(val config: Config, capacityFrames: Int=MAX_BLOCK_SAMPLES) {
    private val header=BlockHeader()
    private val state=BlockState()
    private val correctionHeader=BlockHeader()
    private val groupScratch=if (config.channels>2) IntArray(capacityFrames*2) else null
    // Reserve the format's existing hostile-input cap at open, avoiding growth allocations.
    val buffer=Buffer(config.channels,config.bitDepth,capacityFrames)
    init { config.validate() }
    fun decode(block: ByteBuffer, offset: Int = 0, correction: ByteBuffer?=null): Buffer? {
        val h=header.parse(block,offset)
        if (!h.audio()) return null
        h.supported()
        if (config.channels>2) return decodeGroup(block,offset,correction)
        if (h.channels()!=config.channels || h.bytesPerSample()*8!=config.bitDepth)
            malformed("block at sample ${h.blockIndex} changes format mid-stream")
        buffer.frames=state.unpackBlock(h,block,offset,buffer.samples,correction)
        buffer.position=h.blockIndex
        return buffer
    }
    private fun decodeGroup(block:ByteBuffer,offset:Int,correction:ByteBuffer?):Buffer {
        val position=header.blockIndex;val frames=header.blockSamples
        var at=offset;var channel=0;var correctionAt=0
        while (true) {
            val h=header.parse(block,at);h.supported()
            if (h.blockIndex!=position || h.blockSamples!=frames || h.bytesPerSample()*8!=config.bitDepth || (h.flags and FLOAT_DATA!=0)!=config.isFloat)
                malformed("channel blocks disagree at sample $position")
            if ((channel==0)!=(h.flags and INITIAL_BLOCK!=0)) malformed("invalid initial channel block")
            val channels=h.channels()
            if (channel+channels>config.channels) malformed("too many channels at sample $position")
            state.unpackBlock(h,block,at,groupScratch!!,correction,correctionAt)
            var i=0
            while (i<frames) {
                var ch=0;while (ch<channels) { buffer.samples[i*config.channels+channel+ch]=groupScratch[i*channels+ch];ch++ };i++
            }
            channel+=channels;at+=h.size.toInt()
            if (correction!=null) { correctionHeader.parse(correction,correctionAt);correctionAt+=correctionHeader.size.toInt() }
            if (h.flags and FINAL_BLOCK!=0) break
        }
        if (channel!=config.channels) malformed("incomplete channel group at sample $position")
        buffer.frames=frames;buffer.position=position
        return buffer
    }
    fun drain() = Unit
    fun reset() = Unit
}
