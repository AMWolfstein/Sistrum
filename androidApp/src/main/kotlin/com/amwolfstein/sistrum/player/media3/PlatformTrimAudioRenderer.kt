@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.amwolfstein.sistrum.player.media3

import android.content.Context
import android.media.MediaFormat
import android.os.Handler
import android.util.Log
import androidx.media3.common.Format
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/** Applies verified platform MP3 trim immediately before Media3 configures the audio sink. */
internal class PlatformTrimAudioRenderer(
    context: Context,
    codecAdapterFactory: MediaCodecAdapter.Factory,
    mediaCodecSelector: MediaCodecSelector,
    enableDecoderFallback: Boolean,
    eventHandler: Handler,
    eventListener: AudioRendererEventListener,
    audioSink: AudioSink,
) : MediaCodecAudioRenderer(
    context, codecAdapterFactory, mediaCodecSelector, enableDecoderFallback, eventHandler, eventListener, audioSink,
) {
    private var trimLogged = false

    override fun onProcessedStreamChange() {
        trimLogged = false
        super.onProcessedStreamChange()
    }

    override fun onDisabled() {
        trimLogged = false
        super.onDisabled()
    }

    override fun onOutputFormatChanged(format: Format, mediaFormat: MediaFormat?) {
        val codecName = codecInfo?.name ?: ""
        val trimmed = platformMp3Trim(format, codecName)
        if (trimmed !== format && !trimLogged) {
            val metadata = checkNotNull(format.metadata)
            val info = (0 until metadata.length()).map { metadata[it] }.filterIsInstance<LameGaplessInfo>()
                .first { it.rawDelay == trimmed.encoderDelay && it.rawPadding == trimmed.encoderPadding &&
                    (it.encoderId.startsWith("LAME") || it.encoderId.startsWith("Lavf") || it.encoderId.startsWith("Lavc")) }
            Log.i("Media3Engine", "Platform MP3 trim: codec=$codecName encoder=${info.encoderId} " +
                "Media3 ${format.encoderDelay}/${format.encoderPadding} → raw ${info.rawDelay}/${info.rawPadding}")
            trimLogged = true
        }
        super.onOutputFormatChanged(trimmed, mediaFormat)
    }
}

/** Keeps the default renderer's index and Media3 1.11.1 constructor arguments in both factories. */
internal fun replacePlatformAudioRenderer(
    context: Context,
    codecAdapterFactory: MediaCodecAdapter.Factory,
    mediaCodecSelector: MediaCodecSelector,
    enableDecoderFallback: Boolean,
    audioSink: AudioSink,
    eventHandler: Handler,
    eventListener: AudioRendererEventListener,
    out: ArrayList<Renderer>,
) {
    val index = out.indexOfFirst { it.javaClass == MediaCodecAudioRenderer::class.java }
    check(index >= 0) { "Media3's default audio renderer is missing" }
    out[index] = PlatformTrimAudioRenderer(
        context, codecAdapterFactory, mediaCodecSelector, enableDecoderFallback, eventHandler, eventListener, audioSink,
    )
}
