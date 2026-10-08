// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/musepack/scan.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.musepack

internal class State(val scf:Array<IntArray> = Array(2){IntArray(32)},var r1:Int=1,var r2:Int=1) {
 fun capture(s:FrameState){for(ch in 0..1)for(n in 0..31)scf[ch][n]=s.scf[ch][n][2];r1=s.rng.r1;r2=s.rng.r2}
 fun load(s:FrameState){for(ch in 0..1)for(n in 0..31)s.scf[ch][n][2]=scf[ch][n];s.rng.r1=r1;s.rng.r2=r2}
}
internal class Scanner(val cfg:Config) {
 private val st=FrameState();private val r=BitReader()
 fun load(s:State){st.reset();s.load(st)}
 fun capture(s:State){s.capture(st)}
 fun scan(data:ByteArray,bits:Int,frames:Int){r.reset(data,0,bits);for(i in 0 until frames){if(cfg.streamVersion==7){val n=st.readSV7Header(r,cfg);for(band in 0 until n)for(ch in 0..1)if(st.res[ch][band]== -1)repeat(36){st.rng.next()}}else st.readSV8(r,cfg,i==0)};if(cfg.streamVersion==8&&r.end-r.pos>=8)throw malformed("block has ${r.end-r.pos} bits past its last frame, more than padding")}
}
