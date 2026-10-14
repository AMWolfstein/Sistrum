@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.amwolfstein.sistrum.player.decoders.kotlin

import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.ParserException
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.media3.test.utils.FakeExtractorInput
import com.amwolfstein.sistrum.codecs.audio.Buffer
import com.amwolfstein.sistrum.codecs.codec.wavpack.ErrorCode
import com.amwolfstein.sistrum.codecs.codec.wavpack.WavPackException
import com.amwolfstein.sistrum.codecs.container.RandomAccessSource
import com.amwolfstein.sistrum.player.decoders.DecoderProvider
import com.amwolfstein.sistrum.player.decoders.FormatKey
import com.sun.management.ThreadMXBean
import org.junit.Assert.*
import org.junit.Test
import java.lang.management.ManagementFactory
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

/**
 * T002, tests first: reflection keeps this file compilable before T003 exists.
 * Only an absent production class becomes an assertion; runtime errors propagate.
 * The small API exercised by the bridge is recorded in decision D-12.
 */
class KotlinDecodingExtractorTest {
    @Test
    fun sniffAcceptsMagicWithoutOpeningOrDecodingTheStream() {
        val fake = FakeStream()
        val extractor = adapter(fake)
        val input = input(MAGIC + byteArrayOf(99, 12, 0))
        assertTrue(extractor.sniff(input))
        assertEquals(0L, input.position)
        input.resetPeekPosition()
        assertTrue(extractor.sniff(input))
        assertEquals(0, fake.opens)
        assertEquals(0, fake.blocks)
    }

    @Test
    fun sniffRejectsWrongMagicAndEveryTruncatedPrefix() {
        val fake = FakeStream()
        val extractor = adapter(fake)
        for (length in 0 until MAGIC.size) {
            val input = input(MAGIC.copyOf(length))
            assertFalse("truncated magic: $length", extractor.sniff(input))
            assertEquals(0L, input.position)
        }
        for (index in MAGIC.indices) {
            val wrong = MAGIC.copyOf()
            wrong[index] = (wrong[index].toInt() xor 1).toByte()
            assertFalse("wrong magic byte: $index", extractor.sniff(input(wrong)))
        }
        assertEquals(0, fake.opens)
        assertEquals(0, fake.blocks)
    }

    @Test fun publishes16BitPcm() = checkFormat(C.ENCODING_PCM_16BIT, 16, 44100, 2)
    @Test fun publishes24BitPcm() = checkFormat(C.ENCODING_PCM_24BIT, 24, 96000, 1)
    @Test fun publishes32BitPcm() = checkFormat(C.ENCODING_PCM_32BIT, 32, 192000, 2)
    @Test fun publishesFloatPcm() = checkFormat(C.ENCODING_PCM_FLOAT, 32, 48000, 2)

    @Test
    fun durationUsesExactLongSampleCountAndIntegerMicroseconds() {
        val fake = FakeStream(totalSamples = 4_294_967_311L, rate = 44100)
        val session = session(fake)
        val map = session.output.seekMap!!
        assertEquals(4_294_967_311L * 1_000_000L / 44100, map.durationUs)
        assertTrue(map.isSeekable)
        assertEquals(0, session.output.track.format!!.encoderDelay)
        assertEquals(0, session.output.track.format!!.encoderPadding)
    }

    @Test
    fun seekMapAndDecodeLandOnTheExactRequestedSample() {
        val fake = FakeStream(totalSamples = 20000, rate = 44100)
        val session = session(fake)
        val map = session.output.seekMap!!
        for (timeUs in longArrayOf(0, 23, 123457, 300001)) {
            val sample = timeUs * 44100 / 1_000_000
            val points = map.getSeekPoints(timeUs)
            assertEquals(sample * 1_000_000 / 44100, points.first.timeUs)
            session.extractor.seek(points.first.position, timeUs)
            session.output.track.reset()
            assertEquals(Extractor.RESULT_CONTINUE, session.extractor.read(session.input, session.holder))
            assertEquals("seekSample target", sample, fake.lastSeek)
            assertEquals(sample * 1_000_000 / 44100, session.output.track.times[0])
            assertArrayEquals(pcm(fake, sample, BLOCK_FRAMES), session.output.track.bytes())
        }
        // Rewind after a forward seek must reset the decode position as well.
        val start = map.getSeekPoints(0).first
        session.extractor.seek(start.position, 0)
        session.output.track.reset()
        session.extractor.read(session.input, session.holder)
        assertEquals(0L, fake.lastSeek)
        assertArrayEquals(pcm(fake, 0, BLOCK_FRAMES), session.output.track.bytes())
    }

