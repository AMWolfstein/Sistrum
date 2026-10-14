@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.amwolfstein.sistrum.player.decoders.kotlin

import androidx.media3.common.C
import androidx.media3.extractor.Extractor
import com.amwolfstein.sistrum.codecs.container.RandomAccessSource
import com.amwolfstein.sistrum.codecs.container.mpc.Musepack
import com.amwolfstein.sistrum.player.decoders.DecoderProvider
import com.amwolfstein.sistrum.player.decoders.FormatKey

/** SV7/SV8 float PCM, with the codec's encoder delay and tail trimming already applied. */
internal class KotlinMusepackProvider : DecoderProvider {
    override val id = "kotlin-musepack"
    override fun isAvailable(key: FormatKey) = key.format.equals("mpc", ignoreCase = true)
    override fun extractors(): List<() -> Extractor> = listOf({ KotlinDecodingExtractor(::matches, ::open) })
    override val refusalHeaderBytes = 4096
    override fun refusal(header: ByteArray): String? =
        if (matches(header)) KotlinDecodingExtractor.refusal(header, ::open) else null

    private fun open(source: RandomAccessSource): CodecStream {
        val mpc = Musepack.open(source)
        val header = mpc.info
        return object : FloatCodecStream {
            override val info = CodecStream.Info(header.sampleRate, header.channels, C.ENCODING_PCM_FLOAT, header.totalSamples)
            override fun decodeFloatBlock() = mpc.decodeBlock()
            override fun seekSample(sample: Long) = mpc.seekSample(sample)
        }
    }

    private fun matches(header: ByteArray): Boolean = header.indices.any { at ->
        if (at > header.size - 4 || header[at] != 77.toByte() || header[at + 1] != 80.toByte()) false
        else (header[at + 2] == 67.toByte() && header[at + 3] == 75.toByte()) ||
            (header[at + 2] == 43.toByte() && (header[at + 3].toInt() and 15) in 4..7)
    }
}
