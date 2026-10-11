// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxLabel cfbb45d13135a76f5df6781fdeec75f5cfe63bf9, internal/mp3/mpeg.go.
// Copyright (c) 2026 Cole Springer, MIT; see metadata/THIRD-PARTY-NOTICES.
package me.misa198.airmedy.metadata

internal data class MpegHeader(val version: Int, val layer: Int, val rate: Int, val channels: Int, val bitrate: Int, val samples: Int, val length: Int) {
    val name get() = "MPEG-${if(version == 25) "2.5" else version.toString()} Layer $layer"
}
internal object Mpeg {
    private val bitrates = arrayOf(
        arrayOf(intArrayOf(0,32,64,96,128,160,192,224,256,288,320,352,384,416,448,0),intArrayOf(0,32,48,56,64,80,96,112,128,160,192,224,256,320,384,0),intArrayOf(0,32,40,48,56,64,80,96,112,128,160,192,224,256,320,0)),
        arrayOf(intArrayOf(0,32,48,56,64,80,96,112,128,144,160,176,192,224,256,0),intArrayOf(0,8,16,24,32,40,48,56,64,80,96,112,128,144,160,0),intArrayOf(0,8,16,24,32,40,48,56,64,80,96,112,128,144,160,0)))
    fun header(b: ByteArray): MpegHeader? {
        if (b.size < 4 || b.u(0) != 255 || b.u(1) and 224 != 224) return null
        val vb = (b.u(1) shr 3) and 3
        val lb = (b.u(1) shr 1) and 3
        val bi = (b.u(2) shr 4) and 15
        val ri = (b.u(2) shr 2) and 3
        if (vb == 1 || lb == 0 || bi == 15 || ri == 3) return null
        val version = when(vb) { 3 -> 1; 2 -> 2; else -> 25 }
        val layer = 4-lb
        val rate = intArrayOf(44100,48000,32000)[ri] / when(version) { 1 -> 1; 2 -> 2; else -> 4 }
        val bitrate = bitrates[if(version == 1) 0 else 1][layer-1][bi]*1000
        val samples = if(layer == 1) 384 else if(layer == 3 && version != 1) 576 else 1152
        val pad = b.u(2) shr 1 and 1
        val length = if (bitrate == 0) 0 else if(layer == 1) (12*bitrate/rate+pad)*4 else samples/8*bitrate/rate+pad
        return MpegHeader(version,layer,rate,if(b.u(3) shr 6 == 3) 1 else 2,bitrate,samples,length)
    }
    fun properties(input: Input, start: Long, end: Long, b: Builder, probeStart: Long = start): AudioProperties {
        val audioBytes = end-start
        val probeBytes = end-probeStart
        val first = if (probeBytes >= 4) header(input.bytes(probeStart,4)) else null
        var valid = first != null
        if (first != null && first.length > 0 && probeBytes-first.length >= 4) {
            val next = header(input.bytes(probeStart+first.length,4))
            valid = next != null && next.version == first.version && next.layer == first.layer && next.rate == first.rate
        }
        if (first == null || !valid) {
            if (audioBytes > 0) b.warn("no-audio","no MPEG audio frames found; file may not be audio")
            return AudioProperties(container="MP3",codec="MPEG audio")
        }
        var frames = 0L
        val side = if(first.version == 1) if(first.channels == 1) 17 else 32 else if(first.channels == 1) 9 else 17
        for (offset in listOf(4+side,6+side)) {
            if (probeBytes < offset+12) continue
            val tag = input.bytes(probeStart+offset,4).latin()
            if (tag == "Xing" || tag == "Info") {
                val v = input.bytes(probeStart+offset+4,8)
                if (v.be(0,4) and 1 != 0L) frames = v.be(4,4)
                break
            }
        }
        if (frames == 0L && probeBytes >= 54 && input.bytes(probeStart+36,4).latin() == "VBRI") frames = input.bytes(probeStart+50,4).be(0,4)
        val samples: Long
        val duration: Long
        val bitrate: Int
        if (frames > 0) {
            samples = frames*first.samples
            duration = samplesToNs(samples,first.rate)
            bitrate = averageBitrate(audioBytes,duration)
            if (bitrate < 8000) b.warn("truncated-audio","fewer audio frames than the Xing/Info header declares; file may be truncated")
        } else {
            val seconds = if(first.bitrate > 0) audioBytes.toDouble()*8/first.bitrate else 0.0
            duration = (seconds*1e9).toLong()
            samples = (seconds*first.rate).toLong()
            bitrate = first.bitrate
        }
        return AudioProperties("MP3",if(first.layer == 3) "MP3" else "MP${first.layer}",first.name,first.rate,first.channels,
            totalSamples=samples,durationNs=duration,bitrate=bitrate)
    }
}
internal fun samplesToNs(samples: Long, rate: Int): Long {
    if (rate <= 0 || samples < 0) return 0
    val ns = samples.toDouble()/rate*1e9
    return if (ns < Long.MAX_VALUE.toDouble()) ns.toLong() else 0
}
internal fun averageBitrate(bytes: Long, durationNs: Long): Int {
    if (durationNs <= 0 || bytes <= 0) return 0
    val bitrate = bytes.toDouble()*8/(durationNs.toDouble()/1e9)
    return if (bitrate < Int.MAX_VALUE) bitrate.toInt() else 0
}
