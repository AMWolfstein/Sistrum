// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/wavpack/wavpack.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
// libwavpack 5.8.1 adaptation; Copyright (c) 1998-2025 David Bryant.
// BSD-3-Clause; copyright, conditions and disclaimer in THIRD-PARTY-NOTICES.
package me.misa198.airmedy.codecs.codec.wavpack

import java.io.IOException
import java.nio.ByteBuffer

const val VERSION = "wavpack-dec-1"
const val BLOCK_HEADER_LEN = 32
const val MIN_STREAM_VERSION = 0x402
const val MAX_STREAM_VERSION = 0x410
const val UNSUPPORTED_VERSION_MESSAGE = "unsupported stream version: only version-4 streams (0x402..0x410) are supported"
internal const val MAX_BLOCK_SAMPLES = 3 shl 16
internal const val MAX_TERMS = 16
internal const val MAX_TERM = 8
internal const val MONO = 4
internal const val HYBRID = 8
internal const val JOINT_STEREO = 0x10
internal const val FLOAT_DATA = 0x80
internal const val INT32_DATA = 0x100
internal const val INITIAL_BLOCK = 0x800
internal const val FINAL_BLOCK = 0x1000
internal const val FALSE_STEREO = 0x40000000
internal const val DSD = Int.MIN_VALUE
internal const val MONO_DATA = MONO or FALSE_STEREO
internal val SRATE_TABLE = intArrayOf(6000,8000,9600,11025,12000,16000,22050,24000,32000,44100,48000,64000,88200,96000,192000)

enum class ErrorCode { MALFORMED, UNSUPPORTED, INVALID_REQUEST, SOURCE_UNREADABLE }
class WavPackException(val code: ErrorCode, message: String) : IOException("wavpack: $message")
internal fun malformed(message: String): Nothing = throw WavPackException(ErrorCode.MALFORMED, message)
internal fun unsupported(message: String): Nothing = throw WavPackException(ErrorCode.UNSUPPORTED, message)
internal fun u8(b: ByteBuffer, p: Int) = b.get(p).toInt() and 255
internal fun le16(b: ByteBuffer, p: Int) = u8(b,p) or (u8(b,p+1) shl 8)
internal fun le32(b: ByteBuffer, p: Int) = le16(b,p) or (le16(b,p+2) shl 16)
internal fun uint(v: Int) = v.toLong() and 0xffffffffL

fun match(b: ByteBuffer, off: Int = 0): Boolean = off >= 0 && b.limit()-off >= 4 &&
    b.get(off)==119.toByte() && b.get(off+1)==118.toByte() && b.get(off+2)==112.toByte() && b.get(off+3)==107.toByte()

fun syncOK(b: ByteBuffer, off: Int = 0): Boolean {
    if (!match(b,off) || b.limit()-off < BLOCK_HEADER_LEN) return false
    val size = uint(le32(b,off+4))
    val ver = le16(b,off+8)
    return size and 1L == 0L && size >= 24 && size < (1 shl 20) &&
        ver in MIN_STREAM_VERSION..MAX_STREAM_VERSION && u8(b,off+22)<3 && u8(b,off+23)==0
}

/** Mutable, reusable header; unsigned CRC/flags retain their 32-bit bit patterns. */
class BlockHeader {
    var size = 0L
    var streamVersion = 0
    var totalSamples = -1L
    var blockIndex = 0L
    var blockSamples = 0
    var flags = 0
    var crc = 0
    fun audio() = blockSamples > 0
    fun mono() = flags and MONO_DATA != 0
    fun channels() = if (flags and MONO != 0) 1 else 2
    fun bytesPerSample() = (flags and 3) + 1
    fun shift() = (flags ushr 13) and 31
    fun copyFrom(h: BlockHeader) {
        size=h.size; streamVersion=h.streamVersion; totalSamples=h.totalSamples
        blockIndex=h.blockIndex; blockSamples=h.blockSamples; flags=h.flags; crc=h.crc
    }
    fun parse(b: ByteBuffer, off: Int = 0): BlockHeader {
        val len = b.limit()-off
        if (off<0 || len<BLOCK_HEADER_LEN) malformed("block header of $len bytes, want $BLOCK_HEADER_LEN")
        if (!match(b,off)) malformed("not a WavPack block")
        size=uint(le32(b,off+4))+8
        streamVersion=le16(b,off+8)
        blockIndex=uint(le32(b,off+16)) or (u8(b,off+10).toLong() shl 32)
        val samples=uint(le32(b,off+20))
        blockSamples=samples.toInt()
        flags=le32(b,off+24); crc=le32(b,off+28)
        val lo=uint(le32(b,off+12)); val hi=u8(b,off+11).toLong()
        totalSamples=if (lo==0xffffffffL) -1 else lo+(hi shl 32)-hi
        if (size<BLOCK_HEADER_LEN) malformed("block size $size below the $BLOCK_HEADER_LEN-byte header")
        if (streamVersion !in MIN_STREAM_VERSION..MAX_STREAM_VERSION)
            unsupported(UNSUPPORTED_VERSION_MESSAGE)
        if (samples>MAX_BLOCK_SAMPLES) malformed("block of $samples samples exceeds $MAX_BLOCK_SAMPLES")
        if (blockSamples<0 || totalSamples < -1) malformed("negative sample count")
        if (flags and MONO_DATA == MONO_DATA) malformed("block is flagged both mono and false-stereo")
        return this
    }
    fun supported() {
        when {
            flags and DSD != 0 -> unsupported("DSD streams are not supported")
            flags and (INITIAL_BLOCK or FINAL_BLOCK) != INITIAL_BLOCK or FINAL_BLOCK ->
                unsupported("more than 2 channels: only mono and stereo are supported")
        }
    }
}

