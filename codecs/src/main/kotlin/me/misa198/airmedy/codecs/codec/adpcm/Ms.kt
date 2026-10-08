// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/adpcm/ms.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.adpcm

internal val adaptTable=longArrayOf(230,230,230,230,307,409,512,614,768,614,512,409,307,230,230,230)
internal class MsState {
 var coef1=0L;var coef2=0L;var delta=0L;var sample1=0L;var sample2=0L
 fun next(nib:Int):Int {val v=sample1*coef1+sample2*coef2;val predicted=(v+((v shr 63) and 255)) shr 8
 val value=(predicted+delta*((nib shl 4).toByte().toInt() shr 4)).coerceIn(-32768,32767).toInt();sample2=sample1;sample1=value.toLong();delta=((adaptTable[nib]*delta) shr 8).coerceIn(16,1L shl 30);return value}
}
