// SPDX-License-Identifier: GPL-3.0-or-later
package com.amwolfstein.sistrum.analysis
import org.junit.Test
import org.junit.BeforeClass
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import com.google.gson.JsonObject
import java.io.File
import kotlin.math.*
@RunWith(Parameterized::class)
class SpectralOracleTest(private val path: String,private val row: JsonObject) {
    @Test fun matchesOracle() {
        val r=OracleSupport.analyze(path,PcmAnalyzer.SPECTRAL)
        val result=doubleArrayOf(r.spectral_centroid,r.spectral_rolloff,r.spectral_flatness,r.spectral_flux)
        val errors=linkedMapOf<String,Double>()
        for((i,key) in listOf("spectral_centroid","spectral_rolloff","spectral_flatness","spectral_flux").withIndex()) {
            val want=OracleSupport.number(row,key);val got=result[i]
            val error=if(got==want || got.isNaN() && want.isNaN()) 0.0 else abs(got-want)
            val measure=if(error==0.0) 0.0 else if(!want.isFinite() || !got.isFinite()) Double.POSITIVE_INFINITY else if(want!=0.0) error/abs(want)*100 else error
            errors[key]=measure
            if(measure > (if(want==0.0) 0.0 else .5) || measure.isNaN()) errors["FAIL:$key"]=measure
        }
        synchronized(SpectralOracleTest::class.java) {
            report.parentFile.mkdirs();report.appendText(path+"\t"+errors.entries.joinToString("\t") { "${it.key}=${it.value}" }+"\n")
        }
        assertTrue("$path: $errors",errors.keys.none { it.startsWith("FAIL:") })
    }
    companion object {
        private val report=File("build/reports/spectral-errors.tsv")
        @JvmStatic @BeforeClass fun resetReport() { report.delete() }
        @JvmStatic @Parameterized.Parameters(name="{0}") fun files() =
            OracleSupport.rows().map { arrayOf<Any>(it["file"].asString,it) }
    }
}
