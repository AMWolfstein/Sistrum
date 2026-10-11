// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxLabel cfbb45d13135a76f5df6781fdeec75f5cfe63bf9, internal/matroska/chapters.go.
// Copyright (c) 2026 Cole Springer, MIT; see metadata/THIRD-PARTY-NOTICES.
package com.amwolfstein.sistrum.metadata

internal class MatroskaChapters(val eb: Ebml,val b: Builder) {
    private data class Timed(val ns: Long,val chapter: Chapter)
    fun read(el: EbmlElement) {
        val editions=mutableListOf<Pair<Boolean,List<Timed>>>()
        eb.children(el) {ed -> if(ed.id==0x45b9L) {
            var default=false;val chapters=mutableListOf<Timed>()
            eb.children(ed) {when(it.id) {0x45dbL->if(eb.uint(it)!=0L) default=true;0xb6L->chapters+=atom(it)}}
            editions+=default to chapters
        } }
        if(editions.isEmpty()) return
        val selected=editions.firstOrNull {it.first} ?: editions.first()
        b.chapters+=selected.second.sortedBy {it.ns}.map {it.chapter}
        b.native+=NativeBlock("Chapters",selected.second.size.toLong(),"default edition"+if(editions.size>1) " (${editions.size} editions total)" else "","chapters")
    }
    private fun atom(el: EbmlElement): Timed {
        var start=0L;var end=0L;var hidden=false;var disabled=false;var display=false
        var title="";var language="";var ietf=""
        eb.children(el) {c -> when(c.id) {
            0x91L->start=ns(eb.uint(c));0x92L->end=ns(eb.uint(c));0x98L->hidden=eb.uint(c)!=0L;0x4598L->disabled=eb.uint(c)==0L
            0x80L->if(!display) {
                display=true;var titled=false
                eb.children(c) {when(it.id) {
                    0x85L->if(!titled) {title=valid(eb.raw(it));titled=true}
                    0x437cL->language=lang(eb.raw(it));0x437dL->ietf=lang(eb.raw(it))
                } }
            }
        } }
        return Timed(start,Chapter(ms(start),if(end>start) ms(end) else 0,title,language,ietf,hidden,disabled))
    }
    private fun valid(raw: ByteArray): String=if(nativeUtf8(raw).toByteArray(Charsets.UTF_8).contentEquals(raw)) nativeUtf8(raw) else ""
    private fun lang(raw: ByteArray)=valid(raw).takeUnless {it.equals("und",true)} ?: ""
    private fun ns(v: Long)=if(v<0) Long.MAX_VALUE else v
    private fun ms(v: Long)=AudioProperties(durationNs=v).durationMs
}
