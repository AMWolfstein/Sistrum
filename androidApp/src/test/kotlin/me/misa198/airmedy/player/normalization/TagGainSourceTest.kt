package me.misa198.airmedy.player.normalization

import androidx.media3.common.Format
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.BinaryFrame
import androidx.media3.extractor.metadata.id3.CommentFrame
import androidx.media3.extractor.metadata.id3.InternalFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import androidx.media3.extractor.mp3.Mp3InfoReplayGain
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import me.misa198.airmedy.player.PlaybackItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * T049: pins how gain tags are read from Media3 [Format.metadata] before T051 implements
 * [TagGainSource]. Tags are parsed from ID3 TXXX (MP3), Vorbis comments (FLAC/Ogg), M4A freeform
 * `com.apple.iTunes` atoms, R128 (Opus, Q7.8), iTunNORM (Sound Check) and the ReplayGain 1.0 binary
 * frames RVA2/RVAD/RGAD plus Media3's LAME `Mp3InfoReplayGain`.
 *
 * Against the T049 stub every test that expects a value fails on the `assertNotNull` in [assertGain]
 * (an AssertionError, never an NPE); the tests that expect `null` pass.
 */
@OptIn(UnstableApi::class)
class TagGainSourceTest {

    private val source = TagGainSource()

    // ---------------------------------------------------------------------------------------------
    // ReplayGain text tags
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `TXXX track and album gains and peaks are read together`() {
        assertGain(
            form = GainForm.ReplayGain,
            trackGainDb = -7.89f,
            albumGainDb = 3.20f,
            trackPeak = 0.987654f,
            albumPeak = 1.0f,
            entries = arrayOf(
                txxx("REPLAYGAIN_TRACK_GAIN", "-7.89 dB"),
                txxx("REPLAYGAIN_ALBUM_GAIN", "+3.20 dB"),
                txxx("REPLAYGAIN_TRACK_PEAK", "0.987654"),
                txxx("REPLAYGAIN_ALBUM_PEAK", "1.0"),
            ),
        )
    }

    @Test
    fun `TXXX descriptions are case-insensitive`() {
        assertGain(
            form = GainForm.ReplayGain,
            trackGainDb = -7.89f,
            albumGainDb = null,
            trackPeak = 0.987654f,
            albumPeak = null,
            entries = arrayOf(
                txxx("replaygain_track_gain", "-7.89 dB"),
                txxx("Replaygain_Track_Peak", "0.987654"),
            ),
        )
    }

    @Test
    fun `gain with surrounding spaces still parses`() {
        assertGain(
            form = GainForm.ReplayGain,
            trackGainDb = -7.89f,
            albumGainDb = null,
            trackPeak = null,
            albumPeak = null,
            entries = arrayOf(txxx("REPLAYGAIN_TRACK_GAIN", "  -7.89 dB  ")),
        )
    }

    @Test
    fun `gain with a comma decimal separator still parses`() {
        assertGain(
            form = GainForm.ReplayGain,
            trackGainDb = -7.89f,
            albumGainDb = null,
            trackPeak = null,
            albumPeak = null,
            entries = arrayOf(txxx("REPLAYGAIN_TRACK_GAIN", "-7,89 dB")),
        )
    }

    @Test
    fun `gain without a unit still parses`() {
        assertGain(
            form = GainForm.ReplayGain,
            trackGainDb = -7.89f,
            albumGainDb = null,
            trackPeak = null,
            albumPeak = null,
            entries = arrayOf(txxx("REPLAYGAIN_TRACK_GAIN", "-7.89")),
        )
    }

    @Test
    fun `gain with a leading plus sign still parses`() {
        assertGain(
            form = GainForm.ReplayGain,
            trackGainDb = 3.20f,
            albumGainDb = null,
            trackPeak = null,
            albumPeak = null,
            entries = arrayOf(txxx("REPLAYGAIN_TRACK_GAIN", "+3.20 dB")),
        )
    }

    @Test
    fun `gain with an LU unit still parses`() {
        assertGain(
            form = GainForm.ReplayGain,
            trackGainDb = -7.89f,
            albumGainDb = null,
            trackPeak = null,
            albumPeak = null,
            entries = arrayOf(txxx("REPLAYGAIN_TRACK_GAIN", "-7.89 LU")),
        )
    }

