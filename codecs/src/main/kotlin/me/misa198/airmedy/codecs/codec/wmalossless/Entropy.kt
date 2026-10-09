// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/wmalossless/entropy.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.wmalossless

internal class Golomb {
    var aveSum=0;var scaling=0
    fun seed(mean:Int,scale:Int){scaling=scale;aveSum=mean shl (scale+1)}
    fun next(r:BitReader):Int {
        val run=r.unary(32)
        if(run<0){r.check();malformed("a residual run of more than 32 ones")}
        var q=run
        if(run==32){val width=r.bits(5);q+=r.bits(width+1)}
        val mean=(aveSum+(1 shl scaling)) ushr (scaling+1)
        var u=q
        if(mean>1){val k=ceilLog2(mean);u=(q shl k)+r.bits(k)}
        aveSum+=u-(aveSum ushr scaling)
        return (u ushr 1) xor -(u and 1)
    }
}
