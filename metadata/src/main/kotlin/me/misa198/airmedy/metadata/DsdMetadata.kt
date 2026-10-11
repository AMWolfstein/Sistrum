// SPDX-License-Identifier: GPL-3.0-or-later
// Sistrum DSD additions: dsf-meta 0.3.0 / dff-meta 0.2.0 (Apache-2.0), WaxLabel pinned ID3 read path.
// dsf-meta authors Daniel J. R. May / clone206; dff-meta author clone206.
// See THIRD-PARTY-NOTICES and docs/waxlabel/ORACLE.md for source pins and attribution.
package me.misa198.airmedy.metadata

internal object DsdMetadata {
    private fun invalid(message: String): Nothing=throw ReadFailure(ErrorClass.INVALID_DATA,message)
    private fun require(value: Boolean,message: String) {if(!value) invalid(message)}
    private data class Chunk(val id: String,val body: Long,val size: Long) {val end get()=body+size}
    private fun chunks(input: Input,start: Long,end: Long): List<Chunk> {
        val list=mutableListOf<Chunk>();var p=start
        while(end-p>=12) {
            input.cap(list.size);val h=input.bytes(p,12);val n=h.be(4,8)
            if(n<0 || n>Long.MAX_VALUE-p-13) invalid("DFF chunk size overflow")
            list+=Chunk(h.latin(0,4),p+12,n)
            val next=p+12+n+(n and 1)
            if(next<=p || next>end) break
            p=next
        }
        return list
    }
    private fun props(container: String,rate: Long,channels: Int,samples: Long): AudioProperties {
        if(samples<0) throw ReadFailure(ErrorClass.SIZE_TOO_LARGE,"DSD unsigned sample count exceeds JVM range")
        val seconds=if(rate>0) samples/rate else 0;val rem=if(rate>0) samples%rate else 0
        if(seconds>Long.MAX_VALUE/1_000_000_000) throw ReadFailure(ErrorClass.SIZE_TOO_LARGE,"DSD duration exceeds JVM range")
        val base=seconds*1_000_000_000;val fraction=if(rate>0) rem*1_000_000_000/rate else 0
        if(fraction>Long.MAX_VALUE-base) throw ReadFailure(ErrorClass.SIZE_TOO_LARGE,"DSD duration exceeds JVM range")
        val ns=base+fraction
        return AudioProperties(container,"DSD",sampleRate=rate.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),channels=channels,bitsPerSample=1,totalSamples=samples,durationNs=ns,bitrate=(rate*channels).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }
    private fun tag(input: Input,b: Builder,offset: Long,size: Long): Id3Tag? {
        if(size<10) {b.warn("malformed-tag-entry","DSD ID3 region is shorter than its header");return null}
        return try {
            val t=Id3.parse(input,offset,minOf(size,input.length-offset))
            if(Id3.project(t,b,input.options.pictures)) b.warn("numeric-genre","a numeric genre reference was resolved to a name")
            b.warnings+=Id3.encoderWarnings(t);t
        } catch(e: ReadFailure) {
            if(e.kind in listOf(ErrorClass.IO,ErrorClass.SIZE_TOO_LARGE)) throw e
            b.warn("malformed-tag-entry","DSD ID3 region could not be parsed");null
        }
    }
    fun dsf(input: Input,b: Builder) {
        require(input.length>=92,"truncated DSF header")
        val h=input.bytes(0,92)
        require(h.latin(0,4)=="DSD " && h.le(4,8)==28L,"invalid DSF DSD chunk")
        require(h.latin(28,4)=="fmt " && h.le(32,8)==52L,"invalid DSF fmt chunk")
        require(h.le(40,4)==1L && h.le(44,4)==0L,"unsupported DSF format")
        require(h.le(48,4) in 1..7 && h.le(52,4) in 1..6,"invalid DSF channels")
        require(h.le(72,4)==4096L && h.le(76,4)==0L,"invalid DSF block or reserved field")
        require(h.latin(80,4)=="data","invalid DSF data chunk")
        b.format="DSF";b.properties=props("DSF",h.le(56,4),h.le(52,4).toInt(),h.le(64,8))
        b.native+=NativeBlock("DSD ",28);b.native+=NativeBlock("fmt ",52)
        val dataSize=h.le(84,8);val audio=maxOf(0,minOf(if(dataSize<0) input.length-92 else if(dataSize>=12) dataSize-12 else 0,input.length-92))
        b.native+=NativeBlock("data",audio)
        val pointer=h.le(20,8)
        if(pointer!=0L) {
            if(pointer<92 || pointer<92+audio || pointer>input.length) b.warn("malformed-tag-entry","DSF metadata offset does not identify a tag region outside audio")
            else {
                val t=tag(input,b,pointer,input.length-pointer)
                b.native+=NativeBlock("ID3 chunk",input.length-pointer,t?.let {"ID3v2.${it.version}, ${it.note}"} ?: "unparsed")
                if(t!=null) b.native+=t.blocks()
            }
        }
        if(audio==0L) b.warn("no-audio","no audio essence found; file may be tag-only or truncated")
    }
    fun dff(input: Input,b: Builder) {
        require(input.length>=32,"truncated DFF header")
        val h=input.bytes(0,32)
        require(h.latin(0,4)=="FRM8" && h.latin(12,4)=="DSD ","invalid DFF FORM")
        require(h.latin(16,4)=="FVER" && h.be(20,8)==4L,"invalid DFF FVER")
        val all=chunks(input,32,input.length)
        val prop=all.firstOrNull {it.id=="PROP"} ?: invalid("missing DFF PROP")
        require(all.takeWhile {it!=prop}.none {it.id=="DSD "},"DSD precedes PROP")
        require(prop.size>=4 && prop.end<=input.length && input.bytes(prop.body,4).latin()=="SND ","invalid DFF property type")
        val fields=chunks(input,prop.body+4,prop.end);var rate=0L;var channels=0
        for(c in fields) require(c.end<=prop.end,"DFF property exceeds PROP region")
        for(c in fields) when(c.id) {
            "FS  "->{require(c.size==4L && c.end<=input.length,"invalid DFF sample rate chunk");rate=input.bytes(c.body,4).be(0,4)}
            "CHNL"->{require(c.size>=2 && c.end<=input.length,"invalid DFF channel chunk");channels=input.bytes(c.body,2).be(0,2).toInt();require(channels in 1..2 && c.size==2L+channels*4,"unsupported DFF channels")}
            "CMPR"->{require(c.size>=4 && c.end<=input.length,"invalid DFF compression");require(input.bytes(c.body,4).latin()=="DSD ","unsupported DFF compression");if(c.size==15L) require(input.bytes(c.body+4,11).latin()!="DST Encoded","unsupported DFF compression name")}
            "ABSS"->require(c.size==8L,"invalid DFF absolute start")
            "LSCO"->require(c.size==2L,"invalid DFF speaker config")
        }
        require(fields.any {it.id=="FS  "} && fields.any {it.id=="CHNL"},"missing DFF geometry")
        val audio=all.dropWhile {it!=prop}.firstOrNull {it.id=="DSD "} ?: invalid("missing DFF DSD")
        require(audio.size>0 && audio.size<=Long.MAX_VALUE/8,"invalid DFF audio length")
        b.format="DFF";b.properties=props("DFF",rate,channels,audio.size*8/channels)
        b.native+=NativeBlock("FRM8",h.be(4,8));b.native+=NativeBlock("FVER",4)
        var t: Id3Tag?=null;val id3=all.firstOrNull {it.id=="ID3 "}
        if(id3!=null) t=tag(input,b,id3.body,minOf(id3.size,input.length-id3.body))
        val diin=mutableListOf<Pair<String,String>>()
        for(c in all) {
            when(c.id) {
                "DIIN"->{
                    require(c.end<=input.length,"truncated DFF DIIN")
                    b.native+=NativeBlock(c.id,c.size)
                    for(text in chunks(input,c.body,c.end)) {
                        b.native+=NativeBlock("  ${text.id}",text.size)
                        if(text.id in listOf("DIAR","DITI")) {
                            require(text.size>=4 && text.end<=c.end,"invalid DFF DIIN text")
                            val n=input.bytes(text.body,4).be(0,4)
                            require(n<=text.size-4,"truncated DFF DIIN text")
                            if(n>input.options.maxTagBytes) throw ReadFailure(ErrorClass.SIZE_TOO_LARGE,"DFF DIIN text limit")
                            val value=sanitizeUtf8(input.bytes(text.body+4,n.toInt())).trimEnd('\u0000')
                            diin+=(if(text.id=="DIAR") "ARTIST" else "TITLE") to value
                        }
                    }
                }
                "ID3 "->{b.native+=NativeBlock("ID3 chunk",c.size,if(c==id3 && t!=null) "ID3v2.${t.version}, ${t.note}" else "unparsed");if(c==id3 && t!=null) b.native+=t.blocks()}
                else->{b.native+=NativeBlock(c.id,c.size);if(c==prop) b.native+=fields.map {NativeBlock("  ${it.id}",it.size)}}
            }
        }
        for((key,values) in diin.groupBy({it.first},{it.second})) {
            val selected=b.tags[key]?.map {AsfText.fold(it)}?.toSet()
            if(selected==null) b.tags[key]=values.toMutableList()
            b.families+=FamilyValue(key,"dff",values,selected==null || values.all {AsfText.fold(it) in selected})
        }
    }
}
