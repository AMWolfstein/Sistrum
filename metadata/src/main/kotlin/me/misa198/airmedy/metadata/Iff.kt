// SPDX-License-Identifier: GPL-3.0-or-later
// Adapted from WaxLabel cfbb45d13135a76f5df6781fdeec75f5cfe63bf9, internal/wav/, internal/aiff/, internal/iff/.
// Copyright (c) 2026 Cole Springer, MIT; see metadata/THIRD-PARTY-NOTICES.
package me.misa198.airmedy.metadata

/** Complete read-only RIFF/RF64/BW64 and AIFF/AIFF-C projection. */
internal object Iff {
    private data class Text(val key: String?,val id: String,val data: ByteArray) {
        val value get()=Mp4Tags.utf8(data) ?: data.latin()
    }
    private val infoKeys=mapOf("INAM" to "TITLE","IART" to "ARTIST","IPRD" to "ALBUM","ICRD" to "RECORDINGDATE","IGNR" to "GENRE","ICMT" to "COMMENT","ICOP" to "COPYRIGHT","IPRT" to "TRACKNUMBER","ITRK" to "TRACKNUMBER","ISFT" to "ENCODER","ITCH" to "ENCODEDBY","IENG" to "ENGINEER")
    private val aiffKeys=mapOf("NAME" to "TITLE","AUTH" to "ARTIST","(c) " to "COPYRIGHT","ANNO" to "COMMENT")
    private fun size(n: Long,input: Input): Int {
        if(n>input.options.maxTagBytes) throw ReadFailure(ErrorClass.SIZE_TOO_LARGE,"metadata chunk allocation limit")
        return n.toInt()
    }
    private fun clean(b: ByteArray)=b.copyOf(b.indexOf(0).let {if(it<0) b.size else it})
    private data class Info(val items: List<Text>,val unread: Int,val rescued: Boolean)
    private fun info(body: ByteArray,input: Input): Info {
        val items=mutableListOf<Text>();var pos=4;var unread=0;var fallback=false;var rescued=false
        fun plausible(p: Int): Boolean=if(p==body.size) true else p>=0 && body.size-p>=8 && (p..p+3).all {body.u(it) in 32..126} && body.le(p+4,4)<=body.size-p-8
        fun lost(start: Int,end: Int)=if((start until end).any {body[it]!=0.toByte()}) end-start else 0
        while(body.size-pos>=8 && plausible(pos)) {
            input.cap(items.size);val n=body.le(pos+4,4).toInt();val start=pos+8
            val raw=body.copyOfRange(start,start+n);val zero=raw.indexOf(0)
            if(zero>=0) unread+=lost(start+zero+1,start+n)
            val id=body.latin(pos,4);items+=Text(infoKeys[id],id,clean(raw))
            if(fallback) {rescued=true;fallback=false}
            pos=start+n
            if(n and 1!=0) {if(pos<body.size && (body[pos]==0.toByte() || plausible(pos+1))) pos++ else fallback=true}
        }
        return Info(items,unread+lost(pos,body.size),rescued)
    }
    fun read(input: Input,b: Builder,aiff: Boolean) {
        val h=input.bytes(0,12);val form=h.latin(8,4);val header=h.latin(0,4)
        if(aiff && (header!="FORM" || form !in listOf("AIFF","AIFC")) || !aiff && (header !in listOf("RIFF","RF64","BW64") || form!="WAVE")) invalid("invalid IFF header")
        b.format=if(aiff) "AIFF" else "WAV"
        val ds=if(!aiff && header!="RIFF") IffWalker.ds64(input) else null
        val declared=ds?.riff ?: if(aiff) h.be(4,4) else h.le(4,4)
        val end=if(declared<4 || declared>input.length-8) input.length else declared+8
        val (walk,distrusted)=IffWalker.recovering(input,end,aiff,ds)
        if(distrusted) b.warn("distrusted-block-size","the ${if(aiff) "FORM" else "RIFF"} header declared $declared bytes, short of the file's own chunks; the file size was used instead")
        val chunks=walk.chunks;val audio=chunks.getOrNull(walk.audio)
        var audioBytes=audio?.size ?: 0
        if(aiff) audioBytes=if(audio!=null && audio.size>=8) maxOf(0,audio.size-8-input.bytes(audio.offset,4).be(0,4)) else 0
        val fmt=chunks.firstOrNull {it.id==(if(aiff) "COMM" else "fmt ") && it.size>=if(aiff) 18 else 16}
        val formatBytes=fmt?.let {input.bytes(it.offset,minOf(it.size,if(aiff) 22 else 16).toInt())}
        val fact=if(!aiff) chunks.firstOrNull {it.id=="fact" && it.size>=4}?.let {val n=input.bytes(it.offset,4).le(0,4);ds?.samples ?: n} else null
        val ap=if(aiff) IffProperties.aiff(formatBytes,audioBytes,form) else null
        b.properties=ap?.properties ?: IffProperties.wav(formatBytes,audioBytes,fact,if(ds==null) "WAV" else header)
        val nativeValues=mutableListOf<Text>();val descriptions=mutableMapOf<IffChunk,List<NativeBlock>>()
        val infos=if(aiff) emptyList() else chunks.filter {it.id=="LIST" && it.size>=4 && input.bytes(it.offset,4).latin()=="INFO"}
        var parsedInfo: Info?=null
        if(infos.isNotEmpty()) {
            val chunk=infos.first();val result=info(input.bytes(chunk.offset,size(chunk.size,input)),input);parsedInfo=result;nativeValues+=result.items
            descriptions[chunk]=listOf(NativeBlock("LIST/INFO",chunk.size,"${result.items.size} items"+if(result.unread>0) ", ${result.unread} unparsed byte(s)" else ""))+result.items.map {NativeBlock("  ${it.id}",it.data.size.toLong())}
        }
        if(aiff) for(chunk in chunks) if(chunk.id in aiffKeys) {
            nativeValues+=Text(aiffKeys[chunk.id],chunk.id,clean(input.bytes(chunk.offset,size(chunk.size,input))))
            descriptions[chunk]=listOf(NativeBlock(chunk.id,chunk.size,"text"))
        }
        var tag: Id3Tag?=null;var id3Chunk: IffChunk?=null
        val candidates=chunks.filter {it.id in listOf("ID3 ","id3 ")}
        for(chunk in candidates) {
            try {tag=Id3.parse(input,chunk.offset,chunk.size);id3Chunk=chunk;break}
            catch(e: ReadFailure) {if(e.kind in listOf(ErrorClass.SIZE_TOO_LARGE,ErrorClass.IO)) throw e}
        }
        if(infos.size>1) b.warn("duplicate-tag-block","more than one LIST/INFO chunk; the first is authoritative and the rest are dropped on rewrite")
        if(candidates.size>1 && tag!=null) b.warn("duplicate-tag-block","more than one ${if(aiff) "ID3" else "id3"} chunk; the first that parses is authoritative and the rest are dropped on rewrite")
        if(walk.truncated) b.warn("truncated-audio","${if(aiff) "the SSND chunk" else "the data chunk"} declares more audio than the file holds; file may be truncated")
        for(id in walk.oversized) b.warn("oversized-chunk","the ${quote(id)} chunk declares more bytes than the file holds and was clamped to EOF")
        for(id in walk.unknown) b.warn("unknown-chunk-size","the ${quote(id)} chunk declares the 0xFFFFFFFF size-unknown value; its extent was taken as the rest of the file, so any chunk after it is not read")
        if(parsedInfo?.rescued==true) b.warn("malformed-tag-entry","a LIST/INFO item is not followed by its word-alignment pad byte; the list was re-synchronized to read the items after it")
        if(parsedInfo!=null && parsedInfo.unread>0 && !(infos.first()==chunks.last() && infos.first().id in walk.oversized+walk.unknown)) b.warn("malformed-tag-entry","${parsedInfo.unread} byte(s) of the LIST/INFO chunk could not be read as items; a rewrite drops them")
        if(aiff && !walk.truncated && ap!!.overstates) b.warn("truncated-audio","COMM declares more sample frames than the SSND chunk holds; the length reported is the chunk's")
        var numeric=false
        if(tag!=null) {numeric=Id3.project(tag,b,input.options.pictures);descriptions[id3Chunk!!]=listOf(NativeBlock(if(aiff) "ID3 chunk" else "id3 chunk",id3Chunk.size,"ID3v2.${tag.version}, ${tag.note}"))+tag.blocks()}
        val native=linkedMapOf<String,MutableList<String>>()
        for(t in nativeValues) {
            val key=t.key ?: continue
            if(key in listOf("TRACKNUMBER","TRACKTOTAL","DISCNUMBER","DISCTOTAL") && key in native) continue
            native.getOrPut(key) {mutableListOf()}+=t.value
        }
        if(!aiff) native["TRACKNUMBER"]?.singleOrNull()?.let {v ->
            val pair=numberTotal(v,false)
            if(pair!=(v to "")) {if(pair.first.isEmpty()) native.remove("TRACKNUMBER") else native["TRACKNUMBER"]=mutableListOf(pair.first);if(pair.second.isNotEmpty()) native["TRACKTOTAL"]=mutableListOf(pair.second)}
        }
        for((key,values) in native) if(key !in b.tags) b.tags[key]=values
        val selected=b.tags.mapValues {(_,vs) -> vs.map(::foldValue).toHashSet()}
        for(t in nativeValues) {
            val key=t.key ?: continue;if(t.value.isEmpty()) continue
            val pair=if(key=="TRACKNUMBER") numberTotal(t.value,false) else t.value to ""
            val fields=if(pair!=(t.value to "")) listOf(key to pair.first,"TRACKTOTAL" to pair.second) else listOf(key to t.value)
            for((k,v) in fields) if(v.isNotEmpty()) b.families+=FamilyValue(k,if(aiff) "aiff" else "riff",listOf(v),selected[k]?.contains(foldValue(v)) ?: true)
        }
        if(numeric) b.warn("numeric-genre","a numeric genre reference was resolved to a name")
        if(!aiff) nativeValues.filter {it.id=="ISFT" && transcoder(it.value)}.forEach {b.warn("inherited-encoder","inherited encoder stamp: ${snippet(it.value)}")}
        if(tag!=null) b.warnings+=Id3.encoderWarnings(tag)
        for(chunk in chunks) b.native+=descriptions[chunk] ?: listOf(NativeBlock(chunk.id,chunk.size,when {
            chunk==audio -> if(aiff) "sound data" else b.properties.codec
            aiff && chunk==fmt -> ap!!.raw
            else -> "preserved"
        }))
        if(walk.trailing>0) b.warn("trailing-bytes","${walk.trailing} byte(s) after the last ${if(aiff) "IFF" else "RIFF"} chunk ${if(walk.id3Tail) "are an ID3v1 tag this format preserves but does not read" else "belong to no chunk or page"}; preserved verbatim")
        if(walk.outer>0) b.warn("trailing-bytes","${walk.outer} byte(s) after the ${if(aiff) "FORM" else "RIFF"} container belong to no chunk or page; preserved verbatim")
        finish(b,if(aiff && audio!=null && audio.size<8) audio.size else audioBytes)
    }
    /** Go EqualFold/TrimSpace index, including its separate Turkish-I classes. */
    private fun foldValue(value: String): String {
        val trimmed=value.trim {it.code in 9..13 || it.code in listOf(32,0x85,0xa0,0x1680,0x2028,0x2029,0x202f,0x205f,0x3000) || it.code in 0x2000..0x200a}
        val out=StringBuilder(trimmed.length)
        trimmed.codePoints().forEach {c -> out.appendCodePoint(if(c in listOf(0x130,0x131)) c else Character.toLowerCase(Character.toUpperCase(c)))}
        return out.toString()
    }
    private fun quote(s: String)="\""+s.map { when(it) { '\\' -> "\\\\"; '"' -> "\\\""; '\n' -> "\\n"; '\r' -> "\\r"; '\t' -> "\\t"; else -> if(it.code<32 || it.code==127) "\\x%02x".format(it.code) else it.toString() } }.joinToString("")+"\""
}
