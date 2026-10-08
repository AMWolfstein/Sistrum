package com.amwolfstein.sistrum.codecs

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import com.amwolfstein.sistrum.codecs.container.RandomAccessSource
import com.amwolfstein.sistrum.codecs.container.FileChannelSource

/** Test-only adapter: spool a non-seekable stream to disk, never whole-file heap storage. */
internal class TestInputStreamSource(input: InputStream): RandomAccessSource, AutoCloseable {
    private val path=Files.createTempFile("wavpack-test-", ".wv")
    private val channel: FileChannel
    private val source: FileChannelSource
    init {
        try { input.use { stream -> Files.newOutputStream(path).use { stream.copyTo(it) } } }
        catch (e: Throwable) { Files.deleteIfExists(path); throw e }
        channel=FileChannel.open(path,StandardOpenOption.READ)
        source=FileChannelSource(channel)
    }
    override val length get()=source.length
    override fun read(position: Long, buffer: ByteBuffer)=source.read(position,buffer)
    override fun close() { try { channel.close() } finally { Files.deleteIfExists(path) } }
}
