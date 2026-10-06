@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package me.misa198.airmedy.player.decoders.aiff

import androidx.media3.extractor.Extractor
import me.misa198.airmedy.player.decoders.DecoderProvider
import me.misa198.airmedy.player.decoders.FormatKey

/**
 * The bundled pure-Kotlin AIFF decoder: an [AiffExtractor] demuxes uncompressed
 * AIFF/AIFC PCM and declares little-endian `AUDIO_RAW`, so the platform's own
 * audio path plays it with no codec at all.
 *
 * It is available on every device; the decoder table decides which keys use it.
 */
internal class KotlinAiffProvider : DecoderProvider {
    override val id: String = "kotlin-aiff"

    override fun isAvailable(key: FormatKey): Boolean = true

    override fun extractors(): List<() -> Extractor> = listOf({ AiffExtractor() })

    override val refusalHeaderBytes: Int get() = 4096

    override fun refusal(header: ByteArray): String? = aiffRefusal(header)
}
