// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/wavpack/decode.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.wavpack

import java.nio.ByteBuffer
import me.misa198.airmedy.codecs.audio.Buffer

/** One independently decodable block. All decode state and PCM storage are reused. */
class Decoder(val config: Config) {
    private val header=BlockHeader()
    private val state=BlockState()
    // Reserve the format's existing hostile-input cap at open, avoiding growth allocations.
    val buffer=Buffer(config.channels,config.bitDepth,MAX_BLOCK_SAMPLES)
    init { config.validate() }
    fun decode(block: ByteBuffer, offset: Int = 0): Buffer? {
        val h=header.parse(block,offset)
        if (!h.audio()) return null
        h.supported()
        if (h.channels()!=config.channels || h.bytesPerSample()*8!=config.bitDepth)
            malformed("block at sample ${h.blockIndex} changes format mid-stream")
        buffer.frames=state.unpackBlock(h,block,offset,buffer.samples)
        buffer.position=h.blockIndex
        return buffer
    }
    fun drain() = Unit
    fun reset() = Unit
}
