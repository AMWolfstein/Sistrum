// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/alac/fuzz_test.go, codec/alac/alac.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs

import java.nio.ByteBuffer
import java.nio.ByteOrder
import me.misa198.airmedy.codecs.codec.alac.*
import org.junit.Assert.*
import org.junit.Test

class AlacContractsTest {
    private fun cookie()=AlacPackets(OracleCorpus.allRows.single { it.name.endsWith("/alac-stereo.m4a") }).cookie
    @Test fun namedCookieRefusals() {
        val original=cookie()
        for ((offset,value,message) in listOf(Triple(5,8,"bit depth 8, want 16/20/24/32"),Triple(9,6,"channel count 6: only mono and stereo are supported"))) {
            val b=ByteBuffer.allocate(24); b.put(original.duplicate()); b.put(offset,value.toByte()); b.position(0)
            try { Config(b); fail("Accepted $message") }
            catch (e: AlacException) { assertEquals("alac: $message",e.message) }
        }
        val huge=ByteBuffer.allocate(24).order(ByteOrder.BIG_ENDIAN); huge.put(original.duplicate()); huge.putInt(0,16385); huge.position(0)
        try { Config(huge); fail("Accepted oversized frame") }
        catch (e: AlacException) { assertEquals("alac: frame length 16385 outside 1..16384",e.message) }
    }
    @Test fun unsupportedElementsRetainSourceReasons() {
        val decoder=Decoder(Config(cookie()))
        for (tag in intArrayOf(2,5)) {
            try { decoder.decode(ByteBuffer.wrap(byteArrayOf((tag shl 5).toByte()))); fail("Accepted element $tag") }
            catch (e: AlacException) { assertEquals(AlacException.Reason.UNSUPPORTED,e.reason); assertEquals("alac: unsupported element type $tag",e.message) }
        }
    }
}
