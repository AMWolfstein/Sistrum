// SPDX-License-Identifier: GPL-3.0-or-later
// WaxFlow b7857aff88820ad37936026421d1641e64611dbe, Cole Springer (MIT).
package com.amwolfstein.sistrum.dsp.silence

import kotlin.math.pow

/** Strict peak-band detector, preserving WaxFlow silence-1 and integer ns→frame rounding.
 * Preallocated span storage keeps process/flush allocation-free. Supply capacity for the
 * whole stream (at most ceil(frames/(minFrames+1))); exhaustion throws rather than losing spans.
 * Read endpoints by index without allocation; [spans] makes a snapshot outside processing. */
class Detector(rate: Int, val channels: Int, thresholdDB: Double, minDurationNanos: Long, capacity: Int = 4096) {
    data class Span(val from: Long, val to: Long) { val length get() = to-from }
    val threshold: Float
    val minFrames: Long
    private val starts: LongArray
    private val ends: LongArray
    private var inRun=false
    private var runFrom=0L
    private var flushed=false
    var spanCount=0; private set
    var dropped=0; private set
    var droppedSamples=0L; private set
    var totalSamples=0L; private set
    var samples=0L; private set
    init {
        require(rate>0 && channels>0 && thresholdDB<0 && thresholdDB.isFinite() && minDurationNanos>0 && capacity>=0)
        threshold=10.0.pow(thresholdDB/20).toFloat()
        minFrames=Math.addExact(Math.multiplyExact(minDurationNanos/1_000_000_000,rate.toLong()),
            ((minDurationNanos%1_000_000_000)*rate+999_999_999)/1_000_000_000)
        starts=LongArray(capacity); ends=LongArray(capacity)
    }
    fun process(src: Array<FloatArray>, offset: Int = 0, frames: Int = src[0].size-offset) {
        check(!flushed) { "Process after Flush" }
        require(src.size==channels && offset>=0 && frames>=0)
        for(c in src.indices) require(src[c].size==src[0].size && offset<=src[c].size-frames)
        var i=0
        while(i<frames) {
            var peak=0f; var c=0
            while(c<channels) { var v=src[c][offset+i];if(v<0) v=-v;if(v>peak)peak=v;c++ }
            if(peak<threshold) { if(!inRun){inRun=true;runFrom=samples+i} }
            else if(inRun) close(samples+i)
            i++
        }
        samples+=frames
    }
    private fun close(to: Long) {
        val len=to-runFrom
        if(len<minFrames) { dropped++; droppedSamples+=len }
        else {
            check(spanCount<starts.size) { "Silence span capacity exhausted; allocate for the whole stream" }
            starts[spanCount]=runFrom;ends[spanCount]=to;spanCount++;totalSamples+=len
        }
        inRun=false
    }
    fun flush() { if(flushed)return; if(inRun)close(samples);flushed=true }
    fun from(index: Int): Long { require(index in 0 until spanCount);return starts[index] }
    fun to(index: Int): Long { require(index in 0 until spanCount);return ends[index] }
    fun spans(): List<Span> = List(spanCount){Span(starts[it],ends[it])}
    /** Clears counters/storage ownership for a new stream without allocating. */
    fun reset() { inRun=false;runFrom=0;flushed=false;spanCount=0;dropped=0;droppedSamples=0;totalSamples=0;samples=0 }
    companion object { const val VERSION="silence-1" }
}
