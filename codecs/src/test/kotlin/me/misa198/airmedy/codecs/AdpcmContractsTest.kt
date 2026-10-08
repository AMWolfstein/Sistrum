// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/adpcm/adpcm_test.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import me.misa198.airmedy.codecs.codec.adpcm.*

class AdpcmContractsTest {
 @Test fun unsupportedGeometryMatchesSource(){val cases=listOf(
 Config(Layout.MS,3,21,2) to "3 channels of MS ADPCM, whose nibbles alternate between at most 2",
 Config(Layout.IMAQuickTime,2,34,64) to "34-byte blocks, but the IMA ADPCM (QuickTime layout) is 34 bytes per channel",
 Config(Layout.MS,1,6,0) to "6-byte blocks hold no MS ADPCM header for 1 channels",
 Config(Layout.IMAQuickTime,1,34,63) to "63 samples per block, but 34 bytes of IMA ADPCM (QuickTime layout) hold 64")
 for((cfg,msg) in cases)try{cfg.validate();fail()}catch(e:AdpcmException){assertTrue(e.unsupported);assertEquals("adpcm: $msg",e.message)}}
 @Test fun namedBlockErrorsMatchSource(){val qt=Decoder(Config(Layout.IMAQuickTime,1,34,64));val bytes=ByteArray(34);bytes[1]=89
 try{qt.decodeBlock(ByteBuffer.wrap(bytes));fail()}catch(e:AdpcmException){assertFalse(e.unsupported);assertEquals("adpcm: step index 89 in a block header (max 88)",e.message)}
 val ms=Decoder(Config(Layout.MS,1,7,2,defaultCoefs));val block=ByteArray(7);block[0]=7
 try{ms.decodeBlock(ByteBuffer.wrap(block));fail()}catch(e:AdpcmException){assertFalse(e.unsupported);assertEquals("adpcm: predictor index 7 in a block header (the table holds 7)",e.message)}
 try{ms.decodeBlock(ByteBuffer.wrap(ByteArray(6)));fail()}catch(e:AdpcmException){assertEquals("adpcm: packet of 6 bytes is not a whole number of 7-byte blocks",e.message)}}
}
