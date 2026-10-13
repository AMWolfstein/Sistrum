package com.amwolfstein.sistrum.player.media3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Expected assertion failures until first-frame provenance parsing is implemented in T062j. */
class LameTagParserTest {
    @Test
    fun mpegOneStereoInfoAt44100WithAllXingFields() {
        assertParsed(firstFrame(header = intArrayOf(0xFF, 0xFB, 0x90, 0x00), tagOffset = 36, frameSize = 417))
    }

    @Test
    fun mpegOneStereoInfoAt48000WithAllXingFields() {
        assertParsed(firstFrame(header = intArrayOf(0xFF, 0xFB, 0x94, 0x00), tagOffset = 36, frameSize = 384))
    }

    @Test
    fun mpegOneStereoXingWithoutToc() {
        assertParsed(
            firstFrame(
                header = intArrayOf(0xFF, 0xFB, 0x90, 0x00), tagOffset = 36, frameSize = 417,
                tag = "Xing", flags = 0x0B,
            ),
        )
    }

    @Test
    fun mpegOneMonoInfoAtOffset21() {
        assertParsed(firstFrame(header = intArrayOf(0xFF, 0xFB, 0x90, 0xC0), tagOffset = 21, frameSize = 417))
    }

    @Test
    fun mpegTwoStereoInfoAtOffset21() {
        assertParsed(firstFrame(header = intArrayOf(0xFF, 0xF3, 0x80, 0x00), tagOffset = 21, frameSize = 208))
    }

    private fun assertParsed(frame: ByteArray) {
        assertEquals("raw LAME provenance", LameGaplessInfo("LAME3.100", 576, 1344), parseLameGaplessInfo(frame))
    }
}

class LameTagParserGuardTest {
    @Test
    fun frameWithoutXingOrInfoReturnsNull() {
        val frame = ByteArray(417)
        frame[0] = 0xFF.toByte()
        frame[1] = 0xFB.toByte()
        frame[2] = 0x90.toByte()
        frame[3] = 0x00
        assertNull(parseLameGaplessInfo(frame))
    }

    @Test
    fun truncatedLameSectionReturnsNull() {
        val frame = firstFrame(header = intArrayOf(0xFF, 0xFB, 0x90, 0x00), tagOffset = 36, frameSize = 417)
        // 36-byte prefix + tag/flags (8) + all optional fields (112) + only 23 of 24 LAME bytes.
        assertNull(parseLameGaplessInfo(frame.copyOf(179)))
    }

    @Test
    fun emptyArrayReturnsNull() {
        assertNull(parseLameGaplessInfo(byteArrayOf()))
    }

    @Test
    fun shortHeaderReturnsNull() {
        assertNull(parseLameGaplessInfo(byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte())))
    }
}

/**
 * Synthetic MPEG Layer III frames, built byte by byte independently of the parser under test.
 * Header bytes encode sync, MPEG version, Layer III, no CRC, bitrate, sample rate, and channel mode:
 * FF FB 90 00 = MPEG-1, 128 kbps, 44.1 kHz, stereo (417 bytes);
 * FF FB 94 00 = MPEG-1, 128 kbps, 48 kHz, stereo (384 bytes);
 * FF FB 90 C0 = MPEG-1, 128 kbps, 44.1 kHz, mono (417 bytes);
 * FF F3 80 00 = MPEG-2, 64 kbps, 22.05 kHz, stereo (208 bytes).
 *
 * Header + side information ends at explicit offset 36 or 21 (Mp3Extractor.java:595-598).
 * There: ASCII Xing/Info (4), big-endian flags (4), frames (4, bit 1), bytes (4, bit 2),
 * TOC (100, bit 4), quality (4, bit 8), then the LAME section (XingFrame.java:95-134).
 * LAME section offsets: encoder id [0..8], revision [9], lowpass [10], ReplayGain [11..18],
 * encoding flags [19], bitrate [20], packed delay/padding [21..23]. The upper 12 bits are delay,
 * lower 12 padding: 576/1344 packs to 0x240540. Remaining frame bytes are zero audio payload.
 */
private fun firstFrame(
    header: IntArray,
    tagOffset: Int,
    frameSize: Int,
    tag: String = "Info",
    flags: Int = 0x0F,
): ByteArray {
    val frame = ByteArray(frameSize)
    header.forEachIndexed { index, value -> frame[index] = value.toByte() }
    var position = tagOffset
    fun writeByte(value: Int) {
        frame[position++] = value.toByte()
    }
    fun writeInt(value: Int) {
        writeByte(value ushr 24)
        writeByte(value ushr 16)
        writeByte(value ushr 8)
        writeByte(value)
    }
    tag.forEach { writeByte(it.code) }
    writeInt(flags)
    if (flags and 1 != 0) writeInt(1459)
    if (flags and 2 != 0) writeInt(500000)
    if (flags and 4 != 0) repeat(100) { writeByte(it * 2) }
    if (flags and 8 != 0) writeInt(83)
    "LAME3.100".forEach { writeByte(it.code) }
    writeByte(1) // Revision/VBR method.
    writeByte(19) // Lowpass.
    repeat(8) { writeByte(0) } // No ReplayGain or peak: provenance must not depend on gain metadata.
    writeByte(0) // Encoding flags/ATH.
    writeByte(128) // Bitrate.
    writeByte(0x24)
    writeByte(0x05)
    writeByte(0x40)
    return frame
}
