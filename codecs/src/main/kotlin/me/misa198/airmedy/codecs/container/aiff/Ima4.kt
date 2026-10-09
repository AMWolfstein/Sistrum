// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow container/aiff/demux.go and container/aiff/ext80.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.container.aiff

import java.nio.ByteBuffer
import java.io.IOException
import me.misa198.airmedy.codecs.container.*

/** Compatibility entry point for the existing IMA4 decoder API. */
class Ima4 private constructor(private val aiff:Aiff) {
 val stream=aiff.adpcm ?: throw IOException("aiff: expected ima4 compression")
 val warnings get()=aiff.warnings
 val info get()=stream.info
 fun decodeBlock()=stream.decodeBlock()
 fun seekSample(sample:Long)=stream.seekSample(sample)
 companion object {fun open(source:RandomAccessSource,strict:Boolean=false)=Ima4(Aiff.open(source,strict));fun open(source:ByteBuffer,strict:Boolean=false)=open(ByteBufferSource(source),strict)}
}
