package com.amwolfstein.sistrum.player.decoders.kotlin

import com.amwolfstein.sistrum.codecs.audio.Buffer
import com.amwolfstein.sistrum.codecs.audio.FloatBuffer
import java.io.Closeable

/** A codec's stream API, independent of Media3's extraction lifecycle. */
internal interface CodecStream : Closeable {
    /** Sample counts and positions are per channel, including for interleaved output. */
    data class Info(val sampleRate: Int, val channels: Int, val pcmEncoding: Int, val totalSamples: Long)

    val info: Info

    /** The original typed refusal; a refused stream must never be decoded. */
    val refusal: Exception? get() = null

    /** Borrowed PCM valid until the next decode/seek. Float samples contain raw IEEE-754 words. */
    fun decodeBlock(): Buffer?

    /** The next block starts at this exact sample (codec-specific pre-roll stays in the codec). */
    fun seekSample(sample: Long)

    /** The extractor owns the source; close only codec-owned resources here. */
    override fun close() = Unit
}

/** Native float buffers retain their codec-owned storage without an intermediate PCM copy. */
internal interface FloatCodecStream : CodecStream {
    override fun decodeBlock(): Buffer? = null
    fun decodeFloatBlock(): FloatBuffer?
}