    @Test
    fun emitsExactFramesAndPcmWithNoTailPaddingOrDuplicateEof() {
        // Two full blocks plus a short tail; unused capacity contains sentinel PCM.
        val fake = FakeStream(totalSamples = 131, channels = 2, bits = 24,
            encoding = C.ENCODING_PCM_24BIT)
        val session = session(fake)
        drain(session)
        val track = session.output.track
        assertEquals(3, track.metadataCount)
        assertArrayEquals(intArrayOf(64 * 6, 64 * 6, 3 * 6), track.sizes.copyOf(3))
        assertArrayEquals(longArrayOf(0, 64L * 1_000_000 / fake.rate,
            128L * 1_000_000 / fake.rate), track.times.copyOf(3))
        assertArrayEquals(pcm(fake, 0, 131), track.bytes())
        assertEquals(131L, track.byteCount.toLong() / 6)
        for (i in 0 until track.metadataCount) {
            assertEquals(C.BUFFER_FLAG_KEY_FRAME, track.flags[i])
            assertEquals(0, track.offsets[i])
        }
        assertEquals(Extractor.RESULT_END_OF_INPUT,
            session.extractor.read(session.input, session.holder))
        assertEquals(3, track.metadataCount)
        assertEquals(131 * 6, track.byteCount)
        assertEquals(0, track.format!!.encoderDelay)
        assertEquals(0, track.format!!.encoderPadding)
    }

    @Test
    fun typedRefusalReachesProviderAndTheNormalParserErrorPath() {
        val refusal = WavPackException(ErrorCode.UNSUPPORTED, "test unsupported coding mode")
        val fake = FakeStream(refused = refusal)
        val extractor = adapter(fake)
        val companion = requiredClass("KotlinDecodingExtractor").getDeclaredField("Companion")
            .also { it.isAccessible = true }.get(null)
        val method = companion.javaClass.getDeclaredMethod("refusal", ByteArray::class.java,
            Function1::class.java).also { it.isAccessible = true }
        val provider = object : DecoderProvider {
            override val id = "test-kotlin-stream"
            override fun isAvailable(key: FormatKey) = true
            override fun extractors(): List<() -> Extractor> = listOf({ adapter(fake) })
            override val refusalHeaderBytes = MAGIC.size
            override fun refusal(header: ByteArray): String? =
                invoke { method.invoke(companion, header, factory(fake)) } as String?
        }
        assertEquals(MAGIC.size, provider.refusalHeaderBytes)
        assertEquals(refusal.message, provider.refusal(MAGIC))
        assertNull(provider.refusal(byteArrayOf(0, 0, 0, 0)))
        assertNull(object : DecoderProvider by provider {
            override fun refusal(header: ByteArray): String? =
                invoke { method.invoke(companion, header, factory(FakeStream())) } as String?
        }.refusal(MAGIC))
        val output = RecordingOutput()
        extractor.init(output)
        val error = assertThrows(ParserException::class.java) {
            extractor.read(input(MAGIC), PositionHolder())
        }
        assertTrue(error.message!!.contains(refusal.message!!))
        assertSame("preserve the typed refusal", refusal, error.cause)
        assertEquals(ErrorCode.UNSUPPORTED, (error.cause as WavPackException).code)
        assertEquals(0, fake.blocks)
        assertEquals(0, output.track.byteCount)
        assertEquals(0, output.track.metadataCount)
    }

