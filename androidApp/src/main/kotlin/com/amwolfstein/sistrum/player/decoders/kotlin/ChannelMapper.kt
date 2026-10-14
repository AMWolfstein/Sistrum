@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.amwolfstein.sistrum.player.decoders.kotlin

import androidx.media3.common.C
import com.amwolfstein.sistrum.dsp.mix.Media3Layout
import com.amwolfstein.sistrum.dsp.mix.Matrix
import com.amwolfstein.sistrum.dsp.mix.Speakers
import java.io.IOException

internal class ChannelLayoutException(message: String) : IOException(message)

/** Placement preserves PCM words; downmix emits float without clipping before the session limiter. */
internal class ChannelMapper(info: CodecStream.Info, maxOutputChannels: Int) {
    private val matrix: Matrix
    val downmix: Boolean
    val channels: Int get() = matrix.outputs
    private val sourceIndices: IntArray

    init {
        if (info.channels !in 1..8 || maxOutputChannels !in 1..8) {
            throw ChannelLayoutException("unsupported channel count ${info.channels}/$maxOutputChannels")
        }
        val mask = info.channelMask ?: Media3Layout.maskForCount(info.channels)
        if (Integer.bitCount(mask) != info.channels || mask == 0 || mask and 0x3ffff.inv() != 0) {
            throw ChannelLayoutException("unsupported speaker mask 0x${mask.toUInt().toString(16)} for ${info.channels} channels")
        }
        val placement = Media3Layout.forMask(mask)
        // The imported matrices support mono/stereo reductions. A smaller multichannel
        // device uses stereo rather than discarding speakers absent from its layout.
        matrix = if (placement.outputs > maxOutputChannels)
            Matrix.forLayouts(mask, if (maxOutputChannels == 1) Speakers.MONO else Speakers.STEREO)
        else placement
        downmix = matrix.sourceMask != matrix.targetMask &&
            (matrix.outputs < info.channels || matrix.sourceMask and matrix.targetMask.inv() != 0)
        sourceIndices = IntArray(channels) { out ->
            (0 until info.channels).firstOrNull { matrix.coefficient(out, it) == 1f } ?: -1
        }
    }

    fun placedIndex(channel: Int): Int = sourceIndices[channel]

    fun mix(channel: Int, frame: Int, integers: IntArray?, floats: FloatArray?, encoding: Int, sourceChannels: Int): Int {
        var sample = 0f
        for (input in 0 until matrix.inputs) {
            val gain = matrix.coefficient(channel, input)
            if (gain == 0f) continue
            val index = frame * sourceChannels + input
            val value = floats?.get(index) ?: when (encoding) {
                C.ENCODING_PCM_FLOAT -> Float.fromBits(checkNotNull(integers)[index])
                C.ENCODING_PCM_8BIT -> checkNotNull(integers)[index] / 128f
                C.ENCODING_PCM_16BIT -> checkNotNull(integers)[index] / 32768f
                C.ENCODING_PCM_24BIT -> checkNotNull(integers)[index] / 8388608f
                C.ENCODING_PCM_32BIT -> checkNotNull(integers)[index] / 2147483648f
                else -> error("PCM encoding validated by extractor")
            }
            sample += value * gain
        }
        return sample.toRawBits()
    }
}
