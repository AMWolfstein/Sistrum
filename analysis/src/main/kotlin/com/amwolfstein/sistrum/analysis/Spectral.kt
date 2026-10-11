// SPDX-License-Identifier: GPL-3.0-or-later
// Port of FFmpeg 8.1.2 libavfilter/af_aspectralstats.c and window_func.h (LGPL-2.1-or-later).
// Copyright (c) 2021 Paul B Mahol. Mathematical FFT is expressed as radix-2 butterflies on the JVM.
package com.amwolfstein.sistrum.analysis
import java.io.Serializable
import kotlin.math.*

internal class Spectral(private val rate: Int): Serializable {
    private val window = FloatArray(2048)
    private val hann = FloatArray(2048) { (.5 * (1 - cos(2*PI*it/2047))).toFloat() }
    private val real = FloatArray(2048)
    private val imag = FloatArray(2048)
    private val previous = FloatArray(1024)
    private val magnitude = FloatArray(1024)
    private val cosine = FloatArray(1024) { cos(-2*PI*it/2048).toFloat() }
    private val sine = FloatArray(1024) { sin(-2*PI*it/2048).toFloat() }
    private var fill = 0
    private var count = 0L
    private var centroid = 0.0
    private var rolloff = 0.0
    private var flatness = 0.0
    private var flux = 0.0
    fun put(value: Float) {
        window[1024+fill] = value
        if (++fill == 1024) { transform(); fill = 0; window.copyInto(window,0,1024,2048) }
    }
    fun finish() {
        if(fill != 0) { window.fill(0f,1024+fill,2048);transform();fill=0 }
    }
    fun result() = doubleArrayOf(centroid/count,rolloff/count,flatness/count,flux/count)
    private fun transform() {
        var i = 0
        while (i < 2048) {
            val j = Integer.reverse(i) ushr 21
            real[j] = window[i] * hann[i]; imag[j] = 0f;i++
        }
        var size = 2
        while(size <= 2048) {
            val half=size/2;val step=2048/size
            var start=0
            while(start<2048) {
                var j=0
                while(j<half) {
                    val a=start+j;val b=a+half;val t=j*step
                    val re=real[b]*cosine[t]-imag[b]*sine[t]
                    val im=real[b]*sine[t]+imag[b]*cosine[t]
                    real[b]=real[a]-re;imag[b]=imag[a]-im
                    real[a]+=re;imag[a]+=im;j++
                }
                start+=size
            }
            size*=2
        }
        i=0
        while(i<1024) { magnitude[i]=hypot((real[i]/2048f).toDouble(),(imag[i]/2048f).toDouble()).toFloat();i++ }
        val scale=(rate/2)/1024f
        var numerator=0f;var denominator=0f;var logSum=0f;var flatSum=0f;var fluxSum=0f
        i=0
        while(i<1024) {
            val v=magnitude[i]
            numerator+=v*i*scale;denominator+=v
            val positive=EPS+v;logSum+=ln(positive.toDouble()).toFloat();flatSum+=positive
            val delta=v-previous[i];fluxSum+=delta*delta
            previous[i]=v;i++
        }
        var accumulated=0f;var index=0;i=0
        val threshold=denominator*.85f
        while(i<1024) { accumulated+=magnitude[i];if(accumulated>=threshold){index=i;break};i++ }
        centroid+=metadata(if(denominator<=EPS) 1f else numerator/denominator)
        rolloff+=metadata(index*scale)
        val flatMean=flatSum/1024
        flatness+=metadata(if(flatMean<=EPS) 0f else exp((logSum/1024).toDouble()).toFloat()/flatMean)
        flux+=metadata(sqrt(fluxSum.toDouble()).toFloat())
        count++
    }
    companion object {
        private const val EPS=1.1920928955078125e-7f
        /** aspectralstats prints C %g: six significant decimal digits. */
        private fun metadata(value: Float): Double {
            if(value==0f || !value.isFinite()) return value.toDouble()
            val factor=10.0.pow(5-floor(log10(abs(value.toDouble()))))
            return Math.rint(value.toDouble()*factor)/factor
        }
    }
}
