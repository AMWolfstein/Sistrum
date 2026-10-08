// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow audio/buffer.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.audio

/** Borrowed interleaved float PCM; valid until the next decode or seek. */
class FloatBuffer(val channels:Int,capacity:Int) {
 val samples=FloatArray(channels*capacity);var frames=0;var position=0L;var discontinuity=false
 val bitsPerSample=32
}
