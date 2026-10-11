// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxLabel cfbb45d13135a76f5df6781fdeec75f5cfe63bf9, internal/apen/ and internal/wavpack/.
// Copyright (c) 2026 Cole Springer, MIT; see metadata/THIRD-PARTY-NOTICES.
package me.misa198.airmedy.metadata

internal object ApeContainers {
    fun monkeys(input: Input,b: Builder) {
        val initial=input.bytes(0,minOf(input.length,6).toInt())
        if(initial.size<6 || initial.latin(0,4)!="MAC ") invalid("Monkey's Audio header truncated")
        val version=initial.le(4,2).toInt()
        if(version<3800) throw ReadFailure(ErrorClass.UNSUPPORTED_FORMAT,"unsupported Monkey's Audio version")
        val window=minOf(4096L,input.length); var floor: Long; val rate: Int; val channels: Int; val bits: Int; val samples: Long
        if(version>=3980) {
            if(input.length<52) invalid("APE_DESCRIPTOR truncated")
            val descriptor=input.bytes(0,52)
            var desc=descriptor.le(8,4); var hdr=descriptor.le(12,4)
            if(desc<52 || desc+24>window) desc=52
            if(hdr<24 || desc+hdr>window) hdr=24
            if(desc+24>window) invalid("Monkey's Audio header truncated")
            val h=input.bytes(desc,24); floor=desc+hdr
            val frames=h.le(12,4); samples=if(frames==0L) 0 else (frames-1)*h.le(4,4)+h.le(8,4)
            bits=h.le(16,2).toInt(); channels=h.le(18,2).toInt(); rate=h.le(20,4).toInt()
        } else {
            if(input.length<32) invalid("legacy Monkey's Audio header truncated")
            val h=input.bytes(0,32); val compression=h.le(6,2); val flags=h.le(8,2)
            val blocks=when { version>=3950 -> 73728*4L; version>=3900 || compression>=4000 -> 73728L; else -> 9216L }
            val frames=h.le(24,4); samples=if(frames==0L) 0 else (frames-1)*blocks+h.le(28,4)
            channels=h.le(10,2).toInt(); rate=h.le(12,4).toInt(); bits=when { flags and 1!=0L -> 8; flags and 8!=0L -> 24; else -> 16 }
            val wav=h.le(16,4); floor=32+if(32+wav<=window) wav else 0
        }
        val trailer=Ape.peel(input,floor,b)
        b.format="Monkey's Audio"
        val duration=samplesToNs(samples,rate)
        b.properties=AudioProperties("Monkey's Audio","Monkey's Audio",sampleRate=rate,channels=channels,bitsPerSample=bits,totalSamples=samples,durationNs=duration,bitrate=averageBitrate(trailer.start,duration))
        Ape.project(input,trailer.tag,b); trailer.id3?.let { v1Families(it,b) }
        Ape.native(trailer,b,"Monkey's Audio frames","Monkey's Audio")
        if(samples==0L && trailer.start>0) b.warn("no-audio","the Monkey's Audio header declares no frames; the file may not be audio")
    }
    private data class WvHeader(val length: Long,val flags: Long,val samples: Long,val unknown: Boolean,val index: Long) {
        val channels get()=if(flags and 4!=0L) 1 else 2
        val rate get()=intArrayOf(6000,8000,9600,11025,12000,16000,22050,24000,32000,44100,48000,64000,88200,96000,192000).getOrElse((flags ushr 23 and 15).toInt()) { 0 }
        val bits get(): Int { val b=((flags and 3).toInt()+1)*8; val shift=(flags ushr 13 and 31).toInt(); return if(shift<b) b-shift else b }
    }
    private fun wvHeader(input: Input,off: Long,end: Long): WvHeader {
        if(end-off<32) invalid("WavPack block header truncated")
        val h=input.bytes(off,32); if(h.latin(0,4)!="wvpk") invalid("missing wvpk marker")
        val version=h.le(8,2); if(version !in 0x402..0x410) throw ReadFailure(ErrorClass.UNSUPPORTED_FORMAT,"unsupported WavPack version")
        val size=h.le(4,4)+8; if(size<32 || size>end-off) invalid("WavPack block size invalid")
        var samples=(h.u(11) and 15).toLong() shl 32 or h.le(12,4); val correction=h.u(11) shr 4
        if(correction<=samples) samples-=correction
        return WvHeader(size,h.le(24,4),samples,h.le(12,4)==0xffffffffL,h.u(10).toLong() shl 32 or h.le(16,4))
    }
    fun wavpack(input: Input,b: Builder) {
        val first=wvHeader(input,0,input.length); val trailer=Ape.peel(input,first.length,b)
        var rate=first.rate; var channels=0; var bits=if(first.flags and 128!=0L) 32 else first.bits; var dsd=false
        var off=0L
        for(i in 0 until 64) {
            if(trailer.start-off<32) break
            val h=try { wvHeader(input,off,trailer.start) } catch(e: ReadFailure) { if(e.kind==ErrorClass.IO || e.kind==ErrorClass.SIZE_TOO_LARGE) throw e; break }
            if(h.index!=0L) break
            if(i==0 && (rate==0 || h.flags and (1L shl 31)!=0L)) {
                var pos=off+32; val end=pos+minOf(h.length-32,65536)
                while(end-pos>=2) {
                    input.cap(((pos-off)/2).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                    val sh=input.bytes(pos,2); val id=sh.u(0); var size=sh.u(1).toLong()*2; pos+=2
                    if(id and 128!=0) { if(end-pos<2) break; val extra=input.bytes(pos,2); size+=extra.le(0,2)*512; pos+=2 }
                    if(id and 64!=0) size--
                    if(size<0 || size>end-pos) break
                    when(id and 63) {
                        0x27 -> if(size>=3) { val sr=input.bytes(pos,3).le(0,3).toInt(); if(sr>0) rate=sr }
                        0x0e -> dsd=true // The DSD payload is encoded audio; only its metadata identifier is read.
                    }
                    pos+=size+(size and 1)
                }
            }
            channels+=h.channels
            if(h.flags and (1L shl 12)!=0L) break
            off+=h.length
        }
        if(channels==0) channels=first.channels
        if(dsd) bits=1
        val samples=if(first.unknown) 0 else first.samples; val duration=samplesToNs(samples,rate)
        b.format="WavPack"
        b.properties=AudioProperties("WavPack","WavPack",if(dsd) "WavPack DSD" else "",rate,channels,bits,samples,duration,averageBitrate(trailer.start,duration))
        Ape.project(input,trailer.tag,b); trailer.id3?.let { v1Families(it,b) }
        Ape.native(trailer,b,"WavPack blocks",if(dsd) "WavPack DSD" else "WavPack")
        if(samples==0L && trailer.start>0) b.warn("no-audio","no WavPack audio samples were found; the file may not be audio")
    }
}
