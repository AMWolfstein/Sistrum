// SPDX-License-Identifier: GPL-3.0-or-later
// Port of all mood Go test cases from misa198/Airmedy 7d3a08d0eaa2c88d3518f19665eaffc4762dd6a1.
package me.misa198.airmedy.analysis
import org.junit.Assert.*
import org.junit.Test
class MoodTest {
    private val p = Percentile(0.0, 10.0, 50.0, 90.0, 100.0)
    private val table = Mood.ALL_FEATURE_NAMES.associateWith { p }
    private fun equal(a: Double, b: Double) = assertEquals(a, b, 1e-12)
    @Test fun tempoKnownValues() {
        for ((bpm, expected) in listOf(0.0 to 0.0, 60.0 to 0.0, 115.0 to 1.0, 180.0 to 0.0,
            200.0 to 0.0, 87.5 to .5, 147.5 to .5)) equal(expected, Mood.tempoScore(bpm))
    }
    @Test fun tempoMonotonic() {
        for (a in listOf(doubleArrayOf(60.0,70.0,90.0,115.0), doubleArrayOf(115.0,140.0,160.0,180.0)))
            for (i in 1 until a.size) assertTrue(if (a[0] == 60.0) Mood.tempoScore(a[i]) >= Mood.tempoScore(a[i-1])
                else Mood.tempoScore(a[i]) <= Mood.tempoScore(a[i-1]))
    }
    @Test fun energyMedian() { equal(.5, Mood.deriveEnergy(RawFeatures(50.0,50.0,50.0,50.0,50.0),table)) }
    @Test fun energyTempoCap() {
        val t = table + ("tempo" to Percentile(60.0,80.0,115.0,160.0,180.0))
        val f = RawFeatures(50.0,50.0,50.0,180.0,50.0)
        equal(Mood.deriveEnergy(f,t),Mood.deriveEnergy(f.copy(tempo=200.0),t))
    }
    @Test fun brightnessNormalizedCentroid() {
        equal(.5,Mood.deriveBrightness(RawFeatures(spectralCentroid=50.0),table))
        assertTrue(Mood.deriveBrightness(RawFeatures(spectralCentroid=90.0),table) >
            Mood.deriveBrightness(RawFeatures(spectralCentroid=10.0),table))
    }
    @Test fun danceabilityMedian() {
        equal(.45+.30*.5+.15*.5+.10*.5,Mood.deriveDanceability(RawFeatures(tempo=115.0,onsetVariance=50.0,crest=50.0,loudnessRange=50.0),table))
    }
    @Test fun danceabilityTempoScore() {
        val f=RawFeatures(onsetVariance=50.0,crest=50.0,loudnessRange=50.0)
        equal(.45,Mood.deriveDanceability(f.copy(tempo=115.0),table)-Mood.deriveDanceability(f.copy(tempo=300.0),table))
    }
    @Test fun normalizeMedian() { equal(.5,Mood.normalize(50.0,p)) }
    @Test fun normalizeClamps() { equal(Mood.normalize(100.0,p),Mood.normalize(1000.0,p)); equal(Mood.normalize(0.0,p),Mood.normalize(-1000.0,p)) }
    @Test fun normalizeMonotonic() {
        var previous = -1.0
        for (x in doubleArrayOf(0.0,10.0,25.0,50.0,75.0,90.0,100.0)) { val y=Mood.normalize(x,p);assertTrue(y>=previous);previous=y }
    }
    @Test fun normalizeDegenerate() { equal(.5,Mood.normalize(5.0,Percentile(5.0,5.0,5.0,5.0,5.0))) }
    @Test fun normalizeSteepness() { assertTrue(Mood.normalize(75.0,p,5.0)>Mood.normalize(75.0,p,1.0)) }
    @Test fun percentileKnownValues() {
        val a=doubleArrayOf(10.0,20.0,30.0,40.0,50.0)
        for ((q,w) in listOf(.5 to 30.0,.25 to 20.0,0.0 to 10.0,1.0 to 50.0)) equal(w,Mood.interpolatedPercentile(a,q))
    }
    @Test fun percentileSingle() { for(q in doubleArrayOf(.01,.5,.99)) equal(42.0,Mood.interpolatedPercentile(doubleArrayOf(42.0),q)) }
    @Test fun percentileTwo() { equal(50.0,Mood.interpolatedPercentile(doubleArrayOf(0.0,100.0),.5)) }
    @Test fun corpusComputes() {
        val v=table.mapValues { doubleArrayOf(1.0,2.0,3.0,4.0,5.0) }.toMutableMap()
        v["rms"]=doubleArrayOf(10.0,20.0,30.0,40.0,50.0);v["tempo"]=doubleArrayOf(60.0,90.0,120.0,150.0,180.0)
        val r=Mood.computeCorpusPercentiles(v)
        assertEquals(5,r.sampleCount);assertEquals(7,r.percentiles.size)
        equal(30.0,r.percentiles.getValue("rms").p50);equal(120.0,r.percentiles.getValue("tempo").p50)
        equal(10.4,r.percentiles.getValue("rms").p1);equal(49.6,r.percentiles.getValue("rms").p99)
    }
    @Test fun corpusEmpty() { val r=Mood.computeCorpusPercentiles(table.mapValues { doubleArrayOf() });assertEquals(0,r.sampleCount);assertTrue(r.percentiles.isEmpty()) }
}
