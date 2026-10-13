package com.amwolfstein.sistrum.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import com.amwolfstein.sistrum.ui.components.TrackAudioQuality
import com.amwolfstein.sistrum.ui.components.trackAudioQuality

/**
 * The scanner used MediaStore's MIME subtype as the format, so tracks were stored as
 * "mpeg", "x-wav", "alac" and, for AIFF, MediaStore's catch-all "ffmpeg". The last three
 * got no quality badge, and Track info showed those raw names.
 */
class AudioFormatTest {

    private val music = "/storage/emulated/0/Music"

    @Test fun `MIME subtypes map to canonical format names`() {
        assertEquals("mp3", audioFormatOf("audio/mpeg", "$music/a.mp3"))
        assertEquals("wav", audioFormatOf("audio/x-wav", "$music/a.wav"))
        assertEquals("wav", audioFormatOf("audio/vnd.wave", "$music/a.wav"))
        assertEquals("aiff", audioFormatOf("audio/x-aiff", "$music/a.aif"))
        assertEquals("flac", audioFormatOf("audio/flac", "$music/a.flac"))
        assertEquals("m4a", audioFormatOf("audio/alac", "$music/a.m4a"))
        assertEquals("m4a", audioFormatOf("audio/mp4", "$music/a.m4a"))
        assertEquals("mp4", audioFormatOf("audio/mp4", "$music/a.mp4"))
        assertEquals("ogg", audioFormatOf("audio/ogg", "$music/a.opus"), "existing Opus-in-Ogg tracks keep \"ogg\"")
    }

    @Test fun `MIME matching ignores case and parameters`() {
        assertEquals("mp3", audioFormatOf("Audio/MPEG; charset=binary", "$music/a.mp3"))
    }

    @Test fun `every uninformative MIME type falls back to the file extension`() {
        val generic = listOf("audio/ffmpeg", "audio/unknown", "audio/*", "application/octet-stream", "", "audio/x-not-a-real-format")
        val byExtension = mapOf(
            "aiff" to "aiff", "aif" to "aiff", "AIFC" to "aiff", "wav" to "wav", "wave" to "wav",
            "flac" to "flac", "mp3" to "mp3", "m4a" to "m4a", "m4b" to "m4a", "mp4" to "mp4",
            "ogg" to "ogg", "oga" to "ogg", "opus" to "opus", "ape" to "ape", "wv" to "wv", "dsf" to "dsf",
        )
        for (mime in generic) for ((extension, expected) in byExtension) {
            assertEquals(expected, audioFormatOf(mime, "$music/Some.Dotted.Name.$extension"), "$mime + .$extension")
        }
    }

    @Test fun `an unrecognized extension is kept as is`() {
        assertEquals("xyz", audioFormatOf("audio/ffmpeg", "$music/a.xyz"))
    }

    @Test fun `without an extension a specific subtype is still used, a generic one is not`() {
        assertEquals("amr", audioFormatOf("audio/x-amr", "$music/voice"))
        assertEquals("", audioFormatOf("audio/ffmpeg", "$music/voice"))
        assertEquals("", audioFormatOf("audio/ffmpeg", "$music.d/voice"), "a dot in a folder name is not an extension")
    }

    @Test fun `quality badges recognise every canonical format`() {
        // Codec as the scanner stores it: the format itself, or the sniffed codec for MP4.
        val expected = listOf(
            Triple("audio/mpeg", "a.mp3", null) to TrackAudioQuality.Lossy,
            Triple("audio/aac", "a.aac", null) to TrackAudioQuality.Lossy,
            Triple("audio/ogg", "a.opus", null) to TrackAudioQuality.Lossy,
            Triple("audio/opus", "a.opus", null) to TrackAudioQuality.Lossy,
            Triple("audio/mp4", "a.m4a", "aac") to TrackAudioQuality.Lossy,
            Triple("audio/alac", "a.m4a", "alac") to TrackAudioQuality.Lossless,
            Triple("audio/mp4", "a.mp4", "flac") to TrackAudioQuality.Lossless,
            Triple("audio/flac", "a.flac", null) to TrackAudioQuality.Lossless,
            Triple("audio/x-wav", "a.wav", null) to TrackAudioQuality.Lossless,
            Triple("audio/ffmpeg", "a.aiff", null) to TrackAudioQuality.Lossless,
            Triple("audio/x-ape", "a.ape", null) to TrackAudioQuality.Lossless,
            Triple("audio/x-wavpack", "a.wv", null) to TrackAudioQuality.Lossless,
            Triple("audio/x-dsf", "a.dsf", null) to TrackAudioQuality.Dsd,
        )
        for ((input, quality) in expected) {
            val (mime, file, sniffedCodec) = input
            val format = audioFormatOf(mime, "$music/$file")
            val codec = sniffedCodec ?: format
            val track = LibraryTrack(id = "t", title = "T", artists = "A", metadataJson = """{"format":"$format","codec":"$codec","bit_depth":16,"sample_rate":44100}""")
            assertEquals(quality, trackAudioQuality(track), "$mime $file -> $format/$codec")
        }
    }
}
