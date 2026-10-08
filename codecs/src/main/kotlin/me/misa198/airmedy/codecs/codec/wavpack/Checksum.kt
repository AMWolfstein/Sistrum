// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/wavpack/checksum.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.wavpack

import java.nio.ByteBuffer

/** Optional encoded-byte checksum. WaxFlow's decode path deliberately does not check it. */
fun verifyBlockChecksum(block: ByteBuffer, offset: Int = 0): Boolean? {
    val h=try { BlockHeader().parse(block,offset) } catch (_: WavPackException) { return null }
    if (h.size>block.limit()-offset) return null
    val m=Metadata(); m.reset(block,offset,h.size.toInt())
    while (m.next()) {
        if (m.id!=0x2f || (m.size!=2 && m.size!=4)) continue
        var sum=-1; var i=offset
        while (i+1<m.offset-2) { sum=sum*3+le16(block,i); i+=2 }
        return if (m.size==4) sum==m.int(0) else ((sum xor (sum ushr 16)) and 65535)==m.short(0)
    }
    return null
}
