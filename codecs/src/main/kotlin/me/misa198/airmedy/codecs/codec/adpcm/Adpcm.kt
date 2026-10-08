// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/adpcm/adpcm.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.adpcm

import java.io.IOException
class AdpcmException(val unsupported:Boolean,message:String):IOException("adpcm: $message")
internal fun malformed(s:String)=AdpcmException(false,s)
internal fun unsupported(s:String)=AdpcmException(true,s)
enum class Layout { IMAQuickTime, MS;
 override fun toString()=if(this==MS)"MS ADPCM" else "IMA ADPCM (QuickTime layout)"
}
val defaultCoefs=arrayOf(intArrayOf(256,0),intArrayOf(512,-256),intArrayOf(0,0),intArrayOf(192,64),intArrayOf(240,0),intArrayOf(460,-208),intArrayOf(392,-232))
data class Config(val layout:Layout,val channels:Int,val blockAlign:Int,val samplesPerBlock:Int,val coefs:Array<IntArray> = Array(7){IntArray(2)}) {
 val blockFrames get()=if(channels<1)0 else if(layout==Layout.IMAQuickTime)64 else if(blockAlign<7*channels)0 else (blockAlign-7*channels)*2/channels+2
 fun validate(){if(channels !in 1..8)throw unsupported("$channels channels (supported: 1..8)")
 if(layout==Layout.MS&&channels>2)throw unsupported("$channels channels of MS ADPCM, whose nibbles alternate between at most 2")
 if(layout==Layout.IMAQuickTime){if(blockAlign!=34*channels)throw unsupported("$blockAlign-byte blocks, but the $layout is 34 bytes per channel")}else if(blockAlign<7*channels)throw unsupported("$blockAlign-byte blocks hold no $layout header for $channels channels")
 if(samplesPerBlock!=blockFrames)throw unsupported("$samplesPerBlock samples per block, but $blockAlign bytes of $layout hold $blockFrames")
 if(layout!=Layout.MS&&coefs.any{pair->pair.any{it!=0}})throw unsupported("$layout carries a coefficient table, which only MS ADPCM has")
 require(coefs.size==7&&coefs.all{it.size==2})
 }
}
