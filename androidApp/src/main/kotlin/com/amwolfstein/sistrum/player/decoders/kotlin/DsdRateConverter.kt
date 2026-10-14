package com.amwolfstein.sistrum.player.decoders.kotlin

import com.amwolfstein.sistrum.codecs.audio.FloatBuffer
import com.amwolfstein.sistrum.dsp.resample.Profile
import com.amwolfstein.sistrum.dsp.resample.Resampler

/** HQ conversion with bounded storage, delay compensation and FIR history restored on seeks. */
internal class DsdRateConverter(private val source: FloatCodecStream) : FloatCodecStream {
    private val rate = if (source.info.sampleRate % 44100 == 0) 176400 else 192000
    private val converter = Resampler(source.info.sampleRate, rate, source.info.channels, Profile.HQ)
    override val info = source.info.copy(sampleRate = rate,
        totalSamples = Resampler.outputLen(source.info.totalSamples, source.info.sampleRate, rate))
    private val output = FloatBuffer(info.channels, 4096)
    private val planarOutput = Array(info.channels) { FloatArray(4096) }
    private var input: FloatBuffer? = null
    private var inputOffset = 0
    private val planarInput = Array(info.channels) { FloatArray(4096) }
    private var position = 0L
    private var discardUntil = 0L
    private var ended = false
    private var discontinuity = false

    override fun decodeFloatBlock(): FloatBuffer? {
        while (position < info.totalSamples) {
            if (input == null && !ended) {
                input = source.decodeFloatBlock()
                inputOffset = 0
                ended = input == null
            }
            val block = input
            val produced = if (block == null) converter.drain(planarOutput) else {
                val count = minOf(4096, block.frames - inputOffset)
                for (ch in 0 until info.channels) for (i in 0 until count)
                    planarInput[ch][i] = block.samples[(inputOffset + i) * info.channels + ch]
                converter.process(planarOutput, planarInput, frames = count).also {
                    inputOffset += converter.consumed
                    if (inputOffset == block.frames) input = null
                }
            }
            if (produced == 0) {
                if (ended) return null
                continue
            }
            val count = minOf(produced.toLong(), info.totalSamples - position).toInt()
            val skip = (discardUntil - position).coerceIn(0, count.toLong()).toInt()
            output.position = position + skip
            output.frames = count - skip
            position += count
            if (output.frames == 0) continue
            for (ch in 0 until info.channels) for (i in 0 until output.frames)
                output.samples[i * info.channels + ch] = planarOutput[ch][i + skip]
            output.discontinuity = discontinuity
            discontinuity = false
            return output
        }
        return null
    }

    override fun seekSample(sample: Long) {
        require(sample in 0..info.totalSamples)
        val target = sample * converter.denominator / converter.numerator
        // 4096 input frames exceed HQ FIR history for the supported DSD ratios (2:1 / 4:1).
        val start = (target - 4096).coerceAtLeast(0) / converter.denominator * converter.denominator
        source.seekSample(if (sample == info.totalSamples) source.info.totalSamples else start)
        converter.reset(converter.offsetFor(start))
        input = null
        inputOffset = 0
        ended = false
        position = if (sample == info.totalSamples) sample else converter.outputPosition(start)
        discardUntil = sample
        discontinuity = true
    }
    override fun close() = source.close()
}
