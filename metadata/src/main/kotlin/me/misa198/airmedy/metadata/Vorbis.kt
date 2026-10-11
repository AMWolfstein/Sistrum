// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxLabel cfbb45d13135a76f5df6781fdeec75f5cfe63bf9, internal/vorbis/ (read path).
// Copyright (c) 2026 Cole Springer, MIT; see metadata/THIRD-PARTY-NOTICES.
package me.misa198.airmedy.metadata

import java.util.Locale

internal data class Comment(val name: String, val value: String, val unseparated: Boolean=false,
                            val picture: Region?=null)
internal data class CommentList(val vendor: String, val comments: List<Comment>, val consumed: Long)
internal object Vorbis {
    val blockNames=listOf("STREAMINFO","PADDING","APPLICATION","SEEKTABLE","VORBIS_COMMENT","CUESHEET","PICTURE")
    fun blockName(code: Int)=blockNames.getOrElse(code) { "UNKNOWN" }
    fun streamInfo(input: Input, offset: Long, size: Int, container: String): AudioProperties {
        if(size<34) throw ReadFailure(ErrorClass.INVALID_DATA,"STREAMINFO is $size bytes, need 34")
        val h=input.bytes(offset,34)
        val rate=(h.u(10) shl 12) or (h.u(11) shl 4) or (h.u(12) shr 4)
        if(rate==0) throw ReadFailure(ErrorClass.INVALID_DATA,"STREAMINFO sample rate is zero")
        val samples=((h.u(13) and 15).toLong() shl 32) or h.be(14,4)
        return AudioProperties(container=container,codec="FLAC",profile="flac",sampleRate=rate,channels=((h.u(12) shr 1) and 7)+1,
            bitsPerSample=((h.u(12) and 1) shl 4 or (h.u(13) shr 4))+1,totalSamples=samples,durationNs=sampleDuration(samples,rate))
    }
    fun comments(input: Input, offset: Long, size: Int): CommentList {
        val c=Cursor(input,offset,size)
        val vendor=c.string(c.integer(false))
        val count=c.integer(false)
        val comments=ArrayList<Comment>()
        repeat(count) {
            val n=c.integer(false)
            c.ensure(n); input.cap(comments.size)
            // Scan only the bounded name first, so an image comment is never materialized as text.
            val start=c.position
            var nameEnd=-1; var scan=0
            while(scan<n && nameEnd<0) {
                val data=input.bytes(start+scan,minOf(if(scan<32) 1 else 256,n-scan))
                val eq=data.indexOf('='.code.toByte())
                if(eq>=0) nameEnd=scan+eq else scan+=data.size
                if(scan>65536) throw ReadFailure(ErrorClass.SIZE_TOO_LARGE,"Vorbis comment name exceeds 64 KiB header limit")
            }
            if(nameEnd>=0) {
                val name=String(input.bytes(start,nameEnd),Charsets.UTF_8)
                val valueOffset=start+nameEnd+1
                val valueSize=n-nameEnd-1
                if(name.equals("METADATA_BLOCK_PICTURE",true)) comments+=Comment(name,"",picture=Region(valueOffset,valueSize))
                else comments+=Comment(name,sanitizeUtf8(input.bytes(valueOffset,valueSize)))
                c.position+=n
            } else comments+=Comment("",c.string(n),true)
        }
        return CommentList(vendor,comments,c.position-offset)
    }
    fun picture(input: Input, offset: Long, size: Int): Picture {
        val c=Cursor(input,offset,size)
        val rawType=c.unsigned()
        val type=if(rawType>255) 0 else rawType.toInt()
        val mime=c.string(c.integer())
        val desc=c.string(c.integer())
        val w=c.unsigned().toInt(); val h=c.unsigned().toInt(); val d=c.unsigned().toInt(); val colors=c.unsigned().toInt()
        val n=c.integer(); c.ensure(n)
        return PictureReader.region(input,c.position,n,type,mime,desc,w,h,d,colors)
    }
    fun pictures(input: Input, comments: List<Comment>, b: Builder) {
        if(input.options.pictures==Pictures.NONE) return
        for(cm in comments) cm.picture?.let { region ->
            try {
                val decoded=Input(Base64Source(input,region.offset,region.size),input.options)
                b.pictures+=picture(decoded,0,decoded.length.toInt())
            } catch(e: ReadFailure) {
                if(e.kind==ErrorClass.SIZE_TOO_LARGE || e.kind==ErrorClass.IO) throw e
                b.warn("invalid-picture",e.message.orEmpty())
            }
        }
    }
    fun project(list: CommentList, b: Builder) {
        val names=linkedMapOf<String,MutableSet<String>>()
        val families=linkedMapOf<String,MutableList<String>>()
        val invalid=mutableListOf<Comment>()
        val unseparated=mutableListOf<Comment>()
        for(cm in list.comments) {
            if(cm.unseparated) { unseparated+=cm; continue }
            if(reserved(cm.name)) continue
            val raw=TagVocabulary.parseKey(cm.name)
            val key=raw?.let { TagVocabulary.aliasKey(it) ?: it }
            if(key==null) { invalid+=cm; continue }
            b.tags.getOrPut(key) { mutableListOf() } += cm.value
            families.getOrPut(key) { mutableListOf() } += cm.value
            names.getOrPut(key) { linkedSetOf() } += cm.name.uppercase(Locale.ROOT)
        }
        for((key,values) in families) b.families+=FamilyValue(key,"vorbis",values,
            names[key]!!.size<=1 || values.map { it.trim().lowercase(Locale.ROOT) }.distinct().size<=1)
        for((number,total) in listOf("TRACKNUMBER" to "TRACKTOTAL","DISCNUMBER" to "DISCTOTAL")) {
            val values=b.tags[number] ?: continue
            val totals=mutableListOf<String>()
            for(i in values.indices) {
                val pair=numberTotal(values[i],false)
                if(pair.second.isNotEmpty()) { values[i]=pair.first; totals+=pair.second }
            }
            if(total !in b.tags && totals.isNotEmpty()) b.tags[total]=totals
        }
        val chapters=linkedMapOf<Long,Chapter>()
        val starts=mutableSetOf<Long>()
        for(cm in list.comments) {
            val match=chapterName.matchEntire(cm.name) ?: continue
            val index=match.groupValues[1].toLongOrNull() ?: continue
            val old=chapters[index] ?: Chapter(0)
            if(match.groupValues[2].isNotEmpty()) chapters[index]=old.copy(title=cm.value)
            else time(cm.value.trim(),3,3_600_000_000L,1L shl 32)?.let { ms ->
                chapters[index]=old.copy(startMs=ms); starts+=index
            }
        }
        b.chapters+=chapters.toSortedMap().filterKeys { it in starts }.values.sortedBy { it.startMs }
        for(cm in list.comments.filter { it.name.equals("SYNCEDLYRICS",true) }) {
            val lines=lyrics(cm.value,b)
            if(lines.isNotEmpty()) { b.lyrics+=SyncedLyrics("","",lines); break }
        }
        val vendorStamp=transcoder(list.vendor)
        val echoed=vendorStamp && list.comments.any { it.name.equals("ENCODER",true) && it.value.equals(list.vendor,true) }
        if(vendorStamp) b.warn("inherited-encoder",if(echoed) "transcoder stamp in vendor string and encoder comment: ${snippet(list.vendor)}"
            else "container vendor string (distinct from the ENCODER tag) is a transcoder stamp: ${snippet(list.vendor)}")
        for(cm in list.comments) if(cm.name.equals("ENCODER",true) && transcoder(cm.value) && !(echoed && cm.value.equals(list.vendor,true)))
            b.warn("inherited-encoder","inherited encoder comment: ${snippet(cm.value)}")
        for(cm in invalid) b.warn("invalid-tag-key","tag key not represented in canonical tags (not carried): ${snippet(cm.name)}")
        if(unseparated.isNotEmpty()) b.warn("malformed-tag-entry","comment entry has no '=' separator (preserved in place, not carried): ${snippet(unseparated.first().value)}"+
            if(unseparated.size>1) " (and ${unseparated.size-1} more)" else "")
    }
    private val chapterName=Regex("CHAPTER([0-9]+)(NAME)?",RegexOption.IGNORE_CASE)
    private fun reserved(name: String)=name.equals("METADATA_BLOCK_PICTURE",true) || name.equals("SYNCEDLYRICS",true) ||
        chapterName.matchEntire(name)?.groupValues?.get(1)?.toLongOrNull()!=null
    private fun time(value: String, fields: Int, ceilingSeconds: Long, maxField: Long): Long? {
        val parts=value.split(':'); if(parts.size !in 1..fields) return null
        val seconds=parts.last().split('.',limit=2)
        val whole=parts.dropLast(1)+seconds.first()
        if(whole.any { it.isEmpty() || it.any { c -> c !in '0'..'9' } }) return null
        val nums=whole.map { it.toLongOrNull()?.takeIf { n -> n<=maxField } ?: return null }
        var total=0L; for(n in nums) total=total*60+n
        if(total>ceilingSeconds) return null
        val frac=seconds.getOrNull(1).orEmpty()
        if(frac.any { it !in '0'..'9' }) return null
        return total*1000+frac.padEnd(3,'0').take(3).toLong()
    }
    private fun lyrics(value: String, b: Builder): List<SyncedLine> {
        val lines=mutableListOf<SyncedLine>(); var offset: Long?=null; var truncated=false
        for(raw in value.removePrefix("\uFEFF").replace("\r\n","\n").replace('\r','\n').split('\n')) {
            var p=0; val times=mutableListOf<Long>()
            while(p<raw.length && raw[p]=='[') {
                val end=raw.indexOf(']',p); if(end<0) break
                val stamp=raw.substring(p+1,end)
                if(stamp.trim().lowercase(Locale.ROOT).startsWith("offset:")) {
                    val n=stamp.trim().substringAfter(':').trim().toLongOrNull()
                    if(n!=null) { if(offset==null) offset=n.coerceIn(-(1L shl 40),1L shl 40); p=end+1; continue }
                }
                val parts=stamp.trim().split(':')
                if(parts.size !in 2..3) break
                val seconds=parts.last().substringBefore('.').toLongOrNull() ?: break
                if(seconds>=60) break
                if(parts.size==3 && (parts[1].toLongOrNull() ?: 60)>=60) break
                val t=time(stamp.trim(),3,((1L shl 21)*60+59),(1L shl 21)) ?: break
                times+=t; p=end+1
            }
            val text=if(times.isNotEmpty()) raw.substring(p).removePrefix(" ") else raw.substring(p)
            for(t in times) {
                if(lines.size>=65536) { truncated=true; break }
                lines+=SyncedLine(t,text)
            }
            if(truncated) break
        }
        if(truncated) b.warn("synced-lyrics-truncated","a SYNCEDLYRICS comment carried more than 65536 synced-lyric lines; the lines past the limit were dropped on read")
        return (if(offset==null) lines else lines.map { it.copy(timeMs=(it.timeMs-offset).coerceIn(0,(1L shl 21)*60_000)) }).sortedBy { it.timeMs }
    }
}
internal fun sampleDuration(samples: Long, rate: Int): Long {
    if(rate<=0 || samples<0) return 0
    val ns=samples.toDouble()/rate*1_000_000_000
    return if(ns>=0 && ns<Long.MAX_VALUE.toDouble()) ns.toLong() else 0
}
internal class Cursor(private val input: Input, private val start: Long, private val size: Int) {
    var position=start
    fun ensure(n: Int) { if(n<0 || n.toLong()>start+size-position) throw ReadFailure(ErrorClass.INVALID_DATA,"picture block: invalid data: want $n bytes, ${start+size-position} remaining") }
    fun unsigned(big: Boolean=true): Long { ensure(4); val raw=input.bytes(position,4); position+=4; return if(big) raw.be(0,4) else raw.le(0,4) }
    fun integer(big: Boolean=true): Int {
        val n=unsigned(big)
        if(n>input.options.maxTagBytes) throw ReadFailure(ErrorClass.SIZE_TOO_LARGE,"metadata allocation limit")
        return n.toInt()
    }
    fun string(n: Int): String { ensure(n); val bytes=input.bytes(position,n); position+=n; return sanitizeUtf8(bytes) }
}
