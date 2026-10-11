// SPDX-License-Identifier: GPL-3.0-or-later
// Sistrum parity harness; WaxLabel cfbb45d13135a76f5df6781fdeec75f5cfe63bf9.
// Copyright (c) 2026 Cole Springer (MIT); see metadata/THIRD-PARTY-NOTICES.
package com.amwolfstein.sistrum.metadata

import com.google.gson.JsonObject
import com.amwolfstein.sistrum.codecs.container.ByteBufferSource
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.ByteBuffer
import java.security.MessageDigest

@RunWith(Parameterized::class)
class VorbisOracleParityTest(private val name: String,private val row: JsonObject) {
    @Test fun readEqualsPinnedCli() {
        val data=fixtureBytes(row)
        for(mode in listOf(Pictures.METADATA_ONLY,Pictures.FULL)) {
            val doc=Metadata.read(ByteBufferSource(ByteBuffer.wrap(data)),ReadOptions(pictures=mode))
            if(row.has("errorClass")) { assertEquals(name,row["errorClass"].asString,doc.error?.errorClass?.name); continue }
            assertNull("$name: ${doc.error}",doc.error)
            assertEquals(name,expected(doc),jsonProjection(doc))
            assertEquals(name,row.getAsJsonObject("supplement")["totalSamples"].asLong,doc.properties.totalSamples)
            assertEquals(name,row.getAsJsonObject("supplement").getAsJsonArray("pictureSha256").map { it.asString },doc.pictures.map { it.sha256 })
            for(pic in doc.pictures) {
                if(mode==Pictures.METADATA_ONLY) assertNull(pic.bytes())
                else { val bytes=pic.bytes()!!; assertEquals(pic.sha256,MessageDigest.getInstance("SHA-256").digest(bytes).hex())
                    if(bytes.isNotEmpty()) bytes[0]=(bytes[0].toInt() xor 255).toByte()
                    assertEquals(pic.sha256,MessageDigest.getInstance("SHA-256").digest(pic.bytes()!!).hex()) }
            }
        }
    }
    /** PATCH-5 permits precisely these audio-scan deltas; the stored CLI JSON remains untouched. */
    private fun expected(doc: Document): JsonObject {
        val expected=row.getAsJsonObject("oracle").deepCopy()
        if(name=="owned/vorbis-tail-junk.flac") {
            val warnings=expected.getAsJsonArray("warnings")
            assertEquals(1,warnings.size())
            assertEquals("trailing-bytes",warnings[0].asJsonObject["code"].asString)
            assertEquals("4096 byte(s) after the FLAC stream belong to no frame; preserved verbatim",warnings[0].asJsonObject["message"].asString)
            warnings.remove(0)
            val native=expected.getAsJsonArray("native")
            val tail=native.remove(native.size()-1).asJsonObject
            assertEquals("trailing bytes",tail["kind"].asString); assertEquals(4096L,tail["size"].asLong)
            val props=expected.getAsJsonObject("properties")
            assertEquals(5680,props["bitrateBps"].asInt)
            assertEquals(169520,doc.properties.bitrate)
            props.addProperty("bitrateBps",169520)
        }
        if(name in setOf("owned/vorbis-truncated-audio.flac","owned/vorbis-truncated-8427.flac")) {
            val warning=expected.getAsJsonArray("warnings").remove(0).asJsonObject
            assertEquals("truncated-audio",warning["code"].asString)
            assertEquals("STREAMINFO declares more audio than the file holds; file may be truncated",warning["message"].asString)
        }
        return expected
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name="{0}") fun corpus(): List<Array<Any>> = fixtures(2).map { arrayOf(it["path"].asString,it) }
    }
}
