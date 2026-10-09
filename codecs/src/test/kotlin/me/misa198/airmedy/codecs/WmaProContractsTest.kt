// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/wmapro/refuse_test.go and huff_test.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs

import org.junit.Assert.*
import org.junit.Test
import me.misa198.airmedy.codecs.codec.wmapro.*

class WmaProContractsTest {
    @Test fun refusalShapesRemainNamed(){
        val cases=listOf(
            Config(44100,2,16,4096,0) to "frames with no length prefix",
            Config(384001,2,16,4096,64) to "sample rate 384001 (this build decodes up to 384000)",
            Config(44100,9,16,4096,64) to "9 channels (this build decodes up to 8)",
            Config(44100,2,32,4096,64) to "32 bits per sample (this build decodes 16 and 24)",
            Config(44100,2,16,4096,112) to "subframe depth 6, so 64 subframes a frame"
        )
        for((c,text) in cases){val e=assertThrows(ProException::class.java){c.validate()};assertTrue(e.unsupported);assertEquals("wmapro: $text",e.message)}
    }
    @Test fun invalidChannelCountFailsBeforeAllocation(){assertFalse(assertThrows(ProException::class.java){Decoder(Config(44100,-1,16,4096,64))}.unsupported)}
    @Test fun readerHonors57BitWindowAndZeroWidth(){
        val r=BitReader();r.reset(ByteArray(8){-1},64);r.skip(7);assertEquals((1L shl 57)-1,r.wide(57));assertEquals(0L,r.wide(0));r.check();r.bit();assertThrows(ProException::class.java){r.check()}
    }
    @Test fun huffmanBookCannotConsumePaddingAsPayload(){
        val book=Vlc(intArrayOf(1,2,2),intArrayOf(3,4,5));val r=BitReader()
        r.reset(byteArrayOf(0x40),3);assertEquals(3,book.decode(r));assertEquals(4,book.decode(r));r.check()
        r.reset(byteArrayOf(-128),1);assertEquals(-1,book.decode(r));assertThrows(ProException::class.java){r.check()}
    }
}
