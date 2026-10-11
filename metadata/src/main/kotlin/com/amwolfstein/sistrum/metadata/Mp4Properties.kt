// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxLabel cfbb45d13135a76f5df6781fdeec75f5cfe63bf9, internal/mp4/parse.go, esds.go, internal/mpeg4audio/config.go.
// Copyright (c) 2026 Cole Springer, MIT; see metadata/THIRD-PARTY-NOTICES.
package com.amwolfstein.sistrum.metadata

import java.util.Locale
import kotlin.math.roundToLong

internal data class Mp4Audio(val fourcc: String,val properties: AudioProperties)
internal object Mp4Properties {
    fun scale(units: Long,timescale: Long): Long {
        if(timescale<=0) return 0
        val unsigned=if(units>=0) units.toDouble() else (units and Long.MAX_VALUE).toDouble()+9223372036854775808.0
        return (unsigned/timescale*1_000_000_000).coerceAtMost(Long.MAX_VALUE.toDouble()).roundToLong()
    }
    fun timing(r: Mp4Reader,a: Mp4Atom?,movie: Boolean=false): Pair<Long,Long> {
        if(a==null) return 0L to 0L
        val p=r.prefix(a,32); if(p.isEmpty()) return 0L to 0L
        val off=when(p.u(0)) { 0 -> 12; 1 -> 20; else -> return 0L to 0L }; val width=if(off==12) 4 else 8
        if(p.size<off+4+width) return 0L to 0L
        val ts=p.be(off,4); var duration=p.be(off+4,width)
        if(movie && (if(width==4) duration==0xffffffffL else duration==-1L)) duration=0
        return ts to duration
    }
    fun edit(r: Mp4Reader,trak: Mp4Atom): ByteArray? {
        val edts=trak.find("edts") ?: return null; val p=r.whole(edts); var off=0
        while(p.size-off>=8) { val n=p.be(off,4); if(n<8 || n>p.size-off) return null
            if(p.latin(off+4,4)=="elst") return p.copyOfRange(off+8,off+n.toInt()); off+=n.toInt() }
        return null
    }
    fun editDuration(p: ByteArray?): Long {
        if(p==null || p.size<8) return 0
        val width=when(p.u(0)) { 0 -> 12; 1 -> 20; else -> return 0 }; val n=p.be(4,4)
        if(n>(p.size-8)/width) return 0
        var sum=0L; repeat(n.toInt()) { sum+=p.be(8+it*width,if(width==12) 4 else 8) }; return sum
    }
    fun read(r: Mp4Reader,moov: Mp4Atom): Mp4Audio {
        val trak=moov.all("trak").firstOrNull { r.handler(it.find("mdia")?.find("hdlr"))=="soun" }
            ?: return Mp4Audio("",AudioProperties(container="MP4"))
        val mdia=trak.find("mdia")!!; val (ts,dur)=timing(r,mdia.find("mdhd")); var duration=scale(dur,ts)
        val movieTs=timing(r,moov.find("mvhd"),true).first
        val edited=scale(editDuration(edit(r,trak)),movieTs); if(edited>0 && edited<duration) duration=edited
        val stsd=mdia.find("minf")?.find("stbl")?.find("stsd")
            ?: return Mp4Audio("",AudioProperties(container="MP4",durationNs=duration))
        val p=r.prefix(stsd,minOf(1024,r.input.options.maxTagBytes)); if(p.size<44) return Mp4Audio("",AudioProperties(container="MP4",durationNs=duration))
        val fourcc=p.latin(12,4); var raw=fourcc
        if(fourcc.startsWith("ms")) raw=waveCodec(p.be(14,2).toInt())
        var rate=0; var channels=0; var bits=0; val version=p.be(24,2).toInt(); var ext=44
        val entrySize=p.be(8,4); val end=if(entrySize>0 && entrySize<p.size-8) 8+entrySize.toInt() else p.size
        val layout=fixedDepth(fourcc)
        if(version>=2) {
            if(p.size>=72) {
                val n=p.be(44,4)
                if(n>=72 && n<=end-8) {
                    val f=Double.fromBits(p.be(48,8)); if(f>0 && f<Int.MAX_VALUE) rate=f.toInt()
                    val ch=p.be(56,4); if(ch in 1..255) channels=ch.toInt()
                    val depth=p.be(64,4); if(depth in 1..64) bits=depth.toInt()
                    if(fourcc=="lpcm" && p.be(68,4) and 1!=0L) { if(bits==32) raw="IEEE float"; if(bits==64) raw="IEEE float64" }
                    ext=8+n.toInt()
                } else return result(fourcc,raw,rate,channels,bits,duration)
            } else return result(fourcc,raw,rate,channels,bits,duration)
        } else {
            channels=p.be(32,2).toInt(); bits=p.be(34,2).toInt(); rate=p.be(40,2).toInt()
            if(layout!=null && layout>0) bits=layout
            if(version==1) ext+=16
        }
        var pcmDepth=0
        fun config(start: Int,stop: Int,depth: Int=0) {
            if(depth>=32) return
            var off=start
            while(stop-off>=8) {
                val n=p.be(off,4); val id=p.latin(off+4,4)
                if(id=="dfLa" && fourcc=="fLaC") {
                    if(n>=50 && stop-off>=50 && p.u(off+12) and 127==0) {
                        val v=p.be(off+26,8); val sr=(v ushr 44).toInt()
                        if(sr>0) { rate=sr; channels=((v ushr 41) and 7).toInt()+1; bits=((v ushr 36) and 31).toInt()+1 }
                    }; return
                }
                if(n<8 || n>stop-off) return
                when {
                    id=="wave" -> config(off+8,off+n.toInt(),depth+1)
                    id=="alac" && fourcc=="alac" -> {
                        if(n>=36) { if(p.u(off+17) in 1..32) bits=p.u(off+17); if(p.u(off+21) in 1..8) channels=p.u(off+21)
                            val sr=p.be(off+32,4); if(sr in 1 until Int.MAX_VALUE.toLong()) rate=sr.toInt() }; return
                    }
                    id=="pcmC" && fourcc in listOf("ipcm","fpcm") -> {
                        if(n>=14) { val d=p.u(off+13)
                            if(fourcc=="ipcm" && d in listOf(16,24,32) || fourcc=="fpcm" && d in listOf(32,64)) { bits=d; pcmDepth=d }
                            if(fourcc=="fpcm" && d==64) raw="IEEE float64" }; return
                    }
                    id=="esds" && fourcc=="mp4a" -> {
                        val cfg=esds(p,off,off+n.toInt())
                        if(cfg!=null) {
                            if(cfg.first=="MP3") raw="MP3"
                            cfg.second?.let { asc ->
                                raw=asc.profile().takeIf { it!="AAC" } ?: raw
                                when {
                                    asc.sbr -> { val sr=asc.extensionRate.takeIf { it>0 } ?: asc.rate; if(sr>0) rate=sr
                                        val ch=if(asc.ps && asc.channel==1) 2 else asc.channels; if(ch>0) channels=ch }
                                    !asc.signalled && asc.aot==2 && asc.rate>0 && rate==2*asc.rate -> { raw=if(channels==2 && asc.channel==1) "HE-AAC v2" else "HE-AAC" }
                                    else -> { if(asc.rate>0) rate=asc.rate; if(asc.channels>0) channels=asc.channels }
                                }
                            }
                        }; return
                    }
                }
                off+=n.toInt()
            }
        }
        if(fourcc in listOf("alac","fLaC","mp4a","ipcm","fpcm")) config(ext,end)
        if(layout!=null) {
            // No valid float width can be inferred from the v0 entry's fixed 16-bit field.
            if(fourcc=="fpcm" && pcmDepth==0) bits=0
            if(rate==0 && ts in 1 until Int.MAX_VALUE.toLong()) rate=ts.toInt()
        }
        return result(fourcc,raw,rate,channels,bits,duration)
    }
    private fun result(fourcc: String,raw: String,rate: Int,channels: Int,bits: Int,duration: Long): Mp4Audio {
        val canon=canonical(raw)
        return Mp4Audio(fourcc,AudioProperties(container="MP4",codec=canon,profile=if(canon==raw) "" else raw,sampleRate=rate,channels=channels,bitsPerSample=bits,durationNs=duration))
    }
    private fun fixedDepth(s: String): Int?=when(s.uppercase(Locale.ROOT)) {
        "IN24" -> 24; "IN32","FL32" -> 32; "FL64" -> 64; "ULAW","ALAW" -> 8; "IMA4" -> 4
        "LPCM","IPCM","FPCM","SOWT","TWOS","RAW ","NONE","MAC3","MAC6" -> 0
        else -> if(s.startsWith("ms")) when(s.toByteArray(Charsets.ISO_8859_1).be(2,2).toInt()) { 1,3 -> 0; 6,7 -> 8; else -> null } else null
    }
    private fun waveCodec(n: Int)=when(n) { 1 -> "PCM"; 3 -> "IEEE float"; 6 -> "A-law"; 7 -> "mu-law"; 17 -> "IMA ADPCM"; 80 -> "MP2"; 85 -> "MP3"; 255 -> "AAC"; 0x160 -> "WMA v1"; 0x161 -> "WMA v2"; 0x162 -> "WMA Pro"; 0x163 -> "WMA Lossless"; else -> "WAVE format 0x%04X".format(n) }
    private fun canonical(raw: String): String=when(raw.uppercase(Locale.ROOT)) {
        "MP4A","HE-AAC","HE-AAC V2","XHE-AAC" -> "AAC"; "ALAC" -> "ALAC"; "FLAC" -> "FLAC"
        "AC-3" -> "AC-3"; "EC-3","EAC3" -> "E-AC-3"; ".MP3" -> "MP3"; ".MP2" -> "MP2"; ".MP1" -> "MP1"
        "LPCM","IPCM","SOWT","TWOS","IN24","IN32","RAW ","NONE" -> "PCM"; "FL32","FPCM" -> "IEEE float"; "FL64" -> "IEEE float64"
        "ULAW" -> "mu-law"; "ALAW" -> "A-law"; "IMA4" -> "IMA ADPCM"; "MAC3" -> "MAC3"; "MAC6" -> "MAC6"
        else -> if(raw.uppercase(Locale.ROOT).startsWith("AAC ")) "AAC" else raw
    }
    private data class Descriptor(val tag: Int,val start: Int,val end: Int)
    private fun descriptor(p: ByteArray,start: Int,end: Int): Descriptor? {
        if(start<0 || start>=end) return null
        val tag=p.u(start); var off=start+1; var n=0
        repeat(4) { i ->
            if(off>=end) return null; val c=p.u(off++); n=n shl 7 or (c and 127)
            if(c and 128==0) return if(n<=end-off) Descriptor(tag,off,off+n) else null
            if(i==3) return null
        }; return null
    }
    private fun esds(p: ByteArray,start: Int,end: Int): Pair<String,AacConfig?>? {
        if(end-start<12) return null
        val es=descriptor(p,start+12,end)?.takeIf { it.tag==3 && it.end-it.start>=3 } ?: return null
        var off=es.start+3; val flags=p.u(es.start+2)
        if(flags and 128!=0) off+=2
        if(flags and 64!=0) { if(off>=es.end) return null; off+=1+p.u(off) }
        if(flags and 32!=0) off+=2
        while(off<es.end) {
            val dec=descriptor(p,off,es.end) ?: return null
            if(dec.tag==4) {
                if(dec.end-dec.start<13) return null
                val oti=p.u(dec.start); if(oti in listOf(0x69,0x6b)) return "MP3" to null
                if(oti !in listOf(0x40,0x66,0x67,0x68)) return null
                var q=dec.start+13
                while(q<dec.end) { val d=descriptor(p,q,dec.end) ?: return null
                    if(d.tag==5) return "" to (aac(p.copyOfRange(d.start,d.end)) ?: return null); q=d.end }
                return null
            }; off=dec.end
        }; return null
    }
    private data class AacConfig(var aot: Int,var rate: Int,val channel: Int,var sbr: Boolean=false,var ps: Boolean=false,var signalled: Boolean=false,var extensionRate: Int=0) {
        val channels get()=intArrayOf(0,1,2,3,4,5,6,8,0,0,0,7,8,24,8,0)[channel]
        fun profile()=when { sbr && ps && channel==1 -> "HE-AAC v2"; sbr -> "HE-AAC"; else -> when(aot) { 1 -> "AAC Main"; 2 -> "AAC LC"; 3 -> "AAC SSR"; 4 -> "AAC LTP"; 23 -> "AAC LD"; 39 -> "AAC ELD"; 42 -> "xHE-AAC"; else -> "AAC" } }
    }
    private fun aac(p: ByteArray): AacConfig? {
        var bit=0
        fun take(n: Int): Int? { if(n>p.size*8-bit) return null; var v=0; repeat(n) { v=v shl 1 or ((p.u(bit/8) shr (7-bit%8)) and 1); bit++ }; return v }
        fun objectType(): Int? { val n=take(5) ?: return null; return if(n==31) (take(6) ?: return null)+32 else n }
        fun rate(): Int? { val n=take(4) ?: return null; return if(n==15) (take(24) ?: return null).takeIf { it<=655350 } ?: 0 else intArrayOf(96000,88200,64000,48000,44100,32000,24000,22050,16000,12000,11025,8000,7350).getOrElse(n) { 0 } }
        val type=objectType() ?: return null; val sr=rate() ?: return null; val ch=take(4) ?: return null; val c=AacConfig(type,sr,ch)
        if(type in listOf(5,29)) { c.signalled=true; c.sbr=true; c.ps=type==29; c.extensionRate=rate() ?: return c
            val core=objectType() ?: return c; if(core in listOf(5,29)) { c.extensionRate=0; return c }; c.aot=core
            if(core==22 && take(4)==null) return c }
        if(c.aot !in listOf(1,2,3,4,6,7)) return c
        take(1) ?: return c
        if((take(1) ?: return c)==1 && take(14)==null) return c
        val ext=take(1) ?: return c; if(ch==0) return c
        if(c.aot==6 && take(3)==null) return c
        if(ext==1 && take(1)!=0) return c
        if(c.signalled || p.size*8-bit<16 || take(11)!=0x2b7) return c
        val et=objectType() ?: return c
        if(et in listOf(5,22)) { c.sbr=(take(1) ?: return c)==1; c.signalled=true
            if(c.sbr) c.extensionRate=rate() ?: return c
            if(et==5 && c.sbr && p.size*8-bit>=12 && take(11)==0x548) c.ps=take(1)==1 && ch==1 }
        return c
    }
}
