package me.misa198.airmedy.player.decoders

import android.media.MediaCodecList

/** Whether the platform has any decoder registered for a [mime] type. */
fun interface CodecProbe {
    fun hasDecoder(mime: String): Boolean
}

/**
 * A [CodecProbe] over [platformDecoderMimes]. The set is read and lowercased once,
 * lazily, per instance, so repeated [hasDecoder] calls do no platform work.
 */
class MediaCodecProbe(
    private val listDecoderMimes: () -> Set<String> = ::platformDecoderMimes,
) : CodecProbe {
    private val decoderMimes: Set<String> by lazy { listDecoderMimes().map(String::lowercase).toSet() }

    override fun hasDecoder(mime: String): Boolean = mime.lowercase() in decoderMimes
}

/** The platform decoders, probed once for the whole process. */
object ProcessCodecProbe : CodecProbe by MediaCodecProbe()

/** Every non-encoder MIME type the platform reports through MediaCodecList. */
fun platformDecoderMimes(): Set<String> =
    MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        .filterNot { it.isEncoder }
        .flatMap { it.supportedTypes.asSequence() }
        .toSet()
