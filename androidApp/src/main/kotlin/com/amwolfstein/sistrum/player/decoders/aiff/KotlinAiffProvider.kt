@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.amwolfstein.sistrum.player.decoders.aiff

import androidx.media3.extractor.Extractor
import com.amwolfstein.sistrum.player.decoders.DecoderProvider
import com.amwolfstein.sistrum.player.decoders.FormatKey
import com.amwolfstein.sistrum.player.decoders.kotlin.KotlinPcmVariantsProvider

/**
 * The bundled pure-Kotlin AIFF decoder: an [AiffExtractor] demuxes uncompressed
 * AIFF/AIFC PCM, with the PCM-variants extractor handling supported compressed
 * AIFF-C first. Both declare little-endian `AUDIO_RAW`, so the platform's own
 * audio path plays it with no codec at all.
 *
 * It is available on every device; the decoder table decides which keys use it.
 */
internal class KotlinAiffProvider : DecoderProvider {
    override val id: String = "kotlin-aiff"

    override fun isAvailable(key: FormatKey): Boolean = true

    override fun extractors(): List<() -> Extractor> =
        KotlinPcmVariantsProvider().aiffExtractors() + listOf({ AiffExtractor() })

    override val refusalHeaderBytes: Int get() = 4096

    override fun refusal(header: ByteArray): String? =
        if (KotlinPcmVariantsProvider.supportsAiff(header)) KotlinPcmVariantsProvider().refusal(header)
        else aiffRefusal(header)
}
