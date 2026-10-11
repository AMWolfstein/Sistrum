// SPDX-License-Identifier: GPL-3.0-or-later
// Sistrum parity harness; WaxLabel cfbb45d13135a76f5df6781fdeec75f5cfe63bf9.
// Copyright (c) 2026 Cole Springer, MIT; see metadata/THIRD-PARTY-NOTICES.
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
class MatroskaOracleParityTest(private val name: String,private val row: JsonObject) {
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
    private fun expected(): JsonObject=row.getAsJsonObject("oracle").deepCopy()
    companion object {
        @JvmStatic @Parameterized.Parameters(name="{0}") fun corpus(): List<Array<Any>> = fixtures(7).map { arrayOf(it["path"].asString,it) }
    }
}
