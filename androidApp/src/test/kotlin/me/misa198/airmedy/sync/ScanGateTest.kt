package me.misa198.airmedy.sync

import androidx.media3.extractor.Extractor
import java.io.ByteArrayOutputStream
import me.misa198.airmedy.player.decoders.CodecProbe
import me.misa198.airmedy.player.decoders.DecoderProvider
import me.misa198.airmedy.player.decoders.DecoderTable
import me.misa198.airmedy.player.decoders.DecoderTableEntry
import me.misa198.airmedy.player.decoders.FormatKey
import me.misa198.airmedy.player.decoders.TableDecoderRegistry
import me.misa198.airmedy.player.decoders.defaultDecoderRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanGateTest {

    private fun u16(value: Int): ByteArray =
        byteArrayOf(((value ushr 8) and 0xFF).toByte(), (value and 0xFF).toByte())

    private fun u32(value: Int): ByteArray = byteArrayOf(
        ((value ushr 24) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte(),
    )

    /** A minimal FORM/COMM AIFF/AIFC header, enough for `aiffRefusal` to decide. */
    private fun aiffHeader(form: String, bits: Int = 16, compression: String? = null): ByteArray {
        val comm = ByteArrayOutputStream()
        comm.write(u16(1))
        comm.write(u32(0))
        comm.write(u16(bits))
        comm.write(ByteArray(10))
        if (form == "AIFC") comm.write((compression ?: "NONE").toByteArray(Charsets.US_ASCII))
        val body = comm.toByteArray()

        val out = ByteArrayOutputStream()
        out.write("FORM".toByteArray(Charsets.US_ASCII))
        out.write(u32(4 + 8 + body.size))
        out.write(form.toByteArray(Charsets.US_ASCII))
        out.write("COMM".toByteArray(Charsets.US_ASCII))
        out.write(u32(body.size))
        out.write(body)
        return out.toByteArray()
    }

    @Test
    fun `admit all gate admits everything and never reads a header`() {
        var headerCalls = 0
        val admission = AdmitAllGate.admit("dsf", "dsf") { headerCalls++; ByteArray(it) }

        assertEquals(Admission.Admitted("native"), admission)
        assertEquals(0, headerCalls)
    }

    @Test
    fun `registry gate admits platform formats and the bundled aiff provider`() {
        val gate = RegistryScanGate(defaultDecoderRegistry(CodecProbe { true }))

        listOf(
            "mp3" to "mp3",
            "m4a" to "aac",
            "ogg" to "ogg",
            "flac" to "flac",
            "wav" to "wav",
        ).forEach { (format, codec) ->
            assertEquals(Admission.Admitted("platform"), gate.admit(format, codec) { ByteArray(it) })
        }
        assertEquals(Admission.Admitted("kotlin-aiff"), gate.admit("aiff", "aiff") { aiffHeader("AIFF") })
    }

    @Test
    fun `registry gate skips formats the registry cannot play`() {
        val gate = RegistryScanGate(defaultDecoderRegistry(CodecProbe { true }))
        assertEquals(
            Admission.Skipped("dsf", "dsf", "unsupported format (DSD)"),
            gate.admit("dsf", "dsf") { ByteArray(it) },
        )
        assertEquals(
            Admission.Skipped("wma", "wma", "unsupported format (WMA)"),
            gate.admit("wma", "wma") { ByteArray(it) },
        )

        val withoutAlac = RegistryScanGate(defaultDecoderRegistry(CodecProbe { it != "audio/alac" }))
        assertEquals(
            Admission.Skipped("m4a", "alac", "no decoder on this device (ALAC)"),
            withoutAlac.admit("m4a", "alac") { ByteArray(it) },
        )
    }

    @Test
    fun `registry gate names the refusal and asks for the provider's header size`() {
        val gate = RegistryScanGate(defaultDecoderRegistry(CodecProbe { true }))
        var requested = 0
        assertEquals(
            Admission.Skipped("aiff", "aiff", "compressed AIFF-C (ima4)"),
            gate.admit("aiff", "aiff") { n -> requested = n; aiffHeader("AIFC", compression = "ima4") },
        )
        assertEquals(4096, requested)
        assertEquals(
            Admission.Skipped("aiff", "aiff", "float AIFF-C (fl32)"),
            gate.admit("aiff", "aiff") { aiffHeader("AIFC", compression = "fl32") },
        )
    }

    @Test
    fun `registry gate admits aiff when the header cannot be read`() {
        val gate = RegistryScanGate(defaultDecoderRegistry(CodecProbe { true }))
        assertEquals(Admission.Admitted("kotlin-aiff"), gate.admit("aiff", "aiff") { null })
    }

    @Test
    fun `a throwing refusal admits the file`() {
        val throwing = object : DecoderProvider {
            override val id: String = "boom"
            override fun isAvailable(key: FormatKey): Boolean = true
            override fun extractors(): List<() -> Extractor> = emptyList()
            override val refusalHeaderBytes: Int get() = 16
            override fun refusal(header: ByteArray): String? = error("boom")
        }
        val table = DecoderTable(listOf(DecoderTableEntry("x", "x", listOf("boom"), emptyList(), "X")))
        val gate = RegistryScanGate(TableDecoderRegistry(table, listOf(throwing)))

        assertEquals(Admission.Admitted("boom"), gate.admit("x", "x") { ByteArray(it) })
    }

    @Test
    fun `counter groups by format codec and reason and sorts by count`() {
        val counter = SkippedFilesCounter()
        repeat(3) { counter.add(Admission.Skipped("dsf", "dsf", "unsupported format (DSD)")) }
        repeat(2) { counter.add(Admission.Skipped("dff", "dff", "unsupported format (DSD)")) }
        counter.add(Admission.Skipped("wma", "wma", "unsupported format (WMA)"))

        val summary = counter.summary(42L)
        assertEquals(42L, summary.scanAtMillis)
        assertEquals(
            listOf(
                SkippedEntry("dsf", "dsf", "unsupported format (DSD)", 3),
                SkippedEntry("dff", "dff", "unsupported format (DSD)", 2),
                SkippedEntry("wma", "wma", "unsupported format (WMA)", 1),
            ),
            summary.entries,
        )
    }

    @Test
    fun `summary round-trips through json and rejects garbage`() {
        val summary = SkippedFilesSummary(
            scanAtMillis = 123L,
            entries = listOf(SkippedEntry("dsf", "dsf", "unsupported format (DSD)", 2)),
        )

        assertEquals(summary, decodeSkippedSummary(encodeSkippedSummary(summary)))
        assertNull(decodeSkippedSummary("not json"))
    }
}
