// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/wmavoice/frames.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.wmavoice

internal const val ACB_NONE=0;internal const val ACB_ASYM=1;internal const val ACB_HAMMING=2
internal const val FCB_SILENCE=0;internal const val FCB_HARDCODED=1;internal const val FCB_WINDOW=2;internal const val FCB_INNOVATION=3
internal class FrameDesc(val blocks:Int,val log2:Int,val acb:Int,val fcb:Int,val double:Int=0)
internal val frameTypes=arrayOf(
    FrameDesc(1,0,0,0),FrameDesc(2,1,0,1),FrameDesc(2,1,1,2),
    FrameDesc(2,1,1,3,2),FrameDesc(2,1,1,3,5),FrameDesc(4,2,1,3),FrameDesc(4,2,1,3,2),FrameDesc(4,2,1,3,5),
    FrameDesc(2,1,2,3),FrameDesc(2,1,2,3,2),FrameDesc(2,1,2,3,5),FrameDesc(4,2,2,3),FrameDesc(4,2,2,3,2),FrameDesc(4,2,2,3,5),
    FrameDesc(8,3,2,3),FrameDesc(8,3,2,3,2),FrameDesc(8,3,2,3,5))
private object TypeRuns {
    val first=LongArray(15);val symbol=IntArray(15);val count=IntArray(15)
    init{val lens=intArrayOf(2,2,2,4,4,4,6,6,6,8,8,8,10,10,10,12,12,12,14,14,14,14);var acc=0
        for(sym in lens.indices){val n=lens[sym];val code=(acc ushr (32-n)).toLong();if(count[n]==0){first[n]=code;symbol[n]=sym};count[n]++;acc+=1 shl (32-n)}}
}
internal fun Decoder.readFrameType(r:BitReader):Int {
    var code=0L
    for(n in 1..14){code=(code shl 1) or r.bit().toLong();r.check();val count=TypeRuns.count[n];if(count==0)continue
        val off=code-TypeRuns.first[n];if(off<0||off>=count)continue
        val sym=TypeRuns.symbol[n]+off.toInt();val ft=cfg.tree[sym]
        if(ft<0)malformed("frame type symbol $sym is not in this stream's variable bit mode tree");return ft
    };malformed("no frame type codeword in 14 bits")
}
internal fun warmFrameTypes(){TypeRuns.count}
