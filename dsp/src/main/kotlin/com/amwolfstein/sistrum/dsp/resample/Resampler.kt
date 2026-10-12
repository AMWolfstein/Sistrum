// SPDX-License-Identifier: GPL-3.0-or-later
// WaxFlow b7857aff88820ad37936026421d1641e64611dbe, Cole Springer (MIT).
package com.amwolfstein.sistrum.dsp.resample

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

enum class Profile(val id: String) {
    HQ("hq"), FAST("fast");
    val version get() = "resample-$id-1"
    companion object { fun parse(name: String): Profile = when(name) { "", "hq" -> HQ; "fast" -> FAST; else -> error("Unknown profile: $name") } }
}

/** Planar, delay-compensated WaxFlow resampler. Caller owns buffers and offsets.
 * Process returns produced frames; [consumed] reports input frames accepted.
 * No objects are created by process/drain/reset. Equal rates are refused. */
class Resampler(inputRate: Int, outputRate: Int, val channels: Int, profile: Profile = Profile.HQ) {
    private class Bank(val l: Int, val m: Int, val taps: Int, val delay: Int, val coef: FloatArray)
    private val bank: Bank
    private val win: Array<FloatArray>
    private var start = 0L
    private var have = 0
    private var inputCount = 0L
    private var outputCount = 0L
    private var off = 0
    private var draining = false
    var consumed = 0; private set
    val numerator get() = bank.l
    val denominator get() = bank.m
    val delayNumerator get() = bank.delay
    val delayDenominator get() = bank.l
    init {
        require(inputRate > 0 && outputRate > 0 && inputRate != outputRate && channels > 0)
        val g = gcd(inputRate, outputRate)
        val l = outputRate / g; val m = inputRate / g
        bank = cache.computeIfAbsent("$l/$m/${profile.id}") { design(l, m, profile) }
        win = Array(channels) { FloatArray(bank.taps - 1 + max(4096, bank.taps)) }
        reset()
    }
    fun reset(phaseOffset: Int = 0) {
        require(phaseOffset >= 0 && phaseOffset < bank.m)
        val hist = bank.taps - 1
        for (c in win.indices) win[c].fill(0f, 0, hist)
        start = -hist.toLong(); have = hist; inputCount = 0; outputCount = 0; off = phaseOffset; draining = false; consumed = 0
    }
    fun outputPosition(inputPosition: Long): Long = ceilDiv(inputPosition * bank.l, bank.m.toLong())
    fun offsetFor(inputPosition: Long): Int = (outputPosition(inputPosition) * bank.m - inputPosition * bank.l).toInt()
    fun process(dst: Array<FloatArray>, src: Array<FloatArray>, srcOffset: Int = 0, frames: Int = src[0].size - srcOffset,
                dstOffset: Int = 0, capacity: Int = dst[0].size - dstOffset): Int {
        check(!draining)
        return run(dst, src, srcOffset, frames, dstOffset, capacity)
    }
    fun drain(dst: Array<FloatArray>, dstOffset: Int = 0, capacity: Int = dst[0].size - dstOffset): Int {
        draining = true
        return run(dst, null, 0, 0, dstOffset, capacity)
    }
    private fun run(dst: Array<FloatArray>, src: Array<FloatArray>?, srcOffset: Int, frames: Int, dstOffset: Int, capacity: Int): Int {
        require(dst.size == channels && (src == null || src.size == channels) && frames >= 0 && capacity >= 0)
        for(c in 0 until channels) {
            require(dstOffset >= 0 && dstOffset <= dst[c].size - capacity)
            if(src != null) require(srcOffset >= 0 && srcOffset <= src[c].size - frames)
        }
        consumed = 0
        var produced = 0
        val b = bank; val t = b.taps
        val limit = if(src == null) ceilDiv(inputCount*b.l-off, b.m.toLong()) else -1L
        while(true) {
            while(produced < capacity) {
                if(limit >= 0 && outputCount >= limit) return produced
                val u = outputCount*b.m+b.delay+off
                val k = u/b.l
                if(k >= start+have) break
                val phase = (u%b.l).toInt()*t
                val base = (k-start).toInt()-(t-1)
                if(channels == 2) {
                    var a0=0f; var a1=0f; var b0=0f; var b1=0f; var j=0
                    while(j < (t and -2)) {
                        val c0=b.coef[phase+j]; val c1=b.coef[phase+j+1]
                        a0+=c0*win[0][base+j]; a1+=c1*win[0][base+j+1]
                        b0+=c0*win[1][base+j]; b1+=c1*win[1][base+j+1]; j+=2
                    }
                    if(j<t) { a0+=b.coef[phase+j]*win[0][base+j]; b0+=b.coef[phase+j]*win[1][base+j] }
                    dst[0][dstOffset+produced]=a0+a1; dst[1][dstOffset+produced]=b0+b1
                } else {
                    for(c in 0 until channels) {
                        var s0=0f; var s1=0f; var s2=0f; var s3=0f; var j=0
                        while(j < (t and -4)) {
                            s0+=b.coef[phase+j]*win[c][base+j]; s1+=b.coef[phase+j+1]*win[c][base+j+1]
                            s2+=b.coef[phase+j+2]*win[c][base+j+2]; s3+=b.coef[phase+j+3]*win[c][base+j+3]; j+=4
                        }
                        while(j<t) { s0+=b.coef[phase+j]*win[c][base+j]; j++ }
                        dst[c][dstOffset+produced]=(s0+s1)+(s2+s3)
                    }
                }
                produced++; outputCount++
            }
            if(produced >= capacity) return produced
            val u = outputCount*b.m+b.delay+off
            val drop = min((u/b.l-(t-1)-start).toInt(), have)
            if(drop > 0) {
                for(c in 0 until channels) System.arraycopy(win[c],drop,win[c],0,have-drop)
                start+=drop; have-=drop
            }
            val free = win[0].size-have
            check(free > 0)
            val take = if(src == null) free else min(free,frames-consumed)
            if(take == 0) return produced
            for(c in 0 until channels) {
                if(src == null) win[c].fill(0f,have,have+take)
                else System.arraycopy(src[c],srcOffset+consumed,win[c],have,take)
            }
            if(src != null) { consumed+=take; inputCount+=take }
            have+=take
        }
    }
    companion object {
        private val cache = ConcurrentHashMap<String,Bank>()
        private fun gcd(a: Int,b: Int): Int { var x=a; var y=b; while(y!=0) { val t=x%y; x=y; y=t }; return x }
        private fun ceilDiv(a: Long,b: Long): Long = a/b+if(a%b>0) 1 else 0
        fun outputLen(n: Long,inputRate: Int,outputRate: Int): Long {
            require(inputRate>0 && outputRate>0)
            if(n<0) return -1
            val g=gcd(inputRate,outputRate)
            return ceilDiv(n*(outputRate/g), (inputRate/g).toLong())
        }
        private fun bessel(x: Double): Double {
            var sum=1.0; var term=1.0; val half=x/2; var k=1
            while(true) { term *= (half/k)*(half/k); sum+=term; if(term<sum*1e-17) return sum; k++ }
        }
        private fun design(l: Int,m: Int,p: Profile): Bank {
            val atten=if(p==Profile.HQ) 115.0 else 72.0
            val pass=if(p==Profile.HQ) .91 else .85; val stop=if(p==Profile.HQ) 1.09 else 1.15
            val cutoff=min(1.0,l.toDouble()/m)/(2*l.toDouble())
            val nf=ceil((atten-7.95)/(2.285*(2*PI*(stop-pass)*cutoff)))+1
            val cap=4 shl 20
            require(nf>0 && nf<=cap && l<=cap) { "Unsupported coefficient bank" }
            var n=max(nf.toInt(),9); if(n%2==0) n++
            val t=(n+l-1)/l; val total=t.toLong()*l
            require(total<=cap)
            val h=DoubleArray(total.toInt()); val d=(n-1)/2
            val beta=.1102*(atten-8.7); val i0=bessel(beta); var sum=0.0
            for(i in 0 until n) {
                val x=(i-d).toDouble(); val pos=x/d
                val w=bessel(beta*sqrt(1-pos*pos))/i0
                val sx=2*cutoff*x
                h[i]=(if(sx==0.0) 1.0 else sin(PI*sx)/(PI*sx))*w; sum+=h[i]
            }
            val scale=l/sum; val coef=FloatArray(h.size)
            for(ph in 0 until l) for(j in 0 until t) coef[ph*t+j]=(h[ph+(t-1-j)*l]*scale).toFloat()
            return Bank(l,m,t,d,coef)
        }
    }
}
