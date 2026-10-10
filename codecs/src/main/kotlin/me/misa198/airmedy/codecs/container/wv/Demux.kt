// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow container/wv/demux.go, container/wv/wv.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
// libwavpack 5.8.1 stream metadata adaptation; Copyright (c) 1998-2025 David Bryant.
// BSD-3-Clause; copyright, conditions and disclaimer in THIRD-PARTY-NOTICES.
package me.misa198.airmedy.codecs.container.wv

import java.nio.ByteBuffer
import me.misa198.airmedy.codecs.container.RandomAccessSource
import me.misa198.airmedy.codecs.container.internal.srcwin.Window
import me.misa198.airmedy.codecs.container.internal.srcwin.CHUNK
import me.misa198.airmedy.codecs.codec.wavpack.*

private const val MAX_RESYNC=1 shl 20
private const val SEEK_WINDOW=128 shl 10
private const val MAX_META_BLOCKS=16
private const val MAX_TAIL_SCAN=1 shl 22
private const val MAX_PROBE=2 shl 20
private const val MAX_BLOCK_BYTES=(1 shl 20)+8

data class Warning(val offset: Long, val message: String, val note: Boolean=false)
data class StreamInfo(val sampleRate: Int, val channels: Int, val bits: Int,
                      val validBits: Int, val totalSamples: Long, val samplesExact: Boolean, val isFloat: Boolean=false, val channelMask: Long=if (channels==1) 4 else 3)

/** Native .wv block walk and sample-index bisection. RandomAccessSource provides bounded positional reads.
 * Headers and packet descriptors are borrowed and reused; the source itself is never copied.
 */
class Demuxer(val source: RandomAccessSource, private var strict: Boolean=false, private val correctionConfig:Config?=null) {
    lateinit var config: Config
        private set
    lateinit var info: StreamInfo
        private set
    val warnings=ArrayList<Warning>()
    private val dataEnd: Long
    private val w=Window(source)
    private val scans=Window(source)
    var packetData: ByteBuffer=ByteBuffer.allocate(MAX_BLOCK_BYTES)
        private set
    var capacityFrames=MAX_BLOCK_SAMPLES
        private set
    private var firstBlock=0L
    private var initialIndex=0L
    private var off=0L
    private val cur=BlockHeader()
    private var valid=false
    private val scratch=BlockHeader()
    private val scanHeader=BlockHeader()
    private val confirmHeader=BlockHeader()
    private val candidate=BlockHeader()
    private var candidateOffset=0L
    private val bisectHeader=BlockHeader()
    private var bisectOffset=0L
    private val savedHeader=BlockHeader()
    private val lastHeader=BlockHeader()
    val packetHeader=BlockHeader()
    var packetOffset=0L
        private set
    var packetPosition=0L
        private set

