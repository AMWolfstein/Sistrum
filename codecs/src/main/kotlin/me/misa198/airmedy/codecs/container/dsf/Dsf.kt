// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from dsf-meta 0.3.0 src/lib.rs, gitlab.com/clone206/dsf.
// Copyright 2020 Daniel J. R. May; modified by clone206.
// Original MIT OR Apache-2.0 notices retained in THIRD-PARTY-NOTICES.
package me.misa198.airmedy.codecs.container.dsf

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import me.misa198.airmedy.codecs.container.*
import me.misa198.airmedy.codecs.container.dsd.*

object Dsf {
    fun open(source:RandomAccessSource,targetRate:Int=176400)=Dsd.open(source,targetRate)
    fun open(source:ByteBuffer,targetRate:Int=176400)=Dsd.open(source,targetRate)
    internal fun parse(source:RandomAccessSource):Header {
        val b=headerBytes(source,0,92).order(ByteOrder.LITTLE_ENDIAN)
        fun check(ok:Boolean,error:String){if(!ok)throw IOException("dsf: $error")}
        check(tag(b,0)=="DSD ","A DSD chunk must start with the bytes 'DSD '.")
        check(b.getLong(4)==28L,"A DSD chunk must specify its size as 28 bytes.")
        check(tag(b,28)=="fmt ","A fmt chunk must start with the bytes 'fmt '.")
        check(b.getLong(32)==52L,"A fmt chunk is expected to specify its size as 52 bytes.")
        check(b.getInt(40)==1,"A fmt chunk must specify version 1.")
        check(b.getInt(44)==0,"A fmt chumk must specifiy a format ID of 0.")
        val type=b.getInt(48);check(type in 1..7,"A fmt chunk’s channel type is expected to be in the range 1–7.")
        val channels=b.getInt(52);check(channels in 1..6,"A fmt chunk’s channel num is expected to be in the range 1–6.")
        check(b.getInt(72)==4096,"A fmt chunk is expected to specify its block size per channel as 4096.")
        check(b.getInt(76)==0,"A fmt chunk’s reserved space is expected to be zero filled.")
        check(tag(b,80)=="data","A data chunk must start with the bytes 'data'.")
        val bytes=b.getLong(64)/8
        if(bytes<0||bytes>source.length/channels||b.getLong(84)<12||b.getLong(84)-12>source.length-92)throw IOException("dsd: invalid data bounds")
        if(bytes>0){val end=92+((bytes-1)/4096)*(4096L*channels)+(channels-1)*4096+(bytes-1)%4096+1;if(end>source.length||end>80+b.getLong(84))throw IOException("dsd: invalid data bounds")}
        val layout=when(type){1->"C";2->"FL,FR";3->"FL,FR,C";4->"FL,FR,BL,BR";5->"FL,FR,C,LFE";6->"FL,FR,C,BL,BR";else->"FL,FR,C,LFE,BL,BR"}
        return Header(b.getInt(56),channels,layout,bytes,92,true,b.getInt(60)==1)
    }
}
