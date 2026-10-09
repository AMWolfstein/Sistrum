// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/wma/refuse_test.go and state_test.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test
import me.misa198.airmedy.codecs.codec.wma.Config
import me.misa198.airmedy.codecs.codec.wma.Decoder
import me.misa198.airmedy.codecs.codec.wma.FloatSink
import me.misa198.airmedy.codecs.codec.wma.WmaException

class WmaContractsTest {
    private fun config(tag:Int=0x161,ch:Int=2,rate:Int=44100,bytes:Int=16000,align:Int=743,flags:Int=1):ByteBuffer {
        val extra=if(tag==0x160)4 else 10
        val b=ByteBuffer.allocate(18+extra).order(ByteOrder.LITTLE_ENDIAN)
        b.putShort(0,tag.toShort());b.putShort(2,ch.toShort());b.putInt(4,rate);b.putInt(8,bytes)
        b.putShort(12,align.toShort());b.putShort(14,16);b.putShort(16,extra.toShort())
        b.putShort(if(tag==0x160)20 else 22,flags.toShort());return b
    }
    @Test fun unsupportedShapesAreNamedBeforeDecoding() {
        val cases=listOf(
            config(rate=96000) to "sample rate 96000 above the 50000 Hz this decoder covers",
            config(ch=3) to "3 channels: Windows Media Audio 1 and 2 are mono or stereo",
            config(tag=0x162) to "audio format 0x0162 is not Windows Media Audio 1 or 2",
            config(tag=1) to "audio format 0x0001 is not Windows Media Audio 1 or 2",
            config(tag=0x160,ch=1,flags=5) to "Windows Media Audio 1 with variable block lengths: no encoder writes it and no layout is defined for it",
            config(tag=0x160,flags=3) to "stereo Windows Media Audio 1 with a bit reservoir: its per-channel byte alignment has no defined meaning once a frame can move"
        )
        for((input,message) in cases){val e=assertThrows(WmaException::class.java){Config.parse(input)};assertTrue(e.unsupported);assertEquals("wma: $message",e.message)}
    }
    @Test fun malformedFieldsRemainDistinctFromUnsupportedShapes() {
        for(input in listOf(config(rate=0),config(ch=0),config(align=0),config(bytes=0),config(rate=1 shl 28),config(bytes=1 shl 30),config(ch=1,rate=8000,bytes=1 shl 25),ByteBuffer.wrap(byteArrayOf(0x61,1)))) {
            assertFalse(assertThrows(WmaException::class.java){Config.parse(input)}.unsupported)
        }
    }
    @Test fun flagsQuirkIsAppliedOnlyToExactWord() {
        assertEquals(9,Config.parse(config(flags=13)).flags2)
        assertEquals(15,Config.parse(config(flags=15)).flags2)
        val short=config();short.putShort(16,0);short.limit(18)
        assertEquals(0,Config.parse(short).flags2)
    }
    @Test fun failureIsLatchedUntilReset() {
        val decoder=Decoder(Config.parse(config()))
        val sink=FloatSink { fail("Malformed packet emitted samples") }
        val e=assertThrows(WmaException::class.java){decoder.decode(byteArrayOf(),0,sink)}
        assertSame(e,assertThrows(WmaException::class.java){decoder.decode(byteArrayOf(0),1,sink)})
        assertSame(e,assertThrows(WmaException::class.java){decoder.drain(sink)})
        decoder.reset(fromStart=true)
        assertNotSame(e,assertThrows(WmaException::class.java){decoder.decode(byteArrayOf(),0,sink)})
    }
}
