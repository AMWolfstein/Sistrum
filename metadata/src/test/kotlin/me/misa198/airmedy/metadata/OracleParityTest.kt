// SPDX-License-Identifier: GPL-3.0-or-later
// Sistrum parity harness; WaxLabel cfbb45d13135a76f5df6781fdeec75f5cfe63bf9.
// Copyright (c) 2026 Cole Springer (MIT); see metadata/THIRD-PARTY-NOTICES.
package me.misa198.airmedy.metadata

import com.google.gson.JsonObject
import me.misa198.airmedy.codecs.container.ByteBufferSource
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.ByteBuffer

@RunWith(Parameterized::class)
class OracleParityTest(private val name: String, private val row: JsonObject) {
    @Test fun fullReadProjectionEqualsPinnedCli() {
        val data = fixtureBytes(row)
        val doc = Metadata.read(ByteBufferSource(ByteBuffer.wrap(data)),ReadOptions(formatHint=name.substringAfterLast('.')))
        if(row.has("errorClass")) { assertEquals(name, row["errorClass"].asString,doc.error?.errorClass?.name); return }
        assertNull("$name: ${doc.error}",doc.error)
        if (name in boundedProbeDifferences) assertBoundedProbeDifference(doc) else assertEquals(name,row["oracle"],jsonProjection(doc))
        val supplement = row.getAsJsonObject("supplement")
        if (name !in boundedProbeDifferences) assertEquals(name,supplement["totalSamples"].asLong,doc.properties.totalSamples)
        else assertEquals(0L,doc.properties.totalSamples)
        assertEquals(name,supplement.getAsJsonArray("pictureSha256").map { it.asString },doc.pictures.map { it.sha256 })
        assertTrue(doc.pictures.all { it.bytes() == null })
        val full = Metadata.read(ByteBufferSource(ByteBuffer.wrap(data)),ReadOptions(pictures=Pictures.FULL,formatHint=name.substringAfterLast('.')))
        if (name in boundedProbeDifferences) assertBoundedProbeDifference(full) else assertEquals(name,row["oracle"],jsonProjection(full))
        for(pic in full.pictures) {
            val bytes = pic.bytes()!!
            assertEquals(pic.sha256,java.security.MessageDigest.getInstance("SHA-256").digest(bytes).hex())
            if(bytes.isNotEmpty()) bytes[0] = (bytes[0].toInt() xor 255).toByte()
            assertEquals(pic.sha256,java.security.MessageDigest.getInstance("SHA-256").digest(pic.bytes()!!).hex())
        }
    }
    /** PATCH-2: upstream's arbitrary scan mistakes UTF-16 bytes in unreadable tags
     * for MP1 headers. Assert every unaffected field and the precise allowed changes. */
    private fun assertBoundedProbeDifference(doc: Document) {
        val expected = row.getAsJsonObject("oracle")
        val actual = jsonProjection(doc).asJsonObject
        assertEquals(expected.keySet(),actual.keySet())
        for(key in expected.keySet()-setOf("properties","native","warnings")) assertEquals("$name/$key",expected[key],actual[key])
        assertEquals("MP1",expected.getAsJsonObject("properties")["codec"].asString)
        assertEquals("MPEG-1 Layer 1",expected.getAsJsonObject("properties")["codecProfile"].asString)
        assertEquals(com.google.gson.JsonParser.parseString("""{"container":"MP3","codec":"MPEG audio"}"""),actual["properties"])
        val native = actual.getAsJsonArray("native")
        assertEquals(1,native.size())
        assertEquals("MPEG audio",native[0].asJsonObject["kind"].asString)
        assertEquals("MPEG audio",native[0].asJsonObject["note"].asString)
        assertEquals(expected.getAsJsonArray("native")[0].asJsonObject["size"],native[0].asJsonObject["size"])
        val warnings = actual.getAsJsonArray("warnings")
        assertEquals(expected.getAsJsonArray("warnings").size()+1,warnings.size())
        expected.getAsJsonArray("warnings").forEachIndexed { index,item -> assertEquals(item,warnings[index]) }
        assertEquals(com.google.gson.JsonParser.parseString("""{"code":"no-audio","message":"no MPEG audio frames found; file may not be audio"}"""),warnings.last())
    }
    companion object {
        internal val boundedProbeDifferences = setOf("owned/truncated-mutagen-v23-469.mp3",
            "owned/truncated-mutagen-v23-937.mp3","owned/truncated-mutagen-v24-692.mp3")
        @JvmStatic @Parameterized.Parameters(name="{0}") fun corpus(): List<Array<Any>> = fixtures().map { arrayOf(it["path"].asString,it) }
    }
}
