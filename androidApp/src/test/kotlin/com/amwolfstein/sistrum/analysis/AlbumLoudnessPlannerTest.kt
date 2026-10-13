package com.amwolfstein.sistrum.analysis

import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.pow
import com.amwolfstein.sistrum.analysis.loudness.LoudnessHistogram
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T051b: pins the album loudness planner before T051c implements it. Every positive test fails
 * on an [AssertionError] against the stub ([AlbumLoudnessPlanner] returns empty results), never a
 * crash: records are null-checked before use.
 */
class AlbumLoudnessPlannerTest {

    // -----------------------------------------------------------------------------------------
    // P1 membership
    // -----------------------------------------------------------------------------------------

    @Test
    fun availableTaggedTrackIsAnAlbumMember() {
        assertTrue(AlbumLoudnessPlanner.isAlbumMember(track("A")))
    }

    @Test
    fun albumEqualToParentFolderWithoutTagAlbumIsNotAMember() {
        val fallback = track("A", album = "Music", audioPath = "/sdcard/Music/A.mp3", tagAlbum = "")
        assertFalse(AlbumLoudnessPlanner.isAlbumMember(fallback))
    }

    @Test
    fun albumEqualToParentFolderWithTagAlbumIsAMember() {
        val tagged = track("A", album = "Music", audioPath = "/sdcard/Music/A.mp3", tagAlbum = "Music")
        assertTrue(AlbumLoudnessPlanner.isAlbumMember(tagged))
    }

    @Test
    fun albumDifferentFromFolderWithoutTagAlbumIsAMember() {
        val tagged = track("A", album = "Album One", audioPath = "/sdcard/Music/A.mp3", tagAlbum = "")
        assertTrue(AlbumLoudnessPlanner.isAlbumMember(tagged))
    }

    @Test
    fun hiddenTrackIsNotAnAlbumMember() {
        assertFalse(AlbumLoudnessPlanner.isAlbumMember(track("A", available = false)))
    }

    @Test
    fun blankAlbumIdIsNotAnAlbumMember() {
        assertFalse(AlbumLoudnessPlanner.isAlbumMember(track("A", albumId = "")))
    }

    @Test
    fun failedTrackIsNotAnAlbumMember() {
        assertFalse(AlbumLoudnessPlanner.isAlbumMember(track("A", status = "failed")))
    }

    // -----------------------------------------------------------------------------------------
    // P2 completeness
    // -----------------------------------------------------------------------------------------

    @Test
    fun albumWithAnUnmeasuredMemberGetsNoRecord() {
        val a = track("A", histogram = hist(-14.2 to 100))
        val unmeasured = track("B", status = null, analyzerVersion = null, histogram = null, truePeakDbtp = null)
        val plan = AlbumLoudnessPlanner.plan(listOf(a, unmeasured), emptyMap())
        assertFalse("an incomplete album is not written", plan.write.containsKey("album-1"))
    }

    @Test
    fun albumWithAWrongVersionMemberGetsNoRecord() {
        val a = track("A", histogram = hist(-14.2 to 100))
        val stale = track("B", histogram = hist(-24.2 to 100), analyzerVersion = "other-version")
        val plan = AlbumLoudnessPlanner.plan(listOf(a, stale), emptyMap())
        assertFalse("an old-version member blocks the record", plan.write.containsKey("album-1"))
    }

    @Test
    fun incompleteAlbumThatExistsIsDeleted() {
        val a = track("A", histogram = hist(-14.2 to 100))
        val unmeasured = track("B", status = null, analyzerVersion = null, histogram = null, truePeakDbtp = null)
        val existing = mapOf("album-1" to AlbumRecord(-12.0, -0.5, 2, "old"))
        val plan = AlbumLoudnessPlanner.plan(listOf(a, unmeasured), existing)
        assertTrue("an incomplete existing album is deleted", plan.delete.contains("album-1"))
        assertFalse("it is not rewritten", plan.write.containsKey("album-1"))
    }

