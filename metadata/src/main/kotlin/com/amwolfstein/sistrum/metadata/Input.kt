// SPDX-License-Identifier: GPL-3.0-or-later
// WaxLabel read port, cfbb45d13135a76f5df6781fdeec75f5cfe63bf9; bounded source adapter.
// Copyright (c) 2026 Cole Springer, MIT; see metadata/THIRD-PARTY-NOTICES.
package com.amwolfstein.sistrum.metadata

import com.amwolfstein.sistrum.codecs.container.RandomAccessSource
import java.nio.ByteBuffer
import java.security.MessageDigest

internal class Input(val source: RandomAccessSource, val options: ReadOptions) {
    val length = source.length.also { if (it < 0) throw ReadFailure(ErrorClass.INVALID_DATA, "negative source length") }
    private var readBytes = 0L
    fun bytes(offset: Long, size: Int): ByteArray {
        if (size < 0 || size > options.maxTagBytes) throw ReadFailure(ErrorClass.SIZE_TOO_LARGE, "metadata allocation limit")
        if (offset < 0 || offset > length || size.toLong() > length - offset)
            throw ReadFailure(ErrorClass.INVALID_DATA, "truncated metadata region")
        val out = ByteArray(size)
        fill(offset, ByteBuffer.wrap(out))
        return out
    }
    private fun fill(offset: Long, buffer: ByteBuffer) {
        val size = buffer.remaining()
        if (offset < 0 || offset > length || size.toLong() > length - offset)
            throw ReadFailure(ErrorClass.INVALID_DATA, "truncated metadata region")
        if (size.toLong() > options.maxReadBytes - readBytes) throw ReadFailure(ErrorClass.SIZE_TOO_LARGE, "metadata read limit")
        readBytes += size
        val start = buffer.position()
        while (buffer.hasRemaining()) {
            val before = buffer.position()
            val count = source.read(offset + before - start, buffer)
            if (count <= 0 || buffer.position() - before != count)
                throw ReadFailure(ErrorClass.IO, "source stopped or violated the read contract")
        }
    }
    fun hash(offset: Long, size: Int): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val scratch = ByteArray(minOf(8192,size))
        var position = 0
        while (position < size) {
            val count = minOf(scratch.size, size-position)
            fill(offset+position,ByteBuffer.wrap(scratch,0,count))
            digest.update(scratch,0,count)
            position += count
        }
        return digest.digest().hex()
    }
    fun cap(count: Int) {
        if (count >= options.maxElements) throw ReadFailure(ErrorClass.SIZE_TOO_LARGE, "metadata element limit")
    }
}
internal fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 255) }
internal fun ByteArray.u(p: Int): Int = this[p].toInt() and 255
internal fun ByteArray.be(p: Int, n: Int): Long {
    var v = 0L
    repeat(n) { v = (v shl 8) or u(p + it).toLong() }
    return v
}
internal fun ByteArray.le(p: Int, n: Int): Long {
    var v = 0L
    repeat(n) { v = v or (u(p + it).toLong() shl (8 * it)) }
    return v
}
internal fun ByteArray.sync(p: Int): Int = (u(p) and 127 shl 21) or (u(p+1) and 127 shl 14) or
    (u(p+2) and 127 shl 7) or (u(p+3) and 127)
internal fun ByteArray.latin(p: Int = 0, n: Int = size-p): String = String(this, p, n, Charsets.ISO_8859_1)

/** Local bounded view for chapter subframes; codecs supplies only the interface. */
internal fun byteSource(data: ByteArray): RandomAccessSource = object : RandomAccessSource {
    override val length = data.size.toLong()
    override fun read(position: Long, buffer: ByteBuffer): Int {
        require(position >= 0)
        if (!buffer.hasRemaining()) return 0
        if (position >= length) return -1
        val count = minOf(buffer.remaining().toLong(),length-position).toInt()
        buffer.put(data,position.toInt(),count)
        return count
    }
}
