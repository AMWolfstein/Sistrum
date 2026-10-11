// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxLabel cfbb45d13135a76f5df6781fdeec75f5cfe63bf9, internal/iff/ and internal/wav/rf64.go.
// Copyright (c) 2026 Cole Springer, MIT; see metadata/THIRD-PARTY-NOTICES.
package com.amwolfstein.sistrum.metadata

internal data class IffChunk(val id: String,val offset: Long,val size: Long)
internal data class IffWalk(val chunks: List<IffChunk>,val audio: Int,val truncated: Boolean,
    val oversized: List<String>,val unknown: List<String>,val trailing: Long,val outer: Long,val id3Tail: Boolean)
internal class Ds64(val riff: Long,val data: Long,val samples: Long,val table: List<Pair<String,Long>>) {
    private val used=mutableMapOf<String,Int>();private var dataSeen=false
    private val sizes=table.groupBy({it.first},{it.second})
    fun size(id: String,declared: Long): Long {
        if(declared!=0xffffffffL) return declared
        if(id=="data" && !dataSeen) { dataSeen=true;return if(data<0) declared else data }
        val matching=sizes[id] ?: return declared;val n=used[id] ?: 0
        if(n>=matching.size) return declared
        used[id]=n+1;return matching[n].takeIf {it>=0} ?: declared
    }
}
internal object IffWalker {
    fun ds64(input: Input): Ds64 {
        if(input.length<20) invalid("RF64/BW64 has no ds64")
        val h=input.bytes(12,8);if(h.latin(0,4)!="ds64") invalid("RF64/BW64 has no ds64")
        val n=h.le(4,4);if(n<28 || n>input.length-20) invalid("ds64 body truncated")
        if(n>input.options.maxTagBytes) throw ReadFailure(ErrorClass.SIZE_TOO_LARGE,"ds64 allocation limit")
        val b=input.bytes(20,28);val count=b.le(24,4)
        if(count>65536 || minOf(count,(n-28)/12)>input.options.maxElements) throw ReadFailure(ErrorClass.SIZE_TOO_LARGE,"ds64 table limit")
        val table=mutableListOf<Pair<String,Long>>()
        for(i in 0 until minOf(count,(n-28)/12)) {val e=input.bytes(48+i*12,12);table+=e.latin(0,4) to e.le(4,8)}
        return Ds64(b.le(0,8),b.le(8,8),b.le(16,8),table)
    }
    fun recovering(input: Input,end: Long,aiff: Boolean,ds: Ds64?): Pair<IffWalk,Boolean> {
        val first=walk(input,end,aiff,ds)
        if(ds!=null || end>=input.length) return first to false
        val retry=try {walk(input,input.length,aiff,null)} catch(e: ReadFailure) {return first to false}
        val complete=retry.audio>=0 && retry.trailing==0L && retry.outer==0L && retry.oversized.isEmpty() && retry.chunks.any {it.id==if(aiff) "COMM" else "fmt "}
        return if(complete) retry to true else first to false
    }
    private fun walk(input: Input,end: Long,aiff: Boolean,ds: Ds64?): IffWalk {
        val chunks=mutableListOf<IffChunk>();val oversized=mutableListOf<String>();val unknown=mutableListOf<String>()
        var pos=12L;var audio=-1;var truncated=false;var id3Tail=false
        while(end-pos>=8) {
            input.cap(chunks.size);val h=input.bytes(pos,8);val id=h.latin(0,4)
            val declared=if(aiff) h.be(4,4) else h.le(4,4);val n=ds?.size(id,declared) ?: declared
            val sentinel=ds==null && declared==0xffffffffL
            val body=pos+8
            if(id.startsWith("TAG") && end-pos==128L) {id3Tail=true;break}
            if(audio>=0 && n>end-body) break
            val over=n>input.length-body;val actual=minOf(n,input.length-body)
            chunks+=IffChunk(id,body,actual)
            if(id==(if(aiff) "SSND" else "data") && audio<0) {audio=chunks.lastIndex;truncated=over && !sentinel}
            else if(over && !sentinel) oversized+=id
            if(sentinel && over) unknown+=id
            val next=body+actual+(actual and 1);if(next<=pos) break;pos=next
        }
        if(chunks.isEmpty()) invalid("no ${if(aiff) "IFF" else "RIFF"} chunks")
        return IffWalk(chunks,audio,truncated,oversized,unknown,maxOf(0,end-pos),maxOf(0,input.length-maxOf(pos,end)),id3Tail)
    }
}