    @Test
    fun failedTrackDoesNotBlockItsAlbum() {
        val a = track("A", histogram = hist(-14.2 to 100), truePeakDbtp = -0.5)
        val failed = track("B", status = "failed", histogram = null, truePeakDbtp = null)
        val plan = AlbumLoudnessPlanner.plan(listOf(a, failed), emptyMap())
        val record = plan.write["album-1"]
        assertNotNull("a failed sibling does not block the album", record)
        assertEquals("members exclude the failed track", 1, record!!.members)

        val aValue = hist(-14.2 to 100).integratedLufs()
        assertNotNull("track A integrated", aValue)
        assertEquals("loudness is A's histogram", aValue!!, record.loudnessLufs, 1e-9)
    }

    @Test
    fun okMemberWithoutHistogramBlocksTheRecord() {
        val a = track("A", histogram = hist(-14.2 to 100))
        val noHistogram = track("B", status = "ok", histogram = null, truePeakDbtp = null)
        val plan = AlbumLoudnessPlanner.plan(listOf(a, noHistogram), emptyMap())
        assertFalse("a member without a histogram blocks the record", plan.write.containsKey("album-1"))

        val existing = mapOf("album-1" to AlbumRecord(-12.0, -0.5, 2, "old"))
        val withExisting = AlbumLoudnessPlanner.plan(listOf(a, noHistogram), existing)
        assertTrue("an incomplete existing album is deleted", withExisting.delete.contains("album-1"))
    }

    @Test
    fun existingAlbumWithOnlyHiddenOrFolderFallbackTracksLeftIsDeleted() {
        val hidden = track("A", available = false, histogram = hist(-14.2 to 100))
        val fallback = track(
            "B",
            albumId = "album-1",
            album = "Music",
            audioPath = "/sdcard/Music/B.mp3",
            tagAlbum = "",
            histogram = hist(-24.2 to 100),
        )
        val existing = mapOf("album-1" to AlbumRecord(-12.0, -0.5, 2, "old"))
        val plan = AlbumLoudnessPlanner.plan(listOf(hidden, fallback), existing)
        assertTrue("an album left with no members is deleted", plan.delete.contains("album-1"))
        assertFalse("it is not written", plan.write.containsKey("album-1"))
    }

    // -----------------------------------------------------------------------------------------
    // P3 record value
    // -----------------------------------------------------------------------------------------

    @Test
    fun albumWithAllMembersMeasuredGetsARecord() {
        val a = track("A", histogram = hist(-14.2 to 100))
        val b = track("B", histogram = hist(-24.2 to 100))
        val plan = AlbumLoudnessPlanner.plan(listOf(a, b), emptyMap())
        val record = plan.write["album-1"]
        assertNotNull("a complete album has a record", record)
        assertEquals("member count", 2, record!!.members)
    }

    @Test
    fun silentMembersContributeNoBlocksOrPeak() {
        val a = track("A", histogram = hist(-14.2 to 100), truePeakDbtp = -1.0)
        val silent = track("B", status = "silent", histogram = LoudnessHistogram(), truePeakDbtp = -0.1)
        val plan = AlbumLoudnessPlanner.plan(listOf(a, silent), emptyMap())
        val record = plan.write["album-1"]
        assertNotNull("album with ok and silent members has a record", record)

        val merged = LoudnessHistogram()
        merged.merge(hist(-14.2 to 100))
        val mergedIntegrated = merged.integratedLufs()
        assertNotNull("merged integrated", mergedIntegrated)
        assertEquals("silent adds no blocks", mergedIntegrated!!, record!!.loudnessLufs, 1e-9)
        assertEquals("silent is ignored for true peak", -1.0, record.truePeakDbtp, 1e-9)
        assertEquals("silent still counts as a member", 2, record.members)
    }

    @Test
    fun allSilentAlbumGetsNoRecord() {
        val a = track("A", status = "silent", histogram = LoudnessHistogram(), truePeakDbtp = null)
        val b = track("B", status = "silent", histogram = LoudnessHistogram(), truePeakDbtp = null)
        val plan = AlbumLoudnessPlanner.plan(listOf(a, b), emptyMap())
        assertTrue("an all-silent album has no record", plan.write.isEmpty())
        assertTrue("nothing to delete", plan.delete.isEmpty())
    }

