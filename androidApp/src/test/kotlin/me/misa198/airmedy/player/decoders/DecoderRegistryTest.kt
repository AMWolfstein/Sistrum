@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package me.misa198.airmedy.player.decoders

import androidx.media3.extractor.Extractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeProvider(
    override val id: String,
    private val available: Boolean,
) : DecoderProvider {
    override fun isAvailable(key: FormatKey): Boolean = available
    override fun extractors(): List<() -> Extractor> = emptyList()
}

private fun probeOf(vararg mimes: String): CodecProbe = CodecProbe { it in mimes }

private val probeAll = CodecProbe { true }
private val probeNone = CodecProbe { false }

class DecoderRegistryTest {

    private fun table(format: String, codec: String, vararg providers: String): DecoderTable =
        DecoderTable(listOf(DecoderTableEntry(format, codec, providers.toList(), emptyList(), "Test")))

    @Test
    fun `first available provider wins`() {
        val a = FakeProvider("a", available = false)
        val b = FakeProvider("b", available = true)
        assertEquals("b", TableDecoderRegistry(table("x", "x", "a", "b"), listOf(a, b)).resolve(FormatKey("x", "x"))?.id)

        val aOn = FakeProvider("a", available = true)
        val bOn = FakeProvider("b", available = true)
        assertEquals("a", TableDecoderRegistry(table("x", "x", "a", "b"), listOf(aOn, bOn)).resolve(FormatKey("x", "x"))?.id)
    }

    @Test
    fun `reordering providers is a table-only change`() {
        val a = FakeProvider("a", available = true)
        val b = FakeProvider("b", available = true)
        assertEquals("b", TableDecoderRegistry(table("x", "x", "b", "a"), listOf(a, b)).resolve(FormatKey("x", "x"))?.id)
    }

    @Test
    fun `m4a codecs use the platform provider when probed`() {
        val aac = defaultDecoderRegistry(probeOf("audio/mp4a-latm"))
        assertEquals("platform", aac.resolve(FormatKey("m4a", "aac"))?.id)

        val alac = defaultDecoderRegistry(probeOf("audio/alac"))
        assertEquals("platform", alac.resolve(FormatKey("m4a", "alac"))?.id)
    }

    @Test
    fun `m4a alac without a platform decoder is skipped`() {
        val registry = defaultDecoderRegistry(probeOf("audio/mp4a-latm"))
        assertNull(registry.resolve(FormatKey("m4a", "alac")))
        assertEquals("no decoder on this device (ALAC)", registry.skipReason(FormatKey("m4a", "alac")))
    }

    @Test
    fun `lossless formats without a provider report an unsupported format`() {
        val registry = defaultDecoderRegistry(probeAll)
        assertNull(registry.resolve(FormatKey("dsf", "dsf")))
        assertEquals("unsupported format (DSD)", registry.skipReason(FormatKey("dsf", "dsf")))
        assertEquals("unsupported format (DSD)", registry.skipReason(FormatKey("dff", "dff")))
        assertEquals("unsupported format (APE)", registry.skipReason(FormatKey("ape", "ape")))
        assertEquals("unsupported format (WavPack)", registry.skipReason(FormatKey("wv", "wv")))
        assertEquals("unsupported format (WMA)", registry.skipReason(FormatKey("wma", "wma")))
    }

    @Test
    fun `unknown format is unsupported`() {
        val registry = defaultDecoderRegistry(probeAll)
        assertNull(registry.resolve(FormatKey("xyz", "xyz")))
        assertEquals("unsupported format (XYZ)", registry.skipReason(FormatKey("xyz", "xyz")))
    }

    @Test
    fun `raw PCM resolves without a codec`() {
        val registry = defaultDecoderRegistry(probeNone)
        assertEquals("platform", registry.resolve(FormatKey("wav", "wav"))?.id)
    }

    @Test
    fun `aiff resolves only when the bundled provider is registered`() {
        val registry = defaultDecoderRegistry(probeNone)
        assertEquals("kotlin-aiff", registry.resolve(FormatKey("aiff", "aiff"))?.id)

        val withoutAiff = TableDecoderRegistry(
            DefaultDecoderTable,
            listOf(PlatformProvider(DefaultDecoderTable, probeNone)),
        )
        assertNull(withoutAiff.resolve(FormatKey("aiff", "aiff")))
    }

    @Test
    fun `ogg resolves when only opus is available`() {
        val registry = defaultDecoderRegistry(probeOf("audio/opus"))
        assertEquals("platform", registry.resolve(FormatKey("ogg", "ogg"))?.id)
    }

