@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.amwolfstein.sistrum.player.decoders.kotlin

import android.util.Log
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
    private val channel: FileChannel?,
    private val cacheFile: File?,
) : RandomAccessSource, Closeable {
    constructor(input: ExtractorInput, cacheFile: File = createCacheFile()) :
        this(input, null, cacheFile)

    constructor(channel: FileChannel) : this(null, channel, null)

    private val declaredLength: Long
    private var cache: RandomAccessFile? = null
    private val scratch = ByteArray(if (input != null) 64 * 1024 else 0)
    private var cachedLength = 0L
    private var ended = false
    private var closed = false

    init {
        try {
            if (cacheFile != null) {
                cache = RandomAccessFile(cacheFile, "rw")
                checkNotNull(cache).setLength(0)
            }
            declaredLength = input?.length ?: checkNotNull(channel).size()
        } catch (error: Exception) {
            try { cache?.close() } catch (closeError: IOException) { error.addSuppressed(closeError) }
            cacheFile?.let { deleteCache(it) }
            throw error
        }
    }

    override val length: Long
        get() {
            checkOpen()
            if (declaredLength != C.LENGTH_UNSET.toLong()) return declaredLength
            cacheThrough(Long.MAX_VALUE)
            return cachedLength
        }

    /** Byte offset at which Media3 can resume without replaying the cached prefix. */
    val resumePosition: Long get() = cachedLength

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
        val cache = cache ?: return checkNotNull(channel).read(buffer, position)
        cache.seek(position)
        val count = cache.read(scratch, 0, minOf(buffer.remaining().toLong(), scratch.size.toLong(), cachedLength - position).toInt())
        if (count > 0) buffer.put(scratch, 0, count)
        return count
    }

    private fun cacheThrough(requestedEnd: Long) {
        val end = if (declaredLength >= 0) minOf(requestedEnd, declaredLength) else requestedEnd
        if (ended || end <= cachedLength) return
        val current = checkNotNull(input)
        if (current.position > cachedLength) throw IOException("uncached bytes precede Media3 input position")
        // Fallback for inputs rebound before the cache end without an extractor reopen.
        while (current.position < cachedLength) {
            current.skipFully(minOf(cachedLength - current.position, Int.MAX_VALUE.toLong()).toInt())
        }
        while (cachedLength < end) {
            val wanted = minOf(scratch.size.toLong(), end - cachedLength).toInt()
            val count = current.read(scratch, 0, wanted)
            if (count == C.RESULT_END_OF_INPUT) { ended = true; return }
            if (count <= 0) throw IOException("Media3 input made no progress")
            val cache = checkNotNull(cache)
            cache.seek(cachedLength)
            cache.write(scratch, 0, count)
            cachedLength += count
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
            try { cache?.close() } finally { deleteCache(cacheFile) }
        }
    }

    companion object {
        private var swept = false

        // Serialize the sweep and first creation: no live extractor cache can be deleted.
        @Synchronized private fun createCacheFile(): File {
            val directory = File(checkNotNull(System.getProperty("java.io.tmpdir")))
            if (!swept) {
                sweepStaleCaches(directory)
                swept = true
            }
            return File.createTempFile("sistrum-codec-", ".cache", directory)
        }

        internal fun sweepStaleCaches(directory: File) {
            val files = directory.listFiles()
            if (files == null) {
                Log.w("KotlinCodecCache", "could not list codec cache directory: $directory")
                return
            }
            files.filter { it.isFile && it.name.startsWith("sistrum-codec-") && it.name.endsWith(".cache") }
                .forEach { deleteCache(it) }
        }

        private fun deleteCache(file: File) {
            try {
                if (!file.delete() && file.exists()) Log.w("KotlinCodecCache", "could not delete codec cache: $file")
            } catch (error: SecurityException) {
                Log.w("KotlinCodecCache", "could not delete codec cache: $file", error)
            }
        }
    }
}
