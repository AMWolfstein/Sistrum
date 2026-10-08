// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow container/internal/trailer/trailer.go, container/internal/apev2/apev2.go, container/internal/id3/id3.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.container.wv

import java.nio.ByteBuffer
import me.misa198.airmedy.codecs.container.internal.srcwin.Window
import me.misa198.airmedy.codecs.codec.wavpack.*

// Only trailer recognition needed by the decode/seek API is ported, not tag editing.
internal fun hasMagic(b: ByteBuffer, off: Int, text: String): Boolean {
    if (off<0 || b.limit()-off<text.length) return false
    var i=0
    while (i<text.length) { if (u8(b,off+i)!=text[i].code) return false; i++ }
    return true
}
internal fun id3Size(b: ByteBuffer, off: Int, footer: Boolean): Int {
    if (off<0 || b.limit()-off<10 || !hasMagic(b,off,if (footer) "3DI" else "ID3")) return 0
    var n=0; var i=6
    while (i<10) { val v=u8(b,off+i); if (v and 128 != 0) return 0; n=(n shl 7) or v; i++ }
    return n+10+(if (footer || u8(b,off+5) and 16 != 0) 10 else 0)
}
internal fun stripTrailers(w: Window, floor: Long=32): Long {
    var end=w.dataEnd; var count=0
    while (count++<8) {
        val footer=end-32
        if (footer>=floor && w.ensure(footer,32)==32 && hasMagic(w.data,w.index(footer),"APETAGEX")) {
            val b=w.data; val at=w.index(footer)
            val flags=le32(b,at+20)
            if (flags and (1 shl 29)==0) {
                val hasHeader=flags and Int.MIN_VALUE != 0
                val size=uint(le32(b,at+12))+(if (hasHeader) 32 else 0)
                val start=end-size
                if (size in 32..(16L shl 20) && start>=floor &&
                    (!hasHeader || (w.ensure(start,32)==32 && hasMagic(w.data,w.index(start),"APETAGEX") && le32(w.data,w.index(start)+20) and (1 shl 29)!=0))) {
                    end=start; continue
                }
            }
        }
        val n=if (end-10>=floor && w.ensure(end-10,10)==10) id3Size(w.data,w.index(end-10),true) else 0
        if (n>0 && end-n>=floor && w.ensure(end-n,10)==10 && id3Size(w.data,w.index(end-n),false)==n) { end-=n; continue }
        if (end-128>=floor && w.ensure(end-128,3)==3 && hasMagic(w.data,w.index(end-128),"TAG")) { end-=128; continue }
        break
    }
    return end
}
