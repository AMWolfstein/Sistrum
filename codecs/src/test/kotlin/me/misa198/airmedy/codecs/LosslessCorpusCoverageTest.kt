// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/ape/ape.go and codec/alac/alac.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test
import me.misa198.airmedy.codecs.codec.alac.Config
import me.misa198.airmedy.codecs.codec.alac.AlacException

class LosslessCorpusCoverageTest {
 @Test fun apeMatrixAndLegacyArePresent() {
  val rows=OracleCorpus.allRows.associateBy { it.name }
  for(level in listOf(1000,2000,3000,4000,5000)) for(depth in listOf(16,24)) for(channels in listOf(1,2)) {
   val fixture=rows.getValue("waxflow-ape-tests/generated/signal-c$level-s$depth-${channels}ch.ape")
   assertEquals("ok",fixture.status);assertEquals(depth.toString(),fixture.value("bits"));assertEquals(channels.toString(),fixture.value("channels"))
   val bytes=fixture.byteBuffer().order(ByteOrder.LITTLE_ENDIAN)
   assertEquals(3990,bytes.getShort(4).toInt());val header=bytes.getInt(8)
   assertEquals(level,bytes.getShort(header).toInt())
  }
  val legacy=rows.getValue("waxflow-ape-tests/generated/legacy-3970-silence-s16-mono.ape")
  assertEquals("ok",legacy.status);assertEquals(3970,legacy.byteBuffer().order(ByteOrder.LITTLE_ENDIAN).getShort(4).toInt())
 }
 @Test fun alacDeepMonoStereoAndMultichannelRefusalsArePresent() {
  val rows=OracleCorpus.allRows.associateBy { it.name }
  for(channels in listOf(1,2)) {
   val fixture=rows.getValue("waxflow-alac-tests/generated/signal-s24-${channels}ch.m4a")
   assertEquals("ok",fixture.status);assertEquals("24",fixture.value("bits"));assertEquals(channels.toString(),fixture.value("channels"))
  }
  for(channels in listOf(4,6,8)) {
   val fixture=rows.getValue("waxflow-alac-tests/generated/signal-s24-${channels}ch.m4a")
   // Test-only extraction of the 36-byte ALAC configuration box written by the
   // corpus encoder. Production receives this cookie from Media3's extractor.
   val bytes=fixture.byteBuffer().order(ByteOrder.BIG_ENDIAN);var cookie:ByteBuffer?=null
   for(i in 0..bytes.limit()-36) if(bytes.getInt(i)==36&&bytes.getInt(i+4)==0x616c6163) {
    check(cookie==null) { "Ambiguous ALAC cookie" };cookie=bytes.slice(i+12,24)
   }
   assertNotNull(cookie);assertEquals(channels,cookie!!.get(9).toInt());assertEquals(24,cookie.get(5).toInt())
   try { Config(cookie);fail("Accepted oracle-refused ALAC stream") }
   catch(e:AlacException) {assertEquals(AlacException.Reason.UNSUPPORTED,e.reason);assertEquals(fixture.status.removePrefix("refused: waxflow: "),e.message)}
  }
 }
}
