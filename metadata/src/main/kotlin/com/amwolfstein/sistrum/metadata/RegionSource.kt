// SPDX-License-Identifier: GPL-3.0-or-later
// Sistrum bounded views for the WaxLabel read port, cfbb45d13135a76f5df6781fdeec75f5cfe63bf9.
// Copyright (c) 2026 Cole Springer (WaxLabel, MIT); see metadata/THIRD-PARTY-NOTICES.
package com.amwolfstein.sistrum.metadata

import com.amwolfstein.sistrum.codecs.container.RandomAccessSource
import java.nio.ByteBuffer
import java.util.Base64

internal data class Region(val offset: Long, val size: Int)
/** Packet and base64 views retain offsets, never packet/image-sized byte arrays. */
internal class RegionSource(private val input: Input, private val regions: List<Region>) : RandomAccessSource {
    private val starts = LongArray(regions.size+1).also { a -> regions.forEachIndexed { i,r -> a[i+1]=a[i]+r.size } }
    override val length = starts.last()
    fun fresh() = RegionSource(Input(input.source.freshView(),input.options),regions)
    override fun read(position: Long, buffer: ByteBuffer): Int {
        require(position >= 0)
        if (!buffer.hasRemaining()) return 0
        if (position >= length) return -1
        var p=position
        val initial=buffer.position()
        while(buffer.hasRemaining() && p<length) {
            var lo=0; var hi=regions.size
            while(lo<hi) { val mid=(lo+hi)/2; if(starts[mid+1]<=p) lo=mid+1 else hi=mid }
            val r=regions[lo]; val delta=(p-starts[lo]).toInt()
            val n=minOf(buffer.remaining(),r.size-delta,8192)
            buffer.put(input.bytes(r.offset+delta,n)); p+=n
        }
        return buffer.position()-initial
    }
}
internal class Base64Source(input: Input, offset: Long, size: Int) : RandomAccessSource {
    private val ascii: Input
    override val length: Long
    init {
        val runs=mutableListOf<Region>()
        var start=offset; var end=offset
        var p=0
        while(p<size) {
            val bytes=input.bytes(offset+p,minOf(8192,size-p))
            for(v in bytes) {
                if(v==10.toByte() || v==13.toByte()) {
                    if(end>start) { input.cap(runs.size); runs+=Region(start,(end-start).toInt()) }
                    start=end+1
                }
                end++
            }
            p+=bytes.size
        }
        if(end>start) { input.cap(runs.size); runs+=Region(start,(end-start).toInt()) }
        ascii=Input(RegionSource(input,runs),input.options)
        if(ascii.length%4!=0L) invalid()
        var cursor=0L; var decoded=0L
        while(cursor<ascii.length) {
            val n=minOf(4096L,ascii.length-cursor).toInt()
            val block=ascii.bytes(cursor,n)
            if(cursor+n<ascii.length && block.any { it=='='.code.toByte() }) invalid()
            val data=try { Base64.getDecoder().decode(block) } catch(e: IllegalArgumentException) { invalid() }
            if(data.size != n/4*3 && cursor+n<ascii.length) invalid()
            decoded+=data.size; cursor+=n
        }
        length=decoded
        if(length>input.options.maxTagBytes) throw ReadFailure(ErrorClass.SIZE_TOO_LARGE,"decoded picture exceeds metadata limit")
    }
    private fun invalid(): Nothing = throw ReadFailure(ErrorClass.INVALID_DATA,"METADATA_BLOCK_PICTURE is not valid base64; preserved as a comment")
    fun fresh(): RandomAccessSource {
        val cloned=Input(ascii.source.freshView(),ascii.options)
        return object : RandomAccessSource {
            override val length=this@Base64Source.length
            override fun read(position: Long,buffer: ByteBuffer)=readFrom(cloned,position,buffer)
        }
    }
    override fun read(position: Long, buffer: ByteBuffer): Int = readFrom(ascii,position,buffer)
    private fun readFrom(ascii: Input,position: Long,buffer: ByteBuffer): Int {
        require(position>=0)
        if(!buffer.hasRemaining()) return 0
        if(position>=length) return -1
        val first=position/3*4
        val skip=(position%3).toInt()
        val wanted=minOf(buffer.remaining().toLong(),length-position,3072L-skip).toInt()
        val chars=minOf(ascii.length-first,((wanted+skip+2)/3*4).toLong()).toInt()
        val decoded=Base64.getDecoder().decode(ascii.bytes(first,chars))
        buffer.put(decoded,skip,wanted)
        return wanted
    }
}

internal fun RandomAccessSource.freshView(): RandomAccessSource = when(this) {
    is RegionSource -> fresh()
    is Base64Source -> fresh()
    else -> this
}
