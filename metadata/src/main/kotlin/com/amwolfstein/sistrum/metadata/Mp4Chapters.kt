// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxLabel cfbb45d13135a76f5df6781fdeec75f5cfe63bf9, internal/mp4/chapters.go.
// Copyright (c) 2026 Cole Springer, MIT; see metadata/THIRD-PARTY-NOTICES.
package com.amwolfstein.sistrum.metadata

internal data class Mp4ChapterResult(val chapters: List<Chapter>,val neroCount: Int,val neroVersion: Int,
    val haveQT: Boolean,val conflict: Boolean,val track: Mp4Atom?)
internal object Mp4Chapters {
    private data class Timed(val start: Long,val end: Long=0,val title: String="") {
        fun public()=Chapter(ms(start),ms(end),title)
    }
    private fun ms(ns: Long)=minOf(Long.MAX_VALUE/1_000_000,ns/1_000_000+if(ns%1_000_000>=500_000) 1 else 0)
    private fun add(a: Long,b: Long)=if(b>Long.MAX_VALUE-a) Long.MAX_VALUE else a+b
    private fun nero(r: Mp4Reader,a: Mp4Atom?): Pair<Int,List<Timed>> {
        if(a==null) return 1 to emptyList()
        val p=r.whole(a); if(p.size<5) return 1 to emptyList()
        val version=p.u(0); var pos=if(version==0) 4 else 8
        if(pos>=p.size) return 1 to emptyList()
        val n=p.u(pos++); val out=mutableListOf<Timed>()
        repeat(n) {
            if(p.size-pos<9) return 1 to emptyList()
            val start=Mp4Properties.scale(p.be(pos,8),10_000_000); val len=p.u(pos+8); pos+=9
            if(len>p.size-pos) return 1 to emptyList()
            out+=Timed(start,title=Mp4Tags.utf8(p.copyOfRange(pos,pos+len)).orEmpty()); pos+=len
        }
        for(i in 0 until out.size-1) if(out[i+1].start>out[i].start) out[i]=out[i].copy(end=out[i+1].start)
        return version to out
    }
    private fun id(r: Mp4Reader,t: Mp4Atom): Long? {
        val a=t.find("tkhd") ?: return null; val p=r.prefix(a,32); if(p.isEmpty()) return null
        val off=if(p.u(0)==1) 20 else 12; return if(p.size>=off+4) p.be(off,4) else null
    }
    private fun chapterIds(r: Mp4Reader,t: Mp4Atom): List<Long> {
        val a=t.find("tref") ?: return emptyList(); val p=r.whole(a); var off=0
        while(p.size-off>=8) { val n=p.be(off,4); if(n<8 || n>p.size-off) break
            if(p.latin(off+4,4)=="chap") return List((n.toInt()-8)/4) { p.be(off+8+it*4,4) }; off+=n.toInt() }
        return emptyList()
    }
    private fun stbl(t: Mp4Atom)=t.find("mdia")?.find("minf")?.find("stbl")
    internal fun samples(r: Mp4Reader,table: Mp4Atom,nSamples: Int,offsets: Map<Mp4Atom,List<Long>>): List<Pair<Long,Long>>? {
        val sz=table.find("stsz")?.let { r.whole(it) } ?: return null
        if(sz.size<12) return null
        val shared=sz.be(4,4); val n=minOf(sz.be(8,4),nSamples.toLong()).toInt()
        if(shared==0L && n>(sz.size-12)/4) return null
        val chunks=table.find("stco")?.let { offsets[it] } ?: table.find("co64")?.let { offsets[it] } ?: return null
        if(chunks.isEmpty()) return null
        val sc=table.find("stsc")?.let { r.whole(it) } ?: return null
        if(sc.size<8) return null; val count=sc.be(4,4)
        if(count>(sc.size-8)/12) return null
        val out=mutableListOf<Pair<Long,Long>>(); var si=0; var ei=0
        for((index,chunk) in chunks.withIndex()) {
            if(si>=n) break
            if(count==0L) break
            while(ei+1<count && sc.be(8+(ei+1)*12,4)<=index+1) ei++
            if(sc.be(8+ei*12,4)>index+1) continue
            val perChunk=sc.be(12+ei*12,4); var off=chunk; var k=0L
            while(k<perChunk && si<n) {
                val size=if(shared>0) shared else sz.be(12+si*4,4)
                if(off<0 || size>Long.MAX_VALUE-off) return null
                out+=off to size; off+=size; k++; si++
            }
        }
        return out.takeIf { it.isNotEmpty() }
    }
    fun read(r: Mp4Reader,moov: Mp4Atom,chpl: Mp4Atom?,offsets: Map<Mp4Atom,List<Long>>,mdats: List<Mp4Atom>): Mp4ChapterResult {
        val (ver,nero)=nero(r,chpl)
        val traks=moov.all("trak"); val audio=traks.firstOrNull { r.handler(it.find("mdia")?.find("hdlr"))=="soun" }
        val ids=audio?.let { chapterIds(r,it) }.orEmpty()
        val text=traks.firstOrNull { id(r,it) in ids }
        fun result(qt: List<Timed> = emptyList(),have: Boolean=false,saturated: Boolean=false): Mp4ChapterResult {
            var conflict=false
            val merged=when {
                have && nero.isNotEmpty() -> {
                    val same=nero.size==qt.size && nero.indices.all { nero[it].title==qt[it].title }
                    val agree=same && nero.indices.all { kotlin.math.abs(nero[it].start-qt[it].start)<=500_000_000 }
                    if(agree || saturated && same) nero.mapIndexed { i,ch -> if(i==nero.lastIndex && qt[i].end>=ch.start) ch.copy(end=qt[i].end) else ch }
                    else { conflict=true; qt }
                }
                have -> qt; else -> nero
            }
            return Mp4ChapterResult(merged.map { it.public() },nero.size,ver,have,conflict,text)
        }
        if(text==null) return result()
        val mdia=text.find("mdia") ?: return result(); val (ts,_)=Mp4Properties.timing(r,mdia.find("mdhd")); if(ts==0L) return result()
        val table=stbl(text) ?: return result()
        // A hostile chap reference must never turn an audio sample into a metadata read.
        val entry=table.find("stsd")?.let { r.prefix(it,16) }
        if(entry==null || entry.size<16 || entry.latin(12,4) !in listOf("text","tx3g") || r.handler(mdia.find("hdlr")) !in listOf("text","sbtl","subt")) return result()
        val stts=table.find("stts")?.let { r.whole(it) } ?: return result()
        if(stts.size<8) return result(); val count=stts.be(4,4); if(count>(stts.size-8)/8) return result()
        val times=mutableListOf<Long>(); var time=0L; var completed=true; var saturated=false
        timeLoop@ for(i in 0 until count.toInt()) {
            val n=stts.be(8+i*8,4); val delta=stts.be(12+i*8,4); var j=0L
            while(j<n) {
                if(times.size>=65536) { completed=false; break@timeLoop }
                r.input.cap(times.size); times+=time; time+=delta; j++

            }
        }
        if(times.isEmpty()) return result()
        saturated=times.zipWithNext().any { (a,b) -> b-a==0xffffffffL }
        val samples=samples(r,table,times.size,offsets) ?: return result()
        val (movieTs,movieDur)=Mp4Properties.timing(r,moov.find("mvhd"),true)
        var delay=0L
        val edit=Mp4Properties.edit(r,text)
        if(movieTs>0 && edit!=null && edit.size>=8 && edit.be(4,4)>0) {
            when(edit.u(0)) {
                0 -> if(edit.size>=20 && edit.be(12,4)==0xffffffffL) { val seg=edit.be(8,4); delay=Mp4Properties.scale(seg,movieTs); saturated=saturated || seg==0xffffffffL }
                1 -> if(edit.size>=28 && edit.be(16,8)==-1L) delay=Mp4Properties.scale(edit.be(8,8),movieTs)
            }
        }
        val protected=mutableListOf<Pair<Long,Long>>()
        for(t in traks.filter { it!=text }) {
            val tbl=stbl(t) ?: continue
            val sizes=tbl.find("stsz")?.let { r.prefix(it,12) }
            val n=if(sizes!=null && sizes.size>=12) sizes.be(8,4) else 0
            if(n>r.input.options.maxElements-protected.size) throw ReadFailure(ErrorClass.SIZE_TOO_LARGE,"MP4 protected sample limit")
            val spans=samples(r,tbl,n.toInt(),offsets)
            if(n>0 && spans==null) return result() // cannot establish safe chapter ranges
            protected+=spans.orEmpty().map { it.first to it.first+it.second }
        }
        val protectedSorted=protected.sortedBy { it.first }
        val prefixEnds=LongArray(protectedSorted.size)
        for(i in protectedSorted.indices) prefixEnds[i]=maxOf(protectedSorted[i].second,if(i==0) 0 else prefixEnds[i-1])
        fun safe(off: Long,size: Long): Boolean {
            if(off<0 || off>r.input.length || size>r.input.length-off) return false
            if(mdats.none { off>=it.payload && off+size<=it.end }) return false
            // Locate any overlapping media sample without a quadratic table walk.
            var lo=0; var hi=protectedSorted.size
            while(lo<hi) { val mid=(lo+hi)/2; if(protectedSorted[mid].first<off+size) lo=mid+1 else hi=mid }
            return lo==0 || prefixEnds[lo-1]<=off
        }
        val qt=samples.mapIndexed { i,(off,size) ->
            var title=""
            if(size>=2 && safe(off,size)) {
                val n=minOf(r.input.bytes(off,2).be(0,2),size-2).toInt()
                title=Mp4Tags.utf8(r.input.bytes(off+2,n)).orEmpty()
            }
            val start=add(Mp4Properties.scale(times[i],ts),delay)
            val end=if(i+1<times.size) add(Mp4Properties.scale(times[i+1],ts),delay) else 0
            Timed(start,end,title)
        }.toMutableList()
        if(completed && qt.size==times.size) {
            val last=qt.last(); val delta=time-times.last()
            val placeholder=movieTs>0 && movieDur>0 && last.start>=Mp4Properties.scale(movieDur,movieTs) && (delta==1L || delta==ts)
            if(!placeholder) qt[qt.lastIndex]=last.copy(end=add(Mp4Properties.scale(time,ts),delay))
        }
        return result(qt,true,saturated)
    }
}
