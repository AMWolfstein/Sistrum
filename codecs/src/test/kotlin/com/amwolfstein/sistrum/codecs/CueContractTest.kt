// SPDX-License-Identifier: GPL-3.0-or-later
package com.amwolfstein.sistrum.codecs

import org.junit.Assert.*
import org.junit.Test
import com.amwolfstein.sistrum.codecs.cue.*

class CueContractTest {
    private val text = """
        TITLE "موسيقى Café"
        SONGWRITER "Album writer"
        REM GENRE Jazz
        REM DATE 2026
        REM REPLAYGAIN_ALBUM_GAIN "-5 dB"
        CATALOG 1234567890123
        FILE a.wav WAVE
        TRACK 1 AUDIO
        SONGWRITER "Track writer"
        ISRC XXABC2600001
        PREGAP 00:02:00
        INDEX 01 00:00:00
        POSTGAP 00:01:00
        REM REPLAYGAIN_TRACK_GAIN "-3 dB"
        TRACK 2 AUDIO
        INDEX 00 00:09:00
        INDEX 01 00:10:01
        FILE b.wav WAVE
        TRACK 3 AUDIO
        INDEX 01 00:00:00
    """.trimIndent()

    @Test fun utf8BomAndBothUtf16OrdersPreserveMetadataAndSamplePositions() {
        val vectors = listOf(
            Encoding.UTF8 to text.toByteArray(),
            Encoding.UTF8_BOM to (byteArrayOf(0xef.toByte(),0xbb.toByte(),0xbf.toByte())+text.toByteArray()),
            Encoding.UTF16_LE to (byteArrayOf(0xff.toByte(),0xfe.toByte())+text.toByteArray(Charsets.UTF_16LE)),
            Encoding.UTF16_BE to (byteArrayOf(0xfe.toByte(),0xff.toByte())+text.toByteArray(Charsets.UTF_16BE))
        )
        for((encoding, bytes) in vectors) {
            val s = Cue.parse(bytes)
            assertEquals(encoding,s.encoding); assertNull(s.encodingWarning); assertTrue(s.warnings.isEmpty())
            assertEquals("موسيقى Café",s.title); assertEquals("Album writer",s.songwriter)
            assertEquals("Track writer",s.files[0].tracks[0].songwriter)
            assertEquals("1234567890123",s.catalog); assertEquals("XXABC2600001",s.files[0].tracks[0].isrc)
            assertEquals("Jazz",s.rem("genre")); assertEquals("2026",s.rem("DATE")); assertEquals("-5 dB",s.rem("replaygain_album_gain"))
            assertEquals("-3 dB",s.files[0].tracks[0].rem("REPLAYGAIN_TRACK_GAIN"))
            val tracks = s.trackList(44100,listOf(44100L*30,44100L*20))
            assertEquals(3,tracks.size)
            assertEquals(0,tracks[0].file); assertEquals(0L,tracks[0].start); assertEquals(441588L,tracks[0].end)
            assertEquals(441588L,tracks[1].start); assertEquals(1323000L,tracks[1].end)
            assertEquals(1,tracks[2].file); assertEquals(0L,tracks[2].start); assertEquals(882000L,tracks[2].end)
            assertEquals(150,s.files[0].tracks[0].pregap); assertEquals(75,s.files[0].tracks[0].postgap)
        }
    }
    @Test fun legacyFallbackIsReportedAndPreservesCp1252PunctuationAndHoles() {
        val bytes = "TITLE \"Café – music\"".toByteArray(charset("windows-1252"))
        val s = Cue.parse(bytes)
        assertEquals("Café – music",s.title)
        assertEquals(Encoding.WINDOWS_1252,s.encoding)
        assertNotNull(s.encodingWarning)
        assertTrue(s.warnings.isEmpty())
    }
    @Test fun malformedUtf16FailsExplicitly() {
        for(bytes in listOf(byteArrayOf(0xff.toByte(),0xfe.toByte(),0x41),byteArrayOf(0xfe.toByte(),0xff.toByte(),0xd8.toByte(),0x00))) {
            try { Cue.parse(bytes); fail("malformed UTF-16 must fail") } catch(e: CueException) { assertTrue(e.message!!.contains("malformed UTF16")) }
        }
    }
    @Test fun unknownEndsAndDataBoundariesAreFileRelative() {
        val s = Cue.parse("FILE a.bin BINARY\nTRACK 1 MODE1/2352\nINDEX 01 00:02:00\nTRACK 2 AUDIO\nINDEX 01 00:02:00\nFILE b.wav WAVE\nTRACK 3 AUDIO\nINDEX 01 00:00:00".toByteArray())
        val tracks = s.trackList(48000)
        assertFalse(tracks[0].track.isAudio); assertEquals(0L,tracks[0].start); assertEquals(96000L,tracks[0].end)
        assertEquals(96000L,tracks[1].start); assertEquals(-1L,tracks[1].end)
        assertEquals(0L,tracks[2].start); assertEquals(-1L,tracks[2].end)
    }
    @Test fun missingIndex01NeverInventsATrackStart() {
        val s = Cue.parse("TRACK 1 AUDIO\nINDEX 00 00:00:00".toByteArray())
        assertNull(s.files[0].tracks[0].start)
        try { s.trackList(44100); fail("missing INDEX 01 must refuse sample positions") }
        catch(e: CueException) { assertTrue(e.message!!.contains("has no INDEX 01")) }
    }
    @Test fun integerFrameConversionNeverRoutesThroughDuration() {
        for(frame in listOf(1,74,75,4501,27004499)) for(rate in listOf(44100,48000,96000,32000))
            assertEquals(frame.toLong()*rate/75,Cue.samples(frame,rate))
        assertEquals(27004499,Cue.parseTime("6000:59:74"))
    }
}
