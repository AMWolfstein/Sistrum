package me.misa198.airmedy.player.normalization

import me.misa198.airmedy.player.NormalizationMode
import me.misa198.airmedy.player.NormalizationSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T050: pins [itemGainDb] before T051 implements it. [GainInfo] gains are relative to -18 LUFS,
 * so a tagged/measured gain is shifted by `targetLufs + 18` before the untagged pre-amp and the
 * clip cap are applied.
 *
 * Against the T050 stub (which returns 0 for everything) every test that expects a non-zero
 * result fails on [assertEquals]; only [disabledReturnsZeroForEveryForm] passes.
 */
class ItemGainMathTest {

    @Test
    fun disabledReturnsZeroForEveryForm() {
        val settings = settings(enabled = false)
        assertGain(0f, measuredInfo(-10f), settings)
        assertGain(0f, replayGainInfo(-7.89f), settings)
        assertGain(0f, GainInfo(null, null, null, null, GainForm.None), settings, untaggedPreampDb = 3f)
    }

    // ---------------------------------------------------------------------------------------------
    // Rule 2: global pre-amp is target + 18
    // ---------------------------------------------------------------------------------------------

    @Test
    fun replayGainTrackGainIsShiftedToTheDefaultTarget() {
        assertGain(-3.89f, replayGainInfo(-7.89f), settings())
    }

    @Test
    fun replayGainTrackGainWithTargetMinus18IsUnshifted() {
        assertGain(-7.89f, replayGainInfo(-7.89f), settings(targetLufs = -18f))
    }

    @Test
    fun replayGainTrackGainWithTargetMinus10IsShiftedUp() {
        assertGain(0.11f, replayGainInfo(-7.89f), settings(targetLufs = -10f))
    }

    // ---------------------------------------------------------------------------------------------
    // Rule 3: measured gain equals target - loudness
    // ---------------------------------------------------------------------------------------------

    @Test
    fun measuredGainForLoudnessMinus10IsTargetMinusLoudness() {
        assertGain(-4f, measuredInfo(-10f), settings())
    }

    @Test
    fun measuredGainForLoudnessMinus20IsTargetMinusLoudness() {
        assertGain(6f, measuredInfo(-20f), settings())
    }

    // ---------------------------------------------------------------------------------------------
    // Rule 4: untagged pre-amp applies only to form None
    // ---------------------------------------------------------------------------------------------

    @Test
    fun untaggedPreampAppliesToFormNone() {
        assertGain(6f, GainInfo(null, null, null, null, GainForm.None), settings(), untaggedPreampDb = 2f)
    }

    @Test
    fun untaggedPreampDoesNotApplyToMeasured() {
        assertGain(6f, measuredInfo(-20f), settings(), untaggedPreampDb = 2f)
    }

    @Test
    fun untaggedPreampDoesNotApplyToReplayGain() {
        assertGain(-3.89f, replayGainInfo(-7.89f), settings(), untaggedPreampDb = 2f)
    }

    // ---------------------------------------------------------------------------------------------
    // Rule 5: track/album selection
    // ---------------------------------------------------------------------------------------------

    @Test
    fun albumModeUsesAlbumGainWhenPresent() {
        assertGain(-1f, GainInfo(-2f, -5f, null, null, GainForm.ReplayGain), settings(mode = NormalizationMode.Album))
    }

    @Test
    fun albumModeFallsBackToTrackGainWhenAlbumAbsent() {
        assertGain(2f, GainInfo(-2f, null, null, null, GainForm.ReplayGain), settings(mode = NormalizationMode.Album))
    }

    @Test
    fun trackModeUsesTrackGainEvenWhenAlbumPresent() {
        assertGain(2f, GainInfo(-2f, -5f, null, null, GainForm.ReplayGain), settings(mode = NormalizationMode.Track))
    }

    // ---------------------------------------------------------------------------------------------
    // Rule 6: clip cap
    // ---------------------------------------------------------------------------------------------

    @Test
    fun clipCapLimitsTrackGainToPeak() {
        assertGain(6.0206f, measuredInfo(-24f, trackPeak = 0.5f), settings())
    }

    @Test
    fun clipCapCanBeDisabled() {
        assertGain(10f, measuredInfo(-24f, trackPeak = 0.5f), settings(preventClip = false))
    }

    @Test
    fun clipCapUsesAlbumPeakInAlbumMode() {
        val info = GainInfo(-18f, -1f, 0.5f, 0.9f, GainForm.Measured)
        assertGain(0.9151f, info, settings(mode = NormalizationMode.Album))
    }

    @Test
    fun clipCapFallsBackToTrackPeakWhenAlbumPeakIsNull() {
        val info = GainInfo(-18f, -1f, 0.9f, null, GainForm.Measured)
        assertGain(0.9151f, info, settings(mode = NormalizationMode.Album))
    }

    @Test
    fun clipCapIsSkippedWhenPeakIsNull() {
        assertGain(10f, measuredInfo(-24f, trackPeak = null), settings())
    }

    @Test
    fun clipCapIsSkippedWhenPeakIsNonFinite() {
        assertGain(10f, measuredInfo(-24f, trackPeak = Float.NaN), settings())
    }

    @Test
    fun clipCapIsSkippedWhenPeakIsNotPositive() {
        assertGain(10f, measuredInfo(-24f, trackPeak = 0f), settings())
    }

    // ---------------------------------------------------------------------------------------------
    // Rule 7: non-finite gains are absent
    // ---------------------------------------------------------------------------------------------

    @Test
    fun nonFiniteTrackGainIsTreatedAsNone() {
        assertGain(5f, GainInfo(Float.NaN, null, null, null, GainForm.ReplayGain), settings(), untaggedPreampDb = 1f)
    }

    @Test
    fun nonFiniteAlbumGainFallsBackToTrackInAlbumMode() {
        assertGain(2f, GainInfo(-2f, Float.NaN, null, null, GainForm.ReplayGain), settings(mode = NormalizationMode.Album))
    }

    // ---------------------------------------------------------------------------------------------
    // Rule 8: clamp
    // ---------------------------------------------------------------------------------------------

    @Test
    fun resultIsClampedToMinus30() {
        assertGain(-30f, measuredInfo(20f), settings())
    }

    @Test
    fun resultIsClampedToPlus20() {
        assertGain(20f, GainInfo(null, null, null, null, GainForm.None), settings(), untaggedPreampDb = 30f)
    }

    private fun settings(
        enabled: Boolean = true,
        mode: NormalizationMode = NormalizationMode.Track,
        targetLufs: Float = -14f,
        preventClip: Boolean = true,
    ): NormalizationSettings = NormalizationSettings(enabled, mode, targetLufs, preventClip)

    private fun replayGainInfo(trackGainDb: Float): GainInfo =
        GainInfo(trackGainDb, null, null, null, GainForm.ReplayGain)

    private fun measuredInfo(loudnessLufs: Float, trackPeak: Float? = null): GainInfo =
        GainInfo(-18f - loudnessLufs, null, trackPeak, null, GainForm.Measured)

    private fun assertGain(
        expected: Float,
        info: GainInfo,
        settings: NormalizationSettings,
        untaggedPreampDb: Float = 0f,
    ) {
        val actual = itemGainDb(info, settings, untaggedPreampDb)
        assertTrue("itemGainDb must be finite, was $actual", actual.isFinite())
        assertEquals("itemGainDb($info, $settings, preamp=$untaggedPreampDb)", expected, actual, 1e-3f)
    }
}
