@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.amwolfstein.sistrum.player.decoders.kotlin

import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.ParserException
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.extractor.*
import androidx.media3.test.utils.FakeExtractorInput
import com.amwolfstein.sistrum.codecs.audio.FloatBuffer
import com.amwolfstein.sistrum.codecs.container.dsd.Dsd
import org.junit.Assert.*
import org.junit.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

class ChannelMapperTest {
    @Test fun placementZeroFillsTheMissingCentreWithoutChangingFloatWords() {
        val result = synthetic(5, 0x3b, floatArrayOf(1f, -1f, .5f, .25f, -.25f), 8)
        assertEquals(6, result.format!!.channelCount)
        assertArrayEquals(floatArrayOf(1f, -1f, 0f, .5f, .25f, -.25f), result.floats(), 0f)
    }

    @Test fun stereoDownmixUsesEnergyNormalizedSpeakerGainsAndDropsLfe() {
        // Literal WaxFlow mix-1 gains: FL + C/sqrt(2) + BL/sqrt(2), energy = 2.
        val result = synthetic(6, 0x3f, floatArrayOf(1f, 2f, 3f, 100f, 4f, 5f), 2)
        assertEquals(2, result.format!!.channelCount)
        assertEquals(C.ENCODING_PCM_FLOAT, result.format!!.pcmEncoding)
        assertArrayEquals(floatArrayOf(1f / sqrt(2f) + 3f / 2 + 4f / 2,
            2f / sqrt(2f) + 3f / 2 + 5f / 2), result.floats(), 0.000001f)
        assertTrue(result.floats()[0] > 1f) // Leave limiting to the existing session stage.
    }

    @Test fun monoAndOddDeviceLimitsRetainAllNamedContributions() {
        val samples = floatArrayOf(1f, 2f, 3f)
        val mono = synthetic(3, 7, samples, 1)
        assertEquals(1, mono.format!!.channelCount)
        // Mono row (.707, .707, 1) is divided by sqrt(2).
        assertEquals(.5f + 1f + 3f / sqrt(2f), mono.floats().single(), .000001f)
        val limited = synthetic(6, 0x3f, floatArrayOf(1f, 2f, 3f, 0f, 4f, 5f), 3)
        assertEquals(2, limited.format!!.channelCount)
        assertTrue(limited.floats().all { it > 1f })
    }

    @Test fun everyIntegerDepthConvertsFullScaleCorrectlyAndNativeFloatWordsAreSupported() {
        for ((encoding, scale) in listOf(C.ENCODING_PCM_8BIT to 128,
            C.ENCODING_PCM_16BIT to 32768, C.ENCODING_PCM_24BIT to 8388608,
            C.ENCODING_PCM_32BIT to Int.MIN_VALUE)) {
            val mapper = ChannelMapper(CodecStream.Info(192000, 3, encoding, 1, channelMask = 7), 2)
            val sample = if (encoding == C.ENCODING_PCM_32BIT) scale else -scale
            assertEquals(-1f / sqrt(1.5f), Float.fromBits(mapper.mix(0, 0,
                intArrayOf(sample, 0, 0), null, encoding, 3)), .000001f)
        }
        val mapper = ChannelMapper(CodecStream.Info(48000, 3, C.ENCODING_PCM_FLOAT, 1, channelMask = 7), 2)
        assertEquals(.5f / sqrt(1.5f), Float.fromBits(mapper.mix(0, 0,
            intArrayOf(.5f.toRawBits(), 0, 0), null, C.ENCODING_PCM_FLOAT, 3)), .000001f)
    }

