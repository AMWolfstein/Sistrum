// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/wmalossless/refuse_test.go and layout_test.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test
import me.misa198.airmedy.codecs.codec.wmalossless.*

class WmaLosslessContractsTest {
    private fun config(ch:Int=2,bits:Int=16,flags:Int=0)=Config(44100,ch,bits,4096,flags)
    @Test fun validatesBeforeAllocating(){assertThrows(LosslessException::class.java){Decoder(config(ch= -1))}}
    @Test fun namedConfigRefusals(){
        for((cfg,text) in listOf(config(ch=9) to "9 channels (this build decodes up to 8)",config(bits=32) to "32 bits per sample (this build decodes 16 and 24)",config(flags=48) to "subframe depth 6, so 64 subframes a frame")){
            val e=assertThrows(LosslessException::class.java){cfg.validate()};assertTrue(e.unsupported);assertEquals("wmalossless: $text",e.message)
        }
    }
    @Test fun byteReaderPreservesUnsignedAndSigned32BitFields(){
        val r=BitReader();r.reset(byteArrayOf(-1,-1,-1,-1,0),40)
        assertEquals(-1,r.bits(32));assertEquals(0,r.signed(8));r.check()
        r.reset(byteArrayOf(0x40,0,0,0,0),33);assertEquals(0,r.bit());assertEquals(Int.MIN_VALUE,r.signed(32));r.check()
        r.bit();assertThrows(LosslessException::class.java){r.check()}
    }
    @Test fun carryPreservesUnalignedBitsAcrossAppends(){
        val a=BitAppender();a.appendFrom(byteArrayOf(0x70),1,3);a.appendFrom(byteArrayOf(0x28),2,4)
        val r=BitReader();r.reset(a.buf,a.bits);assertEquals(0b1111010,r.bits(7));r.check()
    }
    @Test fun nonPositionalMaskDoesNotChangeChannelCount(){
        val b=ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN)
        b.putShort(0,0x163);b.putShort(2,2);b.putInt(4,44100);b.putShort(12,4096);b.putShort(16,18);b.putShort(18,16);b.putInt(20,Int.MIN_VALUE)
        val c=Config.parse(b);assertEquals(2,c.channels);assertEquals(3,c.layout)
    }
    @Test fun errorsLatchUntilReset(){
        val d=Decoder(config());val e=assertThrows(LosslessException::class.java){d.acceptPacket(byteArrayOf(),0)}
        assertSame(e,assertThrows(LosslessException::class.java){d.finish()});d.reset()
        assertNotSame(e,assertThrows(LosslessException::class.java){d.acceptPacket(byteArrayOf(),0)})
    }
}
