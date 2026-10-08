// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow container/internal/srcwin/srcwin.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.container.internal.srcwin

import java.io.IOException
import java.nio.ByteBuffer
import me.misa198.airmedy.codecs.container.RandomAccessSource
import me.misa198.airmedy.codecs.codec.wavpack.ErrorCode
import me.misa198.airmedy.codecs.codec.wavpack.WavPackException

internal const val CHUNK=128 shl 10

/** Bounded read-ahead, forward extension and in-place rebasing over Long offsets.
 * No ByteBuffer views are created per read. Users keep a separate scan/packet
 * buffer where a nested read must not invalidate borrowed window bytes.
 */
internal class Window(private val source: RandomAccessSource) {
    val data: ByteBuffer=ByteBuffer.allocate(CHUNK*2)
    var dataEnd=source.length
    private var start=0L
    private var count=0
    private var failure: IOException?=null
    fun index(position: Long) = (position-start).toInt()
    fun trim(position: Long) {
        if (position-start<CHUNK) return
        if (position>=start+count) { start=position; count=0; data.clear(); data.limit(0); return }
        val skip=(position-start).toInt()
        System.arraycopy(data.array(),skip,data.array(),0,count-skip)
        count-=skip; start=position; data.position(0); data.limit(count)
    }
    /** Make up to n bytes resident, using exact reads at open or CHUNK read-ahead in the walk. */
    fun ensure(position: Long, n: Int, readAhead: Boolean=false): Int {
        failure?.let { throw it }
        if (position<0 || n<=0 || position>=dataEnd) { data.limit(count); return 0 }
        val needed=minOf(n.toLong(),dataEnd-position).toInt()
        if (position>=start && position+needed<=start+count) return needed
        val want=minOf((if (readAhead) maxOf(n,CHUNK) else n).toLong(),dataEnd-position).toInt()
        val oldEnd=start+count
        if (position>=start && position<=oldEnd && position+want-start<=data.capacity()) {
            val extra=(position+want-oldEnd).toInt()
            if (extra>0) {
                readFull(oldEnd,count,extra)
                count+=extra
            }
        } else {
            start=position; count=0
            readFull(position,0,want)
            count=want
        }
        data.position(0); data.limit(count)
        return needed
    }
    private fun readFull(position: Long, offset: Int, size: Int) {
        data.clear(); data.position(offset); data.limit(offset+size)
        try {
            while (data.hasRemaining()) {
                val before=data.position()
                val read=source.read(position+before-offset,data)
                if (read<=0 || data.position()-before!=read)
                    throw IOException("source made no progress or ended before its declared length")
            }
        } catch (e: IOException) {
            val wrapped=WavPackException(ErrorCode.SOURCE_UNREADABLE,"reading block data")
            wrapped.initCause(e); failure=wrapped; throw wrapped
        }
    }
}
