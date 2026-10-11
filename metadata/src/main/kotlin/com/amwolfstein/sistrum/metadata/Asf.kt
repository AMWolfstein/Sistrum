// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxLabel cfbb45d13135a76f5df6781fdeec75f5cfe63bf9, internal/asf/.
// Copyright (c) 2026 Cole Springer, MIT; see metadata/THIRD-PARTY-NOTICES.
package com.amwolfstein.sistrum.metadata


internal data class AsfObject(val id: String,val offset: Long,val size: Long) {
    val body get()=offset+24
    val end get()=offset+size
}
internal class AsfReader(val input: Input,val b: Builder) {
    private var objects=0
    private var duration=0L;private var preroll=0L;private var maxBitrate=0L
    private var haveAudio=false;private var formatTag=0;private var sourceRate=0L;private var rate=0;private var channels=0;private var bits=0;private var byteRate=0L
    private val contributions=linkedMapOf<String,MutableList<Pair<String,String>>>()
    private val seen=hashSetOf<Pair<String,String>>();private val invalidKeys=mutableListOf<String>()
    private val markers=mutableListOf<Pair<Long,String>>()
    fun walk(start: Long,end: Long,count: Long=0): List<AsfObject> {
        val out=mutableListOf<AsfObject>();var pos=start
        while(end-pos>=24) {
            val h=input.bytes(pos,24);val size=h.le(16,8)
            if(size<24 || size>end-pos) break
            input.cap(objects++);out+=AsfObject(h.latin(0,16),pos,size);pos+=size
            if(count>0 && out.size>=count) break
        }
        return out
    }
    private fun scaled(ticks: Long,unit: Long): Long?=if(ticks<0 || ticks>Long.MAX_VALUE/unit) null else ticks*unit
    private fun emit(key: String?,value: String,source: String) {
        if(key.isNullOrEmpty() || value.isEmpty() || !seen.add(key to value)) return
        input.cap(seen.size-1);contributions.getOrPut(key) {mutableListOf()}+=source to value
    }
    private fun key(name: String): String? {
        val norm=AsfText.fold(name)
        if(norm in AsfNames.names) return AsfNames.names[norm]?.takeIf {it.isNotEmpty()}
        val bare=AsfText.upper(AsfText.trim(norm.substringAfter('/')))
        if(bare.isEmpty() || bare.any {it.code !in 32..125 || it=='='}) return null
        return TagVocabulary.aliasKey(bare) ?: bare
    }
    private fun text(off: Long,size: Long): String {
        if(size>input.options.maxTagBytes) throw ReadFailure(ErrorClass.SIZE_TOO_LARGE,"ASF text limit")
        val bytes=input.bytes(off,(size-size%2).toInt());var n=bytes.size
        while(n>=2 && bytes[n-1]==0.toByte() && bytes[n-2]==0.toByte()) n-=2
        // Java and Go both replace unpaired UTF-16 surrogates with U+FFFD.
        return String(bytes,0,n,Charsets.UTF_16LE)
    }
    private fun file(o: AsfObject) {
        if(o.size-24<80) return
        val h=input.bytes(o.body,80);val play=scaled(h.le(40,8),100);val pre=scaled(h.le(56,8),1_000_000)
        if(pre!=null) preroll=pre
        if(play!=null && pre!=null && play>pre) duration=play-pre
        maxBitrate=h.le(76,4)
    }
    private fun stream(o: AsfObject) {
        if(o.size-24<54) return
        val h=input.bytes(o.body,54);val len=h.le(40,4)
        if(h.latin(0,16)!=AsfGuid.AudioMedia || haveAudio || len<16 || len>o.size-24-54) return
        val w=input.bytes(o.body+54,16);haveAudio=true;formatTag=w.le(0,2).toInt()
        channels=w.le(2,2).toInt();sourceRate=w.le(4,4);rate=sourceRate.coerceAtMost(Int.MAX_VALUE.toLong()).toInt();byteRate=w.le(8,4);bits=w.le(14,2).toInt()
        if(formatTag==0x163 && len>=18) {
            val cb=input.bytes(o.body+70,2).le(0,2)
            if(cb>=18 && cb<=len-18) {val depth=input.bytes(o.body+72,2).le(0,2).toInt();if(depth in listOf(16,24)) bits=depth}
        }
    }
    private fun content(o: AsfObject) {
        if(o.size-24<10) return
        val lengths=input.bytes(o.body,10);var pos=o.body+10
        for((i,name) in listOf("Title","Author","Copyright","Description","Rating").withIndex()) {
            val n=lengths.le(i*2,2);if(n>o.end-pos) return
            if(key(name)!=null) emit(key(name),text(pos,n),name)
            pos+=n
        }
    }
    private fun records(o: AsfObject,metadata: Boolean) {
        if(o.size-24<2) return
        val count=input.bytes(o.body,2).le(0,2).toInt();var pos=o.body+2
        for(i in 0 until minOf(count,input.options.maxElements)) {
            val nameLen: Long;val valueLen: Long;val type: Int
            if(metadata) {
                if(o.end-pos<12) return
                val h=input.bytes(pos,12);nameLen=h.le(4,2);type=h.le(6,2).toInt();valueLen=h.le(8,4);pos+=12
                if(nameLen>o.end-pos || valueLen>o.end-pos-nameLen) return
            } else {
                if(o.end-pos<2) return
                nameLen=input.bytes(pos,2).le(0,2);pos+=2
                if(nameLen>o.end-pos-4) return
                val h=input.bytes(pos+nameLen,4);type=h.le(0,2).toInt();valueLen=h.le(2,2)
                if(valueLen>o.end-pos-nameLen-4) return
            }
            val name=text(pos,nameLen);pos+=nameLen+if(metadata) 0 else 4
            descriptor(name,type,pos,valueLen);pos+=valueLen
        }
    }
    private fun descriptor(name: String,type: Int,off: Long,len: Long) {
        if(AsfText.trim(name).equals("wm/picture",true) && name.none {it.code in listOf(0x130,0x131)}) {if(input.options.pictures!=Pictures.NONE) picture(off,len);return}
        if(type==1) return
        val value=when(type) {
            0->text(off,len)
            2->if(len in listOf(2L,4L)) if(input.bytes(off,len.toInt()).le(0,len.toInt())!=0L) "1" else "0" else return
            3->if(len>=4) input.bytes(off,4).le(0,4).toString() else return
            4->if(len>=8) java.lang.Long.toUnsignedString(input.bytes(off,8).le(0,8)) else return
            5->if(len>=2) input.bytes(off,2).le(0,2).toString() else return
            else->return
        }
        val key=key(name)
        if(key==null) {if(AsfText.fold(name) !in AsfNames.names) {input.cap(invalidKeys.size);invalidKeys+=name};return}
        emit(key,value,name)
    }
    private fun picture(off: Long,len: Long) {
        fun bad(message: String) {b.warn("invalid-picture","invalid data: $message")}
        if(len<5) {bad("WM/Picture value is $len bytes, too short for its header");return}
        val h=input.bytes(off,5);var pos=off+5
        fun cut(): String? {
            val start=pos
            while(off+len-pos>=2) {
                if(pos-start>=65536) throw ReadFailure(ErrorClass.SIZE_TOO_LARGE,"ASF picture label limit")
                val next=input.bytes(pos,2);pos+=2
                if(next[0]==0.toByte() && next[1]==0.toByte()) return text(start,pos-start-2)
            }
            return null
        }
        val mime=cut() ?: run {bad("WM/Picture MIME type is not NUL-terminated");return}
        val desc=cut() ?: run {bad("WM/Picture description is not NUL-terminated");return}
        val size=minOf(h.le(1,4),off+len-pos)
        if(size==0L) {bad("WM/Picture carries no image bytes");return}
        if(size>input.options.maxTagBytes) throw ReadFailure(ErrorClass.SIZE_TOO_LARGE,"ASF picture size limit")
        input.cap(b.pictures.size);b.pictures+=PictureReader.region(input,pos,size.toInt(),h.u(0),mime,desc)
    }
    private fun extension(o: AsfObject) {
        if(o.size-24<22) return
        val len=input.bytes(o.body+18,4).le(0,4);val end=o.body+22+minOf(len,o.size-46)
        for(child in walk(o.body+22,end)) if(child.id in listOf(AsfGuid.Metadata,AsfGuid.MetadataLibrary)) records(child,true)
    }
    private fun marker(o: AsfObject) {
        if(o.size-24<24) return
        val h=input.bytes(o.body,24);val count=h.le(16,4);var pos=o.body+24+h.le(22,2)
        fun short(read: Long) {b.warn("malformed-tag-entry","the Marker Object declares $count markers but ends after $read; the rest are not read")}
        for(i in 0 until count) {
            if(i>=input.options.maxElements) {b.warn("element-cap","the Marker Object has more markers than the element limit allows; the rest are not read");return}
            if(o.end-pos<30) {short(i);return}
            val entry=input.bytes(pos,30);val ns=scaled(entry.le(8,8),100);val len=entry.le(26,4)*2;pos+=30
            val n=minOf(len,o.end-pos);val raw=text(pos,n);val desc=raw.substringBefore('\u0000');pos+=n
            if(ns!=null) {input.cap(markers.size);markers+=ns to desc} else b.warn("malformed-tag-entry","marker ${i+1} has a presentation time past any duration; skipped")
            if(len>n) {if(i+1<count) short(i+1);return}
        }
    }
    fun read() {
        if(input.length<30) invalid("ASF Header truncated")
        val h=input.bytes(0,30);if(h.latin(0,16)!=AsfGuid.Header) invalid("missing ASF Header GUID")
        val declared=h.le(16,8);if(declared in 0..29) invalid("ASF Header too short")
        val headerEnd=declared.takeIf {it>=30 && it<=input.length} ?: 0L
        val window=if(headerEnd>0) headerEnd else input.length
        val end=minOf(window,64L*1024*1024,input.options.maxTagBytes.toLong())
        if(end<30) invalid("ASF Header window truncated")
        val children=walk(30,end,h.le(24,4).coerceAtMost(Int.MAX_VALUE.toLong()))
        b.format="WMA"
        for(o in children) {
            when(o.id) {AsfGuid.FileProps->file(o);AsfGuid.StreamProps->stream(o);AsfGuid.ContentDesc->content(o);AsfGuid.ExtContentDesc->records(o,false);AsfGuid.HeaderExt->extension(o);AsfGuid.Marker->marker(o)}
            b.native+=NativeBlock(objectName(o.id),o.size)
        }
        for((key,cs) in contributions) {
            b.tags[key]=cs.map {it.second}.toMutableList()
            b.families+=FamilyValue(key,"asf",cs.map {it.second},cs.map {it.first}.distinct().size<=1 || cs.map {AsfText.fold(it.second)}.distinct().size<=1)
        }
        for((num,total) in listOf("TRACKNUMBER" to "TRACKTOTAL","DISCNUMBER" to "DISCTOTAL")) b.tags[num]?.singleOrNull()?.let {v ->
            val pair=numberTotal(v,false);if(pair!=(v to "")) {if(pair.first.isEmpty()) b.tags.remove(num) else b.tags[num]=mutableListOf(pair.first);if(total !in b.tags && pair.second.isNotEmpty()) b.tags[total]=mutableListOf(pair.second)}
        }
        var dataSize=0L
        // A corrupt Header size may point inside the real Data Object. Establish
        // its packet range at a reached object boundary before probing that offset.
        val boundary=children.lastOrNull()?.end ?: 30L
        var knownPackets: Pair<Long,Long>?=null
        if(headerEnd>boundary && input.length-boundary>=24) {
            val actual=input.bytes(boundary,24);val n=actual.le(16,8)
            if(actual.latin(0,16)==AsfGuid.Data && n>=50 && n<=input.length-boundary) knownPackets=boundary+50 to boundary+n
        }
        val aliasesPackets=knownPackets?.let {headerEnd<it.second && headerEnd+24>it.first} ?: false
        if(headerEnd>0 && input.length-headerEnd>=24 && !aliasesPackets) {
            val data=input.bytes(headerEnd,24);val n=data.le(16,8)
            if(data.latin(0,16)==AsfGuid.Data && n>=50 && n<=input.length-headerEnd) dataSize=n-50
        }
        val raw=IffProperties.waveCodec(formatTag)
        b.properties=if(haveAudio) AudioProperties("ASF",raw,sampleRate=rate,channels=channels,bitsPerSample=bits,durationNs=duration,
            bitrate=if(byteRate>0) averageBitrate(byteRate,1_000_000_000) else averageBitrate(maxBitrate/8,1_000_000_000),
            totalSamples=(duration/1e9*sourceRate).takeIf {it>0 && it<Long.MAX_VALUE.toDouble()}?.toLong() ?: 0) else AudioProperties(container="ASF")
        b.chapters+=markers.sortedBy {maxOf(0,it.first-preroll)}.map {Chapter(AudioProperties(durationNs=maxOf(0,it.first-preroll)).durationMs,title=it.second)}
        for(key in listOf("ENCODER","ENCODEDBY")) for(v in b.tags[key].orEmpty()) if(transcoder(v)) b.warn("inherited-encoder","inherited encoder stamp: ${snippet(v)}")
        for(name in invalidKeys) b.warn("invalid-tag-key","tag key not represented in canonical tags (not carried): ${snippet(name)}")
        repeat(b.pictures.size) {b.native+=NativeBlock("WM/Picture",0,"embedded picture")}
        b.native+=NativeBlock("data",dataSize,raw)
        finish(b,dataSize)
    }
}
internal fun objectName(id: String): String=when(id) {
    AsfGuid.FileProps->"File Properties";AsfGuid.StreamProps->"Stream Properties";AsfGuid.HeaderExt->"Header Extension";AsfGuid.ContentDesc->"Content Description";AsfGuid.ExtContentDesc->"Extended Content Description";AsfGuid.Metadata->"Metadata";AsfGuid.MetadataLibrary->"Metadata Library";AsfGuid.Marker->"Marker";AsfGuid.CodecList->"Codec List";AsfGuid.StreamBitrate->"Stream Bitrate Properties";AsfGuid.ContentEncryption->"Content Encryption";AsfGuid.ExtContentEncryption->"Extended Content Encryption";else->"header object"
}
