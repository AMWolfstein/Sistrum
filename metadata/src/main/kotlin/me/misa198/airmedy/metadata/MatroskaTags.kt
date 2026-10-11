// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxLabel cfbb45d13135a76f5df6781fdeec75f5cfe63bf9, internal/matroska/ and mapping/matroska.go.
// Copyright (c) 2026 Cole Springer, MIT; see metadata/THIRD-PARTY-NOTICES.
package me.misa198.airmedy.metadata

internal class MatroskaTags(val eb: Ebml,val b: Builder) {
    private data class Tag(val name: ByteArray,val value: ByteArray?,val binary: Long,val sub: List<Tag>)
    private data class Group(val scope: String,val target: String,val tags: List<Tag>)
    private data class Contribution(val key: String,val value: String,val scope: String)
    private val groups=mutableListOf<Group>()
    fun read(el: EbmlElement) {
        eb.children(el) {g -> if(g.id==0x7373L) {
            var level=0L;var target="";var track=false;var edition=false;var chapter=false
            val tags=mutableListOf<Tag>()
            eb.children(g) {c -> when(c.id) {
                0x63c0L->eb.children(c) {when(it.id) {0x68caL->level=eb.uint(it);0x63caL->target=eb.string(it);0x63c5L->track=true;0x63c9L->edition=true;0x63c4L->chapter=true}}
                0x67c8L->tags+=simple(c)
            } }
            if(level==0L) level=keywordLevel(AsfText.upper(AsfText.trim(target)))
            if(level==0L) level=50
            val scope=when {chapter->"chapter";edition->"edition";level<0 || level>=60->"edition";level>=40 && !track->"album";else->"track"}
            groups+=Group(scope,target.ifEmpty {java.lang.Long.toUnsignedString(level)},tags)
        } }
    }
    private fun simple(el: EbmlElement): Tag {
        var name=byteArrayOf();var value: ByteArray?=null;var binary=0L;val sub=mutableListOf<Tag>()
        eb.children(el) {when(it.id) {0x45a3L->name=eb.raw(it);0x4487L->value=eb.raw(it);0x4485L->binary=minOf(it.size,64*1024*1024);0x67c8L->sub+=simple(it)}}
        return Tag(name,value,binary,sub)
    }
    private fun describe(t: Tag,indent: String) {
        val value=t.value ?: byteArrayOf()
        b.native+=NativeBlock(indent+nativeUtf8(t.name),value.size.toLong(),if(t.binary>0) "<${t.binary} binary bytes>" else nativeUtf8(value))
        for(sub in t.sub) describe(sub,indent+"  ")
    }
    fun project(title: ByteArray?) {
        val contributions=mutableListOf<Contribution>();val invalid=linkedMapOf<String,ByteArray>()
        fun add(raw: ByteArray,value: String,scope: String) {
            val name=nativeUtf8(raw)
            val key=key(name)
            if(key==null) {if(AsfText.trim(name).isNotEmpty() && !technical(AsfText.upper(AsfText.trim(name)))) invalid[raw.latin()]=raw;return}
            val pair=if(key in listOf("TRACKNUMBER","DISCNUMBER")) numberTotal(value,false) else value to ""
            // A present empty string is a canonical value, not an absent tag.
            if(pair==value to "") contributions+=Contribution(key,value,scope)
            else {if(pair.first.isNotEmpty()) contributions+=Contribution(key,pair.first,scope);if(pair.second.isNotEmpty()) contributions+=Contribution(if(key=="TRACKNUMBER") "TRACKTOTAL" else "DISCTOTAL",pair.second,scope)}
        }
        if(title!=null) contributions+=Contribution("TITLE",sanitizeUtf8(title),"album")
        for(g in groups) {
            b.native+=NativeBlock("Tag",g.tags.size.toLong(),"scope=${g.scope} target=${g.target}","tags")
            for(t in g.tags) {describe(t,"  ");if(t.value!=null) add(t.name,sanitizeUtf8(t.value),g.scope)}
        }
        val byKey=contributions.groupBy {it.key}
        for((key,cs) in byKey) {
            val scopes=cs.map {it.scope}.distinct();val primary=if("album" in scopes) "album" else scopes.first()
            val present=hashSetOf<String>();val values=mutableListOf<String>()
            for(scope in listOf(primary)+scopes.filter {it!=primary}) for(c in cs.filter {it.scope==scope}) {
                val fold=AsfText.fold(c.value)
                if((scope==primary && key!="TITLE") || fold !in present) values+=c.value
                present+=fold
            }
            b.tags[key]=values
        }
        val conflicts=byKey.filterValues {cs -> cs.map {it.scope}.distinct().size>1 && cs.map {AsfText.fold(it.value)}.distinct().size>1}.keys
        for((ks,cs) in contributions.groupBy {it.key to it.scope}) b.families+=FamilyValue(ks.first,"matroska",cs.map {it.value},ks.first !in conflicts,scope=ks.second)
        for(v in b.tags["ENCODER"].orEmpty()) if(transcoder(v)) b.warn("inherited-encoder","inherited encoder stamp: ${snippet(v)}")
        for(key in b.families.filter {!it.selected}.map {it.key}.distinct()) b.warn("conflicting-families","multiple source fields supplied conflicting values ($key)")
        for(raw in invalid.values) b.warn("invalid-tag-key","tag key not represented in canonical tags (not carried): ${matroskaWarningSnippet(raw)}")
    }
    companion object {
        private val mapping=mapOf("ALBUM_ARTIST" to "ALBUMARTIST","LEAD_PERFORMER" to "ARTIST","DATE" to "RECORDINGDATE","DATE_RECORDED" to "RECORDINGDATE","DATE_RELEASED" to "RELEASEDATE","DATE_RELEASE" to "RELEASEDATE","DATE_ORIGINAL" to "ORIGINALDATE","ORIGINAL_DATE" to "ORIGINALDATE","ENCODED_BY" to "ENCODEDBY","PART_NUMBER" to "TRACKNUMBER","TOTAL_PARTS" to "TRACKTOTAL","DISC" to "DISCNUMBER","TOTAL_DISCS" to "DISCTOTAL","CATALOG_NUMBER" to "CATALOGNUMBER","PUBLISHER" to "LABEL","REMIXED_BY" to "REMIXER","CONTENT_GROUP" to "GROUPING","DJ_MIXER" to "DJMIXER","DJ MIXER" to "DJMIXER","DJ-MIXER" to "DJMIXER","MUSICBRAINZ_ALBUMSTATUS" to "RELEASESTATUS","MUSICBRAINZ_ALBUMTYPE" to "RELEASETYPE")
        private fun technical(s: String)=s in setOf("DURATION","BPS","NUMBER_OF_FRAMES","NUMBER_OF_BYTES","NUMBER_OF_BYTES_UNCOMPRESSED","NUMBER_OF_FRAMES_UNCOMPRESSED") || s.startsWith("_STATISTICS")
        internal fun key(name: String): String? {
            val up=AsfText.upper(AsfText.trim(name))
            if(up.isEmpty() || technical(up)) return null
            return mapping[up] ?: up.takeIf {it.all {c->c.code in 32..125 && c!='='}}
        }
        private fun keywordLevel(s: String): Long=when(s) {
            "COLLECTION"->70;"EDITION","ISSUE","VOLUME","OPUS","SEASON","SEQUEL"->60
            "ALBUM","OPERA","CONCERT","MOVIE","EPISODE"->50;"PART","SESSION"->40
            "TRACK","SONG","CHAPTER"->30;"SUBTRACK","PART_BIS","MOVEMENT","SCENE"->20;"SHOT"->10;else->0
        }
    }
}
/** Go's JSON encoding replaces each invalid UTF-8 byte, unlike value sanitization. */
internal fun nativeUtf8(data: ByteArray,escapeInvalid: Boolean=false): String {
    val out=StringBuilder();var pos=0
    while(pos<data.size) {
        val c=data.u(pos);val n=when {c<128->1;c in 0xc2..0xdf->2;c in 0xe0..0xef->3;c in 0xf0..0xf4->4;else->0}
        val valid=n>0 && n<=data.size-pos && (1 until n).all {data.u(pos+it) in 128..191} && when(c) {0xe0->data.u(pos+1)>=160;0xed->data.u(pos+1)<160;0xf0->data.u(pos+1)>=144;0xf4->data.u(pos+1)<144;else->true}
        if(valid) {out.append(String(data,pos,n,Charsets.UTF_8));pos+=n} else {if(escapeInvalid) out.append("\\x%02x".format(c)) else out.append('\ufffd');pos++}
    }
    return out.toString()
}

internal fun matroskaWarningSnippet(data: ByteArray): String {
    var keep=minOf(96,data.size)
    while(keep>0 && keep<data.size && data.u(keep) and 192==128) keep--
    val left=data.size-keep
    val count=if(left<1024) "$left B" else String.format(java.util.Locale.ROOT,"%.1f KiB",left.toDouble()/1024)
    return sanitizeLine(nativeUtf8(data.copyOf(keep),true)+(if(left>0) "…[+$count]" else ""))
}
