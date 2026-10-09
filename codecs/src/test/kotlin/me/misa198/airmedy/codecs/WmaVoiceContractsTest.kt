// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/wmavoice/refuse_test.go and bits_test.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test
import me.misa198.airmedy.codecs.codec.wmavoice.*
import me.misa198.airmedy.codecs.container.asf.WmaVoice

class WmaVoiceContractsTest {
    private fun config()=Config(16000,450,0x0b1991a7,IntArray(24){if(it<17)it else -1})
    private fun packet(speech:Int=0,samples:Int?=null):ByteArray {
        val bytes=ByteArray(450);var pos=0
        fun put(v:Int,n:Int){for(i in n-1 downTo 0){if(v ushr i and 1!=0)bytes[pos ushr 3]=(bytes[pos ushr 3].toInt() or (1 shl (7-(pos and 7)))).toByte();pos++}}
        put(0,4);put(1,1);put(2,6);put(0,config().geom().spilloverBits);put(speech,1)
        if(samples!=null){put(1,1);put(samples,12)}
        return bytes
    }
    @Test fun invalidConfigFailsBeforeAllocation(){
        val e=assertThrows(VoiceException::class.java){Decoder(Config(0,450,0,IntArray(24)))}
        assertFalse(e.unsupported);assertEquals("wmavoice: sample rate 0",e.message)
        assertTrue(assertThrows(VoiceException::class.java){Decoder(Config(16000,1 shl 23,0,IntArray(24)))}.unsupported)
    }
    @Test fun musicPayloadRefusalDoesNotLatch(){
        val d=Decoder(config());d.acceptPacket(packet())
        val e=assertThrows(VoiceException::class.java){d.nextFrame()};assertTrue(e.unsupported)
        assertEquals("wmavoice: the superframe carries a WMA Pro payload rather than speech, unless a count overstated the packet and this is its padding",e.message)
        d.acceptPacket(packet(1,481));val next=assertThrows(VoiceException::class.java){d.nextFrame()};assertFalse(next.unsupported);assertEquals("wmavoice: the superframe declares 481 samples, want at most 480",next.message)
    }
    @Test fun truncatedAndOversizedPacketsAreNamed(){
        val d=Decoder(config())
        assertEquals("wmavoice: the packet header runs past the packet",assertThrows(VoiceException::class.java){d.acceptPacket(byteArrayOf(0))}.message)
        assertEquals("wmavoice: packet of 451 bytes, longer than nBlockAlign 450",assertThrows(VoiceException::class.java){d.acceptPacket(ByteArray(451))}.message)
        d.finish();assertNull(d.nextFrame())
    }
    @Test fun secondRegisteredTagIsRefusedByName(){
        val b=ByteBuffer.allocate(64).order(java.nio.ByteOrder.LITTLE_ENDIAN);b.putShort(0,11)
        val e=assertThrows(VoiceException::class.java){Config.parse(b)};assertTrue(e.unsupported)
        assertEquals("wmavoice: wFormatTag 0x000B is Windows Media Audio Voice 10, which no reference decoder reads",e.message)
    }
    @Test fun readerCannotConsumeBytePadding(){
        val r=BitReader();r.reset(ByteArray(8){-1},64);r.skip(7);assertEquals((1L shl 57)-1,r.wide(57));assertEquals(0L,r.wide(0));r.check();r.bit();assertEquals("wmavoice: the walk reads past the end of its 64-bit payload",assertThrows(VoiceException::class.java){r.check()}.message)
    }
    @Test fun carryAppendsAcrossBitAndByteBoundaries(){
        val src=byteArrayOf(0x69,0xb2.toByte(),0xc7.toByte(),0x15)
        for(start in 0..7)for(prefix in 0..7)for(n in 1..17){
            val a=BitAppender(8);a.appendFrom(src,0,prefix);a.appendFrom(src,start,n)
            val r=BitReader();r.reset(a.buf,a.bits)
            for(i in 0 until prefix)assertEquals((src[i/8].toInt() ushr (7-i%8)) and 1,r.bit())
            for(i in start until start+n)assertEquals((src[i/8].toInt() ushr (7-i%8)) and 1,r.bit());r.check()
            a.reset();a.appendFrom(src,start,n);r.reset(a.buf,a.bits)
            for(i in start until start+n)assertEquals((src[i/8].toInt() ushr (7-i%8)) and 1,r.bit());r.check()
        }
    }
    @Test fun sourceRemainsBorrowedAndRejectsNegativeSeek(){
        val f=OracleCorpus.allRows.first{it.name.endsWith("wmavoice/testdata/corpus/voice-16000-12k.wma")}
        val input=f.byteBuffer();val position=input.position();val s=WmaVoice.open(input)
        assertEquals(position,input.position());assertThrows(java.io.IOException::class.java){s.seekSample(-1)}
        assertNotNull(s.decodeBlock());assertEquals(position,input.position())
    }
}