    @Test
    fun `lower-case Vorbis comment keys are read`() {
        assertGain(
            form = GainForm.ReplayGain,
            trackGainDb = -7.89f,
            albumGainDb = 3.20f,
            trackPeak = 0.987654f,
            albumPeak = 1.0f,
            entries = arrayOf(
                vorbis("replaygain_track_gain", "-7.89 dB"),
                vorbis("replaygain_album_gain", "+3.20 dB"),
                vorbis("replaygain_track_peak", "0.987654"),
                vorbis("replaygain_album_peak", "1.0"),
            ),
        )
    }

    @Test
    fun `M4A freeform com apple iTunes replaygain atoms are read`() {
        assertGain(
            form = GainForm.ReplayGain,
            trackGainDb = -7.89f,
            albumGainDb = 3.20f,
            trackPeak = 0.987654f,
            albumPeak = 1.0f,
            entries = arrayOf(
                m4a("replaygain_track_gain", "-7.89 dB"),
                m4a("replaygain_album_gain", "+3.20 dB"),
                m4a("replaygain_track_peak", "0.987654"),
                m4a("replaygain_album_peak", "1.0"),
            ),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // REPLAYGAIN_REFERENCE_LOUDNESS
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `reference loudness in LUFS shifts replaygain track and album`() {
        assertGain(
            form = GainForm.ReplayGain,
            trackGainDb = -5.0f,
            albumGainDb = -6.0f,
            trackPeak = null,
            albumPeak = null,
            entries = arrayOf(
                txxx("REPLAYGAIN_TRACK_GAIN", "-1.0 dB"),
                txxx("REPLAYGAIN_ALBUM_GAIN", "-2.0 dB"),
                txxx("REPLAYGAIN_REFERENCE_LOUDNESS", "-14.00 LUFS"),
            ),
        )
    }

    @Test
    fun `reference loudness as a bare number shifts replaygain`() {
        assertGain(
            form = GainForm.ReplayGain,
            trackGainDb = -5.0f,
            albumGainDb = null,
            trackPeak = null,
            albumPeak = null,
            entries = arrayOf(
                txxx("REPLAYGAIN_TRACK_GAIN", "-1.0 dB"),
                txxx("REPLAYGAIN_REFERENCE_LOUDNESS", "-14"),
            ),
        )
    }

    @Test
    fun `reference loudness in dB SPL is ignored`() {
        assertGain(
            form = GainForm.ReplayGain,
            trackGainDb = -1.0f,
            albumGainDb = null,
            trackPeak = null,
            albumPeak = null,
            entries = arrayOf(
                txxx("REPLAYGAIN_TRACK_GAIN", "-1.0 dB"),
                txxx("REPLAYGAIN_REFERENCE_LOUDNESS", "89 dB"),
            ),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // ReplayGain 1.0 binary frames and the LAME header
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `RVA2 track master volume gain and peak are read`() {
        assertGain(
            form = GainForm.ReplayGain,
            trackGainDb = -8.0f,
            albumGainDb = null,
            trackPeak = 0.5f,
            albumPeak = null,
            entries = arrayOf(rva2("track", -8.0f, 0.5f)),
        )
    }

    @Test
    fun `RVA2 album master volume gain and peak are read`() {
        assertGain(
            form = GainForm.ReplayGain,
            trackGainDb = null,
            albumGainDb = -8.0f,
            trackPeak = null,
            albumPeak = 0.5f,
            entries = arrayOf(rva2("album", -8.0f, 0.5f)),
        )
    }

    @Test
    fun `RGAD track gain and peak are read`() {
        assertGain(
            form = GainForm.ReplayGain,
            trackGainDb = -3.5f,
            albumGainDb = null,
            trackPeak = 0.5f,
            albumPeak = null,
            entries = arrayOf(rgad(0.5f, packedGainField(1, -3.5f), 0)),
        )
    }

    @Test
    fun `RVAD track gain is read`() {
        assertGain(
            form = GainForm.ReplayGain,
            trackGainDb = 6.0206f,
            albumGainDb = null,
            trackPeak = null,
            albumPeak = null,
            entries = arrayOf(rvad(0x00FF00, 0x00FF00)),
        )
    }

    @Test
    fun `Mp3InfoReplayGain LAME track gain and peak are read`() {
        assertGain(
            form = GainForm.ReplayGain,
            trackGainDb = -2.5f,
            albumGainDb = null,
            trackPeak = 0.5f,
            albumPeak = null,
            entries = arrayOf(mp3Info(packedGainField(1, -2.5f), 0, 0.5f)),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // R128
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `R128 track gain is Q7_8 plus five dB with no peaks`() {
        assertGain(
            form = GainForm.R128,
            trackGainDb = 0.1797f,
            albumGainDb = null,
            trackPeak = null,
            albumPeak = null,
            entries = arrayOf(vorbis("R128_TRACK_GAIN", "-1234")),
        )
    }

    @Test
    fun `R128 album gain is Q7_8 plus five dB with no peaks`() {
        assertGain(
            form = GainForm.R128,
            trackGainDb = null,
            albumGainDb = 4.0f,
            trackPeak = null,
            albumPeak = null,
            entries = arrayOf(vorbis("R128_ALBUM_GAIN", "-256")),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Sound Check / iTunNORM
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `iTunNORM InternalFrame is read`() {
        assertGain(
            form = GainForm.SoundCheck,
            trackGainDb = -0.1030f,
            albumGainDb = null,
            trackPeak = 32767f / 32768f,
            albumPeak = null,
            entries = arrayOf(iTunNormInternal(iTunNormVector)),
        )
    }

    @Test
    fun `iTunNORM CommentFrame is read`() {
        assertGain(
            form = GainForm.SoundCheck,
            trackGainDb = -0.1030f,
            albumGainDb = null,
            trackPeak = 32767f / 32768f,
            albumPeak = null,
            entries = arrayOf(iTunNormComment(iTunNormVector)),
        )
    }

    @Test
    fun `iTunNORM Vorbis comment is read`() {
        assertGain(
            form = GainForm.SoundCheck,
            trackGainDb = -0.1030f,
            albumGainDb = null,
            trackPeak = 32767f / 32768f,
            albumPeak = null,
            entries = arrayOf(iTunNormVorbis(iTunNormVector)),
        )
    }

    @Test
    fun `short iTunNORM is ignored`() {
        assertNoGain(arrayOf(iTunNormInternal("00000400 00000400")))
    }

    // ---------------------------------------------------------------------------------------------
    // Precedence ReplayGain -> R128 -> Sound Check
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `ReplayGain text beats R128`() {
        assertGain(
            form = GainForm.ReplayGain,
            trackGainDb = -1.0f,
            albumGainDb = null,
            trackPeak = null,
            albumPeak = null,
            entries = arrayOf(
                txxx("REPLAYGAIN_TRACK_GAIN", "-1.0 dB"),
                vorbis("R128_TRACK_GAIN", "-256"),
            ),
        )
    }

    @Test
    fun `R128 beats Sound Check`() {
        assertGain(
            form = GainForm.R128,
            trackGainDb = 4.0f,
            albumGainDb = null,
            trackPeak = null,
            albumPeak = null,
            entries = arrayOf(
                vorbis("R128_TRACK_GAIN", "-256"),
                iTunNormInternal(iTunNormVector),
            ),
        )
    }

    @Test
    fun `Sound Check wins when it is the only form`() {
        assertGain(
            form = GainForm.SoundCheck,
            trackGainDb = -0.1030f,
            albumGainDb = null,
            trackPeak = 32767f / 32768f,
            albumPeak = null,
            entries = arrayOf(iTunNormInternal(iTunNormVector)),
        )
    }

    @Test
    fun `album gain from a lower form is not used`() {
        assertGain(
            form = GainForm.ReplayGain,
            trackGainDb = -1.0f,
            albumGainDb = null,
            trackPeak = null,
            albumPeak = null,
            entries = arrayOf(
                txxx("REPLAYGAIN_TRACK_GAIN", "-1.0 dB"),
                vorbis("R128_ALBUM_GAIN", "-256"),
            ),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Nothing usable
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `null format returns null`() {
        assertNull(source.gainFor(item(), null))
    }

    @Test
    fun `format without metadata returns null`() {
        assertNull(source.gainFor(item(), Format.Builder().build()))
    }

    @Test
    fun `unrelated tags return null`() {
        assertNoGain(
            arrayOf(
                TextInformationFrame("TIT2", null, "A Song"),
                TextInformationFrame("TPE1", null, "An Artist"),
                VorbisComment("ARTIST", "An Artist"),
            ),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Robustness: malformed gains
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `non-numeric gain is ignored`() {
        assertNoGain(arrayOf(txxx("REPLAYGAIN_TRACK_GAIN", "abc dB")))
    }

    @Test
    fun `empty gain is ignored`() {
        assertNoGain(arrayOf(txxx("REPLAYGAIN_TRACK_GAIN", "")))
    }

    @Test
    fun `NaN gain is ignored`() {
        assertNoGain(arrayOf(txxx("REPLAYGAIN_TRACK_GAIN", "NaN")))
    }

    @Test
    fun `positive Infinity gain is ignored`() {
        assertNoGain(arrayOf(txxx("REPLAYGAIN_TRACK_GAIN", "Infinity")))
    }

    @Test
    fun `negative Infinity gain is ignored`() {
        assertNoGain(arrayOf(txxx("REPLAYGAIN_TRACK_GAIN", "-Infinity")))
    }

    @Test
    fun `gain above forty dB is ignored`() {
        assertNoGain(arrayOf(txxx("REPLAYGAIN_TRACK_GAIN", "+50 dB")))
    }

    @Test
    fun `an out-of-range track gain keeps a valid album gain`() {
        assertGain(
            form = GainForm.ReplayGain,
            trackGainDb = null,
            albumGainDb = -2.0f,
            trackPeak = null,
            albumPeak = null,
            entries = arrayOf(
                txxx("REPLAYGAIN_TRACK_GAIN", "+50 dB"),
                txxx("REPLAYGAIN_ALBUM_GAIN", "-2.0 dB"),
            ),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Robustness: malformed peaks
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `zero peak is ignored`() {
        assertPeakDropped("0")
    }

    @Test
    fun `negative peak is ignored`() {
        assertPeakDropped("-0.5")
    }

    @Test
    fun `NaN peak is ignored`() {
        assertPeakDropped("NaN")
    }

    @Test
    fun `infinite peak is ignored`() {
        assertPeakDropped("Infinity")
    }

    @Test
    fun `peak above ten is ignored`() {
        assertPeakDropped("11.0")
    }

    // ---------------------------------------------------------------------------------------------
    // T049 round 2 - review findings
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `Sound Check uses the louder channel word and the higher peak`() {
        assertGain(
            form = GainForm.SoundCheck,
            trackGainDb = -3.0103f,
            albumGainDb = null,
            trackPeak = 32767f / 32768f,
            albumPeak = null,
            entries = arrayOf(
                iTunNormInternal(
                    " 00000400 000007D0 00000FA0 00000FA0 00024CA8 00024CA8 00004000 00007FFF 00024CA8 00024CA8",
                ),
            ),
        )
    }

    @Test
    fun `reference loudness does not shift R128`() {
        assertGain(
            form = GainForm.R128,
            trackGainDb = 0.1797f,
            albumGainDb = null,
            trackPeak = null,
            albumPeak = null,
            entries = arrayOf(
                vorbis("R128_TRACK_GAIN", "-1234"),
                vorbis("REPLAYGAIN_REFERENCE_LOUDNESS", "-14 LUFS"),
            ),
        )
    }

    @Test
    fun `reference loudness does not shift Sound Check`() {
        assertGain(
            form = GainForm.SoundCheck,
            trackGainDb = -0.1030f,
            albumGainDb = null,
            trackPeak = 32767f / 32768f,
            albumPeak = null,
            entries = arrayOf(
                iTunNormInternal(iTunNormVector),
                txxx("REPLAYGAIN_REFERENCE_LOUDNESS", "-14 LUFS"),
            ),
        )
    }

    @Test
    fun `reference loudness alone is not a gain`() {
        assertNoGain(arrayOf(txxx("REPLAYGAIN_REFERENCE_LOUDNESS", "-14 LUFS")))
    }

    @Test
    fun `text ReplayGain beats RVA2 for the same value`() {
        assertGain(
            form = GainForm.ReplayGain,
            trackGainDb = -7.89f,
            albumGainDb = null,
            trackPeak = null,
            albumPeak = null,
            entries = arrayOf(
                txxx("REPLAYGAIN_TRACK_GAIN", "-7.89 dB"),
                rva2("track", -8.0f, null),
            ),
        )
    }

    @Test
    fun `RVA2 fills a value the text tags lack`() {
        assertGain(
            form = GainForm.ReplayGain,
            trackGainDb = -7.89f,
            albumGainDb = -8.0f,
            trackPeak = null,
            albumPeak = 0.5f,
            entries = arrayOf(
                txxx("REPLAYGAIN_TRACK_GAIN", "-7.89 dB"),
                rva2("album", -8.0f, 0.5f),
            ),
        )
    }

    @Test
    fun `RVA2 with no channel bytes never throws`() {
        assertNoGain(arrayOf(BinaryFrame("RVA2", rva2Bytes())))
    }

    @Test
    fun `RVA2 truncated in a channel record never throws`() {
        assertNoGain(arrayOf(BinaryFrame("RVA2", rva2Bytes(0x01, 0xF0))))
    }

    @Test
    fun `RVA2 with an unknown channel index never throws`() {
        assertNoGain(arrayOf(BinaryFrame("RVA2", rva2Bytes(0x09, 0xF0, 0x00, 0x00))))
    }

    @Test
    fun `RGAD shorter than a float never throws`() {
        assertNoGain(arrayOf(BinaryFrame("RGAD", byteArrayOf(1, 2, 3))))
    }

    @Test
    fun `RVAD with a zero bit length never throws`() {
        assertNoGain(arrayOf(BinaryFrame("RVAD", byteArrayOf(0x3F, 0x00))))
    }

    @Test
    fun `RVAD with only a sign byte never throws`() {
        assertNoGain(arrayOf(BinaryFrame("RVAD", byteArrayOf(0x3F))))
    }

    @Test
    fun `iTunNORM Vorbis key is case-insensitive`() {
        assertGain(
            form = GainForm.SoundCheck,
            trackGainDb = -0.1030f,
            albumGainDb = null,
            trackPeak = 32767f / 32768f,
            albumPeak = null,
            entries = arrayOf(VorbisComment("ITUNNORM", iTunNormVector)),
        )
    }

    @Test
    fun `iTunNORM comment description is case-insensitive`() {
        assertGain(
            form = GainForm.SoundCheck,
            trackGainDb = -0.1030f,
            albumGainDb = null,
            trackPeak = 32767f / 32768f,
            albumPeak = null,
            entries = arrayOf(CommentFrame("eng", "itunnorm", iTunNormVector)),
        )
    }

    @Test
    fun `iTunNORM internal description is case-insensitive`() {
        assertGain(
            form = GainForm.SoundCheck,
            trackGainDb = -0.1030f,
            albumGainDb = null,
            trackPeak = 32767f / 32768f,
            albumPeak = null,
            entries = arrayOf(InternalFrame("com.apple.iTunes", "ITUNNORM", iTunNormVector)),
        )
    }

    @Test
    fun `R128 key is case-insensitive`() {
        assertGain(
            form = GainForm.R128,
            trackGainDb = 0.1797f,
            albumGainDb = null,
            trackPeak = null,
            albumPeak = null,
            entries = arrayOf(vorbis("r128_track_gain", "-1234")),
        )
    }

    @Test
    fun `gain below minus forty dB is ignored`() {
        assertNoGain(arrayOf(txxx("REPLAYGAIN_TRACK_GAIN", "-50 dB")))
    }

    @Test
    fun `short iTunNORM comment is ignored`() {
        assertNoGain(arrayOf(iTunNormComment("00000400 00000400")))
    }

    @Test
    fun `short iTunNORM Vorbis comment is ignored`() {
        assertNoGain(arrayOf(iTunNormVorbis("00000400 00000400")))
    }

    @Test
    fun `RVAD and Sound Check are not mixed`() {
        assertGain(
            form = GainForm.ReplayGain,
            trackGainDb = 6.0206f,
            albumGainDb = null,
            trackPeak = null,
            albumPeak = null,
            entries = arrayOf(
                rvad(0x00FF00, 0x00FF00),
                iTunNormInternal(iTunNormVector),
            ),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    /** A malformed peak next to a valid gain: the gain is kept, the peak is absent. */
    private fun assertPeakDropped(peak: String) {
        assertGain(
            form = GainForm.ReplayGain,
            trackGainDb = -7.89f,
            albumGainDb = null,
            trackPeak = null,
            albumPeak = null,
            entries = arrayOf(
                txxx("REPLAYGAIN_TRACK_GAIN", "-7.89 dB"),
                txxx("REPLAYGAIN_TRACK_PEAK", peak),
            ),
        )
    }

    private fun assertNoGain(entries: Array<Metadata.Entry>) {
        assertNull(
            "expected no usable gain tag",
            source.gainFor(item(), formatOf(*entries)),
        )
    }

    private fun assertGain(
        form: GainForm,
        trackGainDb: Float?,
        albumGainDb: Float?,
        trackPeak: Float?,
        albumPeak: Float?,
        entries: Array<Metadata.Entry>,
    ) {
        val info = source.gainFor(item(), formatOf(*entries))
        assertNotNull("expected a GainInfo for form $form", info)
        assertEquals("form", form, info!!.form)
        assertFloat("trackGainDb", trackGainDb, info.trackGainDb)
        assertFloat("albumGainDb", albumGainDb, info.albumGainDb)
        assertFloat("trackPeak", trackPeak, info.trackPeak)
        assertFloat("albumPeak", albumPeak, info.albumPeak)
    }

    private fun assertFloat(label: String, expected: Float?, actual: Float?) {
        if (expected == null) {
            assertNull("$label should be absent", actual)
        } else {
            assertNotNull("$label should be present", actual)
            assertEquals(label, expected, actual!!, 0.001f)
        }
    }

    private fun item(): PlaybackItem = PlaybackItem(
        trackId = "track-1",
        title = "Title",
        artist = "Artist",
        audioPath = "/music/song.flac",
    )

    private fun formatOf(vararg entries: Metadata.Entry): Format =
        Format.Builder().setMetadata(Metadata(entries.toList())).build()

    private fun txxx(description: String, value: String): Metadata.Entry =
        TextInformationFrame("TXXX", description, value)

    private fun vorbis(key: String, value: String): Metadata.Entry = VorbisComment(key, value)

    private fun m4a(description: String, value: String): Metadata.Entry =
        InternalFrame("com.apple.iTunes", description, value)

    private fun iTunNormInternal(value: String): Metadata.Entry =
        InternalFrame("com.apple.iTunes", "iTunNORM", value)

    private fun iTunNormComment(value: String): Metadata.Entry =
        CommentFrame("eng", "iTunNORM", value)

    private fun iTunNormVorbis(value: String): Metadata.Entry =
        VorbisComment("iTunNORM", value)

    private fun rva2(identification: String, gainDb: Float, peak: Float?): Metadata.Entry {
        val out = ByteArrayOutputStream()
        out.write(identification.toByteArray(Charsets.ISO_8859_1))
        out.write(0)
        out.write(1) // Master volume channel.
        val gain = Math.round(gainDb * 512f)
        out.write((gain ushr 8) and 0xFF)
        out.write(gain and 0xFF)
        if (peak == null) {
            out.write(0)
        } else {
            out.write(16)
            val value = Math.round(peak * 32768f)
            out.write((value ushr 8) and 0xFF)
            out.write(value and 0xFF)
        }
        return BinaryFrame("RVA2", out.toByteArray())
    }

    /** Raw RVA2 data: the `track` identification followed by the given channel bytes. */
    private fun rva2Bytes(vararg trailing: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("track".toByteArray(Charsets.ISO_8859_1))
        out.write(0)
        trailing.forEach { out.write(it and 0xFF) }
        return out.toByteArray()
    }

    private fun rgad(peak: Float, field1: Int, field2: Int): Metadata.Entry =
        BinaryFrame(
            "RGAD",
            ByteBuffer.allocate(8)
                .order(ByteOrder.BIG_ENDIAN)
                .putFloat(peak)
                .putShort(field1.toShort())
                .putShort(field2.toShort())
                .array(),
        )

    private fun rvad(vararg volumes: Int): Metadata.Entry {
        val bitLen = 24
        val out = ByteArrayOutputStream()
        out.write(0x3F) // All channels: no negative sign multiplier.
        out.write(bitLen)
        volumes.forEach { volume ->
            out.write((volume ushr 16) and 0xFF)
            out.write((volume ushr 8) and 0xFF)
            out.write(volume and 0xFF)
        }
        return BinaryFrame("RVAD", out.toByteArray())
    }

    private fun mp3Info(field1: Int, field2: Int, peak: Float): Metadata.Entry =
        requireNotNull(Mp3InfoReplayGain.parse(peak, field1, field2))

    /** Packed RGAD/LAME gain field: name 1 = track, 2 = album; gain is tenths of a dB. */
    private fun packedGainField(name: Int, gainDb: Float): Int {
        val magnitude = Math.round(kotlin.math.abs(gainDb) * 10f)
        val sign = if (gainDb < 0f) 0x200 else 0
        return (name shl 13) or (3 shl 10) or sign or (magnitude and 0x1FF)
    }

    private companion object {
        const val iTunNormVector =
            " 00000400 00000400 00000FA0 00000FA0 00024CA8 00024CA8 00007FFF 00007FFF 00024CA8 00024CA8"
    }
}
