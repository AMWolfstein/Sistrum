// SPDX-License-Identifier: GPL-3.0-or-later
// Sistrum parity harness; WaxLabel cfbb45d13135a76f5df6781fdeec75f5cfe63bf9.
// Copyright (c) 2026 Cole Springer, MIT; see metadata/THIRD-PARTY-NOTICES.
package me.misa198.airmedy.metadata

import com.google.gson.JsonObject
import me.misa198.airmedy.codecs.container.ByteBufferSource
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.ByteBuffer
import java.security.MessageDigest

@RunWith(Parameterized::class)
class AsfOracleParityTest(private val name: String,private val row: JsonObject) {
    @Test fun readEqualsPinnedCli() {
        val data=fixtureBytes(row)
        for(mode in listOf(Pictures.METADATA_ONLY,Pictures.FULL)) {
            val doc=Metadata.read(ByteBufferSource(ByteBuffer.wrap(data)),ReadOptions(pictures=mode))
            if(row.has("errorClass")) { assertEquals(name,row["errorClass"].asString,doc.error?.errorClass?.name); continue }
            assertNull("$name: ${doc.error}",doc.error)
            assertEquals(name,expected(),jsonProjection(doc))
            assertEquals(name,row.getAsJsonObject("supplement")["totalSamples"].asLong,doc.properties.totalSamples)
            assertEquals(name,row.getAsJsonObject("supplement").getAsJsonArray("pictureSha256").map { it.asString },doc.pictures.map { it.sha256 })
            for(pic in doc.pictures) {
                if(mode==Pictures.METADATA_ONLY) assertNull(pic.bytes())
                else {
                    val bytes=pic.bytes()!!
                    assertEquals(pic.sha256,MessageDigest.getInstance("SHA-256").digest(bytes).hex())
                    if(bytes.isNotEmpty()) bytes[0]=(bytes[0].toInt() xor 255).toByte()
                    assertEquals(pic.sha256,MessageDigest.getInstance("SHA-256").digest(pic.bytes()!!).hex())
                }
            }
        }
    }
    /** PATCH-8: bounded Data probes and the public Int sample-rate range. */
    private fun expected(): JsonObject {
        val expected=row.getAsJsonObject("oracle").deepCopy()
        if(name=="owned/asf-rate-over-int.wma") {
            val p=expected.getAsJsonObject("properties")
            assertEquals(4294967295L,p["sampleRate"].asLong);p.addProperty("sampleRate",Int.MAX_VALUE)
        }
        if(name=="owned/asf-data-audio-alias.wma") {
            val data=expected.getAsJsonArray("native").last().asJsonObject
            assertEquals("data",data["kind"].asString);assertEquals(64,data["size"].asInt);data.addProperty("size",0)
            val warnings=expected.getAsJsonArray("warnings");assertEquals(0,warnings.size())
            warnings.add(com.google.gson.JsonParser.parseString("""{"code":"no-audio","message":"no audio essence found; file may be tag-only or truncated"}"""))
        }
        return expected
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name="{0}") fun corpus(): List<Array<Any>> = fixtures(6).map { arrayOf(it["path"].asString,it) }
    }
}
