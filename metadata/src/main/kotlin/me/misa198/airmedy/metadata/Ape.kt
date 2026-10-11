// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxLabel cfbb45d13135a76f5df6781fdeec75f5cfe63bf9, internal/ape/ and internal/mapping/ape.go.
// Copyright (c) 2026 Cole Springer, MIT; see metadata/THIRD-PARTY-NOTICES.
package me.misa198.airmedy.metadata

import java.util.Locale

internal data class ApeItem(val name: String,val flags: Long,val offset: Long,val size: Int,val value: String="",val invalidUtf8: Boolean=false) {
    val nonText get()=flags ushr 1 and 3!=0L
}
internal data class ApeTag(val version: Long,val offset: Long,val size: Long,val items: List<ApeItem>,val truncated: Boolean=false)
internal data class ApeTrailer(val start: Long,val tag: ApeTag?,val id3: ByteArray?)
internal object Ape {
    fun key(name: String): String?=names[name.trim().lowercase(Locale.ROOT)] ?: TagVocabulary.aliasKey(name.trim()) ?: TagVocabulary.parseKey(name)
    fun cover(name: String)=name.trim().lowercase(Locale.ROOT) in listOf("cover art (front)","cover art (back)")
    fun parse(input: Input,end: Long,minStart: Long=0): ApeTag? {
        if(end-minStart<32) return null
        if(input.bytes(end-32,8).latin()!="APETAGEX") return null
        val footer=input.bytes(end-24,24)
        val flags=footer.le(12,4); if(flags and (1L shl 29)!=0L) return null
        val version=footer.le(0,4); val size=footer.le(4,4); val count=footer.le(8,4)
        if(size<32 || size>end || end-size<minStart) return null
        if(size-32>input.options.maxTagBytes) throw ReadFailure(ErrorClass.SIZE_TOO_LARGE,"APE tag limit")
        var start=end-size; var extent=size
        if(flags and (1L shl 31)!=0L && start-minStart>=32 && input.bytes(start-32,8).latin()=="APETAGEX") { start-=32; extent+=32 }
        val (items,truncated)=items(input,end-size,end-32,count)
        return ApeTag(version,start,extent,items,truncated)
    }
    fun items(input: Input,start: Long,end: Long,count: Long): Pair<List<ApeItem>,Boolean> {
        val out=mutableListOf<ApeItem>(); var pos=start
        for(i in 0 until count) {
            if(end-pos<8) break
            val h=input.bytes(pos,8); pos+=8; val size=h.le(0,4); val flags=h.le(4,4)
            val name=StringBuilder(); var terminated=false
            while(pos<end) {
                if(name.length>=65536) throw ReadFailure(ErrorClass.SIZE_TOO_LARGE,"APE key header limit")
                val byte=input.bytes(pos++,1).u(0)
                if(byte==0) { terminated=true; break }; name.append(byte.toChar())
            }
            if(!terminated || size>end-pos) break
            if(size>input.options.maxTagBytes) throw ReadFailure(ErrorClass.SIZE_TOO_LARGE,"APE item limit")
            if(out.size>=input.options.maxElements) return out to true
            val text=flags ushr 1 and 3==0L
            val raw=if(text) input.bytes(pos,size.toInt()) else null
            val utf=raw?.let { Mp4Tags.utf8(it) }
            val value=if(raw==null) "" else utf ?: raw.latin()
            if(text && value.count { it=='\u0000' }>=input.options.maxElements) throw ReadFailure(ErrorClass.SIZE_TOO_LARGE,"APE text value limit")
            out+=ApeItem(String(name.toString().toByteArray(Charsets.ISO_8859_1),Charsets.UTF_8),flags,pos,size.toInt(),value,raw!=null && utf==null)
            pos+=size
        }
        return out to false
    }
    fun peel(input: Input,minStart: Long,b: Builder): ApeTrailer {
        var end=input.length; var id3: ByteArray?=null
        if(end-minStart>=128 && input.bytes(end-128,3).latin()=="TAG") {
            val raw=input.bytes(end-128,128)
            if(looksLikeV1(raw)) { id3=raw; end-=128; b.warn("trailing-id3v1","legacy ID3v1 tag follows the audio; preserved") }
        }
        val tag=parse(input,end,minStart)
        if(tag!=null) {
            end=tag.offset
            if(tag.truncated) b.warn("element-cap","the APE tag has more items than the element limit allows; the rest are not read and the file cannot be rewritten")
        }
        return ApeTrailer(end,tag,id3)
    }
    private fun values(v: String)=if(v.isEmpty()) listOf("") else v.split('\u0000').filter { it.isNotEmpty() }
    fun legacy(tag: ApeTag?,b: Builder) {
        for(it in tag?.items.orEmpty()) if(!it.nonText) {
            val key=key(it.name) ?: continue
            for(v in values(it.value)) if(v.isNotEmpty()) b.families+=FamilyValue(key,"apev2",listOf(v),b.tags[key]?.any { it.trim().equals(v.trim(),true) } ?: true,true)
        }
    }
    fun project(input: Input,tag: ApeTag?,b: Builder) {
        val groups=linkedMapOf<String,MutableList<Pair<String,String>>>(); var count=0
        for(it in tag?.items.orEmpty()) {
            if(it.nonText) {
                if(!cover(it.name) || input.options.pictures==Pictures.NONE) continue
                var pos=it.offset; var terminated=false
                while(pos<it.offset+it.size) {
                    if(pos-it.offset>=65536) throw ReadFailure(ErrorClass.SIZE_TOO_LARGE,"APE cover filename limit")
                    if(input.bytes(pos++,1).u(0)==0) { terminated=true; break }
                }
                val size=it.size-(pos-it.offset).toInt()
                if(!terminated || size==0) { b.warn("invalid-picture","invalid data: ${it.name} item "+if(!terminated) "has no NUL-terminated file name" else "carries no image bytes"); continue }
                b.pictures+=PictureReader.region(input,pos,size,if(it.name.trim().equals("Cover Art (Back)",true)) 4 else 3,"","")
                continue
            }
            val key=key(it.name) ?: continue
            for(v in values(it.value)) {
                input.cap(count++); b.tags.getOrPut(key) { mutableListOf() }+=v
                groups.getOrPut(key) { mutableListOf() }+=it.name to v
            }
        }
        for((key,cs) in groups) b.families+=FamilyValue(key,"apev2",cs.map { it.second },cs.map { it.first }.distinct().size<=1 || cs.map { it.second.trim().lowercase(Locale.ROOT) }.distinct().size<=1)
        for((number,total) in listOf("TRACKNUMBER" to "TRACKTOTAL","DISCNUMBER" to "DISCTOTAL")) {
            val numbers=b.tags[number] ?: continue; val totals=mutableListOf<String>()
            for(i in numbers.indices) { val pair=numberTotal(numbers[i],false); if(pair.second.isNotEmpty()) { numbers[i]=pair.first; totals+=pair.second } }
            if(total !in b.tags && totals.isNotEmpty()) b.tags[total]=totals
        }
        for(it in tag?.items.orEmpty()) if(!it.nonText && it.name.trim().lowercase(Locale.ROOT) in listOf("encoder","tool name","encodedby","encoded_by") && transcoder(it.value)) b.warn("inherited-encoder","inherited encoder item: ${snippet(it.value)}")
        for(it in tag?.items.orEmpty()) if(it.invalidUtf8) b.warn("invalid-text","APE item ${quote(it.name)} is not valid UTF-8; read as Latin-1")
        for(it in tag?.items.orEmpty()) if(!it.nonText && key(it.name)==null) b.warn("invalid-tag-key","tag key not represented in canonical tags (not carried): ${snippet(it.name)}")
    }
    fun native(t: ApeTrailer,b: Builder,kind: String,codec: String) {
        b.native+=NativeBlock(kind,t.start,codec)
        t.tag?.let { tag ->
            b.native+=NativeBlock("APEv${tag.version/1000}",tag.size,"${tag.items.size} items")
            for(it in tag.items) b.native+=NativeBlock("  ${it.name}",it.size.toLong(),if(it.nonText) "binary" else "")
        }
        if(t.id3!=null) b.native+=NativeBlock("ID3v1",128,"legacy, preserved")
    }
    private fun quote(s: String)="\""+s.map { when(it) { '\\' -> "\\\\"; '"' -> "\\\""; '\n' -> "\\n"; '\r' -> "\\r"; '\t' -> "\\t"; else -> if(it.code<32 || it.code==127) "\\x%02x".format(it.code) else it.toString() } }.joinToString("")+"\""
    private val names=mapOf(
        "title" to "TITLE",
        "artist" to "ARTIST",
        "album" to "ALBUM",
        "album artist" to "ALBUMARTIST",
        "composer" to "COMPOSER",
        "lyricist" to "LYRICIST",
        "producer" to "PRODUCER",
        "engineer" to "ENGINEER",
        "mixer" to "MIXER",
        "arranger" to "ARRANGER",
        "writer" to "WRITER",
        "djmixer" to "DJMIXER",
        "genre" to "GENRE",
        "track" to "TRACKNUMBER",
        "disc" to "DISCNUMBER",
        "year" to "RECORDINGDATE",
        "comment" to "COMMENT",
        "lyrics" to "LYRICS",
        "isrc" to "ISRC",
        "catalog" to "CATALOGNUMBER",
        "label" to "LABEL",
        "musicbrainz_albumstatus" to "RELEASESTATUS",
        "musicbrainz_albumtype" to "RELEASETYPE",
        "lead_performer" to "ARTIST",
        "date_recorded" to "RECORDINGDATE",
        "date_released" to "RELEASEDATE",
        "date_release" to "RELEASEDATE",
        "date_original" to "ORIGINALDATE",
        "original_date" to "ORIGINALDATE",
        "encoded_by" to "ENCODEDBY",
        "part_number" to "TRACKNUMBER",
        "total_parts" to "TRACKTOTAL",
        "total_discs" to "DISCTOTAL",
        "catalog_number" to "CATALOGNUMBER",
        "publisher" to "LABEL",
        "remixed_by" to "REMIXER",
        "content_group" to "GROUPING"
    )
}
