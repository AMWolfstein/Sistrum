// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/wavpack/wavpack_test.go and container/wv/demux_test.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test
import me.misa198.airmedy.codecs.codec.wavpack.*
import me.misa198.airmedy.codecs.container.wv.Wv

class WavPackContractsTest {
    private fun sample() = OracleCorpus.rows.single { it.name=="waxflow-testdata/sine-s16.wv" }
    private fun raw() = sample().byteBuffer().order(ByteOrder.LITTLE_ENDIAN)
    private fun refused(code: ErrorCode, message: String, action: () -> Unit) {
        try { action(); fail("Expected $code: $message") }
        catch (e: WavPackException) { assertEquals(code,e.code); assertEquals("wavpack: $message",e.message) }
    }
    @Test fun unsupportedFlagsAreNamedAndOrdered() {
        val cases=listOf(
            (1 shl 31) to "DSD streams are not supported"
        )
        for ((flag,message) in cases) {
            val b=raw(); b.putInt(24,b.getInt(24) or flag)
            refused(ErrorCode.UNSUPPORTED,message) { Wv.open(b) }
        }
        val multichannel=raw(); multichannel.putInt(24,multichannel.getInt(24) and 0x1000.inv())
        refused(ErrorCode.MALFORMED,"incomplete channel group at offset 0") { Wv.open(multichannel).decodeBlock() }
        val both=raw(); both.putInt(24,both.getInt(24) or (1 shl 31) or 8 or 128)
        refused(ErrorCode.UNSUPPORTED,"DSD streams are not supported") { Wv.open(both) }
    }
    @Test fun hybridFlagWithoutProfileHasZeroErrorLimit() {
        val b=raw(); b.putInt(24,b.getInt(24) or 8)
        val stream=Wv.open(b)
        val digest=java.security.MessageDigest.getInstance("SHA-256")
        while (true) { val block=stream.decodeBlock()?:break;digest.update(pcmBytes(block)) }
        assertEquals(sample().value("pcm_sha256"),digest.digest().hex())
    }
    @Test fun unsupportedVersionsRetainCodecAndContainerBehavior() {
        for (version in intArrayOf(0x397,0x401,0x411,0x500)) {
            val b=raw(); b.putShort(8,version.toShort())
            refused(ErrorCode.UNSUPPORTED,UNSUPPORTED_VERSION_MESSAGE) {
                BlockHeader().parse(b)
            }
            assertFalse(syncOK(b))
            // Container and codec expose the same unsupported-version refusal.
            refused(ErrorCode.UNSUPPORTED,UNSUPPORTED_VERSION_MESSAGE) { Wv.open(b) }
        }
    }
    @Test fun channelMetadataAndConflictingMonoFlags() {
        val original=raw(); val header=BlockHeader().parse(original)
        val block=ByteBuffer.allocate(header.size.toInt()+4).order(ByteOrder.LITTLE_ENDIAN)
        val source=original.duplicate(); source.limit(header.size.toInt()); block.put(source)
        block.put(0xd.toByte()).put(1.toByte()).put(6.toByte()).put(0.toByte())
        block.putInt(4,block.capacity()-8)
        val config=probeBlock(block)
        assertEquals(6,config.channels);assertEquals(0L,config.channelMask)
        refused(ErrorCode.MALFORMED,"incomplete channel group at sample 0") { Decoder(config).decode(block) }
        val flags=raw(); flags.putInt(24,flags.getInt(24) or 4 or 0x40000000)
        refused(ErrorCode.MALFORMED,"block is flagged both mono and false-stereo") { BlockHeader().parse(flags) }
    }
    @Test fun unknownTotalAndDeclaredTotalDisagreements() {
        val fixture=sample(); val frames=fixture.value("frames").toLong()
        val b=raw(); b.putInt(12,-1)
        assertEquals(-1L,BlockHeader().parse(b).totalSamples)
        val unknown=Wv.open(b)
        assertEquals(frames,unknown.info.totalSamples); assertTrue(unknown.info.samplesExact)
        val tooLong=raw(); tooLong.putInt(12,(frames+1).toInt())
        val shortfall=Wv.open(tooLong)
        assertEquals(frames,shortfall.info.totalSamples)
        assertEquals("the header declares ${frames+1} samples but the blocks end at $frames",shortfall.demuxer.warnings.single().message)
        refused(ErrorCode.MALFORMED,"the header declares ${frames+1} samples but the blocks end at $frames (at offset 0)") { Wv.open(tooLong,true) }
        val tooShort=raw(); tooShort.putInt(12,(frames-1).toInt())
        val overrun=Wv.open(tooShort,true)
        assertEquals(frames,overrun.info.totalSamples); assertTrue(overrun.demuxer.warnings.single().note)
    }
    @Test fun invalidRequestsAndSyncBounds() {
        refused(ErrorCode.INVALID_REQUEST,"negative seek target") { Wv.open(raw()).seekSample(-1) }
        val odd=raw(); odd.put(4,(odd.get(4).toInt() or 1).toByte()); assertFalse(syncOK(odd))
        val huge=raw(); huge.put(6,0x10); assertFalse(syncOK(huge))
        val absurd=raw(); absurd.put(22,3); assertFalse(syncOK(absurd))
        val short=raw(); short.limit(31); assertFalse(syncOK(short))
        assertFalse(match(ByteBuffer.wrap(byteArrayOf(119,118,112))))
    }
    @Test fun checksumVerificationIsSeparateFromDecode() {
        // The pinned corpus bad_checksums.wv decodes successfully; integrity verification
        // is an explicit caller request in WaxFlow rather than part of the decode path.
        val f=OracleCorpus.rows.single { it.name.endsWith("/corruption/bad_checksums.wv") }
        assertEquals("ok",f.status)
        val stream=Wv.open(f.byteBuffer())
        var bad=false
        while (stream.demuxer.readPacket()) {
            if (verifyBlockChecksum(stream.demuxer.packetData)==false) bad=true
        }
        assertTrue("Corpus must retain its intentionally bad block checksums",bad)
    }
}
