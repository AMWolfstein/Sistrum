// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/alac/alac.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.alac

import java.io.IOException
import java.nio.ByteBuffer

class AlacException(val reason: Reason,message: String): IOException("alac: $message") {
    enum class Reason { MALFORMED, UNSUPPORTED, SOURCE_UNREADABLE, INVALID_REQUEST }
}
internal fun malformed(message: String): Nothing=throw AlacException(AlacException.Reason.MALFORMED,message)
internal fun unsupported(message: String): Nothing=throw AlacException(AlacException.Reason.UNSUPPORTED,message)
internal fun u8(b: ByteBuffer,p: Int)=b.get(p).toInt() and 255
internal fun be32(b: ByteBuffer,p: Int)=(u8(b,p) shl 24) or (u8(b,p+1) shl 16) or (u8(b,p+2) shl 8) or u8(b,p+3)
internal fun uint(value: Int)=value.toLong() and 0xffffffffL
class Config(cookie: ByteBuffer) {
    val frameLength: Int; val bitDepth: Int; val pb: Int; val mb: Int; val kb: Int
    val channels: Int; val maxRun: Int; val maxFrameBytes: Long; val avgBitRate: Long; val sampleRate: Int
    val cookie: ByteArray
    init {
        val n=cookie.remaining(); val p=cookie.position()
        if (n<24) malformed("magic cookie of $n bytes, want at least 24")
        val length=uint(be32(cookie,p)); frameLength=length.toInt(); bitDepth=u8(cookie,p+5)
        pb=u8(cookie,p+6); mb=u8(cookie,p+7); kb=u8(cookie,p+8); channels=u8(cookie,p+9)
        maxRun=(u8(cookie,p+10) shl 8) or u8(cookie,p+11)
        maxFrameBytes=uint(be32(cookie,p+12)); avgBitRate=uint(be32(cookie,p+16)); sampleRate=be32(cookie,p+20)
        if (bitDepth!=16 && bitDepth!=20 && bitDepth!=24 && bitDepth!=32) malformed("bit depth $bitDepth, want 16/20/24/32")
        if (length !in 1..16384) malformed("frame length $length outside 1..16384")
        if (channels !in 1..2) unsupported("channel count $channels: only mono and stereo are supported")
        if (sampleRate<=0) malformed("sample rate $sampleRate")
        this.cookie=ByteArray(24); cookie.get(p,this.cookie)
    }
}
