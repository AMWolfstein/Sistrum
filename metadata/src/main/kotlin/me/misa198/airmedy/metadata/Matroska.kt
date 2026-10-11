// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxLabel cfbb45d13135a76f5df6781fdeec75f5cfe63bf9, internal/matroska/parse.go.
// Copyright (c) 2026 Cole Springer, MIT; see metadata/THIRD-PARTY-NOTICES.
package me.misa198.airmedy.metadata

internal class MatroskaReader(val input: Input,val b: Builder) {
    val eb=Ebml(input)
    private var docType="";private var title: ByteArray?=null
    private var duration=0L;private var firstCluster=-1L;private var lastCluster=0L
    private val tracks=mutableListOf<AudioProperties>();private var nonAudio=false
    private val tags=MatroskaTags(eb,b)
    fun read() {
        var segment: EbmlElement?=null
        eb.children(0,input.length) {el -> when(el.id) {
            0x1a45dfa3L->eb.children(el) {if(it.id==0x4282L) docType=eb.string(it)}
            0x18538067L->if(segment==null) segment=el
        } }
        val root=segment ?: throw ReadFailure(ErrorClass.INVALID_DATA,"no Matroska Segment")
        b.format="Matroska"
        var chaptersRead=false
        eb.children(root.body,root.end,true) {el -> when(el.id) {
            0x1f43b675L->{if(firstCluster<0) firstCluster=el.start;lastCluster=maxOf(lastCluster,el.end)}
            0x1549a966L->info(el)
            0x1654ae6bL->eb.children(el) {if(it.id==0xaeL) track(it)}
            0x1254c367L->tags.read(el)
            0x1941a469L->attachments(el)
            0x1043a770L->if(!chaptersRead) {MatroskaChapters(eb,b).read(el);chaptersRead=true}
        } }
        val container=if(docType.equals("webm",true)) "WebM" else "Matroska"
        b.properties=(tracks.firstOrNull() ?: AudioProperties()).copy(container=container,durationNs=if(tracks.isNotEmpty()) duration else 0,
            bitrate=if(!nonAudio && tracks.size==1 && firstCluster>=0) averageBitrate(lastCluster-firstCluster,duration) else 0)
        val held=b.native.toList();b.native.clear()
        b.native.add(0,NativeBlock("EBML",0,"DocType ${docType.ifEmpty {"matroska"}}"))
        title?.let {b.native.add(1,NativeBlock("Info.Title",0,nativeUtf8(it)))}
        tags.project(title)
        b.native+=held.filter {it.kind.startsWith("Attachment ")}
        b.native+=held.filter {it.kind=="Chapters"}
        if(firstCluster<0 || lastCluster<=firstCluster) b.warn("no-audio","no audio essence found; file may be tag-only or truncated")
    }
    private fun info(el: EbmlElement) {
        var scale=1_000_000L;var d=0.0
        eb.children(el) {when(it.id) {0x2ad7b1L->eb.uint(it).takeIf {v->v!=0L}?.let {v->scale=v};0x4489L->d=eb.float(it);0x7ba9L->title=eb.raw(it)}}
        val n=d*(if(scale>=0) scale.toDouble() else java.lang.Long.toUnsignedString(scale).toDouble())
        duration=if(n>0 && n<(1L shl 62).toDouble()) n.toLong() else 0
    }
    private fun track(el: EbmlElement) {
        var type=0L;var codec="";var rate=0;var outputRate=0;var channels=0;var bits=0;var private: EbmlElement?=null
        eb.children(el) {c -> when(c.id) {
            0x83L->type=eb.uint(c);0x86L->codec=eb.string(c);0x63a2L->private=c
            0xe1L->eb.children(c) {a -> when(a.id) {
                0xb5L->eb.float(a).takeIf {it>0 && it<Int.MAX_VALUE}?.let {rate=it.toInt()}
                0x78b5L->eb.float(a).takeIf {it>0 && it<Int.MAX_VALUE}?.let {outputRate=it.toInt()}
                0x9fL->channels=eb.count(a);0x6264L->bits=eb.count(a)
            } }
        } }
        if(type!=2L) {nonAudio=true;return}
        var raw=codecName(codec)
        if(outputRate>0) rate=outputRate
        if(codec.startsWith("A_AAC")) {
            val suffix=when {codec.endsWith("/SBR")->"HE-AAC";codec.endsWith("/MAIN")->"AAC Main";codec.endsWith("/LC")->"AAC LC";codec.endsWith("/SSR")->"AAC SSR";codec.endsWith("/LTP")->"AAC LTP";else->"AAC"}
            val asc=private?.let {Mp4Properties.aac(input.bytes(it.body,minOf(32,it.size).toInt()))}
            raw=if(asc==null) suffix else if(asc.signalled || asc.sbr) asc.profile() else if(suffix!="AAC") suffix else asc.profile()
            if(asc!=null && outputRate==0 && asc.sbr) {
                if(asc.extensionRate>0) rate=asc.extensionRate
                val ch=if(asc.ps && asc.channel==1) 2 else asc.channels
                if(ch>0 && channels==asc.channels) channels=ch
            }
        }
        val canonical=when {raw=="HE-AAC" || raw=="HE-AAC v2" || raw=="xHE-AAC" || raw.startsWith("AAC ")->"AAC";raw=="EAC3"->"E-AC-3";else->raw}
        tracks+=AudioProperties(codec=canonical,profile=if(canonical!=raw) raw else "",sampleRate=rate,channels=channels,bitsPerSample=bits)
    }
    private fun attachments(el: EbmlElement) {
        eb.children(el) {af -> if(af.id==0x61a7L) {
            var name="";var mime="";var description=byteArrayOf();var data: EbmlElement?=null
            eb.children(af) {when(it.id) {0x466eL->name=eb.string(it);0x4660L->mime=eb.string(it);0x467eL->description=eb.raw(it);0x465cL->data=it}}
            val base=AsfText.lower(name.substringBeforeLast('.',name))
            val cover= listOf("cover","small_cover").any {base==it || base.startsWith(it+"_") && base.removePrefix(it+"_").let {s->s.isNotEmpty() && s.all {c->c in '0'..'9'}}}
            val image=AsfText.lower(mime).startsWith("image/") || mime=="application/octet-stream" && cover
            b.native+=NativeBlock("Attachment $name",minOf(data?.size ?: 0,64*1024*1024),mime+if(image) " (cover art)" else "")
            if(image && data!=null && input.options.pictures!=Pictures.NONE) {
                val region=requireNotNull(data)
                if(region.size>minOf(64*1024*1024,input.options.maxTagBytes)) throw ReadFailure(ErrorClass.SIZE_TOO_LARGE,"Matroska image limit")
                b.pictures+=PictureReader.region(input,region.body,region.size.toInt(),if(AsfText.lower(name).startsWith("cover")) 3 else 0,mime,sanitizeUtf8(description))
            }
        } }
    }
    private fun codecName(id: String): String=when {
        id=="A_FLAC"->"FLAC";id=="A_OPUS"->"Opus";id=="A_VORBIS"->"Vorbis";id.startsWith("A_AAC")->"AAC"
        id.startsWith("A_MPEG/L3")->"MP3";id.startsWith("A_MPEG/L2")->"MP2";id.startsWith("A_AC3")->"AC-3"
        id.startsWith("A_PCM")->"PCM";id.startsWith("A_")->id.substring(2);else->id
    }
}