    @Test fun unknownAndPartialMasksRefuseWithTypedCause() {
        for (mask in listOf(0, 3, 0x40003)) {
            val stream = object : FloatCodecStream {
                override val info = CodecStream.Info(48000, 3, C.ENCODING_PCM_FLOAT, 0, channelMask = mask)
                override fun decodeFloatBlock(): FloatBuffer? = throw AssertionError("refused before decode")
                override fun seekSample(sample: Long) = Unit
            }
            val error = assertThrows(ParserException::class.java) {
                extract(KotlinDecodingExtractor({ true }, { stream }), byteArrayOf(0))
            }
            assertFalse(error.contentIsMalformed)
            assertTrue(error.cause is ChannelLayoutException)
            assertTrue(error.message!!.contains("speaker mask"))
        }
    }

    @Test fun dffCorpusPlacementMatchesCanonicalDsfAndStereoMixAtAllRates() {
        val owned = System.getProperty("oracle.owned") ?: "${System.getProperty("user.home")}/.cache/sistrum-waxflow-oracle/owned"
        for (rate in listOf(64, 128, 256)) for (layout in listOf("3ch", "6ch", "6ch-reordered")) {
            val name = "dsd$rate-$layout"
            val root = File(owned, "dsd-multichannel")
            val reference = Dsd.open(ByteBuffer.wrap(File(root, "$name.dsf").readBytes()))
            val canonical = mutableListOf<Float>()
            while (true) {
                val block = reference.decodeBlock() ?: break
                for (i in 0 until block.frames * block.channels) canonical += block.samples[i]
            }
            val data = File(root, "$name.dff").readBytes()
            val placed = extract(KotlinDsdProvider().extractors().single()(), data)
            assertEquals(name, reference.info.channels, placed.format!!.channelCount)
            assertArrayEquals(name, canonical.toFloatArray(), placed.floats(), 0f)
            val extractor = KotlinDsdProvider().extractors().single()() as KotlinDecodingExtractor
            extractor.maxOutputChannels = { 2 }
            val mixed = extract(extractor, data)
            val expected = FloatArray(canonical.size / reference.info.channels * 2)
            // Analytic mix-1 coefficients, rounded once from double precision.
            val frontGain = if (reference.info.channels == 3) 0.816496580927726f else 0.7071067811865476f
            val centreGain = if (reference.info.channels == 3) 0.5773502691896258f else .5f
            for (frame in expected.indices step 2) {
                val at = frame / 2 * reference.info.channels
                expected[frame] = canonical[at] * frontGain + canonical[at + 2] * centreGain
                expected[frame + 1] = canonical[at + 1] * frontGain + canonical[at + 2] * centreGain
                if (reference.info.channels == 6) {
                    expected[frame] += canonical[at + 4] * .5f
                    expected[frame + 1] += canonical[at + 5] * .5f
                }
            }
            assertArrayEquals(name, expected, mixed.floats(), 0f)
            assertEquals(2, mixed.format!!.channelCount)
        }
    }

    @Test fun seekAndEmptyEndPreserveTimestampsAndReusePackingStorage() {
        val info = CodecStream.Info(192000, 3, C.ENCODING_PCM_FLOAT, 3, channelMask = 7)
        val block = FloatBuffer(3, 3).also { it.frames = 3; it.samples.fill(.5f) }
        var position = 0L
        val stream = object : FloatCodecStream {
            override val info = info
            override fun decodeFloatBlock(): FloatBuffer? {
                if (position == 3L) return null
                block.position = position; block.frames = (3 - position).toInt(); position = 3
                return block
            }
            override fun seekSample(sample: Long) { position = sample }
        }
        val e = KotlinDecodingExtractor({ true }, { stream }).also { it.maxOutputChannels = { 2 } }
        val output = Output()
        val input = FakeExtractorInput.Builder().setData(byteArrayOf(0)).build()
        val holder = PositionHolder()
        e.init(output)
        try {
            while (e.read(input, holder) != Extractor.RESULT_END_OF_INPUT) { }
            e.seek(0, 6) // ceil(1 sample / 192 kHz)
            while (e.read(input, holder) != Extractor.RESULT_END_OF_INPUT) { }
            assertEquals(listOf(0L, 5L), output.times)
            assertEquals(5 * 2 * 4, output.bytes.size())
            e.seek(0, 16)
            assertEquals(Extractor.RESULT_END_OF_INPUT, e.read(input, holder))
            assertEquals(2, output.times.size)
        } finally { e.release() }
    }

