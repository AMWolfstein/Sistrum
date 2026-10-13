@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.amwolfstein.sistrum.player.media3

import android.net.Uri
import androidx.media3.common.Format
import androidx.media3.common.Metadata
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.ForwardingTrackOutput
import androidx.media3.extractor.MpegAudioUtil
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SniffFailure
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.mp3.Mp3Extractor
import com.amwolfstein.sistrum.player.decoders.DecoderProvider
import java.io.EOFException
import java.io.IOException

/**
 * Remembers which provider's extractor accepted a URI, so [Media3Engine] can name the
 * provider in an [com.amwolfstein.sistrum.player.engine.EngineEvent.Error]. Bounded and
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
 * Adds first-frame LAME provenance without consuming input or changing Media3's trim values.
 * Parse once before the first read; retain the result across seeks and format re-emissions.
 */
private class LameProvenanceExtractor(private val delegate: Extractor) : Extractor by delegate {
    private var parsed = false
    private var info: LameGaplessInfo? = null

    override fun sniff(input: ExtractorInput): Boolean {
        // Mp3Extractor.sniff consumes leading ID3 bytes on success, so capture provenance first.
        parseOnce(input)
        return try {
            delegate.sniff(input)
        } catch (_: EOFException) {
            false // Media3 throws when a short input cannot contain even one header.
        }
    }

    override fun init(output: ExtractorOutput) {
        delegate.init(object : ExtractorOutput {
            override fun track(id: Int, type: Int): TrackOutput =
                object : ForwardingTrackOutput(output.track(id, type)) {
                    override fun format(format: Format) {
                        val lame = info
                        super.format(if (lame == null) format else format.buildUpon()
                            .setMetadata(format.metadata?.copyWithAppendedEntries(lame) ?: Metadata(lame))
                            .build())
                    }
                }

            override fun endTracks() = output.endTracks()
            override fun seekMap(seekMap: SeekMap) = output.seekMap(seekMap)
        })
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        parseOnce(input)
        return delegate.read(input, seekPosition)
    }

    private fun parseOnce(input: ExtractorInput) {
        if (!parsed) {
            parsed = true
            if (input.position == 0L) info = peekLameInfo(input)
        }
    }

    override fun getUnderlyingImplementation(): Extractor = delegate.underlyingImplementation

    private fun peekLameInfo(input: ExtractorInput): LameGaplessInfo? {
        try {
            input.resetPeekPosition()
            val id3 = ByteArray(10)
            input.peekFully(id3, 0, id3.size)
            if (id3[0] == 'I'.code.toByte() && id3[1] == 'D'.code.toByte() && id3[2] == '3'.code.toByte()) {
                if ((6..9).any { id3[it].toInt() and 0x80 != 0 }) return null
                var size = 0
                for (index in 6..9) size = (size shl 7) or (id3[index].toInt() and 0x7F)
                if (id3[5].toInt() and 0x10 != 0) size += 10 // ID3v2 footer.
                input.advancePeekPosition(size)
            } else {
                input.resetPeekPosition()
            }
            val bytes = ByteArray(4)
            input.peekFully(bytes, 0, bytes.size)
            var headerData = 0
            for (byte in bytes) headerData = (headerData shl 8) or (byte.toInt() and 0xFF)
            val header = MpegAudioUtil.Header()
            repeat(64 * 1024) {
                if (headerData ushr 17 and 3 == 1 && header.setForHeaderData(headerData)) {
                    val frame = ByteArray(header.frameSize)
                    for (index in 0..3) frame[index] = (headerData ushr (24 - 8 * index)).toByte()
                    input.peekFully(frame, 4, frame.size - 4)
                    return parseLameGaplessInfo(frame)
                }
                input.peekFully(bytes, 0, 1)
                headerData = (headerData shl 8) or (bytes[0].toInt() and 0xFF)
            }
            return null
        } catch (_: IOException) {
            // Best-effort provenance: EOF or a failed peek must not affect the delegate.
            return null
        } finally {
            input.resetPeekPosition()
        }
    }
}

private fun withLameProvenance(extractor: Extractor): Extractor =
    if (extractor.underlyingImplementation is Mp3Extractor) LameProvenanceExtractor(extractor) else extractor

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
        return extractors.map(::withLameProvenance).toTypedArray()
    }

    override fun createExtractors(
        uri: Uri,
        responseHeaders: Map<String, List<String>>,
    ): Array<Extractor> {
        val key = uri.toString()
        val extractors = mutableListOf<Extractor>()
        for (extractor in base.createExtractors(uri, responseHeaders)) {
            extractors += tagSniff(withLameProvenance(extractor)) { recorder.record(key, "platform") }
        }
        for (provider in providers) {
            for (factory in provider.extractors()) {
                extractors += tagSniff(withLameProvenance(factory())) { recorder.record(key, provider.id) }
            }
        }
        return extractors.toTypedArray()
    }
}
