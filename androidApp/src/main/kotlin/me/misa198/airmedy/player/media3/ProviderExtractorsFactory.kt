@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package me.misa198.airmedy.player.media3

import android.net.Uri
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SniffFailure
import me.misa198.airmedy.player.decoders.DecoderProvider

/**
 * Remembers which provider's extractor accepted a URI, so [Media3Engine] can name the
 * provider in an [me.misa198.airmedy.player.engine.EngineEvent.Error]. Bounded and
 * insertion-ordered: the eldest URI is dropped once [MAX_ENTRIES] are held, so a long
 * playlist cannot grow it without limit. Safe to call from any thread.
 */
internal class ProviderRecorder {

    private val byUri = object : LinkedHashMap<String, String>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > MAX_ENTRIES
    }

    @Synchronized
    fun record(uri: String, providerId: String) {
        byUri[uri] = providerId
    }

    @Synchronized
    fun providerFor(uri: String): String? = byUri[uri]

    @Synchronized
    fun forget(uri: String) {
        byUri.remove(uri)
    }

    private companion object {
        const val MAX_ENTRIES = 32
    }
}

/**
 * Wraps [delegate] so [onAccepted] runs when [delegate]'s `sniff` accepts a stream.
 * Every other call is forwarded unchanged (including the sniff-failure details Media3 puts
 * in its "no extractor" error), and [Extractor.getUnderlyingImplementation] still returns
 * the delegate so Media3's own checks keep working.
 */
internal fun tagSniff(delegate: Extractor, onAccepted: () -> Unit): Extractor =
    object : Extractor {
        override fun sniff(input: ExtractorInput): Boolean {
            val accepted = delegate.sniff(input)
            if (accepted) onAccepted()
            return accepted
        }

        override fun getSniffFailureDetails(): List<SniffFailure> = delegate.sniffFailureDetails

        override fun init(output: ExtractorOutput) = delegate.init(output)

        override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int =
            delegate.read(input, seekPosition)

        override fun seek(position: Long, timeUs: Long) = delegate.seek(position, timeUs)

        override fun release() = delegate.release()

        override fun getUnderlyingImplementation(): Extractor = delegate.underlyingImplementation
    }

/**
 * The Media3 default extractors plus every registered provider's extractors, in
 * provider order. Media3's own set comes first because a provider only adds formats
 * Media3 cannot demux; when a URI is known, each extractor is tagged so the accepting
 * provider can be reported (T038, FR-063/FR-064).
 */
internal class ProviderExtractorsFactory(
    private val providers: List<DecoderProvider>,
    private val base: ExtractorsFactory = DefaultExtractorsFactory(),
    private val recorder: ProviderRecorder,
) : ExtractorsFactory {

    override fun createExtractors(): Array<Extractor> {
        val extractors = base.createExtractors().toMutableList()
        for (provider in providers) {
            for (factory in provider.extractors()) extractors += factory()
        }
        return extractors.toTypedArray()
    }

    override fun createExtractors(
        uri: Uri,
        responseHeaders: Map<String, List<String>>,
    ): Array<Extractor> {
        val key = uri.toString()
        val extractors = mutableListOf<Extractor>()
        for (extractor in base.createExtractors(uri, responseHeaders)) {
            extractors += tagSniff(extractor) { recorder.record(key, "platform") }
        }
        for (provider in providers) {
            for (factory in provider.extractors()) {
                extractors += tagSniff(factory()) { recorder.record(key, provider.id) }
            }
        }
        return extractors.toTypedArray()
    }
}
