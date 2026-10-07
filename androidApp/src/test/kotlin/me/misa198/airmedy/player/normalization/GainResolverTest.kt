package me.misa198.airmedy.player.normalization

import androidx.media3.common.Format
import me.misa198.airmedy.player.PlaybackItem
import me.misa198.airmedy.player.TrackAnalysis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * T050: pins [GainResolver] precedence and [MeasuredGainSource] before T051 implements them.
 * Measured analysis wins over tags; tags are only consulted when measured yields nothing.
 *
 * Against the T050 stubs the tests that expect a value fail on `assertNotNull`/`assertEquals`
 * (AssertionError, never an NPE); the tests that expect `null` pass.
 */
class GainResolverTest {

    // ---------------------------------------------------------------------------------------------
    // Rule 1: resolver precedence
    // ---------------------------------------------------------------------------------------------

    @Test
    fun measuredWinsAndTagsAreNotConsulted() {
        val measuredInfo = GainInfo(-3f, null, null, null, GainForm.Measured)
        val tagsInfo = GainInfo(-5f, null, null, null, GainForm.ReplayGain)
        val measured = RecordingSource(measuredInfo)
        val tags = RecordingSource(tagsInfo)

        val resolved = GainResolver(measured, tags).resolve(item(), null)

        assertEquals(measuredInfo, resolved)
        assertTrue("tags must not be consulted when measured is present", tags.calls.isEmpty())
    }

    @Test
    fun tagsAreUsedWhenMeasuredReturnsNull() {
        val tagsInfo = GainInfo(-5f, null, null, null, GainForm.ReplayGain)
        val resolved = GainResolver(RecordingSource(null), RecordingSource(tagsInfo)).resolve(item(), null)

        assertEquals(tagsInfo, resolved)
    }

    @Test
    fun bothSourcesNullResolvesToNone() {
        val resolved = GainResolver(RecordingSource(null), RecordingSource(null)).resolve(item(), null)

        assertEquals(GainInfo(null, null, null, null, GainForm.None), resolved)
    }

    // ---------------------------------------------------------------------------------------------
    // Rule 2: MeasuredGainSource track and album fields
    // ---------------------------------------------------------------------------------------------

    @Test
    fun measuredSourceUsesAnalysisWithoutAlbum() {
        val source = MeasuredGainSource(RecordingLookup(emptyMap()))

        val info = source.gainFor(item(albumId = "", analysis = TrackAnalysis(-10f, -1f)), null)

        assertNotNull("analysis must produce a gain", info)
        assertEquals(GainForm.Measured, info!!.form)
        assertEquals("trackGainDb", -8f, info.trackGainDb!!, 1e-3f)
        assertEquals("trackPeak", 10.0.pow(-1.0 / 20.0).toFloat(), info.trackPeak!!, 1e-4f)
        assertNull("albumGainDb", info.albumGainDb)
        assertNull("albumPeak", info.albumPeak)
    }

    @Test
    fun measuredSourceAlbumFieldsNullWhenLookupReturnsNull() {
        val source = MeasuredGainSource(RecordingLookup(emptyMap()))

        val info = source.gainFor(item(albumId = "alb-1", analysis = TrackAnalysis(-10f, -1f)), null)

        assertNotNull("analysis must produce a gain", info)
        assertEquals(GainForm.Measured, info!!.form)
        assertNull("albumGainDb", info.albumGainDb)
        assertNull("albumPeak", info.albumPeak)
    }

    @Test
    fun measuredSourceUsesAlbumLoudnessWhenAvailable() {
        val lookup = RecordingLookup(mapOf("alb-1" to AlbumLoudness(-12f, -0.5f)))
        val source = MeasuredGainSource(lookup)

        val info = source.gainFor(item(albumId = "alb-1", analysis = TrackAnalysis(-10f, -1f)), null)

        assertNotNull("analysis must produce a gain", info)
        assertEquals(GainForm.Measured, info!!.form)
        assertEquals("trackGainDb", -8f, info.trackGainDb!!, 1e-3f)
        assertEquals("trackPeak", 10.0.pow(-1.0 / 20.0).toFloat(), info.trackPeak!!, 1e-4f)
        assertEquals("albumGainDb", -6f, info.albumGainDb!!, 1e-3f)
        assertEquals("albumPeak", 10.0.pow(-0.5 / 20.0).toFloat(), info.albumPeak!!, 1e-4f)
    }

    // ---------------------------------------------------------------------------------------------
    // Rule 3: missing or non-finite analysis
    // ---------------------------------------------------------------------------------------------

    @Test
    fun measuredSourceReturnsNullForMissingOrNonFiniteAnalysis() {
        val source = MeasuredGainSource(RecordingLookup(emptyMap()))

        assertNull("null analysis", source.gainFor(item(analysis = null), null))
        assertNull("NaN loudness", source.gainFor(item(analysis = TrackAnalysis(Float.NaN, -1f)), null))
        assertNull("+Inf loudness", source.gainFor(item(analysis = TrackAnalysis(Float.POSITIVE_INFINITY, -1f)), null))
        assertNull("NaN peak", source.gainFor(item(analysis = TrackAnalysis(-10f, Float.NaN)), null))
        assertNull("+Inf peak", source.gainFor(item(analysis = TrackAnalysis(-10f, Float.POSITIVE_INFINITY)), null))
    }

    // ---------------------------------------------------------------------------------------------
    // Rule 4: never look up a blank album id
    // ---------------------------------------------------------------------------------------------

    @Test
    fun measuredSourceNeverLooksUpABlankAlbumId() {
        val lookup = RecordingLookup(mapOf("alb-1" to AlbumLoudness(-12f, -0.5f)))
        val source = MeasuredGainSource(lookup)

        source.gainFor(item(albumId = "", analysis = TrackAnalysis(-10f, -1f)), null)
        source.gainFor(item(albumId = "   ", analysis = TrackAnalysis(-10f, -1f)), null)

        assertTrue("lookup called with a blank album id: ${lookup.calls}", lookup.calls.none { it.isBlank() })
    }

    private fun item(albumId: String = "", analysis: TrackAnalysis? = null): PlaybackItem =
        PlaybackItem(
            trackId = "track-1",
            title = "Title",
            artist = "Artist",
            audioPath = "/music/song.flac",
            albumId = albumId,
            analysis = analysis,
        )

    private class RecordingSource(private val result: GainInfo?) : GainSource {
        val calls = mutableListOf<PlaybackItem>()

        override fun gainFor(item: PlaybackItem, format: Format?): GainInfo? {
            calls += item
            return result
        }
    }

    private class RecordingLookup(private val byId: Map<String, AlbumLoudness>) : AlbumLoudnessLookup {
        val calls = mutableListOf<String>()

        override fun albumLoudness(albumId: String): AlbumLoudness? {
            calls += albumId
            return byId[albumId]
        }
    }
}
