// SPDX-License-Identifier: GPL-3.0-or-later
// Adapted from WaxLabel cfbb45d13135a76f5df6781fdeec75f5cfe63bf9, internal/id3/unsync.go.
// Copyright (c) 2026 Cole Springer, MIT; see metadata/THIRD-PARTY-NOTICES.
package me.misa198.airmedy.metadata

import me.misa198.airmedy.codecs.container.RandomAccessSource
import java.nio.ByteBuffer

/** Sparse positional view of FF 00 -> FF, retaining checkpoints rather than image bytes.
 * Checkpoints are made every 8192 decoded bytes, including the stuffing state at the boundary. */
internal class UnsynchronizedSource(private val input: Input, private val offset: Long, private val size: Int) : RandomAccessSource {
    private data class Checkpoint(val physical: Int, val afterFF: Boolean)
    private data class Window(val physical: Int, val bytes: ByteArray)
    private val checkpoints = mutableListOf(Checkpoint(0,false))
    @Volatile private var cached: Window? = null
    override val length: Long
    init {
        var physical = 0
        var decoded = 0L
        var afterFF = false
        while (physical < size) {
            val chunk = input.bytes(offset+physical,minOf(8192,size-physical))
            for (byte in chunk) {
                physical++
                if (afterFF && byte == 0.toByte()) { afterFF = false; continue }
                decoded++
                afterFF = byte.toInt() and 255 == 255
                if (decoded % 8192 == 0L) checkpoints += Checkpoint(physical,afterFF)
            }
        }
        length = decoded
    }
    override fun read(position: Long, buffer: ByteBuffer): Int {
        require(position >= 0)
        if (!buffer.hasRemaining()) return 0
        if (position >= length) return -1
        val checkpoint = checkpoints[(position/8192).toInt()]
        var physical = checkpoint.physical
        var decoded = position/8192*8192
        var afterFF = checkpoint.afterFF
        val before = buffer.position()
        // A fresh read budget makes FULL reads repeatable and safe across callers.
        val reader = Input(input.source,input.options)
        while (buffer.hasRemaining() && physical < size) {
            val windowStart = physical/8192*8192
            val window = cached?.takeIf { it.physical == windowStart } ?: Window(windowStart,
                reader.bytes(offset+windowStart,minOf(8192,size-windowStart))).also { cached = it }
            val chunk = window.bytes
            for (i in physical-windowStart until chunk.size) {
                val byte = chunk[i]
                physical++
                if (afterFF && byte == 0.toByte()) { afterFF = false; continue }
                afterFF = byte.toInt() and 255 == 255
                if (decoded++ >= position) buffer.put(byte)
                if (!buffer.hasRemaining()) break
            }
        }
        return buffer.position()-before
    }
}