    @Test
    fun steadyStateReadAllocatesZeroBytesAcross512Blocks() {
        val fake = FakeStream(totalSamples = 10_000_000)
        val session = session(fake, capture = false)
        val bean = ManagementFactory.getThreadMXBean() as ThreadMXBean
        assertTrue("host JVM must support allocated-byte measurement", bean.isThreadAllocatedMemorySupported)
        bean.isThreadAllocatedMemoryEnabled = true
        val thread = Thread.currentThread().id
        // All reflection, proxy construction, output setup and JIT warm-up are outside the window.
        repeat(20_000) { session.extractor.read(session.input, session.holder) }
        repeat(10) { bean.getThreadAllocatedBytes(thread) }
        val blocksBefore = fake.blocks
        val bytesBefore = session.output.track.byteCount
        val before = bean.getThreadAllocatedBytes(thread)
        var i = 0
        while (i < 512) {
            session.extractor.read(session.input, session.holder)
            i++
        }
        val allocated = bean.getThreadAllocatedBytes(thread) - before
        assertEquals("measured real decode blocks", 512, fake.blocks - blocksBefore)
        assertEquals(512 * BLOCK_FRAMES * 4, session.output.track.byteCount - bytesBefore)
        assertEquals("adapter + fake + output must reuse their buffers", 0L, allocated)
        // Positive control prevents a disabled/broken counter from reporting a false zero.
        val controlBefore = bean.getThreadAllocatedBytes(thread)
        repeat(512) { allocationEscape = ByteArray(1024) }
        assertTrue(bean.getThreadAllocatedBytes(thread) - controlBefore >= 512L * 1024)
    }

    private fun checkFormat(encoding: Int, bits: Int, rate: Int, channels: Int) {
        val fake = FakeStream(encoding = encoding, bits = bits, rate = rate, channels = channels)
        val session = session(fake)
        val format = session.output.track.format!!
        assertEquals(MimeTypes.AUDIO_RAW, format.sampleMimeType)
        assertEquals(encoding, format.pcmEncoding)
        assertEquals(rate, format.sampleRate)
        assertEquals(channels, format.channelCount)
        assertEquals(0, format.encoderDelay)
        assertEquals(0, format.encoderPadding)
        assertEquals(listOf(C.TRACK_TYPE_AUDIO), session.output.trackTypes)
        assertEquals(1, session.output.endTracksCount)
        drain(session)
        assertArrayEquals(pcm(fake, 0, fake.totalSamples.toInt()), session.output.track.bytes())
    }

    private fun session(fake: FakeStream, capture: Boolean = true): Session {
        val extractor = adapter(fake)
        val output = RecordingOutput(capture)
        val session = Session(extractor, input(MAGIC), output, PositionHolder())
        extractor.init(output)
        assertEquals(Extractor.RESULT_CONTINUE, extractor.read(session.input, session.holder))
        assertNotNull("format before the first sample", output.track.format)
        assertNotNull("seek map before decoding", output.seekMap)
        return session
    }

    private fun drain(session: Session) {
        var reads = 0
        while (true) {
            val result = session.extractor.read(session.input, session.holder)
            if (result == Extractor.RESULT_END_OF_INPUT) break
            assertEquals(Extractor.RESULT_CONTINUE, result)
            assertTrue("finite fake must reach EOF", ++reads < 100)
        }
    }

    private fun adapter(fake: FakeStream): Extractor {
        val type = requiredClass("KotlinDecodingExtractor")
        val constructor = type.getDeclaredConstructor(Function1::class.java, Function1::class.java)
            .also { it.isAccessible = true }
        val sniff: (ByteArray) -> Boolean = { header ->
            header.size >= MAGIC.size && MAGIC.indices.all { header[it] == MAGIC[it] }
        }
        return invoke { constructor.newInstance(sniff, factory(fake)) } as Extractor
    }

    private fun factory(fake: FakeStream): (RandomAccessSource) -> Any {
        val streamType = requiredClass("CodecStream")
        assertTrue("CodecStream must remain a pluggable interface", streamType.isInterface)
        val infoType = requiredClass("CodecStream\$Info")
        val info = infoType.getDeclaredConstructor(Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Long::class.javaPrimitiveType)
            .also { it.isAccessible = true }.newInstance(fake.rate, fake.channels,
                fake.encoding, fake.totalSamples)
        val stream = Proxy.newProxyInstance(streamType.classLoader, arrayOf(streamType)) { _, method, args ->
            when (method.name) {
                "getInfo" -> info
                "getRefusal" -> fake.refused
                "decodeBlock" -> fake.decodeBlock()
                "seekSample" -> { fake.seekSample(args!![0] as Long); null }
                "close" -> null
                else -> throw AssertionError("Unexpected CodecStream method: ${method.name}")
            }
        }
        return { source ->
            fake.opens++
            val header = java.nio.ByteBuffer.allocate(MAGIC.size)
            val count = source.read(0, header)
            assertEquals(MAGIC.size, count)
            if (!header.array().contentEquals(MAGIC)) {
                throw WavPackException(ErrorCode.MALFORMED, "wrong test magic")
            }
            stream
        }
    }

