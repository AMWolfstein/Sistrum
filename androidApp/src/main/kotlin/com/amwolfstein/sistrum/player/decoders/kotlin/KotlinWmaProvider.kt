@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.amwolfstein.sistrum.player.decoders.kotlin

import androidx.media3.common.C
import androidx.media3.extractor.Extractor
import com.amwolfstein.sistrum.codecs.container.RandomAccessSource
import com.amwolfstein.sistrum.codecs.container.asf.*
import com.amwolfstein.sistrum.player.decoders.DecoderProvider
import com.amwolfstein.sistrum.player.decoders.FormatKey
import java.nio.ByteBuffer

/** One ASF family provider; the demuxer selects the audio stream and retains named refusals. */
internal class KotlinWmaProvider : DecoderProvider {
    override val id = "kotlin-wma"
    override fun isAvailable(key: FormatKey) = key.format.equals("wma", ignoreCase = true)
    override fun extractors(): List<() -> Extractor> = listOf({ KotlinDecodingExtractor(::matches, ::open) })
    override val refusalHeaderBytes = 8192
    override fun refusal(header: ByteArray): String? =
        if (matches(header)) KotlinDecodingExtractor.refusal(header, ::open) else null

    private fun open(source: RandomAccessSource): CodecStream {
        // Use the same ASF stream-selection policy as every imported stream. Config validation
        // occurs before packet parsing, preserving codec-specific unsupported causes/messages.
        val tag = Demux(source, resolver = { tag, bytes ->
            try {
                val config = ByteBuffer.wrap(bytes)
                when (tag) {
                    0x160, 0x161 -> com.amwolfstein.sistrum.codecs.codec.wma.Config.parse(config).let {
                        AsfFormat(it.rate, it.channels, 32, true)
                    }
                    0x162 -> com.amwolfstein.sistrum.codecs.codec.wmapro.Config.parse(config).let {
                        AsfFormat(it.rate, it.channels, 32, true)
                    }
                    0x163 -> com.amwolfstein.sistrum.codecs.codec.wmalossless.Config.parse(config).let {
                        AsfFormat(it.rate, it.channels, it.bitsPerSample, false)
                    }
                    0x0a -> com.amwolfstein.sistrum.codecs.codec.wmavoice.Config.parse(config).let {
                        AsfFormat(it.rate, 1, 32, true)
                    }
                    else -> error("ASF selected an unrecognized WMA tag")
                }
            } catch (e: java.io.IOException) {
                val unsupported = when (e) {
                    is com.amwolfstein.sistrum.codecs.codec.wma.WmaException -> e.unsupported
                    is com.amwolfstein.sistrum.codecs.codec.wmapro.ProException -> e.unsupported
                    is com.amwolfstein.sistrum.codecs.codec.wmalossless.LosslessException -> e.unsupported
                    is com.amwolfstein.sistrum.codecs.codec.wmavoice.VoiceException -> e.unsupported
                    else -> false
                }
                throw AsfException(unsupported, checkNotNull(e.message))
            }
        }).tag
        return when (tag) {
            0x160, 0x161 -> Wma.open(source).let { stream ->
                floatStream(stream.info.sampleRate, stream.info.channels, stream.info.totalSamples, stream::decodeBlock, stream::seekSample)
            }
            0x162 -> WmaPro.open(source).let { stream ->
                floatStream(stream.info.sampleRate, stream.info.channels, stream.info.totalSamples, stream::decodeBlock, stream::seekSample)
            }
            0x0a -> WmaVoice.open(source).let { stream ->
                floatStream(stream.info.sampleRate, stream.info.channels, stream.info.totalSamples, stream::decodeBlock, stream::seekSample)
            }
            0x163 -> WmaLossless.open(source).let { stream ->
                object : CodecStream {
                    override val info = CodecStream.Info(stream.info.sampleRate, stream.info.channels,
                        if (stream.info.bitsPerSample == 16) C.ENCODING_PCM_16BIT else C.ENCODING_PCM_24BIT,
                        stream.info.totalSamples, samplesExact = false)
                    override fun decodeBlock() = stream.decodeBlock()
                    override fun seekSample(sample: Long) = stream.seekSample(sample)
                }
            }
            else -> error("ASF selected an unrecognized WMA tag")
        }
    }

    private fun floatStream(rate: Int, channels: Int, samples: Long,
        decode: () -> com.amwolfstein.sistrum.codecs.audio.FloatBuffer?, seek: (Long) -> Unit,
    ): CodecStream = object : FloatCodecStream {
        override val info = CodecStream.Info(rate, channels, C.ENCODING_PCM_FLOAT, samples, samplesExact = false)
        override fun decodeFloatBlock() = decode()
        override fun seekSample(sample: Long) = seek(sample)
    }

    private fun matches(header: ByteArray) = header.size >= ASF_GUID.size &&
        ASF_GUID.indices.all { header[it] == ASF_GUID[it] }

    companion object {
        private val ASF_GUID = byteArrayOf(0x30, 0x26, 0xb2.toByte(), 0x75, 0x8e.toByte(), 0x66,
            0xcf.toByte(), 0x11, 0xa6.toByte(), 0xd9.toByte(), 0, 0xaa.toByte(), 0, 0x62, 0xce.toByte(), 0x6c)
    }
}
