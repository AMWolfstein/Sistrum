# Contract — Decoder Registry (ADR-002)

```kotlin
data class FormatKey(val format: String, val codec: String)   // scanner labels: audioFormatOf / realCodec

interface DecoderProvider {
    val id: String                                    // "platform", "kotlin-aiff"; 002: "kotlin-wavpack", …
    fun isAvailable(key: FormatKey): Boolean          // device probe (MediaCodecList for platform)
    fun extractors(): List<() -> Extractor>           // added to Media3's ExtractorsFactory
    fun audioRenderers(context: Context): List<Renderer> = emptyList()   // 002 Pattern B
    fun refusal(header: ByteArray): String? = null    // named refusal, e.g. "compressed AIFF-C (ima4)"
}

interface DecoderRegistry {
    fun resolve(key: FormatKey): DecoderProvider?     // first available in the table order, or null
    fun skipReason(key: FormatKey): String?           // "unsupported format (DSD)" when resolve() is null
}
```

Table (001), in order per key — illustrative, the task writes the full one from the scanner's labels:

| format / codec | providers |
|---|---|
| mp3/mp3, aac/aac, m4a/aac, flac/flac, ogg/vorbis, ogg/opus, opus/opus, wav/pcm, mka/* | platform |
| m4a/alac | platform (absent on the CPH2307) — 002 adds kotlin-alac |
| aiff/pcm, aifc/pcm | kotlin-aiff |
| ape, wv, dsf, dff, wma, mpc, … | none (skipped) — 002 |

Rules: configuration lives only in the table; the scanner calls `resolve` and never names formats itself;
every playback error carries `provider.id`.
