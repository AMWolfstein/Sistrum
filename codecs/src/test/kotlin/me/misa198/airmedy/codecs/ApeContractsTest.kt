// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/ape/ape_test.go, container/apen/demux_test.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs

import java.nio.ByteOrder
import me.misa198.airmedy.codecs.codec.ape.ApeException
import me.misa198.airmedy.codecs.container.apen.Ape
import org.junit.Assert.*
import org.junit.Test

class ApeContractsTest {
    @Test fun unsupportedShapesRetainPinnedGoMessages() {
        val fixture=OracleCorpus.allRows.single { it.name=="waxflow-testdata/sine-s16.ape" }
        // Header-field mutations exercise named refusals from the pinned parser,
        // independently of the successful corpus PCM checksum assertions.
        val cases=listOf(
            Triple(4,3940,"stream version 3940 predates 3950: the 3.9x bitstreams are a different codec"),
            Triple(4,4000,"stream version 4000 is past the supported 3990"),
            Triple(54,4096,"floating-point streams are not supported"),
            Triple(68,32,"32-bit samples: only 8, 16, and 24-bit are supported"),
            Triple(70,6,"6 channels: only mono and stereo are supported"),
            Triple(52,6000,"compression level 6000 is not one of 1000..5000")
        )
        for ((offset,value,message) in cases) {
            val data=fixture.byteBuffer().order(ByteOrder.LITTLE_ENDIAN)
            data.putShort(offset,value.toShort())
            try { Ape.open(data); fail("Accepted $message") }
            catch (e: ApeException) { assertEquals(ApeException.Reason.UNSUPPORTED,e.reason); assertEquals("ape: $message",e.message) }
        }
    }
    @Test fun invalidSeekAndStrictDescriptorMismatch() {
        val fixture=OracleCorpus.allRows.single { it.name=="waxflow-testdata/sine-s16.ape" }
        val source=fixture.byteBuffer().order(ByteOrder.LITTLE_ENDIAN)
        source.putInt(24,source.getInt(24)+1)
        val stream=Ape.open(source); val warning=stream.demuxer.warnings.single()
        try { Ape.open(source,true); fail("Strict descriptor mismatch accepted") }
        catch (e: ApeException) { assertEquals("ape: ${warning.message} (at offset ${warning.offset})",e.message) }
        try { stream.seekSample(-1); fail("Negative seek accepted") }
        catch (e: ApeException) { assertEquals(ApeException.Reason.INVALID_REQUEST,e.reason); assertEquals("ape: negative seek target",e.message) }
    }
}
