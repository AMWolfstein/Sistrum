// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow container/container.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.container

import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** Seekable input. Reads at [position], advancing [buffer]'s position by the count
 * returned, without changing its limit. Returns -1 at EOF; positive short reads
 * are allowed. An empty destination returns 0. Sources must remain immutable
 * while decoding. The caller owns the source and any underlying file handle.
 */
interface RandomAccessSource {
    val length: Long
    fun read(position: Long, buffer: ByteBuffer): Int
}

/** Zero-copy ownership of the input's remaining region; reads do not move it. */
class ByteBufferSource(source: ByteBuffer) : RandomAccessSource {
    private val data=source.slice().asReadOnlyBuffer()
    override val length=data.remaining().toLong()
    override fun read(position: Long, buffer: ByteBuffer): Int {
        require(position>=0) { "negative read position" }
        if (!buffer.hasRemaining()) return 0
        if (position>=length) return -1
        val count=minOf(buffer.remaining().toLong(),length-position).toInt()
        val start=buffer.position()
        buffer.put(start,data,position.toInt(),count)
        buffer.position(start+count)
        return count
    }
}

/** Positional reads support files larger than 2 GiB without changing channel position. */
class FileChannelSource(private val channel: FileChannel) : RandomAccessSource {
    override val length=channel.size()
    override fun read(position: Long, buffer: ByteBuffer): Int = channel.read(buffer,position)
}
