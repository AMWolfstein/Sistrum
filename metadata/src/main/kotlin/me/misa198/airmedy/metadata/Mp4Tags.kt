// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxLabel cfbb45d13135a76f5df6781fdeec75f5cfe63bf9, internal/mp4/ilst.go, qtmeta.go and internal/mapping/mp4.go.
// Copyright (c) 2026 Cole Springer, MIT; see metadata/THIRD-PARTY-NOTICES.
package me.misa198.airmedy.metadata

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale

internal data class Mp4Contribution(val key: String,val value: String,val source: String)
internal data class Mp4Item(val atom: Mp4Atom,val display: String,val owned: Boolean=false,
    val contributions: List<Mp4Contribution> = emptyList(),val pictures: List<Picture> = emptyList(),val numeric: Boolean=false,val invalidKey: String?=null)
internal data class Mp4Text(val atom: Mp4Atom,val contribution: Mp4Contribution?)
internal data class Mp4Data(val type: Int,val offset: Long,val size: Int)
internal object Mp4Tags {
    fun label(id: String)=if(id.startsWith("\u00a9")) "(c)"+id.substring(1) else id
    fun utf8(p: ByteArray): String?=try { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(p)).toString() } catch(e: java.nio.charset.CharacterCodingException) { null }
    private fun validKey(name: String)=name.isNotEmpty() && name.all { it.code in 32..125 && it!='=' && it !in 'a'..'z' }
    private fun freeKey(name: String)=freeform[name.trim().uppercase(Locale.ROOT)] ?: name.takeIf(::validKey)
    private fun mdtaKey(name: String): String? {
        val bare=name.removePrefix("com.apple.quicktime.")
        return mdta[bare] ?: bare.trim().takeIf { it.isNotEmpty() }?.map { c ->
            val up=if(c in 'a'..'z') c.uppercaseChar() else c
            if(up.code in 32..125 && up!='=') up else '_'
        }?.joinToString("")
    }
    fun keys(p: ByteArray): List<String> {
        if(p.size<8) return emptyList(); val n=p.be(4,4); if(n !in 1..4096) return emptyList()
        val out=mutableListOf<String>(); var pos=8
        repeat(n.toInt()) {
            if(p.size-pos<8) return emptyList(); val size=p.be(pos,4)
            if(size<8 || size>p.size-pos) return emptyList()
            out+=utf8(p.copyOfRange(pos+8,pos+size.toInt())) ?: return emptyList(); pos+=size.toInt()
        }
        return out
    }
    private fun data(r: Mp4Reader,start: Long,end: Long): List<Mp4Data>? {
        val out=mutableListOf<Mp4Data>(); var p=start
        while(end-p>=8) {
            r.element()
            val h=r.input.bytes(p,8); val n=h.be(0,4)
            if(n<16 || n>end-p || h.latin(4,4)!="data") return null
            val t=r.input.bytes(p+8,4).be(1,3).toInt()
            if(n-16>r.input.options.maxTagBytes) throw ReadFailure(ErrorClass.SIZE_TOO_LARGE,"MP4 data limit")
            out+=Mp4Data(t,p+16,(n-16).toInt()); p+=n
        }
        return out.takeIf { p==end && it.isNotEmpty() }
    }
    fun decode(r: Mp4Reader,a: Mp4Atom,resolved: String): Mp4Item {
        if(a.size-a.header>r.input.options.maxTagBytes) throw ReadFailure(ErrorClass.SIZE_TOO_LARGE,"MP4 item limit")
        val display=resolved.ifEmpty { label(a.id) }
        var bad=Mp4Item(a,display); var start=a.payload; var key: String?=null; var source=a.id
        if(resolved.isNotEmpty()) { key=mdtaKey(resolved); source="mdta:$resolved" }
        else if(a.id=="----") {
            val names=mutableListOf<String>()
            for(want in listOf("mean","name")) {
                if(a.end-start<12) return bad
                val h=r.input.bytes(start,12); val n=h.be(0,4)
                if(n<12 || n>a.end-start || h.latin(4,4)!=want) return bad
                // Bound label allocation separately from a potentially large cover item.
                if(n-12>65536) throw ReadFailure(ErrorClass.SIZE_TOO_LARGE,"MP4 freeform label limit")
                names+=r.input.bytes(start+12,(n-12).toInt()).let { utf8(it) ?: it.latin() }; start+=n
            }
            if(names[0]!="com.apple.iTunes") return bad
            key=freeKey(names[1]); source="----:${names[1]}"
            if(key==null) return bad.copy(invalidKey=names[1])
        } else key=text[a.id]
        val atoms=data(r,start,a.end) ?: return bad
        val cover=if(resolved.isNotEmpty()) resolved=="covr" else a.id=="covr"
        if(cover) {
            val pics=if(r.input.options.pictures==Pictures.NONE) emptyList() else atoms.map {
                PictureReader.region(r.input,it.offset,it.size,3,"","")
            }
            return bad.copy(owned=true,pictures=pics)
        }
        if(resolved.isNotEmpty() && key==null) return bad
        fun values(): List<ByteArray> = atoms.map { r.input.bytes(it.offset,it.size) }
        fun contribution(k: String,v: String,s: String=source)=Mp4Contribution(k,v,s)
        if(key!=null) {
            if(atoms.any { it.type !in listOf(0,1) }) return bad
            val vs=values().map { utf8(it) ?: return bad }
            return bad.copy(owned=true,contributions=vs.map { contribution(key,it) })
        }
        when(a.id) {
            "trkn","disk" -> {
                val num=if(a.id=="trkn") "TRACKNUMBER" else "DISCNUMBER"; val total=if(a.id=="trkn") "TRACKTOTAL" else "DISCTOTAL"
                val cs=mutableListOf<Mp4Contribution>()
                for(v in values()) { if(v.size<6) return bad; if(v.be(2,2)>0) cs+=contribution(num,v.be(2,2).toString()); if(v.be(4,2)>0) cs+=contribution(total,v.be(4,2).toString()) }
                return bad.copy(owned=true,contributions=cs)
            }
            "gnre" -> {
                val vs=values().map { if(it.size!=2) return bad; TagVocabulary.genres.getOrNull(it.be(0,2).toInt()-1) ?: return bad }
                return bad.copy(owned=true,numeric=true,contributions=vs.map { contribution("GENRE",it) })
            }
            else -> {
                val k=structured[a.id] ?: return bad
                if(atoms.size!=1 || atoms[0].type !in listOf(0,21)) return bad
                val v=values()[0]; val bool=a.id in listOf("cpil","pgap","shwm")
                if(if(bool) v.size!=1 else v.size !in 1..4) return bad
                val value=if(bool) if(v[0]==0.toByte()) "0" else "1" else v.be(0,v.size).toString()
                return bad.copy(owned=true,contributions=listOf(contribution(k,value,if(bool) k else source)))
            }
        }
    }
    fun udta(r: Mp4Reader,a: Mp4Atom): Mp4Text? {
        val key=text[a.id] ?: when(a.id) { "\u00a9swr" -> "ENCODER"; "\u00a9inf" -> "COMMENT"; else -> return null }
        val p=r.whole(a); val entries=mutableListOf<Pair<Int,String>>(); var pos=0; var valid=p.size>=4
        while(valid && p.size-pos>=4) {
            if(entries.size>=4096) { valid=false; break }
            val n=p.be(pos,2).toInt(); val lang=p.be(pos+2,2).toInt()
            if(n>p.size-pos-4) { valid=false; break }
            val value=utf8(p.copyOfRange(pos+4,pos+4+n))
            if(value==null) { valid=false; break }
            entries+=lang to value; pos+=4+n
        }
        if(!valid || pos!=p.size || entries.isEmpty()) {
            entries.clear(); val atoms=data(r,a.payload,a.end) ?: return null
            for(it in atoms) { if(it.type !in listOf(0,1)) return null; entries+=0x55c4 to (utf8(r.input.bytes(it.offset,it.size)) ?: return null) }
        }
        val value=(entries.firstOrNull { it.first in listOf(0,0x55c4,0x15c7) } ?: entries.first()).second
        return Mp4Text(a,value.takeIf { it.isNotEmpty() }?.let { Mp4Contribution(key,it,"udta.${a.id}") })
    }
    fun project(items: List<Mp4Item>,texts: List<Mp4Text>,b: Builder,input: Input) {
        val count=items.sumOf { it.contributions.size.toLong() }+texts.size
        if(count>input.options.maxElements) throw ReadFailure(ErrorClass.SIZE_TOO_LARGE,"MP4 contribution limit")
        val contributions=items.flatMap { it.contributions }.toMutableList(); val held=contributions.map { it.key }.toSet()
        val secondary=mutableListOf<Mp4Contribution>()
        for(c in texts.mapNotNull { it.contribution }) if(c.key in held) secondary+=c else contributions+=c
        val groups=linkedMapOf<String,MutableList<Mp4Contribution>>()
        for(c in contributions) { b.tags.getOrPut(c.key) { mutableListOf() }+=c.value; groups.getOrPut(c.key) { mutableListOf() }+=c }
        for((key,cs) in groups) b.families+=FamilyValue(key,"mp4",cs.map { it.value },cs.map { it.source }.distinct().size<=1 || cs.map { it.value.trim().lowercase(Locale.ROOT) }.distinct().size<=1)
        for(c in secondary) b.families+=FamilyValue(c.key,"mp4",listOf(c.value),b.tags[c.key]?.any { it.trim().equals(c.value.trim(),true) } ?: false)
        b.pictures+=items.flatMap { it.pictures }
        if(items.any { it.numeric }) b.warn("numeric-genre","a numeric genre reference was resolved to a name")
        for(v in b.tags["ENCODER"].orEmpty()) if(transcoder(v)) b.warn("inherited-encoder","inherited encoder stamp: ${snippet(v)}")
        for(name in items.mapNotNull { it.invalidKey }.distinct()) b.warn("invalid-tag-key","tag key not represented in canonical tags (not carried): ${snippet(name)}")
    }
    private val structured=mapOf("cpil" to "COMPILATION","stik" to "MEDIATYPE","rtng" to "ITUNESADVISORY","tmpo" to "BPM","\u00a9mvi" to "MOVEMENT","\u00a9mvc" to "MOVEMENTTOTAL","pgap" to "ITUNESGAPLESS","shwm" to "SHOWMOVEMENT")
    private val text=mapOf(
        "\u00a9nam" to "TITLE",
        "\u00a9ART" to "ARTIST",
        "aART" to "ALBUMARTIST",
        "\u00a9alb" to "ALBUM",
        "\u00a9wrt" to "COMPOSER",
        "\u00a9day" to "RECORDINGDATE",
        "\u00a9cmt" to "COMMENT",
        "\u00a9gen" to "GENRE",
        "\u00a9too" to "ENCODER",
        "cprt" to "COPYRIGHT",
        "\u00a9grp" to "GROUPING",
        "\u00a9lyr" to "LYRICS",
        "desc" to "DESCRIPTION",
        "ldes" to "LONGDESCRIPTION",
        "soal" to "ALBUMSORT",
        "soaa" to "ALBUMARTISTSORT",
        "soar" to "ARTISTSORT",
        "sonm" to "TITLESORT",
        "soco" to "COMPOSERSORT",
        "\u00a9wrk" to "WORK",
        "\u00a9mvn" to "MOVEMENTNAME",
        "\u00a9enc" to "ENCODEDBY"
    )
    private val freeform=mapOf(
        "MUSICBRAINZ TRACK ID" to "MUSICBRAINZ_TRACKID",
        "MUSICBRAINZ RELEASE TRACK ID" to "MUSICBRAINZ_RELEASETRACKID",
        "MUSICBRAINZ ALBUM ID" to "MUSICBRAINZ_ALBUMID",
        "MUSICBRAINZ RELEASE GROUP ID" to "MUSICBRAINZ_RELEASEGROUPID",
        "MUSICBRAINZ ARTIST ID" to "MUSICBRAINZ_ARTISTID",
        "MUSICBRAINZ ALBUM ARTIST ID" to "MUSICBRAINZ_ALBUMARTISTID",
        "MUSICBRAINZ WORK ID" to "MUSICBRAINZ_WORKID",
        "MUSICBRAINZ DISC ID" to "MUSICBRAINZ_DISCID",
        "ACOUSTID ID" to "ACOUSTID_ID",
        "ACOUSTID FINGERPRINT" to "ACOUSTID_FINGERPRINT",
        "REPLAYGAIN_TRACK_GAIN" to "REPLAYGAIN_TRACK_GAIN",
        "REPLAYGAIN_TRACK_PEAK" to "REPLAYGAIN_TRACK_PEAK",
        "REPLAYGAIN_ALBUM_GAIN" to "REPLAYGAIN_ALBUM_GAIN",
        "REPLAYGAIN_ALBUM_PEAK" to "REPLAYGAIN_ALBUM_PEAK",
        "BARCODE" to "BARCODE",
        "CATALOGNUMBER" to "CATALOGNUMBER",
        "LABEL" to "LABEL",
        "MEDIA" to "MEDIA",
        "ISRC" to "ISRC",
        "ORIGINALDATE" to "ORIGINALDATE",
        "NARRATOR" to "NARRATOR",
        "LYRICIST" to "LYRICIST",
        "MUSICBRAINZ ALBUM RELEASE COUNTRY" to "RELEASECOUNTRY",
        "MUSICBRAINZ ALBUM STATUS" to "RELEASESTATUS",
        "MUSICBRAINZ ALBUM TYPE" to "RELEASETYPE",
        "PRODUCER" to "PRODUCER",
        "ENGINEER" to "ENGINEER",
        "MIXER" to "MIXER",
        "ARRANGER" to "ARRANGER",
        "WRITER" to "WRITER",
        "DJMIXER" to "DJMIXER",
        "DJ MIXER" to "DJMIXER",
        "DJ_MIXER" to "DJMIXER",
        "DJ-MIXER" to "DJMIXER",
        "MUSICBRAINZ_ALBUMSTATUS" to "RELEASESTATUS",
        "MUSICBRAINZ_ALBUMTYPE" to "RELEASETYPE",
        "LEAD_PERFORMER" to "ARTIST",
        "DATE_RECORDED" to "RECORDINGDATE",
        "DATE_RELEASED" to "RELEASEDATE",
        "DATE_RELEASE" to "RELEASEDATE",
        "DATE_ORIGINAL" to "ORIGINALDATE",
        "ORIGINAL_DATE" to "ORIGINALDATE",
        "ENCODED_BY" to "ENCODEDBY",
        "PART_NUMBER" to "TRACKNUMBER",
        "TOTAL_PARTS" to "TRACKTOTAL",
        "TOTAL_DISCS" to "DISCTOTAL",
        "CATALOG_NUMBER" to "CATALOGNUMBER",
        "PUBLISHER" to "LABEL",
        "REMIXED_BY" to "REMIXER",
        "CONTENT_GROUP" to "GROUPING",
        "ITUNESADVISORY" to "ITUNESADVISORY",
        "ITUNESGAPLESS" to "ITUNESGAPLESS",
        "SHOWMOVEMENT" to "SHOWMOVEMENT",
        "BPM" to "BPM",
        "WORK" to "WORK",
        "MOVEMENTNAME" to "MOVEMENTNAME",
        "MOVEMENT" to "MOVEMENT",
        "MOVEMENTTOTAL" to "MOVEMENTTOTAL",
        "ENCODEDBY" to "ENCODEDBY"
    )
    private val mdta=mapOf(
        "title" to "TITLE",
        "artist" to "ARTIST",
        "albumartist" to "ALBUMARTIST",
        "album" to "ALBUM",
        "composer" to "COMPOSER",
        "comment" to "COMMENT",
        "description" to "DESCRIPTION",
        "genre" to "GENRE",
        "encoder" to "ENCODER",
        "software" to "ENCODER",
        "copyright" to "COPYRIGHT",
        "date" to "RECORDINGDATE",
        "creationdate" to "RECORDINGDATE",
        "publisher" to "LABEL",
        "grouping" to "GROUPING",
        "lyrics" to "LYRICS"
    )
}
