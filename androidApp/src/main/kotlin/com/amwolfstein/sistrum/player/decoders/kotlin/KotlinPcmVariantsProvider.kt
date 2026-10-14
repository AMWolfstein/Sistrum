@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.amwolfstein.sistrum.player.decoders.kotlin

import androidx.media3.common.C
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import com.amwolfstein.sistrum.codecs.codec.g711.G711
import com.amwolfstein.sistrum.codecs.codec.g711.Law
import com.amwolfstein.sistrum.codecs.container.ByteBufferSource
import com.amwolfstein.sistrum.codecs.container.RandomAccessSource
import com.amwolfstein.sistrum.codecs.container.aiff.Aiff
import com.amwolfstein.sistrum.codecs.container.riff.AdpcmWav
import com.amwolfstein.sistrum.player.decoders.DecoderProvider
import com.amwolfstein.sistrum.player.decoders.FormatKey
import java.io.EOFException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** MS-ADPCM WAV and fixed-unit compressed AIFF-C; Media3 retains WAV IMA/G.711 priority. */
internal class KotlinPcmVariantsProvider : DecoderProvider {
    override val id = "kotlin-pcm-variants"
    override fun isAvailable(key: FormatKey) = key.format.lowercase() in setOf("wav", "aiff", "aifc")
    override val refusalHeaderBytes = 4096
    override fun refusal(header: ByteArray): String? =
        if (variant(header) != null) KotlinDecodingExtractor.refusal(header, ::open) else null

    override fun extractors(): List<() -> Extractor> = listOf({ extractor(aiffOnly = false) })
    fun aiffExtractors(): List<() -> Extractor> = listOf({ extractor(aiffOnly = true) })

    private fun extractor(aiffOnly: Boolean): Extractor {
        val delegate = KotlinDecodingExtractor({ variant(it) != null }, ::open)
        return object : Extractor by delegate {
            override fun sniff(input: ExtractorInput): Boolean {
                val compression = variant(input)
                return if (aiffOnly) compression in setOf("ima4", "alaw", "ulaw") else compression != null
            }
            override fun getUnderlyingImplementation(): Extractor = delegate.underlyingImplementation
        }
    }

    private fun open(source: RandomAccessSource): CodecStream {
        return when (variant(source)) {
            "ima4", "alaw", "ulaw" -> {
                val aiff = supported { Aiff.open(source) }
                val h = aiff.info
                object : CodecStream {
                    override val info = CodecStream.Info(h.sampleRate, h.channels, C.ENCODING_PCM_16BIT, h.totalSamples)
                    override fun decodeBlock() = aiff.decodeBlock()
                    override fun seekSample(sample: Long) = aiff.seekSample(sample)
                }
            }
            "wav-2" -> {
                val wav = supported { AdpcmWav.open(source) }
                val h = wav.info
                object : CodecStream {
                    override val info = CodecStream.Info(h.sampleRate, h.channels, C.ENCODING_PCM_16BIT, h.totalSamples)
                    override fun decodeBlock() = wav.decodeBlock()
                    override fun seekSample(sample: Long) = wav.seekSample(sample)
                }
            }
            "wav-6", "wav-7" -> openG711(source)
            else -> throw IOException("pcm-variants: unsupported container or compression")
        }
    }

    private fun <T> supported(open: () -> T): T = try { open() } catch (error: IOException) {
        val reason = error.message.orEmpty()
        if (reason.contains("(supported: 1..8)") || reason.contains("channels of MS ADPCM") ||
            reason.contains("predictor coefficient pairs (this build reads 7)")) {
            throw PcmVariantUnsupportedException(reason, error)
        }
        throw error
    }

