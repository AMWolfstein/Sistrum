// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow container/mp4/demux_test.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import me.misa198.airmedy.codecs.codec.alac.PacketIndex

/** Test-only Go demux output; no MP4 parser is ported to the production module. */
internal class AlacPackets(fixture: Fixture) {
    val bytes: ByteBuffer
    val cookie: ByteBuffer
    val index: PacketIndex
    init {
        val root=File(System.getProperty("waxflow.alacPackets","/tmp/sistrum-waxflow-oracle/alac-packets"))
        val file=File(root,fixture.name+".packets")
        check(file.isFile) { "ALAC test packets missing: $file; run scripts/waxflow-alac-packets.sh on the corpus" }
        bytes=ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        val hash=ByteArray(32); bytes.get(hash)
        check(hash.hex()==fixture.value("file_sha256")) { "ALAC packet source drift: ${fixture.name}" }
        val count=bytes.int; check(count>=24 && count<=256)
        val config=ByteArray(count); bytes.get(config); cookie=ByteBuffer.wrap(config)
        val total=bytes.long
        val offsets=ArrayList<Long>(); val sizes=ArrayList<Int>(); val starts=ArrayList<Long>(); var end=0L
        while (bytes.hasRemaining()) {
            val pts=bytes.long; val duration=bytes.long; val size=bytes.int
            check(size>0 && size<=bytes.remaining())
            offsets.add(bytes.position().toLong()); sizes.add(size); starts.add(pts)
            end=maxOf(end,pts+duration); bytes.position(bytes.position()+size)
        }
        bytes.position(0)
        index=PacketIndex(offsets.toLongArray(),sizes.toIntArray(),starts.toLongArray(),if (total>=0) total else end)
    }
}
