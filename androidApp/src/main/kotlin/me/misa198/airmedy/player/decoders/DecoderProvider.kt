@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package me.misa198.airmedy.player.decoders

import android.content.Context
import androidx.media3.exoplayer.Renderer
import androidx.media3.extractor.Extractor

/**
 * A scanner label pair: [format] is the container format and [codec] is the codec
 * inside it (see `MediaStoreLibraryScanner.audioFormatOf` / `realCodec`).
 */
data class FormatKey(val format: String, val codec: String)

/**
 * One way to decode a [FormatKey] — the platform (Media3/MediaCodec) route, or a
 * bundled decoder. [extractors] are combined with Media3's default factory; an
 * empty list means the platform's base set already covers the format.
 */
interface DecoderProvider {
    val id: String

    fun isAvailable(key: FormatKey): Boolean

    fun extractors(): List<() -> Extractor>

    fun audioRenderers(context: Context): List<Renderer> = emptyList()

    /**
     * How many leading file bytes [refusal] needs to decide; 0 means the provider
     * never refuses by header, so a scan never opens the file for it.
     */
    val refusalHeaderBytes: Int get() = 0

    fun refusal(header: ByteArray): String? = null
}