    init {
        require(source.length>=0) { "negative source length" }
        rejectLegacyRiff(w)
        dataEnd=stripTrailers(w)
        w.dataEnd=dataEnd; scans.dataEnd=dataEnd
        if (w.ensure(0,4)<4) malformed("not a WavPack file")
        if (!match(w.data,w.index(0))) {
            if (u8(w.data,w.index(0))!=0x4d || u8(w.data,w.index(0)+1)!=0x5a || !nextCandidate(0,minOf(dataEnd,MAX_RESYNC.toLong())))
                malformed("not a WavPack file")
            firstBlock=candidateOffset
        }
        parse()
    }
    private fun warn(offset: Long, message: String, note: Boolean=false) {
        if (strict && !note) malformed("$message (at offset $offset)")
        if (warnings.size<64) {
            val w=Warning(offset,message,note)
            if (!warnings.contains(w)) warnings.add(w)
        }
    }
    private fun blockAt(offset: Long, into: BlockHeader): Boolean {
        if (offset<0 || dataEnd-offset<32 || w.ensure(offset,32,true)<32 || !syncOK(w.data,w.index(offset))) return false
        into.parse(w.data,w.index(offset))
        return into.size<=dataEnd-offset
    }
    private fun parse() {
        var offset=firstBlock; var i=0
        while (true) {
            checkVersionAt(offset)
            if (!blockAt(offset,cur)) malformed("no parsable block at offset $offset")
            if (cur.audio()) break
            if (i++>=MAX_META_BLOCKS) malformed("more than $MAX_META_BLOCKS blocks before the first audio block")
            offset+=cur.size
        }
        readBlock(offset,cur.size.toInt())
        config=probeBlock(packetData)
        if (correctionConfig!=null) config=config.copy(channels=correctionConfig.channels,channelMask=correctionConfig.channelMask)
        firstBlock=offset; initialIndex=cur.blockIndex; off=offset; valid=true
        if (config.channels>2) reserveChannelGroups()
        var samples=-1L; var exact=false
        if (cur.blockIndex==0L && cur.totalSamples>=0) {
            samples=cur.totalSamples
            val end=deliverableEnd()
            if (end>=0) {
                val got=end-initialIndex
                if (got<samples) warn(offset,"the header declares $samples samples but the blocks end at $got")
                if (got>samples) warn(offset,"the header declares $samples samples but the blocks run to $got",true)
                samples=got; exact=deliverableClean
            }
        } else {
            val end=scanTail()
            if (end>=0) { samples=end-initialIndex; exact=true }
        }
        info=StreamInfo(config.rate,config.channels,config.bitDepth,config.validBits,samples,exact,config.isFloat,config.channelMask)
    }
    private fun tilesToEnd(start: Long): Boolean {
        var offset=start; var i=0
        while (i++<MAX_META_BLOCKS+1) {
            if (offset==dataEnd) return true
            if (offset>dataEnd || !blockAt(offset,scratch)) return false
            offset+=scratch.size
        }
        return false
    }
    private fun scanTail(): Long {
        val lo=maxOf(firstBlock,dataEnd-MAX_TAIL_SCAN)
        var hi=dataEnd
        while (hi>lo) {
            val from=maxOf(lo,hi-CHUNK)
            val length=(hi-from).toInt()
            if (scans.ensure(from,length)<length) return -1
            val scan=scans.data; val base=scans.index(from)
            var i=hi-4
            while (i>=from) {
                if (match(scan,base+(i-from).toInt()) && blockAt(i,scanHeader) && scanHeader.audio() && tilesToEnd(i+scanHeader.size))
                    return scanHeader.blockIndex+scanHeader.blockSamples
                i--
            }
            if (from==lo) break
            hi=from+3
        }
        return -1
    }
    private var deliverableClean=false
    private fun deliverableEnd(): Long {
        val tail=scanTail()
        if (tail>=0) { deliverableClean=true; return tail }
        val savedStrict=strict; val warningCount=warnings.size
        val savedOff=off; val savedValid=valid; savedHeader.copyFrom(cur)
        strict=false; var end=-1L
        try {
            if (!bisect(Long.MAX_VALUE)) { deliverableClean=false; return -1 }
            off=bisectOffset; cur.copyFrom(bisectHeader); valid=true
            while (valid) {
                if (cur.audio()) end=cur.blockIndex+cur.blockSamples
                advance()
            }
            deliverableClean=warnings.size==warningCount
            return end
        } finally {
            strict=savedStrict
            while (warnings.size>warningCount) warnings.removeAt(warnings.lastIndex)
            off=savedOff; cur.copyFrom(savedHeader); valid=savedValid
        }
    }
    private fun confirm(offset: Long): Boolean {
        if (!blockAt(offset,confirmHeader)) return false
        if (::config.isInitialized && confirmHeader.audio() &&
            ((config.channels<=2 && confirmHeader.channels()!=config.channels) || confirmHeader.bytesPerSample()*8!=config.bitDepth)) return false
        val end=offset+confirmHeader.size
        return end==dataEnd || blockAt(end,scratch)
    }
    private fun nextCandidate(from: Long, limit: Long): Boolean {
        val stop=minOf(limit,dataEnd)
        var offset=from
        while (offset<stop) {
            val length=minOf(CHUNK.toLong(),dataEnd-offset).toInt()
            if (length<32) return false
            if (scans.ensure(offset,length)<length) return false
            val scan=scans.data; val base=scans.index(offset)
            var i=offset; val searchEnd=offset+length-4
            while (i<=searchEnd && !match(scan,base+(i-offset).toInt())) i++
            if (i>searchEnd) { offset+=length-3; continue }
            if (i>=stop) return false
            if (confirm(i)) { candidateOffset=i; candidate.copyFrom(confirmHeader); return true }
            offset=i+1
        }
        return false
    }
    private fun nextAudio(from: Long, limit: Long): Boolean {
        var offset=from
        while (offset<limit) {
            if (!nextCandidate(offset,limit)) return false
            if (candidate.audio() && candidate.flags and INITIAL_BLOCK!=0) return true
            offset=candidateOffset+1
        }
        return false
    }
    private fun advance() {
        var end=off+cur.size
        if (end>=dataEnd) { valid=false; return }
        checkVersionAt(end)
        if (!blockAt(end,scratch)) {
            if (!nextCandidate(end,end.toLong()+MAX_RESYNC)) {
                warn(end,"${dataEnd-end} trailing bytes are not a block, dropped"); valid=false; return
            }
            warn(end,"${candidateOffset-end} unparsable bytes between blocks")
            end=candidateOffset; scratch.copyFrom(candidate)
        }
        off=end; cur.copyFrom(scratch)
    }
    private fun reserveChannelGroups() {
        // Scan only headers at open to reserve actual maximum frame/packet spans,
        // avoiding enormous worst-case buffers for high channel counts.
        var offset=firstBlock;var bytes=0L;var channels=0;var active=false
        var maxBytes=0;var maxFrames=0
        while (offset<dataEnd && blockAt(offset,scanHeader)) {
            val h=scanHeader
            if (h.audio()) {
                if (h.flags and INITIAL_BLOCK!=0) {
                    if (active) malformed("incomplete channel group at offset $offset")
                    active=true;bytes=0;channels=0
                } else if (!active) malformed("channel group lacks initial block at offset $offset")
                bytes+=h.size;channels+=h.channels();maxFrames=maxOf(maxFrames,h.blockSamples)
                if (bytes>Int.MAX_VALUE) malformed("channel group is too large")
                if (h.flags and FINAL_BLOCK!=0) {
                    if (channels!=config.channels) malformed("channel group has $channels channels, expected ${config.channels}")
                    maxBytes=maxOf(maxBytes,bytes.toInt());active=false
                }
            } else if (active) malformed("metadata interrupts channel group")
            offset+=h.size
        }
        if (active || maxBytes==0) malformed("incomplete channel group")
        capacityFrames=maxFrames
        packetData=ByteBuffer.allocate(maxBytes)
    }
    fun readPacket(): Boolean {
        while (valid) {
            if (!cur.audio()) { advance(); continue }
            if (cur.flags and INITIAL_BLOCK==0) malformed("channel group lacks initial block at offset $off")
            packetHeader.copyFrom(cur);packetOffset=off;packetPosition=cur.blockIndex-initialIndex
            val start=off;var bytes=0L;var channels=0
            while (true) {
                if (cur.blockIndex!=packetHeader.blockIndex || cur.blockSamples!=packetHeader.blockSamples || cur.bytesPerSample()*8!=config.bitDepth)
                    malformed("mid-stream format change at offset $off")
                channels+=cur.channels();bytes+=cur.size
                if (bytes>packetData.capacity()) malformed("channel group exceeds reserved packet size")
                val final=cur.flags and FINAL_BLOCK!=0
                if (final) break
                advance()
                if (!valid || !cur.audio() || cur.flags and INITIAL_BLOCK!=0) malformed("incomplete channel group at offset $start")
            }
            if (channels!=config.channels) malformed("channel group has $channels channels, expected ${config.channels}")
            readBlock(start,bytes.toInt());w.trim(start);advance()
            return true
        }
        return false
    }
    private fun checkVersionAt(offset: Long) {
        if (w.ensure(offset,32)<32 || !match(w.data,w.index(offset))) return
        val version=le16(w.data,w.index(offset)+8)
        if (version !in MIN_STREAM_VERSION..MAX_STREAM_VERSION) unsupported(UNSUPPORTED_VERSION_MESSAGE)
    }
    private fun readBlock(offset: Long, size: Int) {
        packetData.clear(); packetData.limit(size)
        try {
            while (packetData.hasRemaining()) {
                val before=packetData.position()
                val got=source.read(offset+before,packetData)
                if (got<=0 || packetData.position()-before!=got)
                    throw WavPackException(ErrorCode.SOURCE_UNREADABLE,"reading block data")
            }
        } catch (e: java.io.IOException) {
            if (e is WavPackException) throw e
            throw WavPackException(ErrorCode.SOURCE_UNREADABLE,"reading block data").also { it.initCause(e) }
        }
        packetData.position(0)
    }
    private fun pos(h: BlockHeader) = h.blockIndex-initialIndex
    private fun bisect(sample: Long): Boolean {
        var lo=firstBlock
        if (!blockAt(lo,bisectHeader) || !bisectHeader.audio()) {
            if (!nextAudio(lo,lo.toLong()+MAX_RESYNC)) return false
            lo=candidateOffset; bisectHeader.copyFrom(candidate)
        }
        if (pos(bisectHeader)>sample) { bisectOffset=lo; return true }
        var hi=dataEnd
        while (hi-lo>SEEK_WINDOW) {
            val mid=lo+(hi-lo)/2
            if (!nextAudio(mid,minOf(hi.toLong(),mid.toLong()+MAX_PROBE)) || pos(candidate)>sample) { hi=mid; continue }
            lo=candidateOffset; bisectHeader.copyFrom(candidate)
        }
        bisectOffset=lo; return true
    }
    /** Land on the containing block; the stream wrapper pre-rolls to the exact target. */
    fun seekSample(sample: Long): Long {
        if (sample<0) throw WavPackException(ErrorCode.INVALID_REQUEST,"negative seek target")
        if (!bisect(sample)) malformed("cannot relocate any block for seeking")
        off=bisectOffset; cur.copyFrom(bisectHeader); valid=true
        var lastOff=off; lastHeader.copyFrom(cur)
        while (valid) {
            if (cur.audio() && cur.flags and INITIAL_BLOCK!=0) {
                if (pos(cur)>sample) break
                if (pos(cur)+cur.blockSamples>sample) return pos(cur)
                lastOff=off; lastHeader.copyFrom(cur)
            }
            w.trim(off)
            advance()
        }
        off=lastOff; cur.copyFrom(lastHeader); valid=true
        return pos(cur)
    }
}