    @Test
    fun allSilentAlbumThatExistsIsDeleted() {
        val a = track("A", status = "silent", histogram = LoudnessHistogram(), truePeakDbtp = null)
        val b = track("B", status = "silent", histogram = LoudnessHistogram(), truePeakDbtp = null)
        val existing = mapOf("album-1" to AlbumRecord(-12.0, -0.5, 2, "old"))
        val plan = AlbumLoudnessPlanner.plan(listOf(a, b), existing)
        assertTrue("an all-silent existing album is deleted", plan.delete.contains("album-1"))
        assertFalse("it is not written", plan.write.containsKey("album-1"))
    }

    // -----------------------------------------------------------------------------------------
    // P4 members fingerprint
    // -----------------------------------------------------------------------------------------

    @Test
    fun membersFingerprintIsOrderIndependent() {
        val a = track("A", fingerprint = "aaa")
        val b = track("B", fingerprint = "bbb")
        assertEquals(
            AlbumLoudnessPlanner.membersFingerprint(listOf(a, b)),
            AlbumLoudnessPlanner.membersFingerprint(listOf(b, a)),
        )
    }

    @Test
    fun membersFingerprintIsSha256OfSortedLines() {
        val a = track("A", fingerprint = "aaa")
        val b = track("B", fingerprint = "bbb")
        val expected = sha256("A:aaa:$ANALYZER_VERSION\nB:bbb:$ANALYZER_VERSION")
        assertEquals(expected, AlbumLoudnessPlanner.membersFingerprint(listOf(b, a)))
    }

    @Test
    fun membersFingerprintChangesWhenAMemberIsAdded() {
        val a = track("A", fingerprint = "aaa")
        val b = track("B", fingerprint = "bbb")
        assertNotEquals(
            AlbumLoudnessPlanner.membersFingerprint(listOf(a)),
            AlbumLoudnessPlanner.membersFingerprint(listOf(a, b)),
        )
    }

    @Test
    fun membersFingerprintChangesWhenAFingerprintChanges() {
        assertNotEquals(
            AlbumLoudnessPlanner.membersFingerprint(listOf(track("A", fingerprint = "aaa"))),
            AlbumLoudnessPlanner.membersFingerprint(listOf(track("A", fingerprint = "zzz"))),
        )
    }

    @Test
    fun membersFingerprintChangesWhenTheVersionChanges() {
        assertNotEquals(
            AlbumLoudnessPlanner.membersFingerprint(listOf(track("A", fingerprint = "aaa", analyzerVersion = ANALYZER_VERSION))),
            AlbumLoudnessPlanner.membersFingerprint(listOf(track("A", fingerprint = "aaa", analyzerVersion = "other-version"))),
        )
    }

    // -----------------------------------------------------------------------------------------
    // P5 no rewrite for an unchanged fingerprint
    // -----------------------------------------------------------------------------------------

    @Test
    fun unchangedMembersFingerprintIsNotRewritten() {
        val a = track("A", histogram = hist(-14.2 to 100))
        val b = track("B", histogram = hist(-24.2 to 100))
        val fingerprint = sha256("A:fp-A:$ANALYZER_VERSION\nB:fp-B:$ANALYZER_VERSION")
        val existing = mapOf("album-1" to AlbumRecord(-12.0, -0.5, 2, fingerprint))
        val plan = AlbumLoudnessPlanner.plan(listOf(a, b), existing)
        assertFalse("not rewritten", plan.write.containsKey("album-1"))
        assertFalse("not deleted", plan.delete.contains("album-1"))
    }

    // -----------------------------------------------------------------------------------------
    // P6 albums with no members
    // -----------------------------------------------------------------------------------------

    @Test
    fun albumWithNoMembersIsDeleted() {
        val existing = mapOf(
            "album-1" to AlbumRecord(-12.0, -0.5, 2, "old"),
            "album-2" to AlbumRecord(-13.0, -0.6, 3, "old2"),
        )
        val plan = AlbumLoudnessPlanner.plan(emptyList(), existing)
        assertEquals("every memberless album is deleted", setOf("album-1", "album-2"), plan.delete)
        assertTrue("nothing is written", plan.write.isEmpty())
    }

    // -----------------------------------------------------------------------------------------
    // P7 single-member and untagged albums
    // -----------------------------------------------------------------------------------------

