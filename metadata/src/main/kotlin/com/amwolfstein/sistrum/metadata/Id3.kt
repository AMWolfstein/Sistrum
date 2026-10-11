// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxLabel cfbb45d13135a76f5df6781fdeec75f5cfe63bf9, internal/id3/ (read only).
// Copyright (c) 2026 Cole Springer, MIT; see metadata/THIRD-PARTY-NOTICES.
package com.amwolfstein.sistrum.metadata

import java.util.Locale

internal data class Frame(val id: String, val body: ByteArray, val size: Int, val opaque: Boolean = false, val picture: Picture? = null)
internal class Id3Tag(val version: Int, val frames: List<Frame>, val padding: Int, val malformed: String = "", val tail: Int = 0, val ignored: String = "") {
    val note get() = "${frames.size} frames" + if (tail > 0) ", $tail unparsed byte(s)" else ""
    fun blocks(): List<NativeBlock> = frames.map { NativeBlock("  ${it.id}", it.size.toLong(), frameNote(it)) }
}
internal object Id3 {
    fun tagSize(header: ByteArray): Long? {
        if (header.size < 10 || header.latin(0,3) != "ID3" || header.u(3) == 255 || header.u(4) == 255 ||
            (6..9).any { header.u(it) >= 128 }) return null
        return 10L + header.sync(6) + if (header.u(5) and 16 != 0) 10 else 0
    }
    fun parse(input: Input, offset: Long, available: Long): Id3Tag {
        val h = input.bytes(offset, 10)
        val total = tagSize(h) ?: throw ReadFailure(ErrorClass.INVALID_DATA, "not an ID3v2 header")
        val major = h.u(3)
        if (major !in 2..4) throw ReadFailure(ErrorClass.INVALID_DATA, "ID3v2 major version $major out of range")
        if (total > input.options.maxTagBytes) throw ReadFailure(ErrorClass.SIZE_TOO_LARGE, "ID3 region exceeds allocation limit")
        if (major == 2 && h.u(5) and 64 != 0) return Id3Tag(2, emptyList(),0,ignored=
            "the ID3v2.2 tag sets the compression flag, for which v2.2 defines no scheme; the tag is ignored")
        var end = minOf(available, total - if (h.u(5) and 16 != 0) 10 else 0)
        var pos = 10L
        var active = input
        var base = offset
        val tagUnsync = h.u(5) and 128 != 0
        // Whole-tag unsynchronization changes offsets; a sparse view avoids
        // materializing APIC bytes even for v2.2/v2.3 scan mode.
        if (major <= 3 && tagUnsync) {
            val view = UnsynchronizedSource(input,offset+10,(end-10).toInt())
            active = Input(view,input.options)
            base = 0
            pos = 0
            end = view.length
        }
        if (major >= 3 && h.u(5) and 64 != 0 && end-pos >= 4) {
            val ex = active.bytes(base+pos,4)
            val skip = if (major == 4) ex.sync(0).toLong() else ex.be(0,4)+4
            if (skip in 4..(end-pos)) pos += skip
        }
        return frames(active,base+pos,(end-pos).toInt(),major,tagUnsync)
    }
    private fun frames(input: Input, offset: Long, size: Int, major: Int, tagUnsync: Boolean, cap: Int = input.options.maxElements): Id3Tag {
        val out = mutableListOf<Frame>()
        var pos = 0
        val hdr = if (major == 2) 6 else 10
        while (size-pos >= hdr) {
            val h = input.bytes(offset+pos,hdr)
            if (h[0] == 0.toByte()) break
            if (out.size >= cap) throw ReadFailure(ErrorClass.SIZE_TOO_LARGE,"ID3 frames exceed element limit")
            val rawID = h.latin(0,if (major == 2) 3 else 4)
            if (rawID.first() !in 'A'..'Z' && rawID.first() !in '0'..'9' || rawID.any { it !in 'A'..'Z' && it !in '0'..'9' && it != ' ' }) break
            val id = if (major == 2) TagVocabulary.v22[rawID] ?: rawID.padEnd(4,' ') else rawID
            val n = when (major) { 2 -> h.be(3,3); 3 -> h.be(4,4); else -> h.sync(4).toLong() }
            if (n > size-pos-hdr) return Id3Tag(major,out,0,id,size-pos)
            val flags = if (major == 2) 0 else h.u(9)
            val opaque = (major == 2 && rawID !in TagVocabulary.v22) || id.contains(' ') ||
                (if (major == 3) flags and 192 != 0 else flags and 12 != 0)
            val unsync = major == 4 && (tagUnsync || flags and 2 != 0)
            val group = if (major == 3) flags and 32 != 0 else flags and 64 != 0
            val dl = major == 4 && flags and 1 != 0
            var payloadInput = input
            var payloadOffset = offset+pos+hdr
            var payloadSize = n.toInt()
            if (unsync) {
                val view = UnsynchronizedSource(input,payloadOffset,payloadSize)
                payloadInput = Input(view,input.options)
                payloadOffset = 0
                payloadSize = view.length.toInt()
            }
            if (!opaque) {
                if (group && payloadSize >= 1) { payloadOffset++; payloadSize-- }
                if (dl && payloadSize >= 4) { payloadOffset += 4; payloadSize -= 4 }
            }
            if (id == "APIC" && !opaque) {
                val p = if (input.options.pictures == Pictures.NONE) null else PictureReader.read(payloadInput,payloadOffset,payloadSize,rawID == "PIC")
                val cleanSize = if (rawID == "PIC" && payloadSize >= 5) payloadSize+picMime(payloadInput.bytes(payloadOffset+1,3).latin()).length-2 else payloadSize
                out += Frame(id,byteArrayOf(),cleanSize,picture=p)
            } else {
                val body = payloadInput.bytes(payloadOffset,payloadSize)
                out += Frame(id,body,body.size,opaque)
            }
            pos += hdr+n.toInt()
        }
        return Id3Tag(major,out,size-pos)
    }
    fun project(tag: Id3Tag, b: Builder, pictureMode: Pictures): Boolean {
        val contributions = mutableListOf<Triple<String,String,String>>()
        val localWarnings = mutableListOf<Warning>()
        val chapterWarnings = mutableListOf<Warning>()
        val lyricWarnings = mutableListOf<Warning>()
        val dates = mutableMapOf<String,String>()
        val chapters = mutableListOf<Pair<String,Chapter>>()
        val tocs = mutableListOf<Pair<Boolean,List<String>>>()
        var numeric = false
        fun emit(key: String, values: List<String>, source: String) { values.forEach { contributions += Triple(key,it,source) } }
        if (tag.malformed.isNotEmpty()) localWarnings += Warning("malformed-tag-entry",
            "the ${snippet(tag.malformed)} frame declares more bytes than the tag holds; the ${tag.tail} byte(s) from it to the end of the tag could not be read")
        if (tag.ignored.isNotEmpty()) localWarnings += Warning("malformed-tag-entry",tag.ignored)
        for (f in tag.frames) {
            if (f.opaque) continue
            when (f.id) {
                "APIC" -> if (f.picture != null) b.pictures += f.picture
                "TXXX" -> described(f.body,1)?.let { (desc, vals) ->
                    val key = TagVocabulary.userTextKey(desc)
                    if (key == null) localWarnings += Warning("invalid-tag-key", "tag key not represented in canonical tags (not carried): ${snippet(desc)}")
                    else emit(key,vals,"TXXX\u0000"+desc.trim().uppercase(Locale.ROOT))
                }
                "COMM" -> described(f.body,4)?.let { (desc, vals) -> if (!technicalComment(desc)) emit("COMMENT",vals,"COMM") }
                "USLT" -> described(f.body,4,whole=true)?.let { (desc, vals) -> if (desc.isEmpty()) emit("LYRICS",vals,"USLT") }
                "UFID" -> {
                    val end = f.body.indexOf(0)
                    if (end >= 0 && f.body.latin(0,end) == "http://musicbrainz.org") emit("MUSICBRAINZ_TRACKID",listOf(f.body.copyOfRange(end+1,f.body.size).toString(Charsets.UTF_8)),"UFID")
                }
                "TCON" -> for (v in text(f.body)) { val (names,num) = genres(v); numeric = numeric || num; emit("GENRE",names,"TCON") }
                "TRCK", "TPOS", "MVIN" -> for (v in text(f.body)) {
                    val keys = when(f.id) { "TRCK" -> "TRACKNUMBER" to "TRACKTOTAL"; "TPOS" -> "DISCNUMBER" to "DISCTOTAL"; else -> "MOVEMENT" to "MOVEMENTTOTAL" }
                    val split = numberTotal(v,f.id == "MVIN")
                    if (split.first.isNotEmpty()) emit(keys.first,listOf(split.first),f.id)
                    if (split.second.isNotEmpty()) emit(keys.second,listOf(split.second),f.id)
                }
                "TYER", "TDAT", "TIME", "TORY", "TDRC", "TDRL", "TDOR" -> dates[f.id] = text(f.body).firstOrNull().orEmpty()
                "TIPL", "IPLS" -> {
                    val flat = text(f.body)
                    for (i in 0 until flat.size-1 step 2) {
                        val key = when(flat[i].trim().lowercase(Locale.ROOT)) {
                            "producer" -> "PRODUCER"; "engineer" -> "ENGINEER"; "mix", "mixer" -> "MIXER"; "arranger" -> "ARRANGER"
                            "dj-mix", "djmixer", "dj-mixer", "dj mix", "dj mixer", "dj_mixer" -> "DJMIXER"; else -> null
                        }
                        if (key != null && flat[i+1].isNotEmpty()) emit(key,listOf(flat[i+1]),f.id)
                    }
                }
                "MVNM" -> emit("MOVEMENTNAME",text(f.body),f.id)
                "CHAP" -> decodeChapter(f.body,tag.version)?.let { chapters += it }
                "CTOC" -> decodeToc(f.body)?.let { tocs += it }
                "SYLT" -> decodeLyrics(f.body,lyricWarnings)?.let { b.lyrics += it }
                else -> if (f.id.startsWith('T')) TagVocabulary.parseKey(f.id)?.let { emit(TagVocabulary.textFrames[f.id] ?: it,text(f.body),f.id) }
            }
            if (f.id == "APIC" && f.picture == null && pictureMode != Pictures.NONE)
                localWarnings += Warning("invalid-picture", "APIC: invalid picture data")
        }
        val year = dates["TYER"].orEmpty().trim()
        var date = year
        if (year.matches(Regex("[0-9]{4}")) && dates["TDAT"].orEmpty().matches(Regex("[0-9]{4}"))) {
            val ddmm = dates.getValue("TDAT")
            date += "-${ddmm.substring(2)}-${ddmm.substring(0,2)}"
            val time = dates["TIME"].orEmpty()
            if (time.matches(Regex("[0-9]{4}"))) date += "T${time.substring(0,2)}:${time.substring(2)}"
        }
        val recording = dates["TDRC"].orEmpty().ifEmpty { date }
        if (recording.isNotEmpty()) emit("RECORDINGDATE",listOf(recording),if (dates["TDRC"].orEmpty().isNotEmpty()) "TDRC" else "TYER")
        if (dates["TDRL"].orEmpty().isNotEmpty()) emit("RELEASEDATE",listOf(dates.getValue("TDRL")),"TDRL")
        val original = dates["TDOR"].orEmpty().ifEmpty { dates["TORY"].orEmpty() }
        if (original.isNotEmpty()) emit("ORIGINALDATE",listOf(original),if (dates["TDOR"].orEmpty().isNotEmpty()) "TDOR" else "TORY")
        contributions.forEach { (key,value,_) -> b.tags.getOrPut(key) { mutableListOf() } += value }
        for ((key,group) in contributions.groupBy { it.first }) {
            val selected = group.map { it.third }.distinct().size <= 1 || group.map { it.second.trim().lowercase(Locale.ROOT) }.distinct().size <= 1
            b.families += FamilyValue(key,"id3v2",group.map { it.second },selected)
        }
        if (chapters.isNotEmpty()) {
            val toc = tocs.firstOrNull { it.first } ?: tocs.firstOrNull()
            val remaining = chapters.toMutableList()
            val ordered = mutableListOf<Chapter>()
            for (id in toc?.second.orEmpty()) { val index = remaining.indexOfFirst { it.first == id }; if (index >= 0) ordered += remaining.removeAt(index).second }
            ordered += remaining.map { it.second }
            b.chapters += ordered.sortedBy { it.startMs }
            if (tocs.size > 1) chapterWarnings += Warning("chapters-flattened","ID3 table of contents has a nested hierarchy; chapters were flattened to a single ordered list")
        }
        b.warnings += chapterWarnings + lyricWarnings + localWarnings
        return numeric
    }
    fun encoderWarnings(tag: Id3Tag): List<Warning> = tag.frames.filter { !it.opaque && it.id in listOf("TSSE","TENC") }
        .flatMap { text(it.body) }.filter(::transcoder).map { Warning("inherited-encoder","inherited encoder stamp: ${snippet(it)}") }
    private fun decodeChapter(body: ByteArray, version: Int): Pair<String,Chapter>? {
        val end = body.indexOf(0)
        if (end < 0 || body.size-end-1 < 16) return null
        val pos = end+1
        val start = body.be(pos,4)
        if (start == 0xffffffffL) return null
        val until = body.be(pos+4,4).let { if (it == 0xffffffffL) 0 else it }
        var title = ""
        val data = body.copyOfRange(pos+16,body.size)
        try {
            val subs = frames(Input(byteSource(data),ReadOptions()),0,data.size,maxOf(version,3),false,16)
            title = subs.frames.firstOrNull { it.id == "TIT2" }?.let { text(it.body).firstOrNull() }.orEmpty()
        } catch (_: ReadFailure) { /* Upstream ignores over-cap chapter subframes. */ }
        return body.latin(0,end) to Chapter(start,until,title)
    }
    private fun decodeToc(body: ByteArray): Pair<Boolean,List<String>>? {
        val end = body.indexOf(0)
        if (end < 0 || body.size-end-1 < 2) return null
        val children = mutableListOf<String>()
        var pos = end+3
        repeat(body.u(end+2)) {
            if (pos < body.size) {
                val next = (pos until body.size).firstOrNull { body[it] == 0.toByte() }
                if (next != null) { children += body.latin(pos,next-pos); pos = next+1 }
            }
        }
        return (body.u(end+1) and 2 != 0) to children
    }
    private fun decodeLyrics(body: ByteArray, warnings: MutableList<Warning>): SyncedLyrics? {
        if (body.size < 6 || body.u(0) > 3) return null
        val text = Encoded(body.u(0),body)
        val desc = text.cut(6) ?: return null
        if (body.u(4) != 2) { warnings += Warning("synced-lyrics-timestamp-format","a SYLT frame uses a non-millisecond timestamp format, which is not modeled; it was skipped"); return null }
        if (body.u(5) != 1) { warnings += Warning("synced-lyrics-content-type","a SYLT frame carries a non-lyric content type, which is not modeled; it was skipped"); return null }
        var pos = desc.second
        val lines = mutableListOf<SyncedLine>()
        while (pos < body.size) {
            val line = text.cut(pos) ?: break
            if (body.size-line.second < 4) break
            if (lines.size >= 65536) {
                warnings += Warning("synced-lyrics-truncated","a SYLT frame carried more than 65536 synced-lyric lines; the lines past the limit were dropped on read"); break
            }
            var value = line.first
            value = when { value.startsWith("\r\n") -> value.substring(2); value.startsWith('\r') || value.startsWith('\n') -> value.substring(1); else -> value }
            lines += SyncedLine(body.be(line.second,4),value)
            pos = line.second+4
        }
        if (lines.isEmpty()) return null
        val lang = body.latin(1,3).trimEnd('\u0000',' ').let { if (it.equals("xxx",true)) "" else it }
        return SyncedLyrics(lang,desc.first,lines.sortedBy { it.timeMs })
    }
}
internal fun text(body: ByteArray): List<String> = if (body.isEmpty()) emptyList() else Encoded(body.u(0).let { if (it > 3) 0 else it },body).strings(1)
internal fun described(body: ByteArray, start: Int, whole: Boolean = false): Pair<String,List<String>>? {
    if (body.size < start || body.u(0) > 3) return null
    val decoder = Encoded(body.u(0),body)
    val desc = decoder.cut(start) ?: return null
    return desc.first to if (whole) listOf(decoder.decode(desc.second,body.size)) else decoder.strings(desc.second)
}
internal class Encoded(private val enc: Int, private val data: ByteArray) {
    private var little = false
    private val term = if (enc in 1..2) 2 else 1
    fun cut(start: Int): Pair<String,Int>? {
        var pos = start
        while (pos+term <= data.size) {
            if (data[pos] == 0.toByte() && (term == 1 || data[pos+1] == 0.toByte())) return decode(start,pos) to pos+term
            pos += term
        }
        return null
    }
    fun strings(start: Int): List<String> {
        val out = mutableListOf<String>()
        var pos = start
        while (pos < data.size) {
            if (out.size >= 100_000) throw ReadFailure(ErrorClass.SIZE_TOO_LARGE,"ID3 text values exceed element limit")
            val part = cut(pos)
            if (part == null) { out += decode(pos,data.size); break }
            out += part.first
            pos = part.second
        }
        while (out.size > 1 && out.last().isEmpty()) out.removeAt(out.lastIndex)
        return out.ifEmpty { listOf("") }
    }
    fun decode(start: Int, end: Int): String {
        var from = start
        if (enc == 1 && end-from >= 2) {
            if (data.u(from) == 255 && data.u(from+1) == 254) { little = true; from += 2 }
            else if (data.u(from) == 254 && data.u(from+1) == 255) { little = false; from += 2 }
        }
        val charset = when(enc) { 1 -> if(little) Charsets.UTF_16LE else Charsets.UTF_16BE; 2 -> Charsets.UTF_16BE; 3 -> Charsets.UTF_8; else -> Charsets.ISO_8859_1 }
        val length = end-from
        return if (enc == 3) sanitizeUtf8(data,from,end) else String(data,from,if (enc in 1..2) length-length%2 else length,charset)
    }
}
/** Go strings.ToValidUTF8 replaces a run of invalid bytes once, rather than per byte. */
internal fun sanitizeUtf8(data: ByteArray, start: Int = 0, end: Int = data.size): String {
    val out = StringBuilder()
    var pos = start
    var validStart = start
    var invalid = false
    while (pos < end) {
        val c = data.u(pos)
        val size = when { c < 128 -> 1; c in 0xc2..0xdf -> 2; c in 0xe0..0xef -> 3; c in 0xf0..0xf4 -> 4; else -> 0 }
        var valid = size > 0 && size <= end-pos
        if (valid && size > 1) {
            valid = (1 until size).all { data.u(pos+it) in 128..191 }
            if (valid) valid = when(c) { 0xe0 -> data.u(pos+1) >= 160; 0xed -> data.u(pos+1) < 160; 0xf0 -> data.u(pos+1) >= 144; 0xf4 -> data.u(pos+1) < 144; else -> true }
        }
        if (valid) {
            if (invalid) validStart = pos
            invalid = false
            pos += size
        } else {
            if (!invalid) { out.append(String(data,validStart,pos-validStart,Charsets.UTF_8)); out.append('\ufffd') }
            invalid = true
            pos++
        }
    }
    if (!invalid) out.append(String(data,validStart,end-validStart,Charsets.UTF_8))
    return out.toString()
}
internal fun frameNote(frame: Frame): String {
    if (frame.opaque) return "preserved (opaque)"
    val desc = when(frame.id) { "TXXX" -> described(frame.body,1)?.first; "COMM", "USLT" -> described(frame.body,4)?.first; else -> null }
    return if (desc.isNullOrEmpty()) "" else "description: ${sanitizeLine(desc)}"
}
internal fun sanitizeLine(value: String): String = buildString {
    for (c in value) {
        when {
            c.code < 32 || c.code in 127..159 -> append("\\x%02x".format(c.code))
            c.code in listOf(0x061c,0x200b,0x200e,0x200f,0x2060,0xfeff,0x2028,0x2029) || c.code in 0x202a..0x202e || c.code in 0x2066..0x2069 -> append("\\u%04x".format(c.code))
            else -> append(c)
        }
    }
}
internal fun snippet(value: String): String {
    val data = value.toByteArray(Charsets.UTF_8)
    if (data.size <= 96) return sanitizeLine(value)
    var keep = 96
    while (keep > 0 && data[keep].toInt() and 192 == 128) keep--
    val left = data.size-keep
    val count = if (left < 1024) "$left B" else String.format(Locale.ROOT,"%.1f KiB",left.toDouble()/1024)
    return sanitizeLine(String(data,0,keep,Charsets.UTF_8)+"…[+$count]")
}
internal fun transcoder(value: String): Boolean = listOf("lavf","lavc","libavformat","libavcodec").any { value.lowercase(Locale.ROOT).contains(it) }
private fun technicalComment(desc: String): Boolean {
    val up = desc.trim().uppercase(Locale.ROOT)
    return up in listOf("ITUNNORM","ITUNSMPB","ITUNPGAP","ITUNMOVI","ITUNEXTC") || up.startsWith("ITUNES_CDDB") || up.startsWith("REPLAYGAIN")
}
internal fun numberTotal(v: String, movement: Boolean): Pair<String,String> {
    val parts = v.split('/')
    fun valid(value: String): Boolean {
        val s = value.trim()
        if (s.isEmpty()) return true
        val n = s.toLongOrNull() ?: return false
        return if (movement) n in 0..65535 && s.all { it in '0'..'9' }
        else s.matches(Regex("[+-]?[0-9]+"))
    }
    if (parts.size != 2 || parts.all { it.trim().isEmpty() } || parts.any { !valid(it) }) return v to ""
    return parts[0].trim() to parts[1].trim()
}
private fun genres(raw: String): Pair<List<String>,Boolean> {
    var value = raw.trim()
    if (value.isEmpty()) return emptyList<String>() to false
    fun resolve(ref: String, canonical: Boolean): String? = when(ref) {
        "RX" -> "Remix"; "CR" -> "Cover"; else -> ref.toIntOrNull()?.takeIf { !canonical || it.toString() == ref }?.let { TagVocabulary.genres.getOrNull(it) }
    }
    if (!value.startsWith('(')) return resolve(value,true)?.let { listOf(it) to true } ?: (listOf(value) to false)
    val names = mutableListOf<String>()
    var numeric = false
    while (value.startsWith('(') && !value.startsWith("((")) {
        val end = value.indexOf(')')
        if (end < 0) break
        val ref = value.substring(1,end)
        val name = resolve(ref,false)
        names += name ?: "($ref)"
        numeric = numeric || name != null
        value = value.substring(end+1)
    }
    var rest = value.trim()
    if (rest.startsWith("((")) rest = rest.substring(1)
    if (rest.isNotEmpty() && names.lastOrNull() != rest) names += rest
    return names.ifEmpty { listOf(value.trim()) } to numeric
}
internal fun picMime(code: String): String = when(code) { "JPG" -> "image/jpeg"; "PNG" -> "image/png"; "-->" -> "-->"; else -> "image/"+code.lowercase(Locale.ROOT) }
