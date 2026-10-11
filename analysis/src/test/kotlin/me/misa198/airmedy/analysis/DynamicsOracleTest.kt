// SPDX-License-Identifier: GPL-3.0-or-later
package me.misa198.airmedy.analysis
import org.junit.Test
import org.junit.BeforeClass
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import com.google.gson.JsonObject
import java.io.File
import kotlin.math.*
@RunWith(Parameterized::class)
class DynamicsOracleTest(private val path: String,private val row: JsonObject) {
    @Test fun matchesOracle() {
        val r=OracleSupport.analyze(path)
        val errors=linkedMapOf<String,Double>()
        for((key,got) in mapOf("loudness_lufs" to r.loudness_lufs,"loudness_range" to r.loudness_range,
            "true_peak" to r.true_peak,"rms" to r.rms,"crest" to r.crest,"zcr" to r.zcr)) {
            val want=OracleSupport.number(row,key)
            val error=if(got==want || got.isNaN() && want.isNaN()) 0.0 else abs(got-want)
            val relative=key=="crest" || key=="zcr"
            val measure=if(error==0.0) 0.0 else if(!want.isFinite() || !got.isFinite()) Double.POSITIVE_INFINITY else if(relative && want!=0.0) error/abs(want)*100 else error
            errors[key]=measure
            val tolerance=when(key) { "loudness_lufs","loudness_range","true_peak" -> .1;"rms" -> .05;else -> if(want==0.0) 0.0 else .5 }
            // Collect every feature before asserting, so each attempt gives a complete report.
            if(measure>tolerance || measure.isNaN()) errors["FAIL:$key"]=measure
        }
        synchronized(DynamicsOracleTest::class.java) {
            report.parentFile.mkdirs(); report.appendText(path+"\t"+errors.entries.joinToString("\t") { "${it.key}=${it.value}" }+"\n")
        }
        assertTrue("$path: $errors",errors.keys.none { it.startsWith("FAIL:") })
    }
    companion object {
        private val report=File("build/reports/dynamics-errors.tsv")
        @JvmStatic @BeforeClass fun resetReport() { report.delete() }
        @JvmStatic @Parameterized.Parameters(name="{0}") fun files(): List<Array<Any>> {
            return OracleSupport.rows().map { arrayOf<Any>(it["file"].asString,it) }
        }
    }
}
