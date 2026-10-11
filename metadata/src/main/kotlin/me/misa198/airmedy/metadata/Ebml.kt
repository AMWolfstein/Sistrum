// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxLabel cfbb45d13135a76f5df6781fdeec75f5cfe63bf9, internal/matroska/ebml.go.
// Copyright (c) 2026 Cole Springer, MIT; see metadata/THIRD-PARTY-NOTICES.
package me.misa198.airmedy.metadata

internal data class EbmlElement(val id: Long,val start: Long,val body: Long,val end: Long,val unknown: Boolean=false) {
    val size get()=end-body
}
internal class Ebml(val input: Input) {
    private var elements=0
    private var depth=0
    private fun vint(off: Long,end: Long,marker: Boolean): Pair<Long,Int>? {
        if(off>=end) return null
        val first=input.bytes(off,1).u(0)
        val n=(1..8).firstOrNull {first and (0x80 shr (it-1))!=0} ?: return null
        if(n>end-off) return null
        val bytes=if(n==1) byteArrayOf(first.toByte()) else input.bytes(off,n)
        var value=(first and if(marker) 255 else (0x80 shr (n-1))-1).toLong()
        for(i in 1 until n) value=value shl 8 or bytes.u(i).toLong()
        return value to n
    }
    fun element(off: Long,end: Long): EbmlElement? {
        val (id,n)=vint(off,end,true) ?: return null
        val (size,m)=vint(off+n,end,false) ?: return null
        val body=off+n+m;val unknown=size==(1L shl (7*m))-1
        return EbmlElement(id,off,body,if(unknown) end else body+minOf(size,end-body),unknown)
    }
    fun children(start: Long,end: Long,segment: Boolean=false,visit: (EbmlElement)->Unit) {
        if(++depth>64) {depth--;throw ReadFailure(ErrorClass.SIZE_TOO_LARGE,"Matroska nesting limit")}
        try {
            var off=start
            while(off<end) {
                var el=element(off,end) ?: break
                if(segment && el.unknown) {
                    var p=el.body
                    while(p<end) {
                        val child=element(p,end)
                        if(child==null) {p=end;break}
                        if(child.id in levelOne) break
                        input.cap(elements++);if(child.unknown || child.end<=p) {p=end;break};p=child.end
                    }
                    el=el.copy(end=p)
                }
                if(!segment || el.id!=0x1f43b675L) input.cap(elements++)
                visit(el)
                if(el.end<=off) break
                off=el.end
            }
        } finally {depth--}
    }
    fun children(el: EbmlElement,visit: (EbmlElement)->Unit)=children(el.body,el.end,visit=visit)
    fun uint(el: EbmlElement): Long=if(el.size in 1..8) input.bytes(el.body,el.size.toInt()).be(0,el.size.toInt()) else 0
    fun count(el: EbmlElement): Int=uint(el).takeIf {it in 0..Int.MAX_VALUE.toLong()}?.toInt() ?: 0
    fun float(el: EbmlElement): Double=when(el.size) {
        0L->0.0;4L->Float.fromBits(uint(el).toInt()).toDouble();8L->Double.fromBits(uint(el));else->Double.NaN
    }
    fun raw(el: EbmlElement): ByteArray {
        if(el.size>minOf(64*1024*1024,input.options.maxTagBytes)) throw ReadFailure(ErrorClass.SIZE_TOO_LARGE,"Matroska element limit")
        return input.bytes(el.body,el.size.toInt()).let {it.copyOf(it.indexOfLast {b -> b!=0.toByte()}+1)}
    }
    fun string(el: EbmlElement)=nativeUtf8(raw(el))
    companion object {
        val levelOne=setOf(0x114d9b74L,0x1549a966L,0x1654ae6bL,0x1254c367L,0x1941a469L,0x1f43b675L,0x1c53bb6bL,0x1043a770L)
    }
}
