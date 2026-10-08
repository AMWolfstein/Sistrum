// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow audio/buffer.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.audio

/** Borrowed interleaved integer PCM, right-justified at [bits]. Reused by the decoder.
 * Unlike WaxFlow's planar pipeline buffer, this is the requested JVM interleaved API.
 * Only [frames] * [channels] values are valid. Copy them before the next decode.
 */
class Buffer(val channels: Int, val bits: Int, capacityFrames: Int) {
    val samples=IntArray(capacityFrames*channels)
    var frames=0
        internal set
    var position=0L
        internal set
    var discontinuity=false
        internal set
    val capacityFrames: Int get() = samples.size/channels
}
