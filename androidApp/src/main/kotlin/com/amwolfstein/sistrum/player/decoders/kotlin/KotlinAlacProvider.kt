@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.amwolfstein.sistrum.player.decoders.kotlin

import android.content.Context
import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.decoder.CryptoConfig
import androidx.media3.decoder.DecoderException
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.decoder.SimpleDecoder
import androidx.media3.decoder.SimpleDecoderOutputBuffer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DecoderAudioRenderer
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.extractor.Extractor
import com.amwolfstein.sistrum.codecs.codec.alac.Config
import com.amwolfstein.sistrum.codecs.codec.alac.Decoder
import com.amwolfstein.sistrum.player.decoders.CodecProbe
import com.amwolfstein.sistrum.player.decoders.DecoderProvider
import com.amwolfstein.sistrum.player.decoders.FormatKey
import com.amwolfstein.sistrum.player.decoders.ProcessCodecProbe
import java.nio.ByteBuffer

/** Media3 owns MP4 demux, timestamps and trimming; Kotlin decodes its ALAC access units. */
class KotlinAlacProvider(private val probe: CodecProbe = ProcessCodecProbe) : DecoderProvider {
    override val id = "kotlin-alac"
    override fun isAvailable(key: FormatKey): Boolean =
        key.format.lowercase() in setOf("m4a", "mp4") && key.codec.equals("alac", true) &&
            !probe.hasDecoder(MimeTypes.AUDIO_ALAC)

    override fun extractors(): List<() -> Extractor> = emptyList()

    override fun audioRenderers(context: Context): List<Renderer> =
        if (probe.hasDecoder(MimeTypes.AUDIO_ALAC)) emptyList()
        else listOf(KotlinAlacAudioRenderer(probe, DefaultAudioSink.Builder(context).setEnableFloatOutput(true).build()))

    override fun audioRenderers(
        context: Context,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
    ): List<Renderer> = if (probe.hasDecoder(MimeTypes.AUDIO_ALAC)) emptyList()
        else listOf(KotlinAlacAudioRenderer(probe, audioSink, eventHandler, eventListener))
}

internal class KotlinAlacAudioRenderer(
    private val probe: CodecProbe,
    audioSink: AudioSink,
    eventHandler: Handler? = null,
    eventListener: AudioRendererEventListener? = null,
) : DecoderAudioRenderer<KotlinAlacDecoder>(eventHandler, eventListener, audioSink) {
    override fun getName(): String = "kotlin-alac"

    override fun supportsFormatInternal(format: Format): Int {
        if (format.sampleMimeType != MimeTypes.AUDIO_ALAC || probe.hasDecoder(MimeTypes.AUDIO_ALAC)) {
            return C.FORMAT_UNSUPPORTED_TYPE
        }
        if (format.cryptoType != C.CRYPTO_TYPE_NONE) return C.FORMAT_UNSUPPORTED_DRM
        val config = try { alacConfig(format) } catch (_: DecoderException) {
            // Select the renderer so createDecoder reports the specific typed refusal
            // through Media3's normal decoder-initialization error, rather than hiding it
            // behind a generic unsupported-track error.
            return C.FORMAT_HANDLED
        }
        if (!sinkSupportsFormat(alacOutputFormat(config))) return C.FORMAT_UNSUPPORTED_SUBTYPE
        return C.FORMAT_HANDLED
    }

    public override fun createDecoder(format: Format, cryptoConfig: CryptoConfig?): KotlinAlacDecoder =
        KotlinAlacDecoder(alacConfig(format))

    override fun getOutputFormat(decoder: KotlinAlacDecoder): Format = decoder.outputFormat
}

private fun alacConfig(format: Format): Config = try {
    require(format.initializationData.size == 1) { "ALAC requires one 24-byte Media3 cookie" }
    val cookie = format.initializationData.single()
    require(cookie.size == 24) { "ALAC requires a 24-byte Media3 cookie" }
    Config(ByteBuffer.wrap(cookie)).also {
        require(format.sampleRate == it.sampleRate && format.channelCount == it.channels) {
            "ALAC format disagrees with cookie"
        }
    }
} catch (e: Exception) {
    throw DecoderException("kotlin-alac: invalid configuration: ${e.message}", e)
}

private fun alacOutputFormat(config: Config): Format = Format.Builder()
    .setSampleMimeType(MimeTypes.AUDIO_RAW)
    .setSampleRate(config.sampleRate)
    .setChannelCount(config.channels)
    .setPcmEncoding(when (config.bitDepth) {
        16 -> C.ENCODING_PCM_16BIT
        20, 24 -> C.ENCODING_PCM_24BIT
        else -> C.ENCODING_PCM_32BIT
    })
    .build()

/** Fixed pools and maximum packet/PCM capacities; the access-unit loop reuses all storage. */
@Suppress("UNCHECKED_CAST") // SimpleDecoder fills both pool arrays in its constructor.
internal class KotlinAlacDecoder(config: Config) :
    SimpleDecoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, DecoderException>(
        arrayOfNulls<DecoderInputBuffer>(4) as Array<DecoderInputBuffer>,
        arrayOfNulls<SimpleDecoderOutputBuffer>(4) as Array<SimpleDecoderOutputBuffer>,
    ) {
    private val packetDecoder = Decoder(config)
    val outputFormat: Format = alacOutputFormat(config)
    private val width = (config.bitDepth + 7) / 8
    private val shift = width * 8 - config.bitDepth
    private val maxPcmBytes = config.frameLength * config.channels * width

    init {
        // Uncompressed ALAC can exceed the cookie's maxFrameBytes (often zero).
        setInitialInputBufferSize(config.frameLength * config.channels * 4 + 1024)
    }

    override fun getName(): String = "kotlin-alac"
    override fun createInputBuffer(): DecoderInputBuffer = DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT)
    override fun createOutputBuffer(): SimpleDecoderOutputBuffer = SimpleDecoderOutputBuffer { releaseOutputBuffer(it) }
    override fun createUnexpectedDecodeException(error: Throwable): DecoderException =
        DecoderException("kotlin-alac: unexpected decode failure", error)

    override fun decode(input: DecoderInputBuffer, output: SimpleDecoderOutputBuffer, reset: Boolean): DecoderException? = try {
        // ALAC packets have no inter-packet history, so flush needs no codec reset.
        val block = packetDecoder.decode(requireNotNull(input.data))
        val bytes = output.init(input.timeUs, maxPcmBytes)
        val count = block.frames * block.channels
        for (index in 0 until count) {
            val sample = block.samples[index] shl shift
            for (byte in 0 until width) bytes.put((sample ushr (byte * 8)).toByte())
        }
        bytes.flip()
        null
    } catch (e: Exception) {
        DecoderException("kotlin-alac: packet decode failed: ${e.message}", e)
    }
}
