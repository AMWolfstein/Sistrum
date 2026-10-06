package me.misa198.airmedy.player.decoders

import me.misa198.airmedy.player.decoders.aiff.KotlinAiffProvider

/** The `codec` of a [DecoderTableEntry] that matches any codec of its format. */
const val AnyCodec: String = "*"

/**
 * One row of the decoder table: the [format]/[codec] label pair (lowercase), the
 * ordered [providers] to try, the decoder [mimeTypes] the platform needs (empty
 * means raw PCM, no codec required), and the [label] shown in skip reasons.
 */
data class DecoderTableEntry(
    val format: String,
    val codec: String,
    val providers: List<String>,
    val mimeTypes: List<String>,
    val label: String,
)

/** Lookup of [FormatKey] to its [DecoderTableEntry]. An exact codec match wins over [AnyCodec]. */
class DecoderTable(val entries: List<DecoderTableEntry>) {
    fun entryFor(key: FormatKey): DecoderTableEntry? {
        val format = key.format.lowercase()
        val codec = key.codec.lowercase()
        return entries.firstOrNull { it.format.lowercase() == format && it.codec.lowercase() == codec }
            ?: entries.firstOrNull { it.format.lowercase() == format && it.codec.lowercase() == AnyCodec }
    }
}

/** Resolves a [FormatKey] to a usable [DecoderProvider], and explains a failure to. */
interface DecoderRegistry {
    fun resolve(key: FormatKey): DecoderProvider?

    fun skipReason(key: FormatKey): String?
}

/**
 * A [DecoderRegistry] backed by a [DecoderTable]: the first registered provider of
 * the entry, in table order, whose [DecoderProvider.isAvailable] is true.
 */
class TableDecoderRegistry(
    private val table: DecoderTable,
    providers: List<DecoderProvider>,
) : DecoderRegistry {
    private val providersById = providers.associateBy(DecoderProvider::id)

    override fun resolve(key: FormatKey): DecoderProvider? {
        val entry = table.entryFor(key) ?: return null
        return entry.providers.firstNotNullOfOrNull { id ->
            providersById[id]?.takeIf { it.isAvailable(key) }
        }
    }

    override fun skipReason(key: FormatKey): String? {
        if (resolve(key) != null) return null
        val entry = table.entryFor(key) ?: return if (key.format.isBlank()) {
            "unsupported format (unknown)"
        } else {
            "unsupported format (${key.format.uppercase()})"
        }
        if (entry.providers.isEmpty()) return "unsupported format (${entry.label})"
        return "no decoder on this device (${entry.label})"
    }
}

/**
 * The 001 decoder table. This is the only place format names and decoder MIME
 * types appear; reordering providers is a table-only change.
 */
