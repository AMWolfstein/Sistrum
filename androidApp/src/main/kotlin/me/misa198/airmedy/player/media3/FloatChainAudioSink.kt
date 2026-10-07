package me.misa198.airmedy.player.media3

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink

/**
 * Stub for T048a; implemented in T048b.
 */
@OptIn(UnstableApi::class)
@Suppress("UNUSED_PARAMETER")
internal class FloatChainAudioSink(
    delegate: AudioSink,
    chainProcessors: List<AudioProcessor>,
) : ForwardingAudioSink(delegate)
