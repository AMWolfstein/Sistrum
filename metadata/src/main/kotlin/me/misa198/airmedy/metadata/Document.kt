// SPDX-License-Identifier: GPL-3.0-or-later
// Adapted from WaxLabel cfbb45d13135a76f5df6781fdeec75f5cfe63bf9, internal/core/.
// Copyright (c) 2026 Cole Springer, MIT; see metadata/THIRD-PARTY-NOTICES.
package me.misa198.airmedy.metadata

import java.util.Collections

enum class Pictures { NONE, METADATA_ONLY, FULL }
data class ReadOptions(
    val pictures: Pictures = Pictures.METADATA_ONLY,
    val maxTagBytes: Int = 64 * 1024 * 1024,
    val maxElements: Int = 100_000,
    val maxReadBytes: Long = 128L * 1024 * 1024,
    /** Optional container hint for empty or damaged inputs. */
    val formatHint: String? = null,
)
enum class ErrorClass { UNSUPPORTED_FORMAT, INVALID_DATA, SIZE_TOO_LARGE, IO }
data class MetadataError(val errorClass: ErrorClass, val message: String)
data class Warning(val code: String, val message: String)
data class AudioProperties(
    val container: String = "", val codec: String = "", val profile: String = "",
    val sampleRate: Int = 0, val channels: Int = 0, val bitsPerSample: Int = 0,
    val totalSamples: Long = 0, val durationNs: Long = 0, val bitrate: Int = 0,
    val opusOutputGain: Int = 0, val paddingBytes: Long = 0,
) { val durationMs: Long get() = minOf(Long.MAX_VALUE / 1_000_000,
    durationNs / 1_000_000 + if (durationNs % 1_000_000 >= 500_000) 1 else 0) }
data class NativeBlock(val kind: String, val size: Long, val note: String = "", val unit: String = "")
data class Chapter(val startMs: Long, val endMs: Long = 0, val title: String = "")
data class SyncedLine(val timeMs: Long, val text: String)
class SyncedLyrics(val language: String, val description: String, lines: List<SyncedLine>) {
    val lines: List<SyncedLine> = frozen(lines)
}
class FamilyValue(val key: String, val family: String, values: List<String>, val selected: Boolean, val legacy: Boolean = false) {
    val values: List<String> = frozen(values)
    val scope: String = "track"
}

/** Payload is never retained in scan mode. FULL enables a bounded, on-demand read;
 * the caller must keep its immutable source open until that read finishes. */
class Picture internal constructor(
    val type: Int, val mime: String, val description: String, val width: Int, val height: Int,
    val depth: Int, val colors: Int, val size: Int, val sha256: String,
    private val load: (() -> ByteArray)?,
) {
    val typeName: String get() = typeNames.getOrElse(type) { "reserved" }
    fun bytes(): ByteArray? = load?.invoke()
    companion object {
        private val typeNames = listOf("Other", "File icon", "Other file icon", "Front cover", "Back cover",
            "Leaflet", "Media", "Lead artist", "Artist", "Conductor", "Band", "Composer", "Lyricist",
            "Recording location", "During recording", "During performance", "Video screen capture", "Bright fish",
            "Illustration", "Band logo", "Publisher logo")
    }
}

/** Every exposed collection is detached and unmodifiable, including nested values. */
class Document internal constructor(
    val format: String, tags: Map<String, List<String>>, native: List<NativeBlock>,
    val properties: AudioProperties, pictures: List<Picture>, syncedLyrics: List<SyncedLyrics>,
    chapters: List<Chapter>, warnings: List<Warning>, families: List<FamilyValue>,
    val error: MetadataError? = null,
) {
    val tags: Map<String, List<String>> = Collections.unmodifiableMap(tags.mapValues { frozen(it.value) })
    val native: List<NativeBlock> = frozen(native)
    val pictures: List<Picture> = frozen(pictures)
    val syncedLyrics: List<SyncedLyrics> = frozen(syncedLyrics)
    val chapters: List<Chapter> = frozen(chapters)
    val warnings: List<Warning> = frozen(warnings)
    val families: List<FamilyValue> = frozen(families)
    val legacyOnlyKeys: List<String> = frozen(families.filter { it.legacy && it.key !in tags }.map { it.key }.distinct())
}
internal fun <T> frozen(values: Collection<T>): List<T> = Collections.unmodifiableList(ArrayList(values))

internal class ReadFailure(val kind: ErrorClass, message: String) : RuntimeException(message)
internal class Builder {
    var format = "unknown"
    var properties = AudioProperties()
    val tags = linkedMapOf<String, MutableList<String>>()
    val native = mutableListOf<NativeBlock>()
    val pictures = mutableListOf<Picture>()
    val lyrics = mutableListOf<SyncedLyrics>()
    val chapters = mutableListOf<Chapter>()
    val warnings = mutableListOf<Warning>()
    val families = mutableListOf<FamilyValue>()
    fun warn(code: String, message: String) { warnings += Warning(code, message) }
    fun document(error: MetadataError? = null) = Document(format, tags, native, properties, pictures, lyrics,
        chapters, warnings, families, error)
}
