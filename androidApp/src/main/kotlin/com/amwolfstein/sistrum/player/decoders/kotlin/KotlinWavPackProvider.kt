@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.amwolfstein.sistrum.player.decoders.kotlin

import androidx.media3.common.C
import androidx.media3.extractor.Extractor
import com.amwolfstein.sistrum.codecs.codec.wavpack.ErrorCode
import com.amwolfstein.sistrum.codecs.codec.wavpack.WavPackException
import com.amwolfstein.sistrum.codecs.container.RandomAccessSource
import com.amwolfstein.sistrum.codecs.container.wv.Wv
import com.amwolfstein.sistrum.player.decoders.DecoderProvider
import com.amwolfstein.sistrum.player.decoders.FormatKey
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Native WavPack PCM/float, including hybrid's reference lossy fallback without a sidecar. */
internal class KotlinWavPackProvider : DecoderProvider {
    override val id = "kotlin-wavpack"
    override fun isAvailable(key: FormatKey) = key.format.equals("wv", ignoreCase = true)
    override fun extractors(): List<() -> Extractor> =
        listOf({ KotlinDecodingExtractor(::matches, ::open, PROBE_BYTES) })
    override val refusalHeaderBytes = PROBE_BYTES
    override fun refusal(header: ByteArray): String? =
        if (matches(header)) KotlinDecodingExtractor.refusal(header, ::open) else null

    private fun open(source: RandomAccessSource): CodecStream {
        val wv = Wv.open(source)
        val info = wv.info
        // Raw DSD bytes cannot be advertised as PCM. T007 supplies the DSD conversion path.
        if (info.isDsd) throw WavPackException(ErrorCode.UNSUPPORTED, "DSD output requires PCM conversion")
        val encoding = if (info.isFloat) C.ENCODING_PCM_FLOAT else when (info.bits) {
            8 -> C.ENCODING_PCM_8BIT
            16 -> C.ENCODING_PCM_16BIT
            24 -> C.ENCODING_PCM_24BIT
            32 -> C.ENCODING_PCM_32BIT
            else -> throw WavPackException(ErrorCode.UNSUPPORTED, "unsupported PCM width ${info.bits}")
        }
        return object : CodecStream {
            override val info = CodecStream.Info(info.sampleRate, info.channels, encoding, info.totalSamples)
            override fun decodeBlock() = wv.decodeBlock()
            override fun seekSample(sample: Long) = wv.seekSample(sample)
        }
    }

    private fun matches(header: ByteArray): Boolean {
        fun magic(at: Int, value: String) = at >= 0 && at <= header.size - value.length &&
            value.indices.all { header[at + it].toInt() == value[it].code }
        if (magic(0, "wvpk")) return true
        // Self-extracting files contain native blocks after the executable's stub (bounded probe).
        if (magic(0, "MZ")) {
            for (at in 2..header.size - 32) if (magic(at, "wvpk")) return true
        }
        // Old RIFF-wrapped WavPack must reach its named unsupported-version error, not WAV PCM.
        if (magic(0, "RIFF") && magic(8, "WAVE")) {
            val bytes = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            var at = 12L
            while (at <= header.size - 8) {
                val size = bytes.getInt(at.toInt() + 4).toLong() and 0xffffffffL
                if (magic(at.toInt(), "data")) return magic(at.toInt() + 8, "wvpk")
                at += 8 + size + (size and 1)
            }
        }
        return false
    }

    private companion object { const val PROBE_BYTES = 128 * 1024 }
}
