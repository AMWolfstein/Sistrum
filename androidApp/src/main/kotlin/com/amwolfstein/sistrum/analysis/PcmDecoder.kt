package com.amwolfstein.sistrum.analysis

import com.amwolfstein.sistrum.player.decoders.FormatKey

/** Outcome of decoding one file. */
internal sealed interface DecodeOutcome {
    data class Completed(
        val sampleRate: Int,
        val channels: Int,
        val frames: Long,
        val providerId: String?,
    ) : DecodeOutcome

    data object Cancelled : DecodeOutcome

    /** Decode/IO error or a mid-stream format change. */
    data class Failed(val providerId: String?, val reason: String) : DecodeOutcome

    /** Registry [com.amwolfstein.sistrum.player.decoders.DecoderRegistry.skipReason] or no provider for the key. */
    data class Unsupported(val reason: String) : DecodeOutcome
}

/** Decodes one file to interleaved float PCM; blocks the caller. */
internal interface PcmDecoder {
    fun decode(
        path: String,
        key: FormatKey?,
        consumer: PcmConsumer,
        isCancelled: () -> Boolean = { false },
        startUs: Long = 0L,
    ): DecodeOutcome
}