    @Test
    fun singleMemberTagAlbumGetsThatTracksValue() {
        val a = track("A", histogram = hist(-14.2 to 100), truePeakDbtp = -0.8)
        val plan = AlbumLoudnessPlanner.plan(listOf(a), emptyMap())
        val record = plan.write["album-1"]
        assertNotNull("a single-member tag album has a record", record)

        val trackValue = hist(-14.2 to 100).integratedLufs()
        assertNotNull("track integrated", trackValue)
        assertEquals("value is the track's histogram", trackValue!!, record!!.loudnessLufs, 1e-9)
        assertEquals("true peak", -0.8, record.truePeakDbtp, 1e-9)
        assertEquals("members", 1, record.members)
    }

    @Test
    fun blankAlbumIdNeverProducesARecord() {
        val a = track("A", albumId = "", histogram = hist(-14.2 to 100))
        val plan = AlbumLoudnessPlanner.plan(listOf(a), emptyMap())
        assertTrue("a blank album id is never an album", plan.write.isEmpty())
    }

    @Test
    fun untaggedFolderFallbackTracksProduceNoRecord() {
        val a = track("A", album = "Music", audioPath = "/sdcard/Music/A.mp3", tagAlbum = "", histogram = hist(-14.2 to 100))
        val b = track("B", album = "Music", audioPath = "/sdcard/Music/B.mp3", tagAlbum = "", histogram = hist(-24.2 to 100))
        val plan = AlbumLoudnessPlanner.plan(listOf(a, b), emptyMap())
        assertTrue("a folder fallback group is not an album", plan.write.isEmpty())
    }

    // -----------------------------------------------------------------------------------------
    // P8 pooled, not averaged
    // -----------------------------------------------------------------------------------------

    @Test
    fun albumValueIsPooledNotTheArithmeticMean() {
        val a = track("A", histogram = hist(-14.2 to 100))
        val b = track("B", histogram = hist(-24.2 to 100))
        val plan = AlbumLoudnessPlanner.plan(listOf(a, b), emptyMap())
        val record = plan.write["album-1"]
        assertNotNull("album record", record)

        val merged = LoudnessHistogram()
        merged.merge(hist(-14.2 to 100))
        merged.merge(hist(-24.2 to 100))
        val mergedIntegrated = merged.integratedLufs()
        assertNotNull("merged integrated", mergedIntegrated)

        val trackA = hist(-14.2 to 100).integratedLufs()
        val trackB = hist(-24.2 to 100).integratedLufs()
        assertNotNull("track A integrated", trackA)
        assertNotNull("track B integrated", trackB)
        val mean = (trackA!! + trackB!!) / 2.0

        assertEquals("album is the pooled histogram value", mergedIntegrated!!, record!!.loudnessLufs, 1e-9)
        assertTrue("album is not the arithmetic mean", abs(record.loudnessLufs - mean) > 0.5)
    }

    // -----------------------------------------------------------------------------------------
    // Builders
    // -----------------------------------------------------------------------------------------

    private fun blockPower(loudnessLufs: Double): Double = 10.0.pow((loudnessLufs + 0.691) / 10.0)

    private fun hist(vararg blocks: Pair<Double, Int>): LoudnessHistogram {
        val histogram = LoudnessHistogram()
        blocks.forEach { (loudness, count) -> repeat(count) { histogram.addBlockPower(blockPower(loudness)) } }
        return histogram
    }

    private fun track(
        trackId: String,
        albumId: String = "album-1",
        album: String = "Album One",
        audioPath: String = "/music/some-other-folder/$trackId.flac",
        tagAlbum: String = "Album One",
        available: Boolean = true,
        fingerprint: String = "fp-$trackId",
        status: String? = "ok",
        analyzerVersion: String? = ANALYZER_VERSION,
        histogram: LoudnessHistogram? = hist(-14.2 to 100),
        truePeakDbtp: Double? = -0.5,
    ): PlannerTrack = PlannerTrack(
        trackId = trackId,
        albumId = albumId,
        album = album,
        audioPath = audioPath,
        tagAlbum = tagAlbum,
        available = available,
        fingerprint = fingerprint,
        status = status,
        analyzerVersion = analyzerVersion,
        histogram = histogram?.encode(),
        truePeakDbtp = truePeakDbtp,
    )

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
