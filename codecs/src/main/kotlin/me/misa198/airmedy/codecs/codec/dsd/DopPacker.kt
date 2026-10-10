// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from Flick rust/src/audio/dsd_engine/dsd/dop.rs,
// github.com/moss-apps/Flick at 79da4ed76557c8ddf534e898480dde66bcc90334.
// Copyright (c) 2026 Flick Player Contributors, MIT (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.dsd

/** Codec-only DoP words; no playback/output integration. */
class DopPacker(val dsdRate:Int,val channels:Int,private val needsBitReverse:Boolean=false) {
    val bitsPerFrame=if(dsdRate==22579200)32 else 24
    val bytesPerChannelPerFrame=(bitsPerFrame-8)/8
    val carrierRate=if(dsdRate==22579200)705600 else dsdRate/16
    private var markerState=0x05
    fun packToI32(bytes:ByteArray,offsets:IntArray,bytesPerChannel:Int,output:IntArray):Int {
        val frames=bytesPerChannel/bytesPerChannelPerFrame
        for(frame in 0 until frames){
            for(ch in 0 until channels){var word=markerState shl (bitsPerFrame-8)
                for(i in 0 until bytesPerChannelPerFrame){var b=bytes[offsets[ch]+frame*bytesPerChannelPerFrame+i].toInt() and 255;if(needsBitReverse)b=Integer.reverse(b) ushr 24;word=word or (b shl (bitsPerFrame-8-8*(i+1)))}
                output[frame*channels+ch]=word shl (32-bitsPerFrame)
            }
            markerState=if(markerState==5)250 else 5
        }
        return frames
    }
    fun reset(){markerState=5}
    fun seekFrame(sample:Long){markerState=if(sample and 1L==0L)5 else 250}
}
