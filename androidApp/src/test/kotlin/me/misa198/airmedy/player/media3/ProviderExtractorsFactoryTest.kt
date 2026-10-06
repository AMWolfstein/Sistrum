@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package me.misa198.airmedy.player.media3

import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.media3.test.utils.FakeExtractorInput
import me.misa198.airmedy.player.decoders.DecoderProvider
import me.misa198.airmedy.player.decoders.FormatKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** An [Extractor] that records the calls it receives and reports a fixed sniff result. */
private class RecordingExtractor : Extractor {
    var sniffResult = false
    val calls = mutableListOf<String>()

    override fun sniff(input: ExtractorInput): Boolean {
        calls += "sniff"
        return sniffResult
    }

    override fun init(output: ExtractorOutput) {
        calls += "init"
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        calls += "read"
        return Extractor.RESULT_END_OF_INPUT
    }

    override fun seek(position: Long, timeUs: Long) {
        calls += "seek"
    }

    override fun release() {
        calls += "release"
    }
}

/** An [Extractor] that is identifiable by [name] and accepts nothing. */
private class NamedExtractor(val name: String) : Extractor {
    override fun sniff(input: ExtractorInput): Boolean = false
    override fun init(output: ExtractorOutput) = Unit
    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int = Extractor.RESULT_END_OF_INPUT
    override fun seek(position: Long, timeUs: Long) = Unit
    override fun release() = Unit
}

private class FakeProvider(
    override val id: String,
    private val extractors: List<() -> Extractor>,
) : DecoderProvider {
    override fun isAvailable(key: FormatKey): Boolean = true
    override fun extractors(): List<() -> Extractor> = extractors
}

private class FakeBaseFactory(private val extractors: List<Extractor>) : ExtractorsFactory {
    override fun createExtractors(): Array<Extractor> = extractors.toTypedArray()
}

private val throwingOutput = object : ExtractorOutput {
    override fun track(id: Int, type: Int): TrackOutput = throw UnsupportedOperationException()
    override fun endTracks() = throw UnsupportedOperationException()
    override fun seekMap(seekMap: SeekMap) = throw UnsupportedOperationException()
}

class ProviderExtractorsFactoryTest {

    private fun input() = FakeExtractorInput.Builder().setData(ByteArray(16)).build()

    @Test
    fun `tagSniff calls back only when the delegate accepts`() {
        val delegate = RecordingExtractor()
        var accepted = 0
        val wrapper = tagSniff(delegate) { accepted++ }

        delegate.sniffResult = false
        assertFalse(wrapper.sniff(input()))
        assertEquals("no callback for a rejected sniff", 0, accepted)

        delegate.sniffResult = true
        assertTrue(wrapper.sniff(input()))
        assertEquals("one callback for an accepted sniff", 1, accepted)
    }

    @Test
    fun `tagSniff forwards every other call and the underlying implementation`() {
        val delegate = RecordingExtractor()
        val wrapper = tagSniff(delegate) {}

        wrapper.init(throwingOutput)
        wrapper.read(input(), PositionHolder())
        wrapper.seek(10L, 20L)
        wrapper.release()

        assertEquals(listOf("init", "read", "seek", "release"), delegate.calls)
        assertSame(delegate, wrapper.underlyingImplementation)
    }

    @Test
    fun `provider recorder records forgets and evicts the eldest past 32 entries`() {
        val recorder = ProviderRecorder()
        recorder.record("a", "platform")
        assertEquals("platform", recorder.providerFor("a"))
        recorder.forget("a")
        assertNull(recorder.providerFor("a"))

        for (index in 0..32) recorder.record("u$index", "p$index")
        assertNull("the eldest entry should be evicted", recorder.providerFor("u0"))
        assertEquals("p1", recorder.providerFor("u1"))
        assertEquals("the newest entry should survive", "p32", recorder.providerFor("u32"))
    }

    @Test
    fun `createExtractors places the base set before provider extractors in provider order`() {
        val base = FakeBaseFactory(listOf(NamedExtractor("base1"), NamedExtractor("base2")))
        val first = FakeProvider("first", listOf({ NamedExtractor("first1") }, { NamedExtractor("first2") }))
        val second = FakeProvider("second", listOf({ NamedExtractor("second1") }))

        val factory = ProviderExtractorsFactory(listOf(first, second), base, ProviderRecorder())

        val names = factory.createExtractors().map { (it as NamedExtractor).name }
        assertEquals(listOf("base1", "base2", "first1", "first2", "second1"), names)
    }
}
