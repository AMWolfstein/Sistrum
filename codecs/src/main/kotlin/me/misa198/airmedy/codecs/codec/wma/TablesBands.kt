// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/wma/tables_bands.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
// Tables derived from FFmpeg (LGPL-2.1-or-later) via WaxFlow's extraction; see THIRD-PARTY-NOTICES.
package me.misa198.airmedy.codecs.codec.wma

internal val criticalFreqs = intArrayOf(100,200,300,400,510,630,770,920,1080,1270,1480,1720,2000,2320,2700,3150,3700,4400,5300,6400,7700,9500,12000,15500,24500)
internal val expBands22050 = arrayOf(intArrayOf(4,8,4,8,8,12,20,24,24,16),
intArrayOf(4,8,8,4,12,12,16,24,16,20,24,32,40,36),
intArrayOf(4,4,4,8,4,4,8,8,8,8,8,12,12,16,16,24,24,32,44,48,60,84,72))
internal val expBands32000 = arrayOf(intArrayOf(4,4,8,4,4,12,16,24,20,28,4),
intArrayOf(4,8,4,4,8,8,16,20,12,20,20,28,40,56,8),
intArrayOf(8,4,8,8,12,16,20,24,40,32,32,44,56,80,112,16))
internal val expBands44100 = arrayOf(intArrayOf(4,4,4,4,4,8,8,8,12,16,20,36),
intArrayOf(4,8,4,8,8,4,8,8,12,12,12,24,28,40,76),
intArrayOf(4,8,8,4,12,12,8,8,24,16,20,24,32,40,60,80,152))