    @Test
    fun `wildcard entry matches any codec and exact entry beats it`() {
        val table = DecoderTable(
            listOf(
                DecoderTableEntry("mka", AnyCodec, listOf("wild"), emptyList(), "Matroska"),
                DecoderTableEntry("flac", AnyCodec, listOf("wild"), emptyList(), "WildFLAC"),
                DecoderTableEntry("flac", "flac", listOf("exact"), emptyList(), "FLAC"),
            ),
        )
        val registry = TableDecoderRegistry(
            table,
            listOf(FakeProvider("wild", available = true), FakeProvider("exact", available = true)),
        )
        assertEquals("wild", registry.resolve(FormatKey("mka", "vorbis"))?.id)
        assertEquals("wild", registry.resolve(FormatKey("flac", "opus"))?.id)
        assertEquals("exact", registry.resolve(FormatKey("flac", "flac"))?.id)
    }

    @Test
    fun `keys are case-insensitive`() {
        val registry = defaultDecoderRegistry(probeOf("audio/flac"))
        assertEquals("platform", registry.resolve(FormatKey("FLAC", "FLAC"))?.id)
        assertNull(registry.skipReason(FormatKey("FLAC", "FLAC")))
    }

    @Test
    fun `media codec probe reads the platform mimes once and compares case-insensitively`() {
        var calls = 0
        val probe = MediaCodecProbe {
            calls += 1
            setOf("Audio/MP4A-LATM")
        }
        assertTrue(probe.hasDecoder("audio/mp4a-latm"))
        assertTrue(probe.hasDecoder("AUDIO/MP4A-LATM"))
        assertFalse(probe.hasDecoder("audio/opus"))
        assertEquals(1, calls)
    }

    @Test
    fun `skip reason is null whenever a provider resolves`() {
        val registry = defaultDecoderRegistry(probeAll)
        val keys = listOf(
            FormatKey("mp3", "mp3"),
            FormatKey("m4a", "aac"),
            FormatKey("ogg", "ogg"),
            FormatKey("wav", "wav"),
            FormatKey("mka", "anything"),
        )
        keys.forEach { key ->
            assertNotNull("expected $key to resolve", registry.resolve(key))
            assertNull("expected no skip reason for $key", registry.skipReason(key))
        }
    }

    @Test
    fun `m4a wildcard covers unknown codecs and exact rows win`() {
        val registry = defaultDecoderRegistry(probeOf("audio/mp4a-latm"))
        assertEquals("platform", registry.resolve(FormatKey("m4a", "x-m4a"))?.id)
        assertEquals("MPEG-4 audio", DefaultDecoderTable.entryFor(FormatKey("m4a", "x-m4a"))?.label)
        assertEquals("platform", registry.resolve(FormatKey("m4a", "aac"))?.id)
        assertEquals("AAC", DefaultDecoderTable.entryFor(FormatKey("m4a", "aac"))?.label)
    }

    @Test
    fun `additional platform formats resolve when their codec is present`() {
        assertEquals("platform", defaultDecoderRegistry(probeOf("audio/opus")).resolve(FormatKey("webm", "webm"))?.id)
        assertEquals("platform", defaultDecoderRegistry(probeOf("audio/3gpp")).resolve(FormatKey("amr", "amr"))?.id)
        assertEquals("platform", defaultDecoderRegistry(probeOf("audio/amr-wb")).resolve(FormatKey("3gp", "3gp"))?.id)
        assertEquals("platform", defaultDecoderRegistry(probeOf("audio/ac3")).resolve(FormatKey("ac3", "ac3"))?.id)
        assertEquals("platform", defaultDecoderRegistry(probeOf("audio/eac3-joc")).resolve(FormatKey("m4a", "eac3-joc"))?.id)
    }

    @Test
    fun `additional platform formats without a decoder report their label`() {
        val registry = defaultDecoderRegistry(probeNone)
        assertEquals("no decoder on this device (WebM)", registry.skipReason(FormatKey("webm", "webm")))
        assertEquals("no decoder on this device (AMR)", registry.skipReason(FormatKey("amr", "amr")))
        assertEquals("no decoder on this device (3GP)", registry.skipReason(FormatKey("3gp", "3gp")))
        assertEquals("no decoder on this device (AC-3)", registry.skipReason(FormatKey("ac3", "ac3")))
        assertEquals("no decoder on this device (E-AC-3 JOC)", registry.skipReason(FormatKey("m4a", "eac3-joc")))
    }

    @Test
    fun `blank format is reported as unknown`() {
        val registry = defaultDecoderRegistry(probeAll)
        assertEquals("unsupported format (unknown)", registry.skipReason(FormatKey("", "")))
    }
}
