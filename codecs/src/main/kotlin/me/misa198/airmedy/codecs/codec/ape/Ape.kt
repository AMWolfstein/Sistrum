// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/ape/ape.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.ape

import java.io.IOException
import java.nio.ByteBuffer

class ApeException(val reason: Reason, message: String): IOException("ape: $message") {
    enum class Reason { MALFORMED, UNSUPPORTED, SOURCE_UNREADABLE, INVALID_REQUEST }
}
internal fun malformed(message: String): Nothing = throw ApeException(ApeException.Reason.MALFORMED,message)
internal fun unsupported(message: String): Nothing = throw ApeException(ApeException.Reason.UNSUPPORTED,message)
internal fun u8(b: ByteBuffer,p: Int)=b.get(p).toInt() and 255
internal fun le16(b: ByteBuffer,p: Int)=u8(b,p) or (u8(b,p+1) shl 8)
internal fun le32(b: ByteBuffer,p: Int)=le16(b,p) or (le16(b,p+2) shl 16)
internal fun uint(v: Int)=v.toLong() and 0xffffffffL
const val MAX_HEADER_LEN=4096
internal const val MAX_BLOCKS=73728*16
fun match(b: ByteBuffer,p: Int=0)=p>=0 && b.limit()-p>=4 && u8(b,p)==77 && u8(b,p+1)==65 && u8(b,p+2)==67 && (u8(b,p+3)==32 || u8(b,p+3)==70)
class Header {
    var fileVersion=0; var programVersion=0; var compressionLevel=0; var formatFlags=0
    var blocksPerFrame=0; var finalFrameBlocks=0; var totalFrames=0
    var bitsPerSample=0; var channels=0; var rate=0
    var seekTableOffset=0L; var seekTableEntries=0; var frameDataOffset=0L
    var frameDataBytes=0L; var terminatingBytes=0L
    val samples get()=(totalFrames-1L)*blocksPerFrame+finalFrameBlocks
    fun frameBlocks(i: Int)=if (i==totalFrames-1) finalFrameBlocks else blocksPerFrame
    fun parse(b: ByteBuffer,base: Int=0): Header {
        val length=b.limit()-base
        if (!match(b,base)) malformed("not a Monkey's Audio file")
        if (length<8) malformed("file header truncated")
        fileVersion=le16(b,base+4)
        if (fileVersion>=3980) {
            if (length<52) malformed("descriptor truncated")
            val desc=uint(le32(b,base+8)); val hdr=uint(le32(b,base+12)); val seeks=uint(le32(b,base+16))
            var stored=uint(le32(b,base+20))
            frameDataBytes=uint(le32(b,base+24)) or (uint(le32(b,base+28)) shl 32)
            terminatingBytes=uint(le32(b,base+32)); programVersion=le16(b,base+6)
            if (desc<52 || hdr<24) malformed("descriptor declares a $desc-byte descriptor and $hdr-byte header, want at least 52 and 24")
            if (desc+hdr>MAX_HEADER_LEN) malformed("file header of ${desc+hdr} bytes exceeds the $MAX_HEADER_LEN-byte bound")
            if (length<desc+24) malformed("format header truncated: ${length-desc} bytes past a $desc-byte descriptor, want 24")
            val f=base+desc.toInt()
            compressionLevel=le16(b,f); formatFlags=le16(b,f+2); blocksPerFrame=le32(b,f+4)
            finalFrameBlocks=le32(b,f+8); totalFrames=le32(b,f+12); bitsPerSample=le16(b,f+16)
            channels=le16(b,f+18); rate=le32(b,f+20)
            if (seeks%4!=0L) malformed("seek table of $seeks bytes is not a whole number of entries")
            if (formatFlags and 32!=0) stored=0
            if (stored>8 shl 20) malformed("stored source header of $stored bytes exceeds the 8388608-byte bound")
            seekTableOffset=desc+hdr; seekTableEntries=(seeks/4).toInt(); frameDataOffset=seekTableOffset+seeks+stored
        } else {
            if (length<32) malformed("file header truncated")
            compressionLevel=le16(b,base+6); formatFlags=le16(b,base+8); channels=le16(b,base+10)
            rate=le32(b,base+12); totalFrames=le32(b,base+24); finalFrameBlocks=le32(b,base+28)
            terminatingBytes=uint(le32(b,base+20)); frameDataBytes=-1; blocksPerFrame=73728*4
            bitsPerSample=if (formatFlags and 1!=0) 8 else if (formatFlags and 8!=0) 24 else 16
            var stored=uint(le32(b,base+16)); if (formatFlags and 32!=0) stored=0
            if (stored>8 shl 20) malformed("stored source header of $stored bytes exceeds the 8388608-byte bound")
            var off=32; if (formatFlags and 4!=0) off+=4
            seekTableEntries=totalFrames
            if (formatFlags and 16!=0) {
                if (length<off+4) malformed("file header truncated")
                seekTableEntries=le32(b,base+off); off+=4
            }
            seekTableOffset=off+stored; frameDataOffset=seekTableOffset+seekTableEntries*4L
        }
        validate(); return this
    }
    fun validate() {
        when {
            fileVersion<3950 -> unsupported("stream version $fileVersion predates 3950: the 3.9x bitstreams are a different codec")
            fileVersion>3990 -> unsupported("stream version $fileVersion is past the supported 3990")
            formatFlags and 4096!=0 -> unsupported("floating-point streams are not supported")
            bitsPerSample!=8 && bitsPerSample!=16 && bitsPerSample!=24 -> unsupported("$bitsPerSample-bit samples: only 8, 16, and 24-bit are supported")
            channels<1 -> malformed("$channels channels")
            channels>2 -> unsupported("$channels channels: only mono and stereo are supported")
            rate<=0 -> malformed("sample rate $rate")
            compressionLevel !in 1000..5000 || compressionLevel%1000!=0 -> unsupported("compression level $compressionLevel is not one of 1000..5000")
            totalFrames<0 || seekTableEntries<0 -> malformed("negative frame count")
            totalFrames==0 -> malformed("no frames: the file was never finalized")
            seekTableEntries<totalFrames -> malformed("seek table has $seekTableEntries entries for $totalFrames frames")
            blocksPerFrame<=0 -> malformed("frame length of $blocksPerFrame blocks")
            compressionLevel>=5000 && blocksPerFrame>MAX_BLOCKS -> malformed("frame length of $blocksPerFrame blocks exceeds $MAX_BLOCKS")
            compressionLevel<5000 && blocksPerFrame>1000000 -> malformed("frame length of $blocksPerFrame blocks exceeds 1000000")
            finalFrameBlocks<=0 || finalFrameBlocks>blocksPerFrame -> malformed("final frame of $finalFrameBlocks blocks, want 1..$blocksPerFrame")
            terminatingBytes<0 || terminatingBytes>8 shl 20 -> malformed("trailing source data of $terminatingBytes bytes exceeds the 8388608-byte bound")
        }
    }
}