val DefaultDecoderTable: DecoderTable = DecoderTable(
    listOf(
        DecoderTableEntry("mp3", "mp3", listOf("platform"), listOf("audio/mpeg"), "MP3"),
        DecoderTableEntry("aac", "aac", listOf("platform"), listOf("audio/mp4a-latm"), "AAC"),
        DecoderTableEntry("m4a", "aac", listOf("platform"), listOf("audio/mp4a-latm"), "AAC"),
        DecoderTableEntry("mp4", "aac", listOf("platform"), listOf("audio/mp4a-latm"), "AAC"),
        DecoderTableEntry("m4a", "alac", listOf("platform"), listOf("audio/alac"), "ALAC"),
        DecoderTableEntry("mp4", "alac", listOf("platform"), listOf("audio/alac"), "ALAC"),
        DecoderTableEntry("m4a", "flac", listOf("platform"), listOf("audio/flac"), "FLAC"),
        DecoderTableEntry("mp4", "flac", listOf("platform"), listOf("audio/flac"), "FLAC"),
        DecoderTableEntry("m4a", "opus", listOf("platform"), listOf("audio/opus"), "Opus"),
        DecoderTableEntry("mp4", "opus", listOf("platform"), listOf("audio/opus"), "Opus"),
        DecoderTableEntry("m4a", "mpeg", listOf("platform"), listOf("audio/mpeg"), "MP3"),
        DecoderTableEntry("mp4", "mpeg", listOf("platform"), listOf("audio/mpeg"), "MP3"),
        DecoderTableEntry("m4a", "ac3", listOf("platform"), listOf("audio/ac3"), "AC-3"),
        DecoderTableEntry("mp4", "ac3", listOf("platform"), listOf("audio/ac3"), "AC-3"),
        DecoderTableEntry("m4a", "eac3", listOf("platform"), listOf("audio/eac3"), "E-AC-3"),
        DecoderTableEntry("mp4", "eac3", listOf("platform"), listOf("audio/eac3"), "E-AC-3"),
        DecoderTableEntry(
            "m4a",
            AnyCodec,
            listOf("platform"),
            listOf("audio/mp4a-latm", "audio/alac", "audio/flac", "audio/opus", "audio/mpeg", "audio/ac3", "audio/eac3"),
            "MPEG-4 audio",
        ),
        DecoderTableEntry(
            "mp4",
            AnyCodec,
            listOf("platform"),
            listOf("audio/mp4a-latm", "audio/alac", "audio/flac", "audio/opus", "audio/mpeg", "audio/ac3", "audio/eac3"),
            "MPEG-4 audio",
        ),
        DecoderTableEntry("flac", "flac", listOf("platform"), listOf("audio/flac"), "FLAC"),
        DecoderTableEntry("ogg", "ogg", listOf("platform"), listOf("audio/vorbis", "audio/opus", "audio/flac"), "Ogg"),
        DecoderTableEntry("opus", "opus", listOf("platform"), listOf("audio/opus"), "Opus"),
        DecoderTableEntry("wav", "wav", listOf("platform"), emptyList(), "WAV"),
        DecoderTableEntry(
            "mka",
            AnyCodec,
            listOf("platform"),
            listOf("audio/vorbis", "audio/opus", "audio/flac", "audio/mpeg", "audio/mp4a-latm"),
            "Matroska",
        ),
        DecoderTableEntry("aiff", "aiff", listOf("kotlin-aiff"), emptyList(), "AIFF"),
        DecoderTableEntry("m4a", "eac3-joc", listOf("platform"), listOf("audio/eac3-joc"), "E-AC-3 JOC"),
        DecoderTableEntry("mp4", "eac3-joc", listOf("platform"), listOf("audio/eac3-joc"), "E-AC-3 JOC"),
        DecoderTableEntry("m4a", "mpeg-l2", listOf("platform"), listOf("audio/mpeg-l2"), "MP2"),
        DecoderTableEntry("mp4", "mpeg-l2", listOf("platform"), listOf("audio/mpeg-l2"), "MP2"),
        DecoderTableEntry("mp2", "mp2", listOf("platform"), listOf("audio/mpeg-l2"), "MP2"),
        DecoderTableEntry("webm", AnyCodec, listOf("platform"), listOf("audio/opus", "audio/vorbis"), "WebM"),
        DecoderTableEntry("weba", AnyCodec, listOf("platform"), listOf("audio/opus", "audio/vorbis"), "WebM"),
        DecoderTableEntry("amr", "amr", listOf("platform"), listOf("audio/3gpp", "audio/amr-wb"), "AMR"),
        DecoderTableEntry(
            "3gp",
            AnyCodec,
            listOf("platform"),
            listOf("audio/3gpp", "audio/amr-wb", "audio/mp4a-latm"),
            "3GP",
        ),
        DecoderTableEntry(
            "3ga",
            AnyCodec,
            listOf("platform"),
            listOf("audio/3gpp", "audio/amr-wb", "audio/mp4a-latm"),
            "3GP",
        ),
        DecoderTableEntry("ac3", "ac3", listOf("platform"), listOf("audio/ac3"), "AC-3"),
        DecoderTableEntry("eac3", "eac3", listOf("platform"), listOf("audio/eac3"), "E-AC-3"),
        DecoderTableEntry("ape", "ape", emptyList(), emptyList(), "APE"),
        DecoderTableEntry("wv", "wv", emptyList(), emptyList(), "WavPack"),
        DecoderTableEntry("dsf", "dsf", emptyList(), emptyList(), "DSD"),
        DecoderTableEntry("dff", "dff", emptyList(), emptyList(), "DSD"),
        DecoderTableEntry("wma", "wma", emptyList(), emptyList(), "WMA"),
        DecoderTableEntry("mpc", "mpc", emptyList(), emptyList(), "Musepack"),
        DecoderTableEntry("tta", "tta", emptyList(), emptyList(), "TTA"),
    ),
)

/**
 * The decoder providers the app runs: the platform (Media3/MediaCodec) provider and
 * the bundled ones. Shared by [defaultDecoderRegistry] and the Media3 player factory
 * (T038) so the table lookup and the player's extractors cannot drift apart.
 */
fun defaultDecoderProviders(probe: CodecProbe): List<DecoderProvider> =
    listOf(PlatformProvider(DefaultDecoderTable, probe), KotlinAiffProvider())

/** The table-backed registry over [defaultDecoderProviders]. */
fun defaultDecoderRegistry(probe: CodecProbe): DecoderRegistry =
    TableDecoderRegistry(DefaultDecoderTable, defaultDecoderProviders(probe))
