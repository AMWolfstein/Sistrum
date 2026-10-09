// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/wmapro/tables_bands.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
// Tables derived from FFmpeg (LGPL-2.1-or-later) via WaxFlow; see THIRD-PARTY-NOTICES.
package me.misa198.airmedy.codecs.codec.wmapro

internal val bandEdgeFreqs = makebandEdgeFreqs()
private fun makebandEdgeFreqs() = intArrayOf(100,200,300,400,510,630,770,920,1080,1270,1480,1720,2000,2320,2700,3150,3700,4400,5300,6400,7700,9500,12000,15500,20675,28575,41375,63875)
