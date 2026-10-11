// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxLabel cfbb45d13135a76f5df6781fdeec75f5cfe63bf9, internal/mp4/atom.go, parse.go, native.go.
// Copyright (c) 2026 Cole Springer, MIT; see metadata/THIRD-PARTY-NOTICES.
package me.misa198.airmedy.metadata

internal data class Mp4Atom(val id: String, val offset: Long, val size: Long, val header: Int=8,
                            val truncated: Boolean=false, val children: List<Mp4Atom> = emptyList(), val childStart: Long=offset+header) {
    val payload get()=offset+header
    val end get()=offset+size
    fun find(id: String)=children.firstOrNull { it.id==id }
    fun all(id: String): List<Mp4Atom> = children.flatMap { (if(it.id==id) listOf(it) else emptyList())+it.all(id) }
}
internal class Mp4Reader(val input: Input) {
    private var count=0
    fun element() { input.cap(count++) }
    fun walk(start: Long,end: Long,top: Boolean=false,depth: Int=0): List<Mp4Atom> {
        if(depth>=64) throw ReadFailure(ErrorClass.SIZE_TOO_LARGE,"MP4 nesting limit")
        val out=mutableListOf<Mp4Atom>(); var off=start
        while(end-off>=8) {
            element()
            val h=input.bytes(off,8); val id=h.latin(4,4); var size=h.be(0,4); var header=8
            when(size) {
                1L -> { if(end-off<16) invalid("64-bit atom header truncated")
                    size=input.bytes(off+8,8).be(0,8); header=16; if(size<16) invalid("64-bit atom size below 16") }
                0L -> { if(!top) break; size=end-off }
                in 2L..7L -> invalid("atom size below 8")
            }
            val truncated=size>end-off
            if(truncated && !top) invalid("atom declares more bytes than its container")
            size=minOf(size,end-off)
            var cs=off+header
            if(id=="meta" && off+size-cs>=4 && input.bytes(cs,4).all { it==0.toByte() }) cs+=4
            val kids=if(id in containers) walk(cs,off+size,false,depth+1) else emptyList()
            out+=Mp4Atom(id,off,size,header,truncated,kids,cs)
            off+=size
        }
        if(!top && off<end) {
            var p=off
            while(p<end) { val n=minOf(8192L,end-p).toInt(); if(input.bytes(p,n).any { it!=0.toByte() }) invalid("incomplete trailing atom"); p+=n }
        }
        return out
    }
    fun whole(a: Mp4Atom): ByteArray {
        if(a.size-a.header>input.options.maxTagBytes) throw ReadFailure(ErrorClass.SIZE_TOO_LARGE,"MP4 metadata allocation limit")
        return input.bytes(a.payload,(a.size-a.header).toInt())
    }
    fun prefix(a: Mp4Atom,n: Int)=input.bytes(a.payload,minOf(n.toLong(),a.size-a.header).toInt())
    fun handler(a: Mp4Atom?): String { if(a==null) return ""; val b=prefix(a,12); return if(b.size>=12) b.latin(8,4) else "" }
    fun offsets(a: Mp4Atom): List<Long> {
        val p=whole(a); if(p.size<8) invalid("${a.id} atom too short")
        val width=if(a.id=="co64") 8 else 4; val n=p.be(4,4)
        if(n>(p.size-8)/width) invalid("${a.id} entries truncated")
        if(n>input.options.maxElements) throw ReadFailure(ErrorClass.SIZE_TOO_LARGE,"MP4 table element limit")
        return List(n.toInt()) { element(); p.be(8+it*width,width) }
    }
    companion object { private val containers=setOf("moov","trak","mdia","minf","stbl","udta","meta","ilst") }
}
internal fun invalid(message: String): Nothing=throw ReadFailure(ErrorClass.INVALID_DATA,message)
internal object Mp4 {
    /** Detect only structural headers, skipping padding without reading its body. */
    fun sniff(input: Input): Boolean {
        var off=0L
        while(off<=56 && input.length-off>=8) {
            val h=input.bytes(off,8)
            when(h.latin(4,4)) {
                "ftyp" -> return true
                "free","skip","wide" -> { val n=h.be(0,4); if(n<8 || n>64-off) return false; off+=n }
                else -> return false
            }
        }
        return false
    }
    fun read(input: Input,b: Builder) {
        val r=Mp4Reader(input); val top=r.walk(0,input.length,true)
        val fragmented=top.any { it.id=="moof" }
        val moov=top.lastOrNull { it.id=="moov" } ?: throw ReadFailure(
            if(fragmented || top.any { it.id=="styp" }) ErrorClass.UNSUPPORTED_FORMAT else ErrorClass.INVALID_DATA,"MP4 has no moov box")
        val udta=moov.find("udta"); val meta=udta?.find("meta"); val ilst=meta?.find("ilst")
        if(udta==null && moov.end-(moov.children.lastOrNull()?.end ?: moov.childStart)>0) invalid("moov unusable trailing bytes")
        if(meta!=null && ilst==null && meta.end-(meta.children.lastOrNull()?.end ?: meta.childStart)>0) invalid("meta unusable trailing bytes")
        val tables=moov.all("stbl").flatMap { it.children.filter { a -> a.id in listOf("stco","co64") } }
        val offsets=tables.associateWith { r.offsets(it) }
        val props=Mp4Properties.read(r,moov)
        val handler=r.handler(meta?.find("hdlr"))
        val keys=meta?.find("keys")?.let { Mp4Tags.keys(r.whole(it)) }.orEmpty()
        val items=ilst?.children?.map { a ->
            val index=a.id.toByteArray(Charsets.ISO_8859_1).be(0,4)
            Mp4Tags.decode(r,a,if(handler=="mdta" && index in 1..keys.size.toLong()) keys[index.toInt()-1] else "")
        }.orEmpty()
        val texts=udta?.children?.mapNotNull { Mp4Tags.udta(r,it) }.orEmpty()
        Mp4Tags.project(items,texts,b,input)
        val mdats=top.filter { it.id=="mdat" }
        val chapter=Mp4Chapters.read(r,moov,udta?.find("chpl"),offsets,mdats)
        b.chapters+=chapter.chapters
        val nonChapter=offsets.filterKeys { chapter.track==null || it.offset<chapter.track.offset || it.end>chapter.track.end }.values.flatten()
        var ranges=mdats.map { it.payload to it.end }
        if(nonChapter.isNotEmpty()) {
            val trimmed=ranges.mapNotNull { (start,end) -> nonChapter.filter { it>=start && it<end }.minOrNull()?.let { it to end } }
            if(trimmed.isNotEmpty()) ranges=trimmed
        }
        val audioBytes=ranges.sumOf { it.second-it.first }
        var padding=0L
        if(meta!=null && ilst!=null) {
            val idx=meta.children.indexOf(ilst)
            val free=meta.children.getOrNull(idx-1)?.takeIf { it.id in setOf("free","skip") }
                ?:meta.children.getOrNull(idx+1)?.takeIf { it.id in setOf("free","skip") }
            padding=free?.let { maxOf(0,it.size-8) } ?: 0
        }
        b.format="MP4"
        b.properties=props.properties.copy(bitrate=if(props.properties.durationNs>0) (audioBytes.toDouble()*8/(props.properties.durationNs.toDouble()/1_000_000_000)).let { if(it<Int.MAX_VALUE) it.toInt() else 0 } else 0,paddingBytes=padding)
        for(a in top) {
            val note=when(a.id) {
                "ftyp" -> r.prefix(a,4).let { if(it.size>=4) "file type (${it.latin()})" else "file type" }
                "moov" -> "movie box"; "mdat" -> if(props.fourcc.isEmpty()) "media data" else "${props.fourcc} media data"
                "moof" -> "movie fragment"; "free","skip" -> "padding"; else -> "preserved"
            }
            b.native+=NativeBlock(a.id,a.size,note)
        }
        if(udta!=null && udta.children.isNotEmpty()) {
            val n=udta.children.size; b.native+=NativeBlock("moov.udta",udta.size,"$n atom${if(n==1) "" else "s"}")
            for(a in udta.children) b.native+=NativeBlock("  ${Mp4Tags.label(a.id)}",a.size,when {
                a.id=="meta" -> "metadata"+if(handler.isEmpty()) "" else " ($handler handler)"
                a.id=="chpl" -> "Nero chapter list"; texts.any { it.atom==a } -> ""; else -> "preserved"
            })
        }
        if(ilst!=null) {
            val n=items.size; b.native+=NativeBlock("moov.udta.meta.ilst",ilst.size,"$n item${if(n==1) "" else "s"}"+if(handler.isEmpty()) "" else " ($handler handler)")
            for(it in items) b.native+=NativeBlock("  ${it.display}",it.atom.size,if(it.owned) "" else "preserved")
        }
        udta?.find("chpl")?.let { b.native+=NativeBlock("moov.udta.chpl",it.size,if(chapter.neroCount==0) "preserved (unparsed)" else "${chapter.neroCount} chapters (Nero, v${chapter.neroVersion})") }
        if(chapter.haveQT) b.native+=NativeBlock("moov chapter track",0,"QuickTime chapter text track")
        top.lastOrNull()?.takeIf { it.truncated && it.id !in setOf("mdat","moov") }?.let {
            b.warn("oversized-chunk","the \"${it.id}\" atom declares more bytes than the file holds and was clamped to EOF; bytes appended after the last atom read this way")
        }
        if(chapter.conflict) b.warn("chapter-source-conflict","the Nero chpl list and the QuickTime chapter text track disagree")
        if(mdats.any { it.truncated }) b.warn("truncated-audio","an mdat atom declares more audio than the file holds; file may be truncated")
        if(moov.truncated) b.warn("truncated-audio","the moov atom declares more audio than the file holds; file may be truncated")
        if(fragmented) b.warn("fragmented","fragmented MP4 (movie fragments): tags from the initial movie box are read, writing is refused")
        if(audioBytes<=0) {
            b.warnings.removeAll { it.code=="truncated-audio" }
            b.warn("no-audio","no audio essence found; file may be tag-only or truncated")
        }
    }
}
