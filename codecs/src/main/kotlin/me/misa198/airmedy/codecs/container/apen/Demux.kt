// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow container/apen/demux.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.container.apen

import java.io.IOException
import java.nio.ByteBuffer
import me.misa198.airmedy.codecs.container.RandomAccessSource
import me.misa198.airmedy.codecs.container.internal.srcwin.Window
import me.misa198.airmedy.codecs.container.internal.srcwin.CHUNK
import me.misa198.airmedy.codecs.container.wv.stripTrailers
import me.misa198.airmedy.codecs.container.wv.id3Size
import me.misa198.airmedy.codecs.codec.ape.*

class Demuxer(private val source: RandomAccessSource,private val strict: Boolean=false) {
    private val w=Window(source) { cause -> ApeException(ApeException.Reason.SOURCE_UNREADABLE,"reading the file header").also { it.initCause(cause) } }
    val header: Header
    private val base: Long; private val seek: LongArray
    private var frame=0
    val packet: ByteBuffer
    var packetPosition=0L; private set
    data class Warning(val offset: Long,val message: String)
    val warnings=ArrayList<Warning>()
    init {
        w.dataEnd=stripTrailers(w,1)
        val from=if (w.ensure(0,10)==10) id3Size(w.data,w.index(0),false).toLong() else 0L
        val limit=minOf(from+(1 shl 20),w.dataEnd)
        var offset=from; var firstError: ApeException?=null; var tries=0; var found: Header?=null; var foundAt=0L
        while (offset<limit) {
            if (tries++>=1024) malformed("no Monkey's Audio header in the first ${tries-1} candidates")
            val candidate=nextMagic(offset,limit)
            if (candidate<0) break
            w.ensure(candidate,MAX_HEADER_LEN)
            try { found=Header().parse(w.data,w.index(candidate)); foundAt=candidate; break }
            catch (e: ApeException) { if (firstError==null) firstError=e }
            offset=candidate+1
        }
        if (found==null) { if (firstError!=null) throw firstError; malformed("no Monkey's Audio header in the first ${limit-from} bytes") }
        header=found; base=foundAt
        val h=header; val n=h.totalFrames
        if (h.seekTableEntries*4L>16 shl 20) malformed("seek table of ${h.seekTableEntries} entries exceeds the 16777216-byte bound")
        val count=minOf(n*4L,maxOf(0,w.dataEnd-(base+h.seekTableOffset))).toInt()
        if (count!=n*4) malformed("seek table truncated: $count of ${n*4} bytes")
        val raw=ByteBuffer.allocate(count); readFull(raw,base+h.seekTableOffset,"reading the file header"); raw.flip()
        seek=LongArray(n+1); var carry=0L; var previous=0L; var i=0
        while (i<n) { val current=uint(le32(raw,i*4)); if (i>0 && current<previous) carry+=1L shl 32; seek[i]=base+carry+current; previous=current; i++ }
        seek[n]=w.dataEnd-h.terminatingBytes
        if (h.frameDataBytes>=0) {
            val present=seek[n]-(base+h.frameDataOffset)
            if (present!=h.frameDataBytes) warn(seek[n],"the descriptor counts ${h.frameDataBytes} bytes of frame data but $present are present")
        }
        val want=base+h.frameDataOffset
        if (seek[0]!=want) malformed("seek table starts at ${seek[0]} but the header puts the frames at $want")
        val maxFrame=2L*h.blocksPerFrame*h.channels*(h.bitsPerSample/8)+(1 shl 16)
        var capacity=0L; i=0
        while (i<n) {
            val start=seek[i]; val end=seek[i+1]; val length=end-start
            when {
                end<start -> malformed("frame $i ends at $end, before its start at $start")
                end==start -> malformed("frame $i holds no bytes: the audio ends where it starts, at $start")
                end>w.dataEnd -> malformed("frame $i runs to $end, past the ${w.dataEnd} bytes of audio")
                length>maxFrame -> malformed("frame $i of $length bytes exceeds the $maxFrame-byte bound")
            }
            capacity=maxOf(capacity,12+minOf(end+4,w.dataEnd)-(start-((start-seek[0]) and 3)))
            i++
        }
        packet=ByteBuffer.allocate(capacity.toInt())
    }
    private fun warn(off: Long,message: String) {
        if (strict) malformed("$message (at offset $off)")
        val warning=Warning(off,message); if (warnings.size<64 && !warnings.contains(warning)) warnings.add(warning)
    }
    private fun nextMagic(from: Long,limit: Long): Long {
        if (w.ensure(from,4)==4 && match(w.data,w.index(from))) return from
        var off=from
        while (off<limit) {
            val n=w.ensure(off,CHUNK,true)
            if (n<4) return -1
            val at=w.index(off); var i=0
            while (i+4<=n) { if (off+i<limit && match(w.data,at+i)) return off+i; i++ }
            off+=n-3
        }
        return -1
    }
    private fun readFull(buffer: ByteBuffer,position: Long,message: String) {
        val origin=buffer.position()
        try {
            while (buffer.hasRemaining()) {
                val before=buffer.position(); val n=source.read(position+before-origin,buffer)
                if (n<=0 || n!=buffer.position()-before) throw IOException("source ended before its declared length")
            }
        } catch (e: IOException) { throw ApeException(ApeException.Reason.SOURCE_UNREADABLE,message).also { it.initCause(e) } }
    }
    fun readPacket(): Boolean {
        if (frame>=header.totalFrames) return false
        val i=frame; val skip=((seek[i]-seek[0]) and 3).toInt(); val from=seek[i]-skip
        val to=minOf(seek[i+1]+4,w.dataEnd); val blocks=header.frameBlocks(i)
        packet.clear(); packet.limit(12+(to-from).toInt())
        put32(0,blocks); put32(4,skip); put32(8,(seek[i+1]-seek[i]).toInt())
        packet.position(12); readFull(packet,from,"reading frame data"); packet.position(0)
        packetPosition=i.toLong()*header.blocksPerFrame; frame++; return true
    }
    private fun put32(position: Int,value: Int) { var i=0; while (i<4) { packet.put(position+i,(value ushr (i*8)).toByte()); i++ } }
    fun seekSample(sample: Long): Long {
        if (sample<0) throw ApeException(ApeException.Reason.INVALID_REQUEST,"negative seek target")
        frame=minOf(sample/header.blocksPerFrame,header.totalFrames-1L).toInt()
        return frame.toLong()*header.blocksPerFrame
    }
}
