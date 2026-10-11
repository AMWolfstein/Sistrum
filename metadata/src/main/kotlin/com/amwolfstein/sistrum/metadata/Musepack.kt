// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxLabel cfbb45d13135a76f5df6781fdeec75f5cfe63bf9, internal/musepack/.
// Copyright (c) 2026 Cole Springer, MIT; see metadata/THIRD-PARTY-NOTICES.
package com.amwolfstein.sistrum.metadata

internal object Musepack {
    private val rates=intArrayOf(44100,48000,37800,32000)
    internal data class Packet(val key: String,val offset: Long,val size: Long,val header: Int) {
        val payload get()=offset+header
        val end get()=offset+size
    }
    /** Read exactly the key and varint, never a prefix extending into AP audio. */
    private fun variable(input: Input,off: Long,end: Long): Pair<Long,Int>? {
        var value=0L
        for(i in 0 until 9) {
            if(off+i>=end) return null
            val n=input.bytes(off+i,1).u(0); value=value shl 7 or (n and 127).toLong()
            if(n and 128==0) return value to i+1
        }
        return null
    }
    internal fun packet(input: Input,off: Long,end: Long): Packet? {
        if(end-off<3) return null
        val key=input.bytes(off,2).latin(); if(key.any { it !in 'A'..'Z' }) return null
        val (size,n)=variable(input,off+2,end) ?: return null
        if(size<2+n || size>end-off) return null
        return Packet(key,off,size,2+n)
    }
    fun read(input: Input,b: Builder,start: Long=0) {
        var floor=start; var version=0; var rate=0; var channels=0; var samples=0L
        if(start>0) b.warn("stray-leading-id3","ID3v2 tag of $start bytes precedes the Musepack stream; preserved")
        val magic=input.bytes(start,minOf(4L,input.length-start).toInt())
        if(magic.latin()=="MPCK") {
            version=8; var pos=start+4; val end=minOf(input.length,start+8192)
            var found=false; var count=0
            while(pos<end) {
                input.cap(count++); val p=packet(input,pos,end) ?: break
                if(p.key=="SH") {
                    if(p.end-p.payload<5) invalid("SV8 stream header truncated")
                    val h=input.bytes(p.payload,5); if(h.u(4)!=8) throw ReadFailure(ErrorClass.UNSUPPORTED_FORMAT,"unsupported SV8 version")
                    val (raw,n)=variable(input,p.payload+5,p.end) ?: invalid("SV8 sample count truncated")
                    val (silence,m)=variable(input,p.payload+5+n,p.end) ?: invalid("SV8 silence count truncated")
                    val conf=p.payload+5+n+m; if(p.end-conf<2) invalid("SV8 configuration truncated")
                    val c=input.bytes(conf,2); rate=rates.getOrElse(c.u(0) shr 5) { 0 }; channels=(c.u(1) shr 4)+1
                    if(raw>silence) samples=raw-silence
                    floor=p.end; found=true; break
                }
                pos=p.end
            }
            if(!found) invalid("SV8 stream has no SH header")
        } else if(magic.size>=4 && magic.latin(0,3)=="MP+") {
            if(input.length-start<24) invalid("SV7 header truncated")
            val h=input.bytes(start,24); if(h.u(3) !in listOf(7,23)) throw ReadFailure(ErrorClass.UNSUPPORTED_FORMAT,"unsupported SV7 version")
            version=7; rate=rates[h.u(10) and 3]; channels=2; samples=h.le(4,4)*1152; floor=start+24
        } else invalid("missing Musepack marker")
        val t=Ape.peel(input,floor,b)
        var chapterRange: Pair<Long,Long>?=null
        if(version==8 && rate>0) chapterRange=chapters(input,start,t.start,rate,b)
        val duration=samplesToNs(samples,rate)
        b.format="Musepack"
        b.properties=AudioProperties("Musepack","Musepack","Musepack SV$version",rate,channels,totalSamples=samples,durationNs=duration,bitrate=averageBitrate(t.start-start,duration))
        Ape.project(input,t.tag,b); t.id3?.let { v1Families(it,b) }
        if(start>0) {
            val legacy=Builder()
            try {
                Id3.project(Id3.parse(input,0,start),legacy,Pictures.NONE)
                for((key,values) in legacy.tags) for(v in values) b.families+=FamilyValue(key,"id3v2",listOf(v),b.tags[key]?.any { it.trim().equals(v.trim(),true) } ?: true,true)
            } catch(e: ReadFailure) { if(e.kind==ErrorClass.IO || e.kind==ErrorClass.SIZE_TOO_LARGE) throw e }
            b.native+=NativeBlock("ID3v2",start,"leading, legacy, preserved")
        }
        val index=b.native.size
        Ape.native(t,b,if(version==8) "Musepack SV8 packets" else "Musepack SV7 stream","Musepack SV$version")
        chapterRange?.let { if(it.second>it.first) b.native.add(index+1,NativeBlock("  CT chapter packets",it.second-it.first,"${b.chapters.size} chapters")) }
        if(samples==0L && t.start>start) b.warn("no-audio","the Musepack header declares no samples; the file may not be audio")
    }
    private fun chapters(input: Input,start: Long,end: Long,rate: Int,b: Builder): Pair<Long,Long>? {
        var pos=start+4; var run=-1L; var audio=false; var count=0; var selected=false; var seekTarget: Long?=null
        while(pos<end) {
            if(count++>=input.options.maxElements) { b.warn("element-cap","the stream has more packets than the element limit allows; no chapters are read"); return null }
            val p=packet(input,pos,end) ?: return null
            if(pos==seekTarget && p.key=="ST") { run=p.end; selected=true; break }
            when(p.key) {
                "SE" -> { selected=true; break }
                "CT" -> if(run<0) run=pos
                "SO" -> {
                    run=-1
                    if(!audio) {
                        val ptr=variable(input,p.payload,minOf(p.end,p.payload+9))?.first
                        if(ptr!=null && ptr<end-pos) {
                            // Resolve only against packet boundaries reached structurally; never probe AP bodies.
                            seekTarget=pos+ptr
                        }
                    }
                }
                else -> { run=-1; audio=audio || p.key=="AP" }
            }
            pos=p.end
        }
        if(!selected || run<0) return null
        pos=run; count=0
        val first=IntArray(6); val faults=IntArray(6); val chapters=mutableListOf<Chapter>()
        while(pos<end) {
            val p=packet(input,pos,end)?.takeIf { it.key=="CT" } ?: break
            if(count>=input.options.maxElements) { b.warn("element-cap","the stream has more chapter packets than the element limit allows; the rest are not read"); break }
            count++; pos=p.end
            var fault=0; var ch: Chapter?=null
            if(p.end-p.payload>input.options.maxTagBytes) fault=3
            else {
                val v=variable(input,p.payload,p.end)
                if(v==null || p.end-p.payload<v.second+4) fault=1
                else {
                    val ns=samplesToNs(v.first,rate)
                    if(ns==0L && v.first>0) fault=2
                    else {
                        ch=Chapter(ns/1_000_000+if(ns%1_000_000>=500_000) 1 else 0)
                        val tagStart=p.payload+v.second+4
                        val len=p.end-tagStart
                        if(len in 1..23) fault=4
                        else if(len>=24) {
                            val n=input.bytes(tagStart+8,4).le(0,4)
                            val (items,capped)=Ape.items(input,tagStart+24,p.end,n)
                            ch=ch.copy(title=items.firstOrNull { !it.nonText && Ape.key(it.name)=="TITLE" }?.value?.substringBefore('\u0000').orEmpty())
                            if(capped) fault=5
                        }
                    }
                }
            }
            if(fault>0) { if(faults[fault]==0) first[fault]=count; faults[fault]++ }
            if(ch!=null) chapters+=ch
        }
        val messages=listOf("","is truncated before its start sample; skipped","starts past any duration; skipped","is larger than the allocation limit; not read","carries an unreadable tag; the chapter is listed untitled","has more tag items than the element limit allows; items past it are not read")
        for(f in 1..5) if(faults[f]>0) b.warn(if(f==5) "element-cap" else "malformed-tag-entry","chapter packet ${first[f]} ${messages[f]}"+if(faults[f]>1) " (and ${faults[f]-1} more)" else "")
        b.chapters+=chapters.sortedBy { it.startMs }
        return run to pos
    }
}
