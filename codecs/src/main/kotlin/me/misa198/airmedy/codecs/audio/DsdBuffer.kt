// SPDX-License-Identifier: GPL-3.0-or-later
package me.misa198.airmedy.codecs.audio

/** Borrowed interleaved MSB-first DSD bytes. One frame contains eight bits per channel. */
class DsdBuffer(val channels:Int,capacityFrames:Int) {
    val bytes=ByteArray(channels*capacityFrames)
    var bytesPerChannel=0
        internal set
    var bytePosition=0L
        internal set
    var discontinuity=false
        internal set
    val bitPosition get()=bytePosition*8
}
