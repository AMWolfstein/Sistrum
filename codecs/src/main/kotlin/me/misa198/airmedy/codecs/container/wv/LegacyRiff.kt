// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow container/riff/demux.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.container.wv

import me.misa198.airmedy.codecs.container.internal.srcwin.Window
import me.misa198.airmedy.codecs.codec.wavpack.*

/** Recognition only: legacy RIFF WavPack must never enter the PCM or native decode path. */
internal fun rejectLegacyRiff(w: Window) {
    if (w.ensure(0,12)<12) return
    if (!hasMagic(w.data,w.index(0),"RIFF") || !hasMagic(w.data,w.index(0)+8,"WAVE")) return
    var off=12L; var chunks=0
    while (off<=w.dataEnd-8 && chunks++<1024) {
        if (w.ensure(off,8)<8) return
        val at=w.index(off); val n=uint(le32(w.data,at+4)); val payload=off+8
        if (hasMagic(w.data,at,"data")) {
            if (minOf(n,w.dataEnd-payload)<10 || w.ensure(payload,10)<10) return
            val p=w.index(payload)
            if (!hasMagic(w.data,p,"wvpk")) return
            val size=uint(le32(w.data,p+4)); val version=le16(w.data,p+8)
            if ((version==1 && size==2L) || (version==2 && size==4L) || (version==3 && size==28L))
                unsupported(UNSUPPORTED_VERSION_MESSAGE)
            return
        }
        if (n>w.dataEnd-payload) return
        off=payload+n+(n and 1)
    }
}
