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

Table (001), in order per key — illustrative. Keys are the scanner's labels: `realCodec` sniffs a codec only for
m4a/mp4 (aac, alac, flac, opus, mpeg, ac3, eac3, …); every other format's codec equals the format (ogg/ogg covers Ogg
Vorbis and Ogg Opus, wav/wav and aiff/aiff cover all PCM variants). The authoritative table is
`DefaultDecoderTable` in `player/decoders/DecoderRegistry.kt` (T036):

| format / codec | providers |
|---|---|
| mp3/mp3, aac/aac, m4a/aac, flac/flac, ogg/vorbis, ogg/opus, opus/opus, wav/pcm, mka/* | platform |
| m4a/alac | platform (absent on the CPH2307) — 002 adds kotlin-alac |
| aiff/pcm, aifc/pcm | kotlin-aiff |
| ape, wv, dsf, dff, wma, mpc, … | none (skipped) — 002 |

Rules: configuration lives only in the table; the scanner calls `resolve` and never names formats itself;
every playback error carries `provider.id`.