    @Test fun playbackFactorySuppliesTheLimitAtStreamOpenThroughProviderWrappers() {
        val stream = object : FloatCodecStream {
            override val info = CodecStream.Info(48000, 6, C.ENCODING_PCM_FLOAT, 0, channelMask = 0x3f)
            override fun decodeFloatBlock(): FloatBuffer? = null
            override fun seekSample(sample: Long) = Unit
        }
        val provider = object : com.amwolfstein.sistrum.player.decoders.DecoderProvider {
            override val id = "test-multichannel"
            override fun isAvailable(key: com.amwolfstein.sistrum.player.decoders.FormatKey) = true
            override fun extractors(): List<() -> Extractor> = listOf({
                val delegate = KotlinDecodingExtractor({ true }, { stream })
                object : Extractor by delegate {
                    override fun getUnderlyingImplementation(): Extractor = delegate
                }
            })
        }
        var limit = 8
        val factory = com.amwolfstein.sistrum.player.media3.ProviderExtractorsFactory(
            listOf(provider), object : ExtractorsFactory {
                override fun createExtractors(): Array<Extractor> = emptyArray()
            }, com.amwolfstein.sistrum.player.media3.ProviderRecorder(), { limit })
        val extractor = factory.createExtractors().single()
        limit = 2
        assertEquals(2, extract(extractor, byteArrayOf(0)).format!!.channelCount)
    }

    private fun synthetic(channels: Int, mask: Int, samples: FloatArray, limit: Int): Output {
        var decoded = false
        val stream = object : FloatCodecStream {
            override val info = CodecStream.Info(48000, channels, C.ENCODING_PCM_FLOAT, 1, channelMask = mask)
            override fun decodeFloatBlock() = if (decoded) null else FloatBuffer(channels, 1).also {
                decoded = true; it.frames = 1; samples.copyInto(it.samples)
            }
            override fun seekSample(sample: Long) = Unit
        }
        return extract(KotlinDecodingExtractor({ true }, { stream }).also { it.maxOutputChannels = { limit } }, byteArrayOf(0))
    }

    private fun extract(e: Extractor, data: ByteArray): Output {
        val output = Output()
        val input = FakeExtractorInput.Builder().setData(data).build()
        val holder = PositionHolder()
        e.init(output)
        try { while (e.read(input, holder) != Extractor.RESULT_END_OF_INPUT) { } }
        finally { e.release() }
        return output
    }

    private class Output : ExtractorOutput, TrackOutput {
        var format: Format? = null
        val bytes = ByteArrayOutputStream()
        val times = mutableListOf<Long>()
        private var storage: ByteArray? = null
        fun floats(): FloatArray {
            val buffer = ByteBuffer.wrap(bytes.toByteArray()).order(ByteOrder.LITTLE_ENDIAN)
            return FloatArray(bytes.size() / 4) { buffer.float }
        }
        override fun track(id: Int, type: Int): TrackOutput = this
        override fun endTracks() = Unit
        override fun seekMap(seekMap: SeekMap) = Unit
        override fun format(format: Format) { this.format = format }
        override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
            storage?.let { assertSame("packing storage reused", it, data.data) }; storage = data.data
            bytes.write(data.data, data.position, length); data.skipBytes(length)
        }
        override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean, sampleDataPart: Int): Int =
            throw AssertionError("expected PCM")
        override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?) { times += timeUs }
    }

    companion object {
        @JvmStatic @BeforeClass fun enableParserBoundsChecks() { ParsableByteArray.setShouldEnforceLimitOnLegacyMethods(true) }
        @JvmStatic @AfterClass fun restoreParserBoundsChecks() { ParsableByteArray.setShouldEnforceLimitOnLegacyMethods(null) }
    }
}
