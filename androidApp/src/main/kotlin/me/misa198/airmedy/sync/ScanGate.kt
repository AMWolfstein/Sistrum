package me.misa198.airmedy.sync

import kotlinx.serialization.Serializable
import me.misa198.airmedy.player.decoders.DecoderRegistry
import me.misa198.airmedy.player.decoders.FormatKey

/**
 * The outcome of admitting one scanned file (FR-065). [Admitted] carries the id of
 * the provider that will play the file; [Skipped] explains, in the same wording the
 * decoder registry uses, why the file was not admitted.
 */
internal sealed interface Admission {
    data class Admitted(val providerId: String) : Admission

    data class Skipped(val format: String, val codec: String, val reason: String) : Admission
}

/**
 * Decides whether a scanned file enters the library. [header] reads the first `n`
 * bytes of the file, or returns null when it cannot be read.
 */
internal fun interface ScanGate {
    fun admit(format: String, codec: String, header: (Int) -> ByteArray?): Admission
}

/** The native engine admits every file exactly as before the gate existed. */
internal val AdmitAllGate = ScanGate { _, _, _ -> Admission.Admitted("native") }

/**
 * The Media3-engine gate (FR-065): a file is admitted only when the decoder
 * registry has a provider for its format/codec. Providers that refuse by header
 * ([me.misa198.airmedy.player.decoders.DecoderProvider.refusalHeaderBytes]) are
 * consulted only after the registry has resolved them. A throwing [header] or
 * [me.misa198.airmedy.player.decoders.DecoderProvider.refusal] admits the file —
 * the decode-failure path still catches it later.
 */
internal class RegistryScanGate(private val registry: DecoderRegistry) : ScanGate {
    override fun admit(format: String, codec: String, header: (Int) -> ByteArray?): Admission {
        val key = FormatKey(format, codec)
        val provider = runCatching { registry.resolve(key) }.getOrNull()
            ?: return Admission.Skipped(
                format = format,
                codec = codec,
                reason = runCatching { registry.skipReason(key) }.getOrNull() ?: "unsupported format",
            )
        val needed = provider.refusalHeaderBytes
        if (needed > 0) {
            val bytes = runCatching { header(needed) }.getOrNull()
            if (bytes != null) {
                val refusal = runCatching { provider.refusal(bytes) }.getOrNull()
                if (refusal != null) return Admission.Skipped(format, codec, refusal)
            }
        }
        return Admission.Admitted(provider.id)
    }
}

/** One skipped format/codec/reason and how many files of it were skipped in a scan. */
@Serializable
internal data class SkippedEntry(
    val format: String,
    val codec: String,
    val reason: String,
    val count: Int,
)

/** FR-066: the per-scan summary of skipped files, grouped by format, codec and reason. */
@Serializable
internal data class SkippedFilesSummary(
    val scanAtMillis: Long,
    val entries: List<SkippedEntry>,
)

/** Accumulates [Admission.Skipped] results during one scan into a [SkippedFilesSummary]. */
internal class SkippedFilesCounter {
    private val counts = linkedMapOf<Triple<String, String, String>, Int>()

    fun add(skipped: Admission.Skipped) {
        val key = Triple(skipped.format, skipped.codec, skipped.reason)
        counts[key] = (counts[key] ?: 0) + 1
    }

    fun summary(scanAtMillis: Long): SkippedFilesSummary {
        val entries = counts.map { (key, count) ->
            SkippedEntry(format = key.first, codec = key.second, reason = key.third, count = count)
        }.sortedWith(
            compareByDescending<SkippedEntry> { it.count }
                .thenBy { it.format }
                .thenBy { it.codec }
                .thenBy { it.reason },
        )
        return SkippedFilesSummary(scanAtMillis = scanAtMillis, entries = entries)
    }
}
