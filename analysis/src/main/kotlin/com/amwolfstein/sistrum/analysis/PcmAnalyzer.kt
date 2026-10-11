// SPDX-License-Identifier: GPL-3.0-only
// Airmedy feature aggregation port: 7d3a08d0eaa2c88d3518f19665eaffc4762dd6a1, GPL-3.0.
// astats statistics: FFmpeg 8.1.2, LGPL-2.1-or-later, Copyright (c) 2012 Paul B Mahol.
package com.amwolfstein.sistrum.analysis
import java.io.*
import kotlin.math.*

data class AnalysisResult(
    val loudness_lufs: Double, val loudness_range: Double, val true_peak: Double,
    val rms: Double, val crest: Double, val spectral_centroid: Double = 0.0,
    val spectral_rolloff: Double = 0.0, val spectral_flatness: Double = 0.0,
    val spectral_flux: Double = 0.0, val zcr: Double, val tempo: Double = 0.0,
    val onset_variance: Double = 0.0
): Serializable {
    fun rawFeatures() = RawFeatures(rms,spectral_centroid,spectral_flux,tempo,crest,onset_variance,loudness_range)
}

/**
 * Incremental analyzer for interleaved PCM in FFmpeg's default channel order.
 * Format is fixed for a track. All DSP storage is allocated in the constructor.
 * Calls must be serialized. Cancellation is observed before a block, leaving it unconsumed.
 * Checkpoints contain bounded DSP histories, not the full PCM track. Resume at exactly the next input frame.
 */
class PcmAnalyzer(val channels: Int, val sampleRate: Int,
                  private val groups: Int = ALL): Serializable {
    init { require(channels in 1..9 && sampleRate in 8000..2822400); require(groups > 0 && groups and ALL.inv() == 0) }
    private val loudness = if (groups and LOUDNESS != 0) Loudness(channels,sampleRate) else null
    private val peakResampler = if (groups and LOUDNESS != 0) Resampler(sampleRate,192000,channels) else null
    private val spectral = if (groups and SPECTRAL != 0) Spectral(sampleRate) else null
    private val sums = DoubleArray(channels)
    private var firstPeak = 0.0
    private var samplePeak = 0.0
    private var truePeak = 0.0
    private var metadataPeak = 0.0
    private var firstSign = -1
    private var crossings = 0L
    var frames = 0L; private set
    @Volatile private var cancelled = false
    private var result: AnalysisResult? = null
    fun cancel() { cancelled = true }
    fun clearCancellation() { cancelled = false }
    /** True means the entire block was consumed; false means cancelled with no state change. */
    fun feed(interleaved: FloatArray, channels: Int, sampleRate: Int): Boolean {
        check(result == null) { "Analyzer already finished" }
        require(channels == this.channels && sampleRate == this.sampleRate)
        require(interleaved.size % channels == 0)
        if (cancelled) return false
        var offset = 0
        while (offset < interleaved.size) {
            spectral?.put(interleaved[offset])
            loudness?.frame(interleaved,offset)
            peakResampler?.let { resampler ->
                resampler.put(interleaved,offset)
                while (resampler.next()) {
                    var ch = 0
                    while (ch < min(channels,8)) { truePeak = max(truePeak,abs(resampler.output[ch])); ch++ }
                }
            }
            if (groups and LOUDNESS != 0 && groups and DYNAMICS == 0) {
                var ch = 0
                while (ch < channels) { val magnitude=abs(interleaved[offset+ch].toDouble());if(magnitude>samplePeak) samplePeak=magnitude;ch++ }
            }
            if (groups and DYNAMICS != 0) {
                var ch = 0
                while (ch < channels) {
                    val value = interleaved[offset+ch].toDouble()
                    sums[ch] += value*value
                    if (abs(value) > samplePeak) samplePeak = abs(value)
                    if (ch == 0) {
                        if (abs(value) > firstPeak) firstPeak = abs(value)
                        if (value != 0.0) {
                            val sign = if (value > 0) 1 else -1
                            // FFmpeg FFSIGN(0) = -1, including the initial last_non_zero.
                            if (sign != firstSign) crossings++
                            firstSign = sign
                        }
                    }
                    ch++
                }
            }
            frames++
            // ebur128 only exposes cumulative peaks on complete 100 ms metadata frames.
            if (frames % (sampleRate / 10) == 0L) metadataPeak = truePeak
            offset += channels
        }
        return true
    }
    fun finish(): AnalysisResult {
        result?.let { return it }
        check(frames > 0) { "No PCM frames" }
        var sum = 0.0
        for (s in sums) sum += s
        val peak = roundMetadata(metadataPeak,1000.0)
        val haveMetadata = frames >= sampleRate / 10
        val dynamics = groups and DYNAMICS != 0
        spectral?.finish()
        val sp = spectral?.result() ?: DoubleArray(4)
        val res = AnalysisResult(
            roundMetadata(if (haveMetadata) loudness?.integrated ?: 0.0 else 0.0,1000.0),
            roundMetadata(if (haveMetadata) loudness?.range ?: 0.0 else 0.0,1000.0),
            if (peak > 0) 20*log10(peak) else roundMetadata(db(samplePeak),1e6),
            if (dynamics) roundMetadata(db(sqrt(sum/(frames*channels))),1e6) else 0.0,
            if (dynamics) roundMetadata(if(sums[0]!=0.0) firstPeak/sqrt(sums[0]/frames) else 1.0,1e6) else 0.0,
            spectral_centroid = sp[0], spectral_rolloff = sp[1],
            spectral_flatness = sp[2], spectral_flux = sp[3],
            zcr = if(dynamics) roundMetadata(crossings.toDouble()/frames,1e6) else 0.0
        )
        result = res
        return res
    }
    /** Trusted, same-version local checkpoint. The caller records the decoded PCM cursor. */
    fun checkpoint(): ByteArray {
        val buffer = ByteArrayOutputStream()
        checkpoint(buffer)
        return buffer.toByteArray()
    }
    /** Stream variant avoids a second full-state byte array; the caller owns the stream. */
    fun checkpoint(output: OutputStream) {
        val objects = ObjectOutputStream(output)
        objects.writeInt(CHECKPOINT_VERSION); objects.writeObject(this); objects.flush()
    }
    companion object {
        const val DYNAMICS = 1
        const val LOUDNESS = 2
        const val SPECTRAL = 4
        const val ALL = DYNAMICS or LOUDNESS or SPECTRAL
        private const val CHECKPOINT_VERSION = 2
        fun resume(checkpoint: ByteArray): PcmAnalyzer = resume(ByteArrayInputStream(checkpoint))
        /** The caller owns the stream. */
        fun resume(input: InputStream): PcmAnalyzer {
            val objects = ObjectInputStream(input)
            require(objects.readInt() == CHECKPOINT_VERSION) { "Unsupported checkpoint version" }
            return (objects.readObject() as PcmAnalyzer).also { it.clearCancellation() }
        }
        private fun db(x: Double) = 20 * log10(x)
        private fun roundMetadata(x: Double, scale: Double) = if(x.isFinite()) Math.rint(x*scale)/scale else x
    }
}
