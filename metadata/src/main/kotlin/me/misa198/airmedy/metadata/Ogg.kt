// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxLabel cfbb45d13135a76f5df6781fdeec75f5cfe63bf9, internal/ogg/ (read path).
// Copyright (c) 2026 Cole Springer, MIT; see metadata/THIRD-PARTY-NOTICES.
package me.misa198.airmedy.metadata

import java.util.Locale

internal object Ogg {
    private data class Page(val offset: Long,val body: Long,val size: Int,val segments: ByteArray,
                            val flags: Int,val serial: Long,val granule: Long) {
        val end get()=body+size
    }
    fun read(input: Input,b: Builder) {
        if(input.length<27) unsupported()
        val bodyStart=27L+input.bytes(26,1).u(0)
        if(bodyStart>input.length) unsupported()
        val signature=input.bytes(bodyStart,minOf(8L,input.length-bodyStart).toInt()).latin()
        if(!signature.startsWith("\u0001vorbis") && !signature.startsWith("OpusHead") && !signature.startsWith("\u007FFLAC")) unsupported()
        val pages=mutableListOf<Page>(); var p=0L; var retained=0L
        while(p+27<=input.length) {
            val h=input.bytes(p,27)
            if(h.latin(0,4)!="OggS" || h.u(4)!=0) break
            val segments=input.bytes(p+27,h.u(26)); val n=segments.sumOf { it.toInt() and 255 }
            val body=p+27+segments.size
            if(n>input.length-body) invalid("Ogg page overruns the file")
            retained+=72+segments.size
            if(retained>64L*1024*1024) throw ReadFailure(ErrorClass.SIZE_TOO_LARGE,"Ogg page descriptor limit")
            pages+=Page(p,body,n,segments,h.u(5),h.le(14,4),h.le(6,8)); p=body+n
        }
        if(pages.isEmpty()) invalid("not an Ogg stream")
        if(pages.first().flags and 2==0) invalid("first Ogg page is not a beginning-of-stream page")
        val serial=pages.first().serial
        val chained=pages.map { it.serial }.distinct().size>1 || pages.count { it.flags and 2!=0 }>1
        if(chained) b.warn("chained-stream","chained or multiplexed Ogg stream; the first logical stream is read best-effort and writing is refused")
        val packets=mutableListOf<Input>(); val regions=mutableListOf<Region>()
        var packetSize=0L; var kind=""; var lastHeader=-1
        headers@ for((index,page) in pages.withIndex()) {
            if(page.serial!=serial) continue
            var offset=page.body
            for(segment in page.segments) {
                val n=segment.toInt() and 255
                if(n>0) { input.cap(regions.size); regions+=Region(offset,n) }
                offset+=n; packetSize+=n
                if(packetSize>input.options.maxTagBytes) throw ReadFailure(ErrorClass.SIZE_TOO_LARGE,"Ogg header packet limit")
                if(n==255) continue
                val packet=Input(RegionSource(input,regions.toList()),input.options); packets+=packet
                regions.clear(); packetSize=0
                if(packets.size==1) {
                    val magic=packet.bytes(0,minOf(8,packet.length.toInt())).latin()
                    kind=when { magic.startsWith("\u0001vorbis") -> "Vorbis"; magic=="OpusHead" -> "Opus"
                        magic.startsWith("\u007FFLAC") -> "FLAC"
                        else -> throw ReadFailure(ErrorClass.UNSUPPORTED_FORMAT,"unrecognized Ogg codec") }
                }
                val done=when(kind) { "Opus" -> packets.size==2; "Vorbis" -> packets.size==3
                    else -> packets.size>=2 && packet.length>0 && packet.bytes(0,1).u(0) and 128!=0 }
                if(done) { lastHeader=index; break@headers }
                input.cap(packets.size)
            }
        }
        if(lastHeader<0) invalid("incomplete Ogg header packets")
        b.format="Ogg $kind"
        val id=packets.first(); var blocks=emptyList<FlacBlock>(); var comments: CommentList
        var padding=0L
        if(kind=="FLAC") {
            if(id.length<51) invalid("short Ogg FLAC identification packet")
            val h=id.bytes(0,17)
            if(h.u(5)!=1) throw ReadFailure(ErrorClass.UNSUPPORTED_FORMAT,"unsupported Ogg FLAC mapping version")
            if(h.latin(9,4)!="fLaC" || h.u(13) and 127!=0 || h.be(14,3)!=34L) invalid("invalid Ogg FLAC STREAMINFO header")
            blocks=packets.drop(1).map { packet ->
                val bh=packet.bytes(0,4); val code=bh.u(0) and 127; val n=bh.be(1,3).toInt()
                if(code==127 || n>packet.length-4) invalid("invalid Ogg FLAC metadata block")
                FlacBlock(code,packet,4,n)
            }
            Flac.decodePictures(blocks,b)
            for(block in blocks.filter { it.code>6 }) b.warn("unknown-block","metadata block type ${block.code} preserved verbatim")
            val cm=blocks.filter { it.code==4 }
            // Upstream records duplicate content only for a well-formed extra block.
            if(cm.drop(1).any { block -> try { Vorbis.comments(block.input,block.offset,block.size); true } catch(e: ReadFailure) { if(e.kind==ErrorClass.IO) throw e; false } }) Flac.duplicateWarning(b)
            comments=cm.firstOrNull()?.let { Vorbis.comments(it.input,it.offset,it.size) } ?: CommentList("",emptyList(),0)
        } else {
            val packet=packets[1]; val prefix=if(kind=="Opus") "OpusTags" else "\u0003vorbis"
            if(packet.length<prefix.length || packet.bytes(0,prefix.length).latin()!=prefix) invalid("missing comment packet signature")
            comments=Vorbis.comments(packet,prefix.length.toLong(),(packet.length-prefix.length).toInt())
            if(kind=="Opus") padding=packet.length-prefix.length-comments.consumed
        }
        val commentInput=if(kind=="FLAC") blocks.firstOrNull { it.code==4 }?.input else packets[1]
        if(commentInput!=null) Vorbis.pictures(commentInput,comments.comments,b)
        Vorbis.project(comments,b)
        val audioPages=pages.drop(lastHeader+1).filter { it.serial==serial }
        val audioStart=pages.getOrNull(lastHeader+1)?.offset ?: p
        val audioEnd=audioPages.lastOrNull()?.end ?: audioStart
        val trailing=if(chained) 0 else input.length-audioEnd
        if(trailing>0) b.warn("trailing-bytes","$trailing byte(s) after the Ogg stream belong to no chunk or page; preserved verbatim")
        val granule=pages.drop(lastHeader).filter { it.serial==serial && it.granule!=-1L }.lastOrNull()?.granule ?: 0
        val h=id.bytes(0,minOf(34,id.length.toInt()))
        var properties=when(kind) {
            "FLAC" -> try { Vorbis.streamInfo(id,17,34,"Ogg") } catch(e: ReadFailure) { if(e.kind!=ErrorClass.INVALID_DATA) throw e; AudioProperties(container="Ogg",codec="FLAC") }
            "Vorbis" -> AudioProperties(container="Ogg",codec="Vorbis",sampleRate=if(h.size>=16) h.le(12,4).toInt() else 0,channels=if(h.size>=16) h.u(11) else 0)
            else -> AudioProperties(container="Ogg",codec="Opus",sampleRate=48000,channels=if(h.size>=12) h.u(9) else 0,
                opusOutputGain=if(h.size>=18) h.le(16,2).toShort().toInt() else 0)
        }
        if(properties.totalSamples==0L) {
            val skip=if(kind=="Opus" && h.size>=12) h.le(10,2) else 0
            val samples=maxOf(0,granule-skip)
            properties=properties.copy(totalSamples=samples,durationNs=sampleDuration(samples,properties.sampleRate))
        }
        var bitrate=averageBitrate(audioPages.sumOf { it.size.toLong() },properties.durationNs)
        if(bitrate==0 && kind=="Vorbis" && h.size>=24) bitrate=maxOf(0,h.le(20,4).toInt())
        b.properties=properties.copy(bitrate=bitrate,paddingBytes=padding)
        val gain=b.properties.opusOutputGain
        val gainNote=if(kind=="Opus" && gain!=0) "output gain "+outputGain(gain) else ""
        b.native+=NativeBlock(when(kind) { "Opus" -> "OpusHead"; "FLAC" -> "FLAC identification header"; else -> "Vorbis identification header" },id.length,gainNote)
        b.native+=NativeBlock(when(kind) { "Opus" -> "OpusTags"; "FLAC" -> "VORBIS_COMMENT"; else -> "Vorbis comment header" },0,"vendor=${comments.vendor}")
        if(kind=="Vorbis" && packets[2].length>0) b.native+=NativeBlock("Vorbis setup header",packets[2].length,"preserved")
        if(kind=="FLAC") for(block in blocks.filter { it.code!=4 }) b.native+=NativeBlock(Vorbis.blockName(block.code),block.size.toLong())
        else repeat(b.pictures.size) { b.native+=NativeBlock("METADATA_BLOCK_PICTURE",0,"embedded picture") }
        b.native+=NativeBlock("audio pages",audioPages.size.toLong(),unit="pages")
        if(padding>0) b.native+=NativeBlock("comment padding",padding,"preserved")
        if(trailing>0) b.native+=NativeBlock("trailing bytes",trailing,"preserved")
        // Ogg's upstream document does not use the generic no-audio warning.
    }
    private fun unsupported(): Nothing=throw ReadFailure(ErrorClass.UNSUPPORTED_FORMAT,"unrecognized Ogg codec signature")
    private fun invalid(message: String): Nothing=throw ReadFailure(ErrorClass.INVALID_DATA,message)
}

private fun outputGain(gain: Int): String {
    var s=String.format(Locale.ROOT,"%.4f",gain/256.0).trimEnd('0')
    if(s.length-s.indexOf('.')<3) s=String.format(Locale.ROOT,"%.2f",gain/256.0)
    return "$s dB"
}
