// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/wmapro/bands.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.wmapro

internal class Layout(c:Config) {
    val edges=Array(c.sizeCount){k->
        val length=c.samplesPerFrame ushr k;val list=ArrayList<Int>();list.add(0)
        for(f in bandEdgeFreqs){if(list.last()>=length)break;val e=(length*2*f/c.rate+2) and 3.inv();if(e>list.last())list.add(e);if(e>=length)break}
        if(list.size<2)malformed("the band layout at subframe length $length has no bands")
        list[list.lastIndex]=length;list.toIntArray()
    }
    val resample=Array(c.sizeCount){k->Array(c.sizeCount){j->
        val target=edges[k];val source=edges[j]
        IntArray(target.size-1){b->val mid=((target[b]+target[b+1]-1) shl k) ushr 1;var v=0;while(v+1<source.size&&(source[v+1] shl j)<mid)v++;v}
    }}
    fun numBands(k:Int)=edges[k].size-1
}
