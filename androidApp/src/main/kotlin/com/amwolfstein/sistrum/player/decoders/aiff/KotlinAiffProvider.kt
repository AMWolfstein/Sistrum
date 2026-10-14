@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.amwolfstein.sistrum.player.decoders.aiff

import androidx.media3.common.C
import androidx.media3.extractor.Extractor
import com.amwolfstein.sistrum.codecs.container.RandomAccessSource
import com.amwolfstein.sistrum.codecs.container.aiff.Aiff
import com.amwolfstein.sistrum.player.decoders.DecoderProvider
import com.amwolfstein.sistrum.player.decoders.FormatKey
import com.amwolfstein.sistrum.player.decoders.kotlin.CodecStream
import com.amwolfstein.sistrum.player.decoders.kotlin.FloatCodecStream
import com.amwolfstein.sistrum.player.decoders.kotlin.KotlinDecodingExtractor

/** All AIFF/AIFF-C decoding uses WaxFlow through the Pattern A adapter. */
internal class KotlinAiffProvider : DecoderProvider {
    override val id = "kotlin-aiff"
    override fun isAvailable(key: FormatKey) = true
    override fun extractors(): List<() -> Extractor> = listOf({ KotlinDecodingExtractor(::matches, ::open) })
    override val refusalHeaderBytes = 4096
    override fun refusal(header: ByteArray): String? =
        if (matches(header)) KotlinDecodingExtractor.refusal(header, ::open) else null

    private fun open(source: RandomAccessSource): CodecStream {
        val aiff = Aiff.open(source)
        val h = aiff.info
        val encoding = if (h.floating) C.ENCODING_PCM_FLOAT else when (h.bitsPerSample) {
            in 1..8 -> C.ENCODING_PCM_8BIT
            in 9..16 -> C.ENCODING_PCM_16BIT
            in 17..24 -> C.ENCODING_PCM_24BIT
            else -> C.ENCODING_PCM_32BIT
        }
        val info = CodecStream.Info(h.sampleRate, h.channels, encoding, h.totalSamples)
        return if (h.floating) object : FloatCodecStream {
            override val info = info
            override fun decodeFloatBlock() = aiff.decodeFloatBlock()
            override fun seekSample(sample: Long) = aiff.seekSample(sample)
        } else object : CodecStream {
            override val info = info
            override fun decodeBlock() = aiff.decodeBlock()?.also { block ->
                // Media3 consumes whole-byte words; preserve the declared valid-bit amplitude.
                val shift = (h.bitsPerSample + 7) / 8 * 8 - h.bitsPerSample
                if (shift != 0) for (i in 0 until block.frames * block.channels) {
                    block.samples[i] = block.samples[i] shl shift
                }
            }
            override fun seekSample(sample: Long) = aiff.seekSample(sample)
        }
    }

    private fun matches(header: ByteArray) = header.size >= 12 &&
        String(header, 0, 4, Charsets.US_ASCII) == "FORM" &&
        String(header, 8, 4, Charsets.US_ASCII) in setOf("AIFF", "AIFC")
}
