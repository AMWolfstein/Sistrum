// SPDX-License-Identifier: GPL-3.0-or-later
package com.amwolfstein.sistrum.dsp
import com.google.gson.Gson
import com.amwolfstein.sistrum.dsp.resample.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ResamplerTest {
    class Vector { var Profile=""; var InRate=0; var OutRate=0; var Channels=0; var Offset=0; lateinit var Input:Array<FloatArray>; lateinit var Output:Array<FloatArray> }
    @Test fun goOracleAndChunking() {
        val vectors=Gson().fromJson(File(System.getProperty("dsp.repo"),"docs/dsp/resample-oracle.json").readText(),Array<Vector>::class.java)
        for(v in vectors) {
          var baseline: Array<FloatArray>? = null
          for(chunk in intArrayOf(1,31,513)) {
            val r=Resampler(v.InRate,v.OutRate,v.Channels,Profile.parse(v.Profile))
            r.reset(r.offsetFor(v.Offset.toLong()))
            val dst=Array(v.Channels){FloatArray(37)}
            val actual=Array(v.Channels){FloatArray(v.Output[0].size)}
            var at=0; var out=0
            fun compare(n:Int) {
                for(c in dst.indices) for(i in 0 until n) assertEquals("${v.Profile} ${v.InRate}->${v.OutRate} ch=$c frame=${out+i} chunk=$chunk",v.Output[c][out+i].toDouble(),dst[c][i].toDouble(),2e-7)
                for(c in dst.indices) System.arraycopy(dst[c],0,actual[c],out,n)
                out+=n
            }
            while(at<v.Input[0].size) {
                val n=r.process(dst,v.Input,at,minOf(chunk,v.Input[0].size-at));at+=r.consumed;compare(n)
            }
            while(true) { val n=r.drain(dst);if(n==0)break;compare(n) }
            assertEquals(v.Output[0].size,out)
            if(baseline!=null) for(c in actual.indices) assertArrayEquals("Bit-exact chunking",baseline[c],actual[c],0f)
            baseline=actual
          }
        }
    }
    @Test fun anchorsAndBounds() {
        val r=Resampler(48000,44100,1)
        for(pos in -1000L..1000L) { val out=r.outputPosition(pos); assertTrue(out*160>=pos*147); assertTrue((out-1)*160<pos*147); assertEquals((out*160-pos*147).toInt(),r.offsetFor(pos)) }
        assertEquals(-1L,Resampler.outputLen(-1,44100,48000))
        assertEquals(0,r.drain(arrayOf(FloatArray(16))))
        assertThrows(IllegalStateException::class.java){r.process(arrayOf(FloatArray(1)),arrayOf(FloatArray(1)))}
        assertThrows(IllegalArgumentException::class.java){Resampler(48000,48000,1)}
        assertThrows(IllegalArgumentException::class.java){Resampler(1,Int.MAX_VALUE,1)}
        assertEquals("resample-hq-1",Profile.HQ.version)
    }
}
