// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/musepack/musepack_test.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import me.misa198.airmedy.codecs.codec.musepack.*
import me.misa198.airmedy.codecs.container.mpc.Musepack

class MusepackContractsTest {
 @Test fun legacyVersionsAreNamedIdenticallyByCodecAndContainer(){for(version in 4..6){val message="musepack: Musepack SV$version is not supported: only SV7 and SV8 are"
 try{Config(version,44100,2,31,true).validate();fail()}catch(e:MusepackException){assertTrue(e.unsupported);assertEquals(message,e.message)}
 try{Musepack.open(ByteBuffer.wrap(byteArrayOf(77,80,43,version.toByte())));fail()}catch(e:MusepackException){assertTrue(e.unsupported);assertEquals(message,e.message)}}}
 @Test fun unsupportedChannelsAndMalformedShapes(){for(ch in 3..8){try{Config(8,44100,ch,31,true).validate();fail()}catch(e:MusepackException){assertTrue(e.unsupported);assertEquals("musepack: $ch channels: only mono and stereo are supported",e.message)}}
 try{Config(8,44100,2,32,true).validate();fail()}catch(e:MusepackException){assertFalse(e.unsupported);assertEquals("musepack: max band 32 outside 1..31",e.message)}
 try{Config(8,44100,2,31,true,3).validate();fail()}catch(e:MusepackException){assertFalse(e.unsupported);assertEquals("musepack: block power 3 is not an even number in 0..14",e.message)}}
}
