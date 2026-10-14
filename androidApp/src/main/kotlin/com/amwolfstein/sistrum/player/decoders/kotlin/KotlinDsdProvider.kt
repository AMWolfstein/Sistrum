@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.amwolfstein.sistrum.player.decoders.kotlin

import androidx.media3.common.C
import androidx.media3.extractor.Extractor
import com.amwolfstein.sistrum.codecs.audio.FloatBuffer
import com.amwolfstein.sistrum.codecs.container.RandomAccessSource
import com.amwolfstein.sistrum.codecs.container.dsd.Dsd
import com.amwolfstein.sistrum.codecs.container.wv.WavPackDsd
import com.amwolfstein.sistrum.player.decoders.DecoderProvider
import com.amwolfstein.sistrum.player.decoders.FormatKey
import java.io.IOException

internal class DsdUnsupportedException(cause: IOException) : IOException(cause.message, cause)

/** DSF/DFF use the production flat-response converter, including its bounded seek pre-roll. */
internal class KotlinDsdProvider(private val targetRate: Int = 0) : DecoderProvider {
    override val id = "kotlin-dsd"
    override fun isAvailable(key: FormatKey) = key.format.lowercase() in setOf("dsf", "dff")
    override fun extractors(): List<() -> Extractor> = listOf({ KotlinDecodingExtractor(::matches, ::open) })
    override val refusalHeaderBytes = 4096
    override fun refusal(header: ByteArray): String? =
        if (matches(header)) KotlinDecodingExtractor.refusal(header, ::open) else null

    private fun matches(header: ByteArray) = header.size >= 4 &&
        (String(header, 0, 4, Charsets.US_ASCII) == "DSD " || String(header, 0, 4, Charsets.US_ASCII) == "FRM8")

    internal fun open(source: RandomAccessSource): FloatCodecStream {
        val dsd = try { Dsd.open(source, targetRate) } catch (error: IOException) {
            // Preserve the pinned parser's named refusals while distinguishing malformed data.
            if (error.message in setOf("dff: Compression type must be 'DSD '. DST not supported.",
                    "dsd: unsupported sample rate", "dsd: unsupported PCM target"))
                throw DsdUnsupportedException(error)
            throw error
        }
        return playable(object : FloatCodecStream {
            override val info = CodecStream.Info(dsd.info.sampleRate, dsd.info.channels, C.ENCODING_PCM_FLOAT, dsd.info.totalSamples)
            override fun decodeFloatBlock(): FloatBuffer? = dsd.decodeBlock()
            override fun seekSample(sample: Long) = dsd.seekSample(sample)
        })
    }

    companion object {
        internal fun wavPack(source: RandomAccessSource, targetRate: Int = 0): FloatCodecStream {
            val dsd = WavPackDsd.open(source, targetRate)
            return playable(object : FloatCodecStream {
                override val info = CodecStream.Info(dsd.info.sampleRate, dsd.info.channels, C.ENCODING_PCM_FLOAT, dsd.info.totalSamples)
                override fun decodeFloatBlock(): FloatBuffer? = dsd.decodeBlock()
                override fun seekSample(sample: Long) = dsd.seekSample(sample)
            })
        }
        private fun playable(stream: FloatCodecStream): FloatCodecStream =
            if (stream.info.sampleRate <= 192000) stream else DsdRateConverter(stream)
    }
}
