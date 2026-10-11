// SPDX-License-Identifier: GPL-3.0-or-later
package me.misa198.airmedy.analysis
import com.google.gson.JsonParser
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
class MoodParityTest {
    @Test fun matchesPinnedGoAtOneTrillionth() {
        val p=Percentile(0.0,10.0,50.0,90.0,100.0)
        val table=Mood.ALL_FEATURE_NAMES.associateWith { p }
        val rows=JsonParser.parseString(File(System.getProperty("analysis.repo"),"docs/analysis/mood-oracle.json").readText()).asJsonArray
        var maximumError=0.0
        for((i,row) in rows.withIndex()) {
            val r=row.asJsonArray;val x=r[0].asDouble
            val f=RawFeatures(x,x*.7,x*.3,x*1.4,x*.4,x*.2,x*.5)
            val got=doubleArrayOf(Mood.normalize(x,p),Mood.tempoScore(x),Mood.deriveEnergy(f,table),Mood.deriveDanceability(f,table),Mood.deriveBrightness(f,table),Mood.interpolatedPercentile(doubleArrayOf(-32.0,5.0,17.0,60.0,201.0),i/1023.0))
            for(j in got.indices) {
                maximumError=maxOf(maximumError,kotlin.math.abs(r[j+1].asDouble-got[j]))
                assertEquals("row $i column $j",r[j+1].asDouble,got[j],1e-12)
            }
        }
        val report=File("build/reports/mood-max-error.txt");report.parentFile.mkdirs();report.writeText("$maximumError\n")
    }
    @Test fun corpusNaNOrderingMatchesGoFloat64s() {
        val result=Mood.computeCorpusPercentiles(mapOf("rms" to doubleArrayOf(10.0,Double.NaN,0.0)))
        assertEquals(0.0,result.percentiles.getValue("rms").p50,0.0)
        org.junit.Assert.assertTrue(result.percentiles.getValue("rms").p1.isNaN())
    }
}
