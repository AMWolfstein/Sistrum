package me.misa198.airmedy.sync

import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmbeddedTagReaderTest {

    private val lrc = "[00:01.00]Hello world\n[00:02.50]Second line"
    private val image = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x01, 0x02, 0x03, 0x04, 0x05,
    )

    @Test fun `flac vorbis comment lyrics`() {
        val file = flac(picture = null, comments = listOf("LYRICS=$lrc"))
        assertEquals(lrc, EmbeddedTagReader.embeddedLyricsText(file.path))
    }

    @Test fun `flac prefers lyrics over unsynced`() {
        val file = flac(picture = null, comments = listOf("UNSYNCEDLYRICS=plain", "LYRICS=$lrc"))
        assertEquals(lrc, EmbeddedTagReader.embeddedLyricsText(file.path))
    }

    @Test fun `flac vorbis lyrics decode as utf8`() {
        val korean = "[00:01.00]안녕하세요\n[00:02.00]세계"
        val file = flac(picture = null, comments = listOf("LYRICS=$korean"))
        assertEquals(korean, EmbeddedTagReader.embeddedLyricsText(file.path))
    }

    @Test fun `flac picture block artwork`() {
        val file = flac(picture = image, comments = emptyList())
        val bytes = EmbeddedTagReader.embeddedArtworkBytes(file.path)
        assertNotNull(bytes)
        assertEquals(image.toList(), bytes.toList())
    }

    @Test fun `flac prefers a later front cover over an earlier back cover`() {
        val back = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val file = flac(picture = back, pictureType = 4, secondPicture = image, comments = emptyList())
        val bytes = EmbeddedTagReader.embeddedArtworkBytes(file.path)
        assertNotNull(bytes)
        assertEquals(image.toList(), bytes.toList())
    }

    @Test fun `flac falls back to a non-front picture when no front cover exists`() {
        val file = flac(picture = image, pictureType = 4, comments = emptyList())
        val bytes = EmbeddedTagReader.embeddedArtworkBytes(file.path)
        assertNotNull(bytes)
        assertEquals(image.toList(), bytes.toList())
    }

    @Test fun `flac without picture returns null artwork`() {
        val file = flac(picture = null, comments = listOf("LYRICS=$lrc"))
        assertNull(EmbeddedTagReader.embeddedArtworkBytes(file.path))
    }

    @Test fun `id3 v2_3 uslt lyrics`() {
        val file = temp("id3-uslt", id3v23(frames = listOf(uslt("eng", lrc))))
        assertContains(EmbeddedTagReader.embeddedLyricsText(file.path)!!, "Second line")
    }

    @Test fun `id3 v2_3 apic artwork`() {
        val file = temp("id3-apic", id3v23(frames = listOf(apic(image))))
        val bytes = EmbeddedTagReader.embeddedArtworkBytes(file.path)
        assertNotNull(bytes)
        assertEquals(image.toList(), bytes.toList())
    }

    @Test fun `id3 v2_2 ult and pic`() {
        val file = temp("id3-v22", id3v22(frames = listOf(ult("eng", lrc), pic(image))))
        assertContains(EmbeddedTagReader.embeddedLyricsText(file.path)!!, "Hello world")
        assertEquals(image.toList(), EmbeddedTagReader.embeddedArtworkBytes(file.path)!!.toList())
    }

    @Test fun `id3 without tags returns null`() {
        val file = temp("no-tags", byteArrayOf(0x52, 0x49, 0x46, 0x46, 0x00))
        assertNull(EmbeddedTagReader.embeddedLyricsText(file.path))
        assertNull(EmbeddedTagReader.embeddedArtworkBytes(file.path))
    }

    @Test fun `ogg vorbis lyrics`() {
        val file = temp("ogg", oggVorbis(vorbisComment(comments = listOf("LYRICS=$lrc"))))
        assertEquals(lrc, EmbeddedTagReader.embeddedLyricsText(file.path))
    }

    @Test fun `ogg metadata block picture artwork`() {
        val b64 = java.util.Base64.getEncoder().encodeToString(pictureBlock(image))
        val file = temp("ogg-picture", oggVorbis(vorbisComment(comments = listOf("METADATA_BLOCK_PICTURE=$b64"))))
        assertEquals(image.toList(), EmbeddedTagReader.embeddedArtworkBytes(file.path)!!.toList())
    }

    /**
     * Mirrors the page layout of a real affected SpotiFLAC/libopusenc file: same vendor,
     * tag keys and value lengths, a 6531-byte LYRICS value straddling a 4080-byte page
     * boundary, two covers (75 KB, then 418 KB) and trailing padding, so the ~680 KB
     * comment packet spans ~170 pages and ends past the old 512 KB read prefix.
     */
    @Test fun `opus comment header spanning many pages keeps lyrics artwork and tags`() {
        val lyrics = lrcOfLength(6531 - "LYRICS=".length)
        val front = imageOfSize(75_247, seed = 1)
        val back = imageOfSize(418_458, seed = 2)
        val comments = listOf(
            filler("COMPATIBLE_BRANDS", 38), filler("COPYRIGHT", 27), "DATE=2024-06-12", filler("DISCNUMBER", 12),
            filler("ENCODER", 35), filler("ENCODER", 20), filler("ENCODER_OPTIONS", 29), "ISRC=EGA012400123",
            "LYRICS=$lyrics", filler("MAJOR_BRAND", 16), filler("MINOR_VERSION", 15), filler("ORGANIZATION", 25),
            filler("R128_ALBUM_GAIN", 20), filler("R128_TRACK_GAIN", 20), filler("TRACKNUMBER", 16),
            "UNSYNCEDLYRICS=" + "u".repeat(6539 - "UNSYNCEDLYRICS=".length), "YEAR=2024",
            "METADATA_BLOCK_PICTURE=" + base64Picture(front, pictureType = 3),
            "METADATA_BLOCK_PICTURE=" + base64Picture(back, pictureType = 4),
            filler("TITLE", 16), filler("ARTIST", 24), filler("ALBUMARTIST", 29), filler("ALBUM", 25), filler("GENRE", 14),
        )
        val bytes = oggOpus(vorbisComment(comments, vendor = "libopus 1.6, libopusenc 0.3"), padding = 10_412)
        // Sanity-check the fixture reproduces the failure shape: the lyrics are split by a
        // page header, and the comment packet extends past the old 512 KB read prefix.
        assertEquals(-1, bytes.indexOf(lyrics.toByteArray(Charsets.UTF_8)))
        assertTrue(bytes.size > 600 * 1024)
        val file = temp("opus-multipage", bytes)

        assertEquals(lyrics, EmbeddedTagReader.embeddedLyricsText(file.path))
        assertEquals(front.toList(), EmbeddedTagReader.embeddedArtworkBytes(file.path)!!.toList())
        val tags = assertNotNull(EmbeddedTagReader.embeddedTrackTags(file.path))
        assertEquals("EGA012400123", tags.isrc)
        assertEquals("2024-06-12", tags.releaseDate)
    }

    @Test fun `opus artwork beyond the first 512 KB is read`() {
        val cover = imageOfSize(600_000, seed = 3)
        val comment = vorbisComment(listOf("METADATA_BLOCK_PICTURE=" + base64Picture(cover)), vendor = "libopus 1.6, libopusenc 0.3")
        val file = temp("opus-large-cover", oggOpus(comment))
        assertEquals(cover.toList(), EmbeddedTagReader.embeddedArtworkBytes(file.path)!!.toList())
    }

    /** libvorbis/libogg layout: comment and setup packets share pages of up to 255 segments. */
    @Test fun `vorbis comment header spanning pages keeps lyrics and artwork`() {
        val lyrics = lrcOfLength(70_000)
        val cover = imageOfSize(150_000, seed = 4)
        val comment = vorbisComment(
            listOf("TITLE=Vorbis", "LYRICS=$lyrics", "METADATA_BLOCK_PICTURE=" + base64Picture(cover)),
            vendor = "Xiph.Org libVorbis I 20200704 (Reducing Environment)",
        )
        val bytes = oggVorbis(comment)
        assertEquals(-1, bytes.indexOf(lyrics.toByteArray(Charsets.UTF_8)))
        val file = temp("vorbis-multipage", bytes)
        assertEquals(lyrics, EmbeddedTagReader.embeddedLyricsText(file.path))
        assertEquals(cover.toList(), EmbeddedTagReader.embeddedArtworkBytes(file.path)!!.toList())
    }

    /** Laid out like the device's SpotiFLAC Opus files: text tags, two large covers
     *  (98.8% of a real packet), then more text tags after the pictures. */
    private val twoCoverOpus by lazy {
        val front = imageOfSize(75_247, seed = 11)
        val back = imageOfSize(418_458, seed = 12)
        val comments = listOf(
            "DATE=2024-06-12", "ISRC=EGA012400123", "LYRICS=$lrc",
            "METADATA_BLOCK_PICTURE=${base64Picture(front)}", "METADATA_BLOCK_PICTURE=${base64Picture(back, pictureType = 4)}",
            "ITUNESADVISORY=1", "LABEL=\u0645\u0632\u064A\u0643\u0627", "COPYRIGHT=\u2117 2024",
        )
        front to temp("opus-two-covers", oggOpus(vorbisComment(comments)))
    }

    @Test fun `opus tags and lyrics reads do not decode the pictures, artwork reads decode one`() {
        val (_, file) = twoCoverOpus
        val before = EmbeddedTagReader.pictureDecodeCount.get()
        EmbeddedTagReader.embeddedTrackTags(file.path)
        EmbeddedTagReader.embeddedLyricsText(file.path)
        assertEquals(before, EmbeddedTagReader.pictureDecodeCount.get())
        EmbeddedTagReader.embeddedArtworkBytes(file.path)
        assertEquals(before + 1, EmbeddedTagReader.pictureDecodeCount.get())
    }

    @Test fun `opus tag lyrics and artwork values are unchanged when pictures are skipped`() {
        val (front, file) = twoCoverOpus
        val tags = assertNotNull(EmbeddedTagReader.embeddedTrackTags(file.path))
        assertEquals("2024-06-12", tags.releaseDate)
        assertEquals(2024, tags.year)
        assertEquals("EGA012400123", tags.isrc)
        assertEquals(true, tags.explicit) // written after both pictures
        assertEquals("\u0645\u0632\u064A\u0643\u0627", tags.label)
        assertEquals("\u2117 2024", tags.copyright)
        assertEquals(lrc, EmbeddedTagReader.embeddedLyricsText(file.path))
        assertEquals(front.toList(), EmbeddedTagReader.embeddedArtworkBytes(file.path)!!.toList())
    }

    @Test fun `vorbis entries without a key or equals sign are skipped`() {
        val file = flac(picture = null, comments = listOf("NOEQUALS", "=novalue", "LYRICS=$lrc"))
        assertEquals(lrc, EmbeddedTagReader.embeddedLyricsText(file.path))
    }

    // --- Tags-only Ogg reads stream the comment packet and skip picture bytes ---

    private fun le32(value: Int): ByteArray = bytesOf { writeIntLE(value) }

    /** A comment block from raw entries, with optional overrides for malformed fixtures. */
    private fun rawComment(entries: List<ByteArray>, count: Int = entries.size, vendorLength: Int? = null, lengths: Map<Int, Int> = emptyMap()): ByteArray =
        bytesOf {
            val vendor = "test vendor".toByteArray()
            write(le32(vendorLength ?: vendor.size)); write(vendor); write(le32(count))
            entries.forEachIndexed { index, entry -> write(le32(lengths[index] ?: entry.size)); write(entry) }
        }

    private fun opusFile(name: String, comment: ByteArray, maxSegmentsPerPage: Int = 16, padding: Int = 0): File {
        val tags = "OpusTags".toByteArray(Charsets.US_ASCII) + comment + ByteArray(padding)
        val audio = List(8) { ByteArray(160) { i -> (i * 31 + it).toByte() } }
        return temp(name, oggPages(listOf(opusHead(), tags) + audio, maxSegmentsPerPage = maxSegmentsPerPage, pageStarts = setOf(1, 2))
            .fold(ByteArray(0)) { acc, page -> acc + page })
    }

    private fun coverComments(front: ByteArray, back: ByteArray) = listOf(
        "DATE=2024-06-12", "ISRC=EGA012400123", "LYRICS=$lrc",
        "METADATA_BLOCK_PICTURE=${base64Picture(front)}", "METADATA_BLOCK_PICTURE=${base64Picture(back, pictureType = 4)}",
        "ITUNESADVISORY=1", "LABEL=\u0645\u0632\u064A\u0643\u0627", "COPYRIGHT=\u2117 2024",
    )

    /** Every fixture layout the tags path must read exactly as the reassembling parser does. */
    private fun equivalenceFixtures(): Map<String, File> {
        val entries = listOf(
            "NOEQUALS", "=novalue", "", " metadata_block_picture =not a picture", "lyrics=first", "LYRICS=second",
            "SYNCEDLYRICS=synced", "UNSYNCEDLYRICS=plain", "DATE=1999", "DATE=2000", "  Label  =\uC548\uB155 \u0645\u0631\u062D\u0628\u0627",
            "K".repeat(100) + "=long key", "ITUNESADVISORY=1",
        ).map { it.toByteArray(Charsets.UTF_8) }
        val manyPageLyrics = lrcOfLength(6531 - "LYRICS=".length)
        val manyPage = vorbisComment(listOf(filler("COPYRIGHT", 27), "DATE=2024-06-12", "LYRICS=$manyPageLyrics",
            "METADATA_BLOCK_PICTURE=" + base64Picture(imageOfSize(75_247, seed = 1)),
            "METADATA_BLOCK_PICTURE=" + base64Picture(imageOfSize(418_458, seed = 2), pictureType = 4), filler("TITLE", 16)),
            vendor = "libopus 1.6, libopusenc 0.3")
        val covers = vorbisComment(coverComments(imageOfSize(20_000, seed = 5), imageOfSize(60_000, seed = 6)))
        val multiplexed = run {
            val pages = oggPages(listOf(opusHead(), "OpusTags".toByteArray(Charsets.US_ASCII) + covers), maxSegmentsPerPage = 16)
            val foreign = oggPages(listOf(ByteArray(300) { 7 }), serial = 0x0BADF00D, maxSegmentsPerPage = 16).single()
            temp("opus-eq-multiplexed", (pages.take(3) + foreign + pages.drop(3)).fold(ByteArray(0)) { acc, page -> acc + page })
        }
        val coversFile = opusFile("opus-eq-covers", covers)
        return mapOf(
            "two covers, tags before and after" to twoCoverOpus.second,
            "header spanning many pages" to opusFile("opus-eq-manypage", manyPage, padding = 10_412),
            "one segment per page (keys and lengths split across pages)" to opusFile("opus-eq-tiny", covers, maxSegmentsPerPage = 1),
            "three segments per page" to opusFile("opus-eq-three", rawComment(entries), maxSegmentsPerPage = 3),
            "another stream's pages mixed in" to multiplexed,
            "trailing padding" to opusFile("opus-eq-padding", covers, padding = 5_000),
            "vorbis stream with a picture" to temp("vorbis-eq", oggVorbis(covers)),
            "malformed and duplicate entries" to opusFile("opus-eq-malformed", rawComment(entries)),
            "entry length past the packet end" to opusFile("opus-eq-overrun", rawComment(entries.take(6), lengths = mapOf(4 to 1_000_000))),
            "negative entry length" to opusFile("opus-eq-negative", rawComment(entries.take(6), lengths = mapOf(4 to -1))),
            "count above the entries present" to opusFile("opus-eq-count", rawComment(entries.take(4), count = 50)),
            "vendor length past the packet end" to opusFile("opus-eq-vendor", rawComment(entries, vendorLength = 1_000_000)),
            "comment block too short" to opusFile("opus-eq-short", byteArrayOf(1, 0, 0)),
            "truncated inside a picture" to temp("opus-eq-cut-picture", coversFile.readBytes().let { it.copyOf(it.size / 2) }),
            "truncated in the audio after the header" to temp("opus-eq-cut-audio", coversFile.readBytes().let { it.copyOf(it.size - 300) }),
            "not an Opus or Vorbis stream" to temp("ogg-eq-other", oggPages(listOf("FishHead".toByteArray() + ByteArray(40), "OpusTags".toByteArray() + covers), maxSegmentsPerPage = 16).fold(ByteArray(0)) { acc, page -> acc + page }),
        )
    }

    @Test fun `tags-only ogg reads equal the reassembling parser on every fixture`() {
        var parsed = 0
        for ((name, file) in equivalenceFixtures()) {
            val streamed = EmbeddedTagReader.oggCommentTextForTest(file.path, streaming = true)
            val reassembled = EmbeddedTagReader.oggCommentTextForTest(file.path, streaming = false)
            assertEquals(reassembled, streamed, name)
            if (reassembled != null) parsed++
        }
        // Guard against a vacuous pass: all but the four unreadable fixtures (vendor overrun,
        // short block, cut inside a picture, not Opus/Vorbis) parse.
        assertEquals(12, parsed)
    }

    @Test fun `tags-only ogg reads handle the malformed entries like the reassembling parser`() {
        val (lyrics, unsynced, tags) = assertNotNull(EmbeddedTagReader.oggCommentTextForTest(equivalenceFixtures().getValue("malformed and duplicate entries").path, streaming = true))
        assertEquals("first", lyrics)
        assertEquals("plain", unsynced)
        assertEquals("1999", tags["DATE"])
        assertEquals("\uC548\uB155 \u0645\u0631\u062D\u0628\u0627", tags["LABEL"])
        assertEquals("long key", tags["K".repeat(100)])
        assertTrue("METADATA_BLOCK_PICTURE" !in tags)
    }

    @Test fun `opus artwork reads return the exact cover bytes`() {
        val (front, file) = twoCoverOpus
        assertEquals(front.toList(), EmbeddedTagReader.embeddedArtworkBytes(file.path)!!.toList())
        val tinyPages = equivalenceFixtures().getValue("one segment per page (keys and lengths split across pages)")
        assertEquals(imageOfSize(20_000, seed = 5).toList(), EmbeddedTagReader.embeddedArtworkBytes(tinyPages.path)!!.toList())
        val backOnlyFront = opusFile("opus-back-first", vorbisComment(listOf(
            "METADATA_BLOCK_PICTURE=${base64Picture(imageOfSize(30_000, seed = 8), pictureType = 4)}",
            "METADATA_BLOCK_PICTURE=${base64Picture(imageOfSize(40_000, seed = 9))}",
        )))
        // Unchanged artwork rule: the first picture is returned, whatever its type.
        assertEquals(imageOfSize(30_000, seed = 8).toList(), EmbeddedTagReader.embeddedArtworkBytes(backOnlyFront.path)!!.toList())
    }

    @Test fun `tags-only ogg reads copy no picture bytes`() {
        fun copiedFor(front: ByteArray, back: ByteArray): Long {
            val file = opusFile("opus-copies", vorbisComment(coverComments(front, back)))
            val before = EmbeddedTagReader.oggTextBytesCopied.get()
            assertNotNull(EmbeddedTagReader.embeddedTrackTags(file.path))
            assertEquals(lrc, EmbeddedTagReader.embeddedLyricsText(file.path))
            return EmbeddedTagReader.oggTextBytesCopied.get() - before
        }
        val small = copiedFor(imageOfSize(7_500, seed = 21), imageOfSize(42_000, seed = 22))
        val large = copiedFor(imageOfSize(75_247, seed = 23), imageOfSize(418_458, seed = 24))
        assertEquals(small, large, "bytes copied must not depend on the picture sizes")
        // Per read (tags, then lyrics): the text entries, every entry's 4-byte length, the
        // 24-byte key chunk of each picture, the 8-byte identification prefix and magic,
        // and the vendor length and entry count.
        val comments = coverComments(ByteArray(0), ByteArray(0))
        val textBytes = comments.filterNot { it.startsWith("METADATA") }.sumOf { it.toByteArray().size }
        val perRead = textBytes + 4 * comments.size + 2 * 24 + 8 + 8 + 4 + 4
        assertEquals(2L * perRead, large)
    }

    @Test fun `ogg pages from another multiplexed stream are skipped`() {
        val lyrics = lrcOfLength(10_000)
        val pages = oggPages(
            listOf(opusHead(), "OpusTags".toByteArray(Charsets.US_ASCII) + vorbisComment(listOf("LYRICS=$lyrics"))),
            maxSegmentsPerPage = 16,
        )
        val foreign = oggPages(listOf(ByteArray(300) { 7 }), serial = 0x0BADF00D, maxSegmentsPerPage = 16).single()
        val bytes = (pages.take(2) + foreign + pages.drop(2)).fold(ByteArray(0)) { acc, page -> acc + page }
        val file = temp("opus-multiplexed", bytes)
        assertEquals(lyrics, EmbeddedTagReader.embeddedLyricsText(file.path))
    }

    @Test fun `truncated ogg comment header returns null`() {
        val comment = vorbisComment(listOf("LYRICS=" + lrcOfLength(20_000)))
        val bytes = oggOpus(comment)
        val file = temp("opus-truncated", bytes.copyOf(10_000))
        assertNull(EmbeddedTagReader.embeddedLyricsText(file.path))
    }

    @Test fun `m4a covr artwork`() {
        val file = m4a(ilst = covr(image))
        val bytes = EmbeddedTagReader.embeddedArtworkBytes(file.path)
        assertNotNull(bytes)
        assertEquals(image.toList(), bytes.toList())
    }

    @Test fun `m4a copyright lyric atom lyrics`() {
        val file = m4a(ilst = lyricAtom(lrc))
        assertEquals(lrc, EmbeddedTagReader.embeddedLyricsText(file.path))
    }

    @Test fun `m4a itunes freeform lyrics`() {
        val file = m4a(ilst = itunesLyrics(lrc))
        assertEquals(lrc, EmbeddedTagReader.embeddedLyricsText(file.path))
    }

    // Before the itunesLyrics() fixture gained its mean/name version/flags header this passed
    // only because the freeform atom was unreadable, not because the precedence rule ran.
    @Test fun `m4a prefers copyright lyric over itunes freeform`() {
        val file = m4a(ilst = lyricAtom("plain lyrics") + itunesLyrics(lrc))
        assertEquals("plain lyrics", EmbeddedTagReader.embeddedLyricsText(file.path))
    }

    @Test fun `m4a without ilst returns null`() {
        val file = m4a(ilst = byteArrayOf())
        assertNull(EmbeddedTagReader.embeddedLyricsText(file.path))
        assertNull(EmbeddedTagReader.embeddedArtworkBytes(file.path))
    }

    // Fragmented MP4 (saved DASH/CMAF stream): mvhd/mdhd durations are 0 and Android's
    // parser reports 0, so the length comes from moov > mvex > mehd. Values mirror real
    // LastWave files verified against ffprobe.
    @Test fun `fragmented m4a duration from mehd`() {
        val file = temp("fmp4", fragmentedMp4(timescale = 44_100, fragmentDuration = 11_615_940L)) // "Perfect", 263.4 s
        assertEquals(263_400L, EmbeddedTagReader.fragmentedMp4DurationMillis(file.path))
    }

    @Test fun `fragmented m4a duration at a 96 kHz timescale`() {
        val file = temp("fmp4-96k", fragmentedMp4(timescale = 96_000, fragmentDuration = 21_051_646L)) // ffprobe 219.288 s
        assertEquals(219_287L, EmbeddedTagReader.fragmentedMp4DurationMillis(file.path))
    }

    @Test fun `fragmented m4a 64-bit mehd`() {
        // 13 h at 96 kHz needs more than 32 bits; version-1 mvhd and mehd.
        val file = temp("fmp4-v1", fragmentedMp4(timescale = 96_000, fragmentDuration = 96_000L * 46_800L, version = 1))
        assertEquals(46_800_000L, EmbeddedTagReader.fragmentedMp4DurationMillis(file.path))
    }

    @Test fun `fragmented m4a without mehd returns null`() {
        val file = temp("fmp4-no-mehd", fragmentedMp4(timescale = 44_100, fragmentDuration = null))
        assertNull(EmbeddedTagReader.fragmentedMp4DurationMillis(file.path))
    }

    @Test fun `non-mp4 file has no fragmented duration`() {
        val file = flac(picture = null, comments = listOf("TITLE=x"))
        assertNull(EmbeddedTagReader.fragmentedMp4DurationMillis(file.path))
    }

    // --- Content advisory (SpotiFLAC convention: ITUNESADVISORY=1, rtng 1; clean drops the tag) ---

    private fun explicitOf(file: File): Boolean? = EmbeddedTagReader.embeddedTrackTags(file.path)?.explicit

    @Test fun `flac itunesadvisory marks explicit and clean`() {
        assertEquals(true, explicitOf(flac(picture = null, comments = listOf("TITLE=a", "ITUNESADVISORY=1"))))
        assertEquals(false, explicitOf(flac(picture = null, comments = listOf("TITLE=a", "ITUNESADVISORY=0"))))
        assertEquals(true, explicitOf(flac(picture = null, comments = listOf("TITLE=a", "itunesadvisory=1"))))
    }

    @Test fun `opus itunesadvisory marks explicit`() {
        assertEquals(true, explicitOf(temp("opus-explicit", oggOpus(vorbisComment(listOf("TITLE=a", "ITUNESADVISORY=1"))))))
        assertEquals(false, explicitOf(temp("opus-clean", oggOpus(vorbisComment(listOf("TITLE=a", "ITUNESADVISORY=0"))))))
    }

    @Test fun `id3 txxx itunesadvisory marks explicit`() {
        assertEquals(true, explicitOf(temp("id3-explicit", id3v23(frames = listOf(txxx("ITUNESADVISORY", "1"))))))
        assertEquals(false, explicitOf(temp("id3-clean", id3v23(frames = listOf(txxx("ITUNESADVISORY", "0"))))))
        // Another user-defined frame is not an advisory.
        assertEquals(null, explicitOf(temp("id3-other-txxx", id3v23(frames = listOf(txxx("MOOD", "1"))))))
    }

    @Test fun `m4a rtng 1 is explicit, other values are not`() {
        assertEquals(true, explicitOf(m4a(ilst = rtng(1))))
        assertEquals(false, explicitOf(m4a(ilst = rtng(2)))) // iTunes "clean"
        assertEquals(false, explicitOf(m4a(ilst = rtng(0))))
        assertEquals(false, explicitOf(m4a(ilst = rtng(4)))) // not a value SpotiFLAC or iTunes writes
    }

    @Test fun `files without an advisory are not explicit`() {
        assertEquals(null, explicitOf(flac(picture = null, comments = listOf("TITLE=a"))))
        assertEquals(null, explicitOf(temp("opus-none", oggOpus(vorbisComment(listOf("TITLE=a"))))))
        assertEquals(null, explicitOf(temp("id3-none", id3v23(frames = listOf(uslt("eng", lrc))))))
        assertEquals(null, explicitOf(m4a(ilst = lyricAtom(lrc))))
        assertEquals(false, parseAdvisoryText(" 0 "))
        assertEquals(null, parseAdvisoryText(""))
    }

    // --- Tags of large files (read by declared size / box and chunk walks, not a fixed prefix) ---

    private val largeGap = 35L * 1024 * 1024

    private fun tagFrames(): List<ByteArray> = listOf(
        frame32("TDRC", byteArrayOf(3) + "2002".toByteArray(), syncsafe = true),
        frame32("TSRC", byteArrayOf(3) + "USIR10211570".toByteArray(), syncsafe = true),
        txxx("ITUNESADVISORY", "1"),
    )

    @Test fun `id3 tag size comes from its header`() {
        val v23 = id3v23(frames = listOf(uslt("eng", lrc)))
        assertEquals(v23.size.toLong(), id3TagSize(v23))
        // v2.4 footer flag adds the 10-byte footer.
        val v24Footer = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 4, 0, 0x10, 0, 0, 0x02, 0x01)
        assertEquals(10L + 257 + 10, id3TagSize(v24Footer))
        assertNull(id3TagSize("RIFF\u0000\u0000\u0000\u0000WAVE".toByteArray()))
        // A size byte with its high bit set is not synchsafe: a corrupt header.
        assertNull(id3TagSize(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0, 0, 0, 0x80.toByte(), 0)))
    }

    @Test fun `mp3 tag ahead of 35 MB of audio is read`() {
        val file = sparse("mp3-large", id3v23(frames = tagFrames()), largeGap, byteArrayOf(0xFF.toByte(), 0xFB.toByte()))
        val tags = assertNotNull(EmbeddedTagReader.embeddedTrackTags(file.path))
        assertEquals(2002, tags.year)
        assertEquals("USIR10211570", tags.isrc)
        assertEquals(true, tags.explicit)
    }

    /** Matches the full-length WAV/AIFF copies on the test device: a 57 MB audio chunk,
     *  then the `id3 `/`ID3 ` chunk at the end of the file. */
    private fun riffWithTrailingId3(bigEndian: Boolean, id3Tag: ByteArray): File {
        val audioSize = 57L * 1024 * 1024
        val head = ByteArrayOutputStream().apply {
            write((if (bigEndian) "FORM" else "RIFF").toByteArray(Charsets.ISO_8859_1))
            if (bigEndian) writeIntBE(0) else writeIntLE(0) // container size is not relied on
            write((if (bigEndian) "AIFF" else "WAVE").toByteArray(Charsets.ISO_8859_1))
            write(riffChunk(if (bigEndian) "COMM" else "fmt ", ByteArray(if (bigEndian) 18 else 16), bigEndian))
            write((if (bigEndian) "SSND" else "data").toByteArray(Charsets.ISO_8859_1))
            if (bigEndian) writeIntBE(audioSize.toInt()) else writeIntLE(audioSize.toInt())
        }.toByteArray()
        val tail = riffChunk(if (bigEndian) "ID3 " else "id3 ", id3Tag, bigEndian)
        return sparse(if (bigEndian) "aiff-large" else "wav-large", head, audioSize, tail)
    }

    @Test fun `wav id3 chunk after 57 MB of audio is read`() {
        val file = riffWithTrailingId3(bigEndian = false, id3v23(frames = tagFrames() + uslt("eng", lrc) + apic(image)))
        val tags = assertNotNull(EmbeddedTagReader.embeddedTrackTags(file.path))
        assertEquals(2002, tags.year)
        assertEquals("USIR10211570", tags.isrc)
        assertEquals(true, tags.explicit)
        assertContains(EmbeddedTagReader.embeddedLyricsText(file.path)!!, "Second line")
        assertEquals(image.toList(), EmbeddedTagReader.embeddedArtworkBytes(file.path)!!.toList())
    }

    @Test fun `aiff id3 chunk after 57 MB of audio is read`() {
        val file = riffWithTrailingId3(bigEndian = true, id3v23(frames = tagFrames() + apic(image)))
        val tags = assertNotNull(EmbeddedTagReader.embeddedTrackTags(file.path))
        assertEquals(2002, tags.year)
        assertEquals(true, tags.explicit)
        assertEquals(image.toList(), EmbeddedTagReader.embeddedArtworkBytes(file.path)!!.toList())
    }

    @Test fun `riff chunk overrunning the file returns null`() {
        val head = ByteArrayOutputStream().apply {
            write("RIFF".toByteArray(Charsets.ISO_8859_1)); writeIntLE(0); write("WAVE".toByteArray(Charsets.ISO_8859_1))
            write("data".toByteArray(Charsets.ISO_8859_1)); writeIntLE(1_000_000)
        }.toByteArray()
        assertNull(EmbeddedTagReader.embeddedTrackTags(temp("wav-truncated", head + ByteArray(100)).path))
    }

    /** Matches the device's non-fast-start M4A files: ftyp, free, a 35 MB mdat, then moov. */
    private fun m4aMoovAfterMdat(ilst: ByteArray, largeSizeMdat: Boolean = false): File {
        val moov = box("moov", box("udta", box("meta", ByteArray(4) + box("hdlr", ByteArray(8)) + box("ilst", ilst))))
        val ftyp = box("ftyp", "M4A ".toByteArray(Charsets.ISO_8859_1) + ByteArray(8))
        val mdatHeader = bytesOf {
            if (largeSizeMdat) {
                writeIntBE(1); write("mdat".toByteArray(Charsets.ISO_8859_1)); writeLongBE(16 + largeGap)
            } else {
                writeIntBE((8 + largeGap).toInt()); write("mdat".toByteArray(Charsets.ISO_8859_1))
            }
        }
        return sparse("m4a-large", ftyp + box("free", byteArrayOf()) + mdatHeader, largeGap, moov)
    }

    private fun textAtom(type: String, text: String): ByteArray =
        box(type, box("data", byteArrayOf(0, 0, 0, 1, 0, 0, 0, 0) + text.toByteArray(Charsets.UTF_8)))

    private fun freeform(name: String, text: String): ByteArray {
        val mean = box("mean", ByteArray(4) + "com.apple.iTunes".toByteArray(Charsets.UTF_8))
        val nameBox = box("name", ByteArray(4) + name.toByteArray(Charsets.UTF_8))
        return box("----", mean + nameBox + box("data", byteArrayOf(0, 0, 0, 1, 0, 0, 0, 0) + text.toByteArray(Charsets.UTF_8)))
    }

    @Test fun `m4a moov after 35 MB of mdat is read`() {
        val file = m4aMoovAfterMdat(textAtom("\u00A9day", "2002") + freeform("ISRC", "USIR10211570") + rtng(1) + lyricAtom(lrc) + covr(image))
        val tags = assertNotNull(EmbeddedTagReader.embeddedTrackTags(file.path))
        assertEquals(2002, tags.year)
        assertEquals("USIR10211570", tags.isrc)
        assertEquals(true, tags.explicit)
        assertEquals(lrc, EmbeddedTagReader.embeddedLyricsText(file.path))
        assertEquals(image.toList(), EmbeddedTagReader.embeddedArtworkBytes(file.path)!!.toList())
    }

    @Test fun `m4a moov after a 64-bit mdat is read`() {
        val file = m4aMoovAfterMdat(rtng(1), largeSizeMdat = true)
        assertEquals(true, EmbeddedTagReader.embeddedTrackTags(file.path)?.explicit)
    }

    private fun flacBlockHeader(type: Int, length: Int, last: Boolean): ByteArray =
        byteArrayOf(((if (last) 0x80 else 0) or type).toByte(), (length shr 16 and 0xFF).toByte(), (length shr 8 and 0xFF).toByte(), (length and 0xFF).toByte())

    @Test fun `flac comments and cover after 12 MB of padding are read`() {
        val padding = 12 * 1024 * 1024
        val head = "fLaC".toByteArray(Charsets.ISO_8859_1) + flacBlockHeader(0, 34, last = false) + ByteArray(34) +
            flacBlockHeader(1, padding, last = false)
        val comment = vorbisComment(listOf("DATE=2002", "ISRC=USIR10211570", "ITUNESADVISORY=1", "LYRICS=$lrc"))
        val picture = pictureBlock(image)
        val tail = flacBlockHeader(FLAC_VORBIS_COMMENT, comment.size, last = false) + comment +
            flacBlockHeader(FLAC_PICTURE, picture.size, last = true) + picture + ByteArray(1024) // audio frames
        val file = sparse("flac-padded", head, padding.toLong(), tail)
        val tags = assertNotNull(EmbeddedTagReader.embeddedTrackTags(file.path))
        assertEquals(2002, tags.year)
        assertEquals("USIR10211570", tags.isrc)
        assertEquals(true, tags.explicit)
        assertEquals(lrc, EmbeddedTagReader.embeddedLyricsText(file.path))
        assertEquals(image.toList(), EmbeddedTagReader.embeddedArtworkBytes(file.path)!!.toList())
    }

    @Test fun `flac cover larger than 8 MB is read, and tags after it`() {
        val bigCover = imageOfSize(9 * 1024 * 1024, seed = 3)
        val file = flac(picture = bigCover, comments = listOf("ITUNESADVISORY=1"))
        assertEquals(bigCover.size, EmbeddedTagReader.embeddedArtworkBytes(file.path)!!.size)
        assertEquals(true, EmbeddedTagReader.embeddedTrackTags(file.path)?.explicit)
    }

    // --- Artist/album-artist/album fallback tags ---

    private fun text23(code: String, text: String): ByteArray =
        frame32(code, byteArrayOf(3) + text.toByteArray(Charsets.UTF_8), syncsafe = true)

    private fun text22(code: String, text: String): ByteArray =
        frame22(code, byteArrayOf(3) + text.toByteArray(Charsets.UTF_8))

    @Test fun `id3 v2_3 artist album artist and album`() {
        val file = temp("id3-tag-fields", id3v23(frames = listOf(
            text23("TPE1", "Artist"), text23("TPE2", "Album Artist"), text23("TALB", "Album"),
        )))
        val tags = assertNotNull(EmbeddedTagReader.embeddedTrackTags(file.path))
        assertEquals("Artist", tags.artist)
        assertEquals("Album Artist", tags.albumArtist)
        assertEquals("Album", tags.album)
    }

    @Test fun `id3 v2_2 artist album artist and album`() {
        val file = temp("id3v22-tag-fields", id3v22(frames = listOf(
            text22("TP1", "Artist"), text22("TP2", "Album Artist"), text22("TAL", "Album"),
        )))
        val tags = assertNotNull(EmbeddedTagReader.embeddedTrackTags(file.path))
        assertEquals("Artist", tags.artist)
        assertEquals("Album Artist", tags.albumArtist)
        assertEquals("Album", tags.album)
    }

    @Test fun `id3 v2_4 joins nul separated artist values`() {
        val file = temp("id3v24-tag-fields", id3(4, 0, listOf(
            frame32("TPE1", byteArrayOf(3) + "A\u0000B".toByteArray(Charsets.UTF_8), syncsafe = true),
        )))
        assertEquals("A, B", assertNotNull(EmbeddedTagReader.embeddedTrackTags(file.path)).artist)
    }

    @Test fun `wav id3 chunk artist and album are read with one open`() {
        val file = wav(id3v23(frames = listOf(text23("TPE1", "Artist"), text23("TALB", "Album"))))
        val before = EmbeddedTagReader.fileOpenCount.get()
        val tags = assertNotNull(EmbeddedTagReader.embeddedTrackTags(file.path))
        assertEquals("Artist", tags.artist)
        assertEquals("Album", tags.album)
        assertEquals(1, EmbeddedTagReader.fileOpenCount.get() - before)
    }

    @Test fun `flac vorbis artist album artist and album`() {
        val file = flac(picture = null, comments = listOf("ARTIST=Artist", "ALBUMARTIST=Album Artist", "ALBUM=Album"))
        val tags = assertNotNull(EmbeddedTagReader.embeddedTrackTags(file.path))
        assertEquals("Artist", tags.artist)
        assertEquals("Album Artist", tags.albumArtist)
        assertEquals("Album", tags.album)
    }

    @Test fun `m4a artist album artist and album atoms`() {
        val file = m4a(ilst = textAtom("\u00A9ART", "Artist") + textAtom("aART", "Album Artist") + textAtom("\u00A9alb", "Album"))
        val tags = assertNotNull(EmbeddedTagReader.embeddedTrackTags(file.path))
        assertEquals("Artist", tags.artist)
        assertEquals("Album Artist", tags.albumArtist)
        assertEquals("Album", tags.album)
    }

    // --- One open per read (opening a shared-storage file costs more than reading its tags) ---

    private fun opensDuring(block: () -> Unit): Int {
        val before = EmbeddedTagReader.fileOpenCount.get()
        block()
        return EmbeddedTagReader.fileOpenCount.get() - before
    }

    @Test fun `every read opens its file exactly once, for every format`() {
        val files = mapOf(
            "flac" to flac(picture = image, comments = listOf("LYRICS=$lrc", "ITUNESADVISORY=1")),
            "opus" to temp("opus-once", oggOpus(vorbisComment(listOf("LYRICS=$lrc", "METADATA_BLOCK_PICTURE=${base64Picture(image)}")))),
            "vorbis" to temp("vorbis-once", oggVorbis(vorbisComment(listOf("LYRICS=$lrc", "METADATA_BLOCK_PICTURE=${base64Picture(image)}")))),
            "mp3" to temp("mp3-once", id3v23(frames = tagFrames() + uslt("eng", lrc) + apic(image))),
            "m4a (moov after mdat)" to m4aMoovAfterMdat(rtng(1) + lyricAtom(lrc) + covr(image)),
            "wav" to riffWithTrailingId3(bigEndian = false, id3v23(frames = tagFrames() + uslt("eng", lrc) + apic(image))),
            "aiff" to riffWithTrailingId3(bigEndian = true, id3v23(frames = tagFrames() + apic(image))),
        )
        for ((format, file) in files) {
            assertEquals(1, opensDuring { assertNotNull(EmbeddedTagReader.embeddedTrackTags(file.path), format) }, "$format tags")
            assertEquals(1, opensDuring { EmbeddedTagReader.embeddedLyricsText(file.path) }, "$format lyrics")
            assertEquals(1, opensDuring { assertNotNull(EmbeddedTagReader.embeddedArtworkBytes(file.path), format) }, "$format artwork")
        }
        assertEquals(1, opensDuring { assertEquals(1500L, EmbeddedTagReader.fragmentedMp4DurationMillis(temp("frag-once", fragmentedMp4(timescale = 1000, fragmentDuration = 1500L)).path)) })
    }

    @Test fun `missing and tiny files read as absent`() {
        assertNull(EmbeddedTagReader.embeddedTrackTags(File(System.getProperty("java.io.tmpdir"), "no-such-audio-file.flac").path))
        assertNull(EmbeddedTagReader.embeddedArtworkBytes(File(System.getProperty("java.io.tmpdir"), "no-such-audio-file.flac").path))
        assertNull(EmbeddedTagReader.embeddedTrackTags(temp("tiny-id3", "ID3".toByteArray()).path))
        assertNull(EmbeddedTagReader.embeddedLyricsText(temp("tiny-ogg", "OggS".toByteArray()).path))
        assertNull(EmbeddedTagReader.embeddedTrackTags(temp("empty", ByteArray(0)).path))
    }

    @Test fun `wav id3 chunk artwork and lyrics`() {
        val file = wav(id3v23(frames = listOf(uslt("eng", lrc), apic(image))))
        assertContains(EmbeddedTagReader.embeddedLyricsText(file.path)!!, "Hello world")
        assertEquals(image.toList(), EmbeddedTagReader.embeddedArtworkBytes(file.path)!!.toList())
    }

    @Test fun `aiff id3 chunk artwork`() {
        val file = aiff(id3v23(frames = listOf(apic(image))))
        assertEquals(image.toList(), EmbeddedTagReader.embeddedArtworkBytes(file.path)!!.toList())
    }

    @Test fun `wav without id3 chunk returns null`() {
        val fmt = riffChunk("fmt ", byteArrayOf(0x01, 0x00), bigEndian = false)
        val body = ByteArrayOutputStream().apply {
            write("WAVE".toByteArray(Charsets.ISO_8859_1))
            write(fmt)
        }.toByteArray()
        val out = ByteArrayOutputStream()
        out.write("RIFF".toByteArray(Charsets.ISO_8859_1))
        out.writeIntLE(4 + body.size)
        out.write(body)
        val file = temp("wav-no-id3", out.toByteArray())
        assertNull(EmbeddedTagReader.embeddedLyricsText(file.path))
        assertNull(EmbeddedTagReader.embeddedArtworkBytes(file.path))
    }

    /** [head], then [gap] zero bytes left as a sparse hole (cheap on disk), then [tail]. */
    private fun sparse(name: String, head: ByteArray, gap: Long, tail: ByteArray): File {
        val file = File.createTempFile(name, ".test").apply { deleteOnExit() }
        java.io.RandomAccessFile(file, "rw").use { raf ->
            raf.write(head)
            raf.seek(head.size + gap)
            raf.write(tail)
        }
        return file
    }

    private fun temp(name: String, bytes: ByteArray): File =
        File.createTempFile(name, ".test").apply { writeBytes(bytes) }

    private fun flac(picture: ByteArray?, comments: List<String>, pictureType: Int = 3, secondPicture: ByteArray? = null): File {
        val blocks = mutableListOf<Pair<Int, ByteArray>>()
        picture?.let { blocks += FLAC_PICTURE to pictureBlock(it, pictureType) }
        secondPicture?.let { blocks += FLAC_PICTURE to pictureBlock(it, 3) }
        blocks += FLAC_VORBIS_COMMENT to vorbisComment(comments)
        val out = ByteArrayOutputStream()
        out.write("fLaC".toByteArray(Charsets.ISO_8859_1))
        blocks.forEachIndexed { index, (type, payload) ->
            out.write((if (index == blocks.lastIndex) 0x80 else 0x00) or type)
            out.write(byteArrayOf((payload.size shr 16 and 0xFF).toByte(), (payload.size shr 8 and 0xFF).toByte(), (payload.size and 0xFF).toByte()))
            out.write(payload)
        }
        return temp("flac", out.toByteArray())
    }

    private fun pictureBlock(imageBytes: ByteArray, pictureType: Int = 3): ByteArray {
        val mime = "image/png".toByteArray(Charsets.ISO_8859_1)
        val out = ByteArrayOutputStream()
        out.writeIntBE(pictureType)
        out.writeIntBE(mime.size); out.write(mime)
        out.writeIntBE(0)
        out.writeIntBE(0); out.writeIntBE(0); out.writeIntBE(24); out.writeIntBE(0)
        out.writeIntBE(imageBytes.size); out.write(imageBytes)
        return out.toByteArray()
    }

    private fun vorbisComment(comments: List<String>, vendor: String = ""): ByteArray {
        val out = ByteArrayOutputStream()
        val vendorBytes = vendor.toByteArray(Charsets.UTF_8)
        out.writeIntLE(vendorBytes.size)
        out.write(vendorBytes)
        out.writeIntLE(comments.size)
        comments.forEach { entry ->
            val bytes = entry.toByteArray(Charsets.UTF_8)
            out.writeIntLE(bytes.size)
            out.write(bytes)
        }
        return out.toByteArray()
    }

    // ---------------------------------------------------------------- OGG fixtures

    /** "KEY=value" padded to exactly [length] bytes, matching a real tag's size. */
    private fun filler(key: String, length: Int): String = "$key=" + "x".repeat(length - key.length - 1)

    /** LRC-shaped UTF-8 text (multi-byte Arabic included) of exactly [length] bytes. */
    private fun lrcOfLength(length: Int): String {
        val sb = StringBuilder()
        var line = 0
        while (sb.toString().toByteArray(Charsets.UTF_8).size < length) {
            sb.append("[%02d:%02d.00]سطر رقم %d line\n".format(line / 60, line % 60, line))
            line++
        }
        var text = sb.toString()
        while (text.toByteArray(Charsets.UTF_8).size > length) text = text.dropLast(1)
        return text + "a".repeat(length - text.toByteArray(Charsets.UTF_8).size)
    }

    private fun imageOfSize(size: Int, seed: Int): ByteArray =
        kotlin.random.Random(seed).nextBytes(size).also { image.copyInto(it, endIndex = 8) }

    private fun base64Picture(imageBytes: ByteArray, pictureType: Int = 3): String =
        java.util.Base64.getEncoder().encodeToString(pictureBlock(imageBytes, pictureType))

    private fun opusHead(): ByteArray = ByteArrayOutputStream().apply {
        write("OpusHead".toByteArray(Charsets.US_ASCII))
        write(byteArrayOf(1, 2, 0x38, 0x01, 0x44, 0xAC.toByte(), 0, 0, 0, 0, 0))
    }.toByteArray()

    /**
     * libopusenc layout: OpusHead alone on the BOS page, then the OpusTags packet (plus
     * [padding] zero bytes, as libopusenc reserves) on pages of 16 lacing values (4080
     * bytes), then audio pages.
     */
    private fun oggOpus(comment: ByteArray, padding: Int = 0): ByteArray {
        val tags = "OpusTags".toByteArray(Charsets.US_ASCII) + comment + ByteArray(padding)
        val audio = List(40) { ByteArray(160) { i -> (i * 31 + it).toByte() } }
        return oggPages(listOf(opusHead(), tags) + audio, maxSegmentsPerPage = 16, pageStarts = setOf(1, 2))
            .fold(ByteArray(0)) { acc, page -> acc + page }
    }

    /** libvorbis layout: id header alone on the BOS page; comment and setup headers share pages. */
    private fun oggVorbis(comment: ByteArray): ByteArray {
        val id = byteArrayOf(0x01) + "vorbis".toByteArray(Charsets.US_ASCII) + ByteArray(23) { 1 }
        val commentPacket = byteArrayOf(0x03) + "vorbis".toByteArray(Charsets.US_ASCII) + comment + byteArrayOf(0x01)
        val setup = byteArrayOf(0x05) + "vorbis".toByteArray(Charsets.US_ASCII) + ByteArray(3_000) { 5 }
        val audio = List(20) { ByteArray(400) { i -> (i + it).toByte() } }
        return oggPages(listOf(id, commentPacket, setup) + audio, maxSegmentsPerPage = 255, pageStarts = setOf(1, 3))
            .fold(ByteArray(0)) { acc, page -> acc + page }
    }

    /**
     * Lays [packets] out as spec-conformant Ogg pages: lacing values in each page's
     * segment table, packets continued across pages (header type 0x01), BOS on the
     * first page, EOS on the last, and a valid page CRC. A page is flushed once it
     * holds [maxSegmentsPerPage] lacing values, and before each packet in [pageStarts].
     */
    private fun oggPages(
        packets: List<ByteArray>,
        serial: Int = 0x16F3F5BA,
        maxSegmentsPerPage: Int,
        pageStarts: Set<Int> = emptySet(),
    ): List<ByteArray> {
        val pages = ArrayList<ByteArray>()
        val lacing = ArrayList<Int>()
        val body = ByteArrayOutputStream()
        var continued = false
        var nextPageContinued = false
        fun flush(eos: Boolean) {
            if (lacing.isEmpty()) return
            val flags = (if (continued) 0x01 else 0) or (if (pages.isEmpty()) 0x02 else 0) or (if (eos) 0x04 else 0)
            pages += oggPage(flags, serial, pages.size, lacing, body.toByteArray())
            lacing.clear(); body.reset()
            continued = nextPageContinued
        }
        packets.forEachIndexed { index, packet ->
            if (index in pageStarts) flush(eos = false)
            var offset = 0
            while (true) {
                val length = minOf(255, packet.size - offset)
                lacing += length
                body.write(packet, offset, length)
                offset += length
                val packetDone = length < 255
                nextPageContinued = !packetDone
                if (lacing.size == maxSegmentsPerPage) flush(eos = false)
                if (packetDone) break
            }
            nextPageContinued = false
        }
        flush(eos = true)
        return pages
    }

    private fun oggPage(flags: Int, serial: Int, sequence: Int, lacing: List<Int>, body: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("OggS".toByteArray(Charsets.US_ASCII))
        out.write(0) // stream structure version
        out.write(flags)
        repeat(8) { out.write(0) } // granule position (unused by the tag reader)
        out.writeIntLE(serial)
        out.writeIntLE(sequence)
        out.writeIntLE(0) // CRC placeholder
        out.write(lacing.size)
        lacing.forEach { out.write(it) }
        out.write(body)
        val page = out.toByteArray()
        val crc = oggCrc(page)
        for (i in 0 until 4) page[22 + i] = (crc ushr (8 * i)).toByte()
        return page
    }

    /** Ogg's CRC-32 (polynomial 0x04C11DB7, zero init, unreflected) over the page with a zeroed CRC field. */
    private fun oggCrc(page: ByteArray): Int {
        var crc = 0
        for (byte in page) {
            crc = crc xor ((byte.toInt() and 0xFF) shl 24)
            repeat(8) { crc = if (crc and 0x80000000.toInt() != 0) (crc shl 1) xor 0x04C11DB7 else crc shl 1 }
        }
        return crc
    }

    private fun ByteArray.indexOf(needle: ByteArray): Int {
        outer@ for (start in 0..size - needle.size) {
            for (i in needle.indices) if (this[start + i] != needle[i]) continue@outer
            return start
        }
        return -1
    }

    private fun id3v23(frames: List<ByteArray>): ByteArray = id3(3, 0, frames)
    private fun id3v22(frames: List<ByteArray>): ByteArray = id3(2, 0, frames)

    private fun id3(major: Int, revision: Int, frames: List<ByteArray>): ByteArray {
        val body = ByteArrayOutputStream().apply { frames.forEach { write(it) } }.toByteArray()
        val out = ByteArrayOutputStream()
        out.write("ID3".toByteArray(Charsets.ISO_8859_1))
        out.write(major); out.write(revision); out.write(0)
        out.writeSyncSafe(body.size)
        out.write(body)
        return out.toByteArray()
    }

    private fun uslt(lang: String, text: String): ByteArray {
        val content = ByteArrayOutputStream()
        content.write(3)
        content.write(lang.toByteArray(Charsets.ISO_8859_1))
        content.write(0)
        content.write(text.toByteArray(Charsets.UTF_8))
        return frame32(code = "USLT", content.toByteArray(), syncsafe = true)
    }

    private fun txxx(description: String, value: String): ByteArray {
        val content = ByteArrayOutputStream()
        content.write(3)
        content.write(description.toByteArray(Charsets.UTF_8))
        content.write(0)
        content.write(value.toByteArray(Charsets.UTF_8))
        return frame32(code = "TXXX", content.toByteArray(), syncsafe = true)
    }

    /** `rtng` as SpotiFLAC writes it: a type-21 (integer) data atom holding one byte. */
    private fun rtng(value: Int): ByteArray =
        box("rtng", box("data", byteArrayOf(0, 0, 0, 21, 0, 0, 0, 0, value.toByte())))

    private fun apic(imageBytes: ByteArray): ByteArray {
        val content = ByteArrayOutputStream()
        content.write(0)
        content.write("image/png".toByteArray(Charsets.ISO_8859_1)); content.write(0)
        content.write(3)
        content.write(0)
        content.write(imageBytes)
        return frame32(code = "APIC", content.toByteArray(), syncsafe = true)
    }

    private fun ult(lang: String, text: String): ByteArray {
        val content = ByteArrayOutputStream()
        content.write(3)
        content.write(lang.toByteArray(Charsets.ISO_8859_1))
        content.write(0)
        content.write(text.toByteArray(Charsets.UTF_8))
        return frame22(code = "ULT", content.toByteArray())
    }

    private fun pic(imageBytes: ByteArray): ByteArray {
        val content = ByteArrayOutputStream()
        content.write(0)
        content.write("PNG".toByteArray(Charsets.ISO_8859_1))
        content.write(3)
        content.write(0)
        content.write(imageBytes)
        return frame22(code = "PIC", content.toByteArray())
    }

    /** ID3v2.2 frames carry a 3-byte code and a 24-bit big-endian size (no flags). */
    private fun frame22(code: String, data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(code.toByteArray(Charsets.ISO_8859_1))
        out.write(data.size shr 16 and 0xFF)
        out.write(data.size shr 8 and 0xFF)
        out.write(data.size and 0xFF)
        out.write(data)
        return out.toByteArray()
    }

    private fun frame32(code: String, data: ByteArray, syncsafe: Boolean): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(code.toByteArray(Charsets.ISO_8859_1))
        if (syncsafe) out.writeSyncSafe(data.size) else out.writeIntBE(data.size)
        out.write(0); out.write(0)
        out.write(data)
        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.writeIntBE(value: Int) {
        write(value shr 24 and 0xFF)
        write(value shr 16 and 0xFF)
        write(value shr 8 and 0xFF)
        write(value and 0xFF)
    }

    private fun ByteArrayOutputStream.writeIntLE(value: Int) {
        write(value and 0xFF)
        write(value shr 8 and 0xFF)
        write(value shr 16 and 0xFF)
        write(value shr 24 and 0xFF)
    }

    private fun ByteArrayOutputStream.writeSyncSafe(value: Int) {
        write(value shr 21 and 0x7F)
        write(value shr 14 and 0x7F)
        write(value shr 7 and 0x7F)
        write(value and 0x7F)
    }

    // --------------------------------------------------- MP4 / RIFF / AIFF fixtures

    /** Builds an MP4 container: 4-byte big-endian size + type + payload. */
    private fun box(type: String, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.writeIntBE(8 + payload.size)
        out.write(type.toByteArray(Charsets.ISO_8859_1))
        out.write(payload)
        return out.toByteArray()
    }

    private fun m4a(ilst: ByteArray): File {
        val ilstBox = box("ilst", ilst)
        val meta = box("meta", (ByteArray(4) + box("hdlr", ByteArray(8))).let { it + ilstBox })
        val udta = box("udta", meta)
        val moov = box("moov", udta)
        val ftyp = box("ftyp", ("M4A ".toByteArray(Charsets.ISO_8859_1) + ByteArray(8)))
        return temp("m4a", ftyp + moov)
    }

    private fun fullBox(type: String, version: Int, payload: ByteArray): ByteArray =
        box(type, byteArrayOf(version.toByte(), 0, 0, 0) + payload)

    private fun ByteArrayOutputStream.writeLongBE(value: Long) {
        for (shift in 56 downTo 0 step 8) write((value ushr shift).toInt() and 0xFF)
    }

    private fun bytesOf(block: ByteArrayOutputStream.() -> Unit): ByteArray = ByteArrayOutputStream().apply(block).toByteArray()

    /**
     * A fragmented M4A laid out like the real LastWave files: iso8/cmfc ftyp; moov with
     * zero-duration mvhd/mdhd, an fLaC sample entry and empty sample tables, mvex (mehd +
     * trex) and a ~150 KB cover in udta; then moof (mfhd, traf: tfhd, tfdt, trun) + mdat
     * fragments. [fragmentDuration] null omits mehd.
     */
    private fun fragmentedMp4(timescale: Int, fragmentDuration: Long?, version: Int = 0): ByteArray {
        val ftyp = box("ftyp", "iso8".toByteArray(Charsets.ISO_8859_1) + ByteArray(4) + "mp41dashcmfc".toByteArray(Charsets.ISO_8859_1))
        val times = if (version == 1) ByteArray(16) else ByteArray(8) // creation + modification
        val zeroDuration = if (version == 1) ByteArray(8) else ByteArray(4)
        val mvhd = fullBox("mvhd", version, bytesOf {
            write(times); writeIntBE(timescale); write(zeroDuration)
            writeIntBE(0x00010000); write(byteArrayOf(0x01, 0x00)); write(ByteArray(10))
            write(ByteArray(36)); write(ByteArray(24)); writeIntBE(2)
        })
        val mdhd = fullBox("mdhd", version, bytesOf { write(times); writeIntBE(timescale); write(zeroDuration); write(ByteArray(4)) })
        val fLaC = box("fLaC", bytesOf {
            write(ByteArray(6)); write(byteArrayOf(0, 1)); write(ByteArray(8))
            write(byteArrayOf(0, 2, 0, 16)); write(ByteArray(4)); writeIntBE(timescale shl 16)
            write(fullBox("dfLa", 0, ByteArray(38)))
        })
        val stbl = box("stbl",
            fullBox("stsd", 0, bytesOf { writeIntBE(1); write(fLaC) }) +
                fullBox("stts", 0, bytesOf { writeIntBE(0) }) + fullBox("stsc", 0, bytesOf { writeIntBE(0) }) +
                fullBox("stsz", 0, bytesOf { writeIntBE(0); writeIntBE(0) }) + fullBox("stco", 0, bytesOf { writeIntBE(0) }),
        )
        val trak = box("trak",
            fullBox("tkhd", 0, ByteArray(80)) +
                box("mdia", mdhd + fullBox("hdlr", 0, ByteArray(4) + "soun".toByteArray(Charsets.ISO_8859_1) + ByteArray(13)) +
                    box("minf", fullBox("smhd", 0, ByteArray(4)) + box("dinf", fullBox("dref", 0, ByteArray(4))) + stbl)),
        )
        val mehd = fragmentDuration?.let { duration ->
            fullBox("mehd", version, bytesOf { if (version == 1) writeLongBE(duration) else writeIntBE(duration.toInt()) })
        } ?: ByteArray(0)
        val mvex = box("mvex", mehd + fullBox("trex", 0, bytesOf { writeIntBE(1); writeIntBE(1); writeIntBE(0); writeIntBE(0); writeIntBE(0) }))
        val udta = box("udta", box("meta", ByteArray(4) + box("hdlr", ByteArray(8)) + box("ilst", covr(ByteArray(150_000) { (it % 251).toByte() }))))
        val moov = box("moov", mvhd + trak + mvex + udta)
        val fragments = (0 until 4).fold(ByteArray(0)) { acc, index ->
            val sampleCount = 8
            val sampleSize = 64
            // flags 0x000301: data-offset, per-sample duration and size present.
            val trun = box("trun", bytesOf {
                write(byteArrayOf(0, 0x00, 0x03, 0x01)); writeIntBE(sampleCount); writeIntBE(0)
                repeat(sampleCount) { writeIntBE(4096); writeIntBE(sampleSize) }
            })
            val traf = box("traf", fullBox("tfhd", 0, bytesOf { writeIntBE(1) }) +
                fullBox("tfdt", 1, bytesOf { writeLongBE(index * 32_768L) }) + trun)
            acc + box("moof", fullBox("mfhd", 0, bytesOf { writeIntBE(index + 1) }) + traf) +
                box("mdat", ByteArray(sampleCount * sampleSize) { 0x55 })
        }
        return ftyp + moov + fragments
    }

    private fun covr(imageBytes: ByteArray): ByteArray {
        val dataPayload = ByteArrayOutputStream()
        dataPayload.write(ByteArray(8))
        dataPayload.write(imageBytes)
        return box("covr", box("data", dataPayload.toByteArray()))
    }

    private fun lyricAtom(text: String): ByteArray {
        val dataPayload = ByteArrayOutputStream()
        dataPayload.write(ByteArray(8))
        dataPayload.write(text.toByteArray(Charsets.UTF_8))
        return box("\u00A9lyr", box("data", dataPayload.toByteArray()))
    }

    /** `mean`/`name` carry a 4-byte version/flags header before the text, as written by
     *  real taggers (verified against mutagen's `----:com.apple.iTunes:LYRICS` output). */
    private fun itunesLyrics(text: String): ByteArray {
        val mean = box("mean", ByteArray(4) + "com.apple.iTunes".toByteArray(Charsets.UTF_8))
        val name = box("name", ByteArray(4) + "LYRICS".toByteArray(Charsets.UTF_8))
        val dataPayload = ByteArrayOutputStream()
        dataPayload.write(ByteArray(8))
        dataPayload.write(text.toByteArray(Charsets.UTF_8))
        return box("----", mean + name + box("data", dataPayload.toByteArray()))
    }

    /** RIFF/AIFF chunk: id + size (chosen endianness) + payload + pad to even bounds. */
    private fun riffChunk(id: String, payload: ByteArray, bigEndian: Boolean): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(id.toByteArray(Charsets.ISO_8859_1))
        if (bigEndian) out.writeIntBE(payload.size) else out.writeIntLE(payload.size)
        out.write(payload)
        if (payload.size and 1 != 0) out.write(0)
        return out.toByteArray()
    }

    private fun wav(id3Tag: ByteArray): File {
        val fmt = riffChunk("fmt ", byteArrayOf(0x01, 0x00), bigEndian = false)
        val id3 = riffChunk("id3 ", id3Tag, bigEndian = false)
        val body = ByteArrayOutputStream().apply {
            write("WAVE".toByteArray(Charsets.ISO_8859_1))
            write(fmt)
            write(id3)
        }.toByteArray()
        val out = ByteArrayOutputStream()
        out.write("RIFF".toByteArray(Charsets.ISO_8859_1))
        out.writeIntLE(4 + body.size)
        out.write(body)
        return temp("wav", out.toByteArray())
    }

    private fun aiff(id3Tag: ByteArray): File {
        val comm = riffChunk("COMM", ByteArray(18), bigEndian = true)
        val id3 = riffChunk("ID3 ", id3Tag, bigEndian = true)
        val body = ByteArrayOutputStream().apply {
            write("AIFF".toByteArray(Charsets.ISO_8859_1))
            write(comm)
            write(id3)
        }.toByteArray()
        val out = ByteArrayOutputStream()
        out.write("FORM".toByteArray(Charsets.ISO_8859_1))
        out.writeIntBE(4 + body.size)
        out.write(body)
        return temp("aiff", out.toByteArray())
    }

    private companion object {
        const val FLAC_PICTURE = 6
        const val FLAC_VORBIS_COMMENT = 4
    }
}