    private fun openG711(source: RandomAccessSource): CodecStream {
        var offset = 12L
        var fmt: ByteArray? = null
        var start = -1L
        var size = -1L
        var chunks = 0
        while (offset + 8 <= source.length) {
            if (chunks++ >= 1024) throw IOException("wav: more than 1024 chunks")
            val chunk = read(source, offset, 8) ?: throw EOFException("wav: truncated chunk")
            val bytes = u32(chunk, 4, false)
            val data = offset + 8
            if (bytes > source.length - data) throw IOException("wav: chunk extends past end of file")
            when (id(chunk, 0)) {
                "fmt " -> if (fmt == null) {
                    if (bytes < 16) throw IOException("wav: truncated G.711 fmt")
                    fmt = read(source, data, 16)
                }
                "data" -> if (start < 0) { start = data; size = bytes }
            }
            offset = data + bytes + (bytes and 1)
        }
        val h = fmt ?: throw IOException("wav: no fmt chunk")
        if (start < 0) throw IOException("wav: no data chunk")
        val b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN)
        val channels = b.getShort(2).toInt() and 65535
        val rate = b.getInt(4)
        if (channels !in 1..8 || rate <= 0) throw IOException("wav: invalid G.711 channels or rate")
        val g711 = G711.open(if (b.getShort(0).toInt() == 6) Law.ALaw else Law.MuLaw,
            rate, channels, source, start, size / channels)
        return object : CodecStream {
            override val info = CodecStream.Info(rate, channels, C.ENCODING_PCM_16BIT, size / channels)
            override fun decodeBlock() = g711.decodeBlock()
            override fun seekSample(sample: Long) = g711.seekSample(sample)
        }
    }

    companion object {
        fun supportsAiff(header: ByteArray) = variant(header) in setOf("ima4", "alaw", "ulaw")
        fun wavCodec(input: ExtractorInput): String? = when (variant(input)) {
            "wav-2" -> "msadpcm"
            "wav-6" -> "alaw"
            "wav-7" -> "ulaw"
            else -> null
        }

        private fun variant(header: ByteArray) = variant(ByteBufferSource(ByteBuffer.wrap(header)))
        private fun variant(source: RandomAccessSource) = variant { at, n -> read(source, at, n) }
        private fun read(source: RandomAccessSource, at: Long, n: Int): ByteArray? {
            if (at > source.length - n) return null
            val buffer = ByteBuffer.allocate(n)
            var offset = at
            while (buffer.hasRemaining()) {
                val count = source.read(offset, buffer)
                if (count <= 0) return null
                offset += count
            }
            return buffer.array()
        }

        private fun variant(input: ExtractorInput): String? {
            input.resetPeekPosition()
            var position = 0L
            return try {
                variant { at, n ->
                    val skip = at - position
                    if (skip < 0 || skip > Int.MAX_VALUE) null else {
                        input.advancePeekPosition(skip.toInt())
                        val bytes = ByteArray(n)
                        input.peekFully(bytes, 0, n)
                        position = at + n
                        bytes
                    }
                }
            } catch (_: EOFException) { null }
            finally { input.resetPeekPosition() }
        }

        /** Scan chunk headers, never compressed data; large leading metadata is skipped without allocation. */
        private fun variant(read: (Long, Int) -> ByteArray?): String? {
            val h = read(0, 12) ?: return null
            val wav = id(h, 0) in setOf("RIFF", "RF64", "BW64") && id(h, 8) == "WAVE"
            val aifc = id(h, 0) == "FORM" && id(h, 8) == "AIFC"
            if (!wav && !aifc) return null
            var offset = 12L
            repeat(1024) {
                val chunk = read(offset, 8) ?: return null
                val n = u32(chunk, 4, aifc)
                val data = offset + 8
                if (wav && id(chunk, 0) == "fmt ") {
                    if (n < 2) return null
                    val fmt = read(data, 2) ?: return null
                    val tag = (fmt[0].toInt() and 255) or ((fmt[1].toInt() and 255) shl 8)
                    return if (tag in setOf(2, 6, 7)) "wav-$tag" else null
                }
                if (aifc && id(chunk, 0) == "COMM") {
                    if (n < 22) return null
                    val comm = read(data, 22) ?: return null
                    return id(comm, 18).lowercase().takeIf { it in setOf("ima4", "alaw", "ulaw") }
                }
                offset = data + n + (n and 1)
            }
            return null
        }
        private fun id(bytes: ByteArray, at: Int) = String(bytes, at, 4, Charsets.ISO_8859_1)
        private fun u32(bytes: ByteArray, at: Int, big: Boolean): Long =
            ByteBuffer.wrap(bytes).order(if (big) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN)
                .getInt(at).toLong() and 0xffffffffL
    }
}

/** Keeps supported-container limits distinct from damaged headers/packets in the normal error path. */
internal class PcmVariantUnsupportedException(message: String, cause: IOException) : IOException(message, cause)