    private fun requiredClass(name: String): Class<*> = try {
        Class.forName("com.amwolfstein.sistrum.player.decoders.kotlin.$name")
    } catch (_: ClassNotFoundException) {
        throw AssertionError("T003 has not implemented $name (T002 contract)")
    }

    private fun <T> invoke(block: () -> T): T = try { block() } catch (error: InvocationTargetException) {
        throw error.targetException
    }

    private fun input(bytes: ByteArray) = FakeExtractorInput.Builder().setData(bytes).build()

    private fun pcm(fake: FakeStream, start: Long, frames: Int): ByteArray =
        ByteArray(frames * fake.channels * (fake.bits / 8)) { index ->
            val sampleIndex = index / (fake.bits / 8)
            val value = fake.value(start + sampleIndex / fake.channels, sampleIndex % fake.channels)
            (value ushr (8 * (index % (fake.bits / 8)))).toByte()
        }

    private data class Session(val extractor: Extractor, val input: FakeExtractorInput,
        val output: RecordingOutput, val holder: PositionHolder)

    private class FakeStream(val rate: Int = 48000, val channels: Int = 2,
        val encoding: Int = C.ENCODING_PCM_16BIT, val bits: Int = 16,
        val totalSamples: Long = 131, val refused: WavPackException? = null) {
        val buffer = Buffer(channels, bits, BLOCK_FRAMES)
        private val framesField = Buffer::class.java.getDeclaredField("frames").also { it.isAccessible = true }
        private val positionField = Buffer::class.java.getDeclaredField("position").also { it.isAccessible = true }
        private var position = 0L
        var opens = 0
        var blocks = 0
        var lastSeek = -1L

        fun value(frame: Long, channel: Int): Int = if (encoding == C.ENCODING_PCM_FLOAT) {
            (if (channel == 0) 0.25f else -0.5f).toRawBits()
        } else {
            val value = ((frame * channels + channel) % 16384).toInt() + 1
            if (channel == 0) value else -value
        }

        fun decodeBlock(): Buffer? {
            check(refused == null) { "refused streams must not decode" }
            if (position >= totalSamples) return null
            val frames = minOf(BLOCK_FRAMES.toLong(), totalSamples - position).toInt()
            java.util.Arrays.fill(buffer.samples, 0x5a5a5a5a)
            var i = 0
            while (i < frames * channels) {
                buffer.samples[i] = value(position + i / channels, i % channels)
                i++
            }
            framesField.setInt(buffer, frames)
            positionField.setLong(buffer, position)
            position += frames
            blocks++
            return buffer
        }

        fun seekSample(sample: Long) { lastSeek = sample; position = sample }
    }

    private class RecordingOutput(capture: Boolean = true) : ExtractorOutput {
        val track = RecordingTrack(capture)
        val trackTypes = mutableListOf<Int>()
        var endTracksCount = 0
        var seekMap: SeekMap? = null
        override fun track(id: Int, type: Int): TrackOutput { trackTypes.add(type); return track }
        override fun endTracks() { endTracksCount++ }
        override fun seekMap(seekMap: SeekMap) { this.seekMap = seekMap }
    }

    /** No collection growth, copies or boxed counters in the measured read path. */
    private class RecordingTrack(private val capture: Boolean) : TrackOutput {
        var format: Format? = null
        private val pcm = ByteArray(8192)
        val times = LongArray(64)
        val sizes = IntArray(64)
        val flags = IntArray(64)
        val offsets = IntArray(64)
        var byteCount = 0
        var metadataCount = 0
        override fun format(format: Format) { this.format = format }
        override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
            if (capture) data.readBytes(pcm, byteCount, length) else data.skipBytes(length)
            byteCount += length
        }
        override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean,
            sampleDataPart: Int): Int = throw AssertionError("PCM must come from the decoded reusable buffer")
        override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int,
            cryptoData: TrackOutput.CryptoData?) {
            if (capture) {
                times[metadataCount] = timeUs
                sizes[metadataCount] = size
                this.flags[metadataCount] = flags
                offsets[metadataCount] = offset
            }
            metadataCount++
        }
        fun reset() { byteCount = 0; metadataCount = 0 }
        fun bytes() = pcm.copyOf(byteCount)
    }

    private companion object {
        val MAGIC = byteArrayOf(0x53, 0x49, 0x53, 0x54)
        const val BLOCK_FRAMES = 64
        @Volatile var allocationEscape: ByteArray? = null
    }
}
