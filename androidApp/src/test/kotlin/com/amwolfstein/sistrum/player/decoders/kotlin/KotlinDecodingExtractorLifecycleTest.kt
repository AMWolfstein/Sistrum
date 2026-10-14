@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.amwolfstein.sistrum.player.decoders.kotlin

import androidx.media3.common.C
import androidx.media3.common.ParserException
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.PositionHolder
import androidx.media3.test.utils.FakeExtractorInput
import androidx.media3.test.utils.FakeExtractorOutput
import com.amwolfstein.sistrum.codecs.codec.wavpack.ErrorCode
import com.amwolfstein.sistrum.codecs.codec.wavpack.WavPackException
import com.amwolfstein.sistrum.codecs.container.RandomAccessSource
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.nio.ByteBuffer

class KotlinDecodingExtractorLifecycleTest {
    private fun input() = FakeExtractorInput.Builder().setData(byteArrayOf(1, 2, 3, 4)).build()
    private class Stream : CodecStream {
        override val info = CodecStream.Info(48000, 2, C.ENCODING_PCM_16BIT, 96000)
        var decoded = 0
        var closed = 0
        var sought = -1L
        override fun decodeBlock(): com.amwolfstein.sistrum.codecs.audio.Buffer? { decoded++; return null }
        override fun seekSample(sample: Long) { sought = sample }
        override fun close() { closed++ }
    }

    @Test fun initialSeekIsAppliedAndSeekingAfterEofResumesTheCodec() {
        val stream = Stream()
        val extractor = KotlinDecodingExtractor({ true }, { stream })
        val input = input()
        val holder = PositionHolder()
        extractor.init(FakeExtractorOutput())
        try {
            extractor.seek(0, 500_000)
            assertEquals(Extractor.RESULT_CONTINUE, extractor.read(input, holder))
            assertEquals(Extractor.RESULT_END_OF_INPUT, extractor.read(input, holder))
            assertEquals(24000L, stream.sought)
            assertEquals(Extractor.RESULT_END_OF_INPUT, extractor.read(input, holder))
            assertEquals(1, stream.decoded)
            extractor.seek(0, 0)
            extractor.read(input, holder)
            assertEquals(0L, stream.sought)
            assertEquals(2, stream.decoded)
        } finally { extractor.release() }
    }

    @Test fun releaseClosesTheCodecAndItsCachedSourceExactlyOnce() {
        val stream = Stream()
        lateinit var source: RandomAccessSource
        val extractor = KotlinDecodingExtractor({ true }, { source = it; stream })
        extractor.init(FakeExtractorOutput())
        extractor.read(input(), PositionHolder())
        source.read(0, ByteBuffer.allocate(4))
        extractor.release()
        extractor.release()
        assertEquals(1, stream.closed)
        assertThrows(IOException::class.java) { source.read(0, ByteBuffer.allocate(1)) }
    }

    @Test fun thrownTypedUnsupportedErrorsAreRefusalsButMalformedHeadersAreNot() {
        val failure = WavPackException(ErrorCode.UNSUPPORTED, "unsupported header mode")
        assertEquals(failure.message, KotlinDecodingExtractor.refusal(byteArrayOf(1)) { throw failure })
        assertNull(KotlinDecodingExtractor.refusal(byteArrayOf(1)) {
            throw WavPackException(ErrorCode.MALFORMED, "truncated header")
        })
        val extractor = KotlinDecodingExtractor({ true }, { throw failure })
        extractor.init(FakeExtractorOutput())
        try {
            val error = assertThrows(ParserException::class.java) { extractor.read(input(), PositionHolder()) }
            assertSame(failure, error.cause)
            assertFalse(error.contentIsMalformed)
            assertEquals(C.DATA_TYPE_MEDIA, error.dataType)
        } finally { extractor.release() }
    }

    @Test fun malformedOpenPreservesItsCauseAndSourceCanBeReleased() {
        val failure = WavPackException(ErrorCode.MALFORMED, "invalid stream header")
        lateinit var source: RandomAccessSource
        val extractor = KotlinDecodingExtractor({ true }, { source = it; throw failure })
        extractor.init(FakeExtractorOutput())
        val error = assertThrows(ParserException::class.java) { extractor.read(input(), PositionHolder()) }
        assertSame(failure, error.cause)
        assertTrue(error.contentIsMalformed)
        extractor.release()
        assertThrows(IOException::class.java) { source.read(0, ByteBuffer.allocate(1)) }
    }
}