/** Reused cursor replaces Go's metadata slices without allocating ByteBuffer views. */
internal class Metadata {
    lateinit var block: ByteBuffer
    var at=0; var end=0; var id=0; var offset=0; var size=0
    fun reset(b: ByteBuffer, start: Int, length: Int) { block=b; at=start+32; end=start+length }
    fun next(): Boolean {
        if (end-at<2) return false
        id=u8(block,at); var n=u8(block,at+1) shl 1; at+=2
        if (id and 0x80 != 0) {
            if (end-at<2) return false
            n+=(u8(block,at) shl 9)+(u8(block,at+1) shl 17); at+=2; id=id and 0x7f
        }
        if (id and 0x40 != 0) {
            if (n==0) return false
            id=id and 0xbf; n--
        }
        if (end-at<n+(n and 1)) return false
        offset=at; size=n; at+=n+(n and 1)
        return true
    }
    fun byte(i: Int) = u8(block,offset+i)
    fun short(i: Int) = le16(block,offset+i)
    fun int(i: Int) = le32(block,offset+i)
}

data class Config(val rate: Int, val channels: Int, val bitDepth: Int, val validBits: Int, val hybrid: Boolean=false, val isFloat: Boolean=false) {
    fun validate() {
        if (rate<=0) malformed("sample rate $rate outside 1..2147483647")
        if (channels !in 1..2) unsupported("$channels channels: only mono and stereo are supported")
        if (bitDepth !in intArrayOf(8,16,24,32)) malformed("bit depth $bitDepth, want 8/16/24/32")
        if (isFloat && bitDepth!=32) malformed("float stream must store 32-bit samples")
        if (validBits !in 1..bitDepth) malformed("valid bits $validBits outside 1..$bitDepth")
    }
}

fun probeBlock(b: ByteBuffer, off: Int = 0): Config {
    val h=BlockHeader().parse(b,off)
    if (!h.audio()) malformed("block carries no samples")
    h.supported()
    if (h.size>b.limit()-off) malformed("block declares ${h.size} bytes but only ${b.limit()-off} are present")
    val idx=(h.flags ushr 23) and 15
    var rate=if (idx<SRATE_TABLE.size) SRATE_TABLE[idx] else 0
    val m=Metadata(); m.reset(b,off,h.size.toInt())
    while (m.next()) {
        when (m.id) {
            0x27 -> if (m.size==3 || m.size==4) {
                rate=m.byte(0) or (m.byte(1) shl 8) or (m.byte(2) shl 16)
                if (m.size==4) rate=rate or ((m.byte(3) and 127) shl 24)
            }
            0xd -> if (m.size>0 && m.byte(0)>2) unsupported("${m.byte(0)} channels: only mono and stereo are supported")
        }
    }
    return Config(rate,h.channels(),h.bytesPerSample()*8,h.bytesPerSample()*8-h.shift(),h.flags and HYBRID!=0,h.flags and FLOAT_DATA!=0).also { it.validate() }
}

internal fun crcMono(crc: Int,v: Int) = crc*3+v
internal fun crcStereo(crc: Int,l: Int,r: Int) = crc+(crc shl 3)+(l shl 1)+l+r
internal fun crcExtension(crc: Int,v: Int) = crc*9+(v and 65535)*3+((v shr 16) and 65535)
