// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/musepack/musepack.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.musepack

import java.io.IOException
class MusepackException(val unsupported:Boolean,message:String):IOException("musepack: $message")
internal fun malformed(s:String)=MusepackException(false,s)
internal fun unsupported(s:String)=MusepackException(true,s)
internal val rates=intArrayOf(44100,48000,37800,32000,0,0,0,0)
data class Config(val streamVersion:Int,val rate:Int,val channels:Int,val maxBand:Int,val ms:Boolean,val blockPwr:Int=0,var pns:Int=255,val trueGapless:Boolean=false,val lastFrameSamples:Int=0) {
 val framesPerBlock get()=if(streamVersion==7)1 else 1 shl blockPwr
 fun validate() {when {
 streamVersion in 4..6->throw unsupported("Musepack SV$streamVersion is not supported: only SV7 and SV8 are")
 streamVersion!=7&&streamVersion!=8->throw malformed("stream version $streamVersion")
 channels !in 1..2->throw unsupported("$channels channels: only mono and stereo are supported")
 streamVersion==7&&channels!=2->throw malformed("SV7 streams are always two channels, not $channels")
 rate !in rates.take(4)->throw malformed("sample rate $rate is not one of 44100, 48000, 37800, 32000")
 maxBand !in 1..31->throw malformed("max band $maxBand outside 1..31")
 blockPwr !in 0..14||blockPwr and 1!=0->throw malformed("block power $blockPwr is not an even number in 0..14")
 streamVersion==7&&blockPwr!=0->throw malformed("SV7 has no blocks")
 pns>1&&pns!=255->throw malformed("PNS flag $pns")
 lastFrameSamples !in 0..1152->throw malformed("last frame of $lastFrameSamples samples exceeds 1152")
 }}
}
