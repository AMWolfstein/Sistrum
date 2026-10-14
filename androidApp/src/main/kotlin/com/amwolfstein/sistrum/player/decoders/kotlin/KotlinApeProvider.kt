@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.amwolfstein.sistrum.player.decoders.kotlin

import androidx.media3.common.C
import androidx.media3.extractor.Extractor
import com.amwolfstein.sistrum.codecs.container.RandomAccessSource
import com.amwolfstein.sistrum.codecs.container.apen.Ape
import com.amwolfstein.sistrum.player.decoders.DecoderProvider
import com.amwolfstein.sistrum.player.decoders.FormatKey

/** Monkey's Audio integer PCM; codec-owned sample trimming and seeking are preserved. */
internal class KotlinApeProvider : DecoderProvider {
    override val id = "kotlin-ape"
    override fun isAvailable(key: FormatKey) = key.format.equals("ape", ignoreCase = true)
    override fun extractors(): List<() -> Extractor> = listOf({ KotlinDecodingExtractor(::matches, ::open) })
    override val refusalHeaderBytes = 4096
    override fun refusal(header: ByteArray): String? =
        if (matches(header)) KotlinDecodingExtractor.refusal(header, ::open) else null

    private fun open(source: RandomAccessSource): CodecStream {
        val ape = Ape.open(source)
        val header = ape.info
        val encoding = when (header.bitsPerSample) {
            8 -> C.ENCODING_PCM_8BIT
            16 -> C.ENCODING_PCM_16BIT
            24 -> C.ENCODING_PCM_24BIT
            else -> error("APE validated an unsupported PCM width")
        }
        return object : CodecStream {
            override val info = CodecStream.Info(header.rate, header.channels, encoding, header.samples)
            override fun decodeBlock() = ape.decodeBlock()
            override fun seekSample(sample: Long) = ape.seekSample(sample)
        }
    }

    private fun matches(header: ByteArray): Boolean = header.indices.any { at ->
        at <= header.size - 4 && header[at] == 77.toByte() && header[at + 1] == 65.toByte() &&
            header[at + 2] == 67.toByte() && (header[at + 3] == 32.toByte() || header[at + 3] == 70.toByte())
    }
}
