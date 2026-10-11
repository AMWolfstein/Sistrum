// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxLabel cfbb45d13135a76f5df6781fdeec75f5cfe63bf9, internal/flac/ (read path).
// Copyright (c) 2026 Cole Springer, MIT; see metadata/THIRD-PARTY-NOTICES.
package com.amwolfstein.sistrum.metadata

internal data class FlacBlock(val code: Int, val input: Input, val offset: Long, val size: Int)
internal object Flac {
    fun read(input: Input,b: Builder,start: Long=0) {
        b.format="FLAC"
        var p=start+4
        val blocks=mutableListOf<FlacBlock>()
        if(start>0) {
            b.warn("stray-leading-id3","ID3v2 tag of $start bytes precedes the FLAC stream; preserved")
            b.native+=NativeBlock("ID3v2 (leading)",start,"preserved")
        }
        while(true) {
            val h=input.bytes(p,4); p+=4
            val type=h.u(0) and 127; val size=h.be(1,3).toInt()
            if(type==127) throw ReadFailure(ErrorClass.INVALID_DATA,"invalid FLAC block type 127")
            if(size>input.length-p) throw ReadFailure(ErrorClass.INVALID_DATA,"truncated FLAC metadata block")
            if(size>input.options.maxTagBytes) throw ReadFailure(ErrorClass.SIZE_TOO_LARGE,"metadata block limit")
            input.cap(blocks.size); blocks+=FlacBlock(type,input,p,size); p+=size
            if(h.u(0) and 128!=0) break
        }
        if(blocks.first().code!=0) throw ReadFailure(ErrorClass.INVALID_DATA,"first FLAC block must be STREAMINFO")
        var end=input.length; var trailer: ByteArray?=null
        if(end-p>=128 && input.bytes(end-128,3).latin()=="TAG") {
            val raw=input.bytes(end-128,128)
            if(looksLikeV1(raw)) { trailer=raw; end-=128; b.warn("trailing-id3v1","legacy ID3v1 tag follows the audio; preserved") }
        }
        val first=blocks.first()
        b.properties=Vorbis.streamInfo(input,first.offset,first.size,"FLAC")
        val commentBlocks=blocks.filter { it.code==4 }
        repeat(maxOf(0,commentBlocks.size-1)) { duplicateWarning(b) }
        val comments=commentBlocks.firstOrNull()?.let { Vorbis.comments(input,it.offset,it.size) } ?: CommentList("",emptyList(),0)
        Vorbis.project(comments,b)
        trailer?.let { v1Families(it,b) }
        if(start>0) {
            try {
                val legacy=Builder(); Id3.project(Id3.parse(input,0,start),legacy,Pictures.NONE)
                for((key,values) in legacy.tags) for(value in values)
                    b.families+=FamilyValue(key,"id3v2",listOf(value),b.tags[key]?.any { it.trim().equals(value.trim(),true) } ?: true,true)
            } catch(e: ReadFailure) { if(e.kind==ErrorClass.IO || e.kind==ErrorClass.SIZE_TOO_LARGE) throw e }
        }
        decodePictures(blocks,b)
        Vorbis.pictures(input,comments.comments,b)
        for(block in blocks) {
            val note=when(block.code) {
                4 -> "vendor="+vendor(block)
                6 -> "embedded picture"
                else -> ""
            }
            b.native+=NativeBlock(Vorbis.blockName(block.code),block.size.toLong(),note)
            if(block.code>6) b.warn("unknown-block","metadata block type ${block.code} preserved verbatim")
        }
        if(trailer!=null) b.native+=NativeBlock("ID3v1 (trailing)",128,"preserved")
        var padding=blocks.filter { it.code==1 }.sumOf { it.size.toLong()+4 }
        var paddingBodies=0L
        while(padding>0) {
            val n=if(padding>4+0xffffff) minOf(0xffffffL,padding-8) else padding-4
            paddingBodies+=n; padding-=4+n
        }
        b.properties=b.properties.copy(bitrate=averageBitrate(end-p,b.properties.durationNs),paddingBytes=paddingBodies)
        finish(b,end-p)
    }
    fun vendor(block: FlacBlock): String {
        if(block.size<4) return String(block.input.bytes(block.offset,block.size),Charsets.UTF_8)
        val n=block.input.bytes(block.offset,4).le(0,4)
        return String(block.input.bytes(block.offset+4,minOf(n,(block.size-4).toLong()).toInt()),Charsets.UTF_8)
    }
    fun decodePictures(blocks: List<FlacBlock>,b: Builder) {
        for(block in blocks.filter { it.code==6 }) {
            if(block.input.options.pictures==Pictures.NONE) continue
            try { b.pictures+=Vorbis.picture(block.input,block.offset,block.size) }
            catch(e: ReadFailure) {
                if(e.kind==ErrorClass.SIZE_TOO_LARGE || e.kind==ErrorClass.IO) throw e
                b.warn("invalid-picture",e.message.orEmpty())
            }
        }
    }
    fun duplicateWarning(b: Builder)=b.warn("multiple-vorbis-comment","more than one Vorbis comment block; the first is authoritative and the extras are dropped if the file is rewritten")
}
