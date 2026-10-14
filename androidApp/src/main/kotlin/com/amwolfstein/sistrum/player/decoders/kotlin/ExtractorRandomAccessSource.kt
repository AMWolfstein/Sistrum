@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.amwolfstein.sistrum.player.decoders.kotlin

import androidx.media3.common.C
import androidx.media3.extractor.ExtractorInput
import com.amwolfstein.sistrum.codecs.container.RandomAccessSource
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Positional codec reads over a forward-only Media3 input. Bytes are cached on demand in a temporary
 * file, so synchronous header/tail scans and backwards seeks need neither a whole-file heap buffer
 * nor a restart of an in-progress codec operation. Memory use is bounded; disk use is at most the
 * input length. Release deletes the cache. A direct file channel remains owned by its caller.
 *
 * Single-threaded, like Extractor. [bind] attaches the input reopened by Media3 after a seek/retry.
 */
internal class ExtractorRandomAccessSource private constructor(
    private var input: ExtractorInput?,
    private val channel: FileChannel,
    private val cacheFile: File?,
) : RandomAccessSource, Closeable {
    constructor(input: ExtractorInput, cacheFile: File = File.createTempFile("sistrum-codec-", ".cache")) :
        this(input, RandomAccessFile(cacheFile, "rw").channel, cacheFile)

    constructor(channel: FileChannel) : this(null, channel, null)

    private val declaredLength = input?.length ?: channel.size()
    private val scratch = ByteArray(if (input != null) 64 * 1024 else 0)
    private val scratchBuffer = ByteBuffer.wrap(scratch)
    private var cachedLength = 0L
    private var ended = false
    private var closed = false

    init {
        if (input != null) channel.truncate(0)
    }

    override val length: Long
        get() {
            checkOpen()
            if (declaredLength != C.LENGTH_UNSET.toLong()) return declaredLength
            cacheThrough(Long.MAX_VALUE)
            return cachedLength
        }

    fun bind(input: ExtractorInput) {
        checkOpen()
        check(cacheFile != null) { "a file-channel source has no Media3 input" }
        this.input = input
    }

    override fun read(position: Long, buffer: ByteBuffer): Int {
        checkOpen()
        require(position >= 0) { "negative read position" }
        if (!buffer.hasRemaining()) return 0
        if (declaredLength >= 0 && position >= declaredLength) return -1
        if (cacheFile != null) {
            val end = position + minOf(buffer.remaining().toLong(), Long.MAX_VALUE - position)
            cacheThrough(end)
            if (position >= cachedLength) return -1
        }
        return channel.read(buffer, position)
    }

    private fun cacheThrough(requestedEnd: Long) {
        val end = if (declaredLength >= 0) minOf(requestedEnd, declaredLength) else requestedEnd
        if (ended || end <= cachedLength) return
        val current = checkNotNull(input)
        if (current.position > cachedLength) throw IOException("uncached bytes precede Media3 input position")
        // Media3's seek map reopens at zero; advance across bytes already held in the cache.
        while (current.position < cachedLength) {
            current.skipFully(minOf(cachedLength - current.position, Int.MAX_VALUE.toLong()).toInt())
        }
        while (cachedLength < end) {
            val wanted = minOf(scratch.size.toLong(), end - cachedLength).toInt()
            val count = current.read(scratch, 0, wanted)
            if (count == C.RESULT_END_OF_INPUT) { ended = true; return }
            if (count <= 0) throw IOException("Media3 input made no progress")
            scratchBuffer.clear()
            scratchBuffer.limit(count)
            var position = cachedLength
            while (scratchBuffer.hasRemaining()) {
                val written = channel.write(scratchBuffer, position)
                if (written <= 0) throw IOException("codec cache made no progress")
                position += written
            }
            cachedLength = position
        }
    }

    private fun checkOpen() {
        if (closed) throw IOException("codec source is closed")
    }

    override fun close() {
        if (closed) return
        closed = true
        input = null
        if (cacheFile != null) {
            try { channel.close() } finally {
                if (!cacheFile.delete() && cacheFile.exists()) throw IOException("could not delete codec cache")
            }
        }
    }
}
