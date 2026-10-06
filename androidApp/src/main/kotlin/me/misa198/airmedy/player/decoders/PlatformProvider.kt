@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package me.misa198.airmedy.player.decoders

import androidx.media3.extractor.Extractor

/**
 * The platform (Media3/MediaCodec) decoder. It is available when the table lists
 * it for the key and the platform can decode one of the entry's MIME types — or
 * the entry needs no codec at all (empty [DecoderTableEntry.mimeTypes], raw PCM).
 */
class PlatformProvider(
    private val table: DecoderTable,
    private val probe: CodecProbe,
) : DecoderProvider {
    override val id: String = "platform"

    override fun isAvailable(key: FormatKey): Boolean {
        val entry = table.entryFor(key) ?: return false
        if (id !in entry.providers) return false
        if (entry.mimeTypes.isEmpty()) return true
        return entry.mimeTypes.any(probe::hasDecoder)
    }

    /** Media3's DefaultExtractorsFactory is the base set; T038 combines them. */
    override fun extractors(): List<() -> Extractor> = emptyList()
}
