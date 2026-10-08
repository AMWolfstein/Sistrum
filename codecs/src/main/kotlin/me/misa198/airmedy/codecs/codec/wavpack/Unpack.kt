// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/wavpack/unpack.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.wavpack

import java.nio.ByteBuffer
import me.misa198.airmedy.codecs.bitstream.BitReader

internal class DecorrPass {
    var term=0; var delta=0; var weightA=0; var weightB=0
    val samplesA=IntArray(MAX_TERM); val samplesB=IntArray(MAX_TERM)
    fun reset() { term=0; delta=0; weightA=0; weightB=0; samplesA.fill(0); samplesB.fill(0) }
}
internal fun applyWeight(weight: Int, sample: Int): Int {
    if (sample!=sample.toShort().toInt())
        return ((((sample and 65535)*weight shr 9)+((sample and -65536) shr 9)*weight+1) shr 1)
    return (weight*sample+512) shr 10
}
internal fun updateWeight(weight: Int, delta: Int, source: Int, result: Int): Int {
    if (source==0 || result==0) return weight
    val s=(source xor result) shr 31
    return (delta xor s)+(weight-s)
}
internal fun updateWeightClip(weight: Int, delta: Int, source: Int, result: Int): Int {
    if (source==0 || result==0) return weight
    val s=(source xor result) shr 31
    val w=minOf((weight xor s)+(delta-s),1024)
    return (w xor s)-s
}
internal fun decorrMonoPass(d: DecorrPass, buf: IntArray, length: Int) {
    var weight=d.weightA
    var m=0; var k=d.term and 7; var i=0
    while (i<length) {
        val residual=buf[i]
        val sam: Int
        val value: Int
        when (d.term) {
            17,18 -> {
                // Preserve the mono encoder's wrapping (3*a-b)>>1, not stereo's form.
                sam=if (d.term==17) 2*d.samplesA[0]-d.samplesA[1] else (3*d.samplesA[0]-d.samplesA[1]) shr 1
                d.samplesA[1]=d.samplesA[0]
                value=applyWeight(weight,sam)+residual; d.samplesA[0]=value
            }
            else -> { sam=d.samplesA[m]; value=applyWeight(weight,sam)+residual; d.samplesA[k]=value; m=(m+1) and 7; k=(k+1) and 7 }
        }
        weight=updateWeight(weight,d.delta,sam,residual); buf[i++]=value
    }
    d.weightA=weight
}
internal fun decorrStereoPass(d: DecorrPass, buf: IntArray, length: Int) {
    var m=0; var k=d.term and 7; var i=0
    while (i<length) {
        when (d.term) {
            17,18 -> {
                var sam=if (d.term==17) 2*d.samplesA[0]-d.samplesA[1] else d.samplesA[0]+((d.samplesA[0]-d.samplesA[1]) shr 1)
                d.samplesA[1]=d.samplesA[0]
                var tmp=buf[i]; d.samplesA[0]=applyWeight(d.weightA,sam)+tmp; buf[i]=d.samplesA[0]
                d.weightA=updateWeight(d.weightA,d.delta,sam,tmp)
                sam=if (d.term==17) 2*d.samplesB[0]-d.samplesB[1] else d.samplesB[0]+((d.samplesB[0]-d.samplesB[1]) shr 1)
                d.samplesB[1]=d.samplesB[0]
                tmp=buf[i+1]; d.samplesB[0]=applyWeight(d.weightB,sam)+tmp; buf[i+1]=d.samplesB[0]
                d.weightB=updateWeight(d.weightB,d.delta,sam,tmp)
            }
            -1 -> {
                val sam=buf[i]+applyWeight(d.weightA,d.samplesA[0])
                d.weightA=updateWeightClip(d.weightA,d.delta,d.samplesA[0],buf[i]); buf[i]=sam
                d.samplesA[0]=buf[i+1]+applyWeight(d.weightB,sam)
                d.weightB=updateWeightClip(d.weightB,d.delta,sam,buf[i+1]); buf[i+1]=d.samplesA[0]
            }
            -2 -> {
                val sam=buf[i+1]+applyWeight(d.weightB,d.samplesB[0])
                d.weightB=updateWeightClip(d.weightB,d.delta,d.samplesB[0],buf[i+1]); buf[i+1]=sam
                d.samplesB[0]=buf[i]+applyWeight(d.weightA,sam)
                d.weightA=updateWeightClip(d.weightA,d.delta,sam,buf[i]); buf[i]=d.samplesB[0]
            }
            -3 -> {
                val samA=buf[i]+applyWeight(d.weightA,d.samplesA[0]); d.weightA=updateWeightClip(d.weightA,d.delta,d.samplesA[0],buf[i])
                val samB=buf[i+1]+applyWeight(d.weightB,d.samplesB[0]); d.weightB=updateWeightClip(d.weightB,d.delta,d.samplesB[0],buf[i+1])
                buf[i]=samA; buf[i+1]=samB; d.samplesB[0]=samA; d.samplesA[0]=samB
            }
            else -> {
                var sam=d.samplesA[m]; d.samplesA[k]=applyWeight(d.weightA,sam)+buf[i]
                d.weightA=updateWeight(d.weightA,d.delta,sam,buf[i]); buf[i]=d.samplesA[k]
                sam=d.samplesB[m]; d.samplesB[k]=applyWeight(d.weightB,sam)+buf[i+1]
                d.weightB=updateWeight(d.weightB,d.delta,sam,buf[i+1]); buf[i+1]=d.samplesB[k]
                m=(m+1) and 7; k=(k+1) and 7
            }
        }
        i+=2
    }
}

internal class BlockState {
    private val terms=Array(MAX_TERMS) { DecorrPass() }
    private var nterm=0
    private val w=WordCoder()
    private val wv=BitReader(); private val wvx=BitReader()
    private val metadata=Metadata()
    private lateinit var h: BlockHeader
    private var int32Sent=0; private var int32Zeros=0; private var int32Ones=0; private var int32Dups=0
    private var int32MaxWidth=0; private var crcWVX=0
    fun unpackBlock(header: BlockHeader, block: ByteBuffer, offset: Int, out: IntArray): Int {
        h=header
        if (h.size>block.limit()-offset) malformed("block declares ${h.size} bytes but only ${block.limit()-offset} are present")
        // In-place equivalent of Go's *s = blockState{h:h}; no state crosses blocks.
        for (term in terms) term.reset()
        nterm=0; w.reset(); wv.clear(); wvx.clear()
        int32Sent=0; int32Zeros=0; int32Ones=0; int32Dups=0; int32MaxWidth=0; crcWVX=0
        readMetadata(block,offset)
        if (!wv.open()) malformed("block has no wv bitstream")
        val n=h.blockSamples; val mono=h.mono(); val span=if (mono) n else n*2
        val got=w.getWordsLossless(wv,out,n,mono)
        if (got!=n) malformed("bitstream ends after $got of $n samples")
        if (wv.over) malformed("block at sample ${h.blockIndex} reads past the end of its bitstream")
        var crc=-1
        if (mono) {
            var t=0; while (t<nterm) decorrMonoPass(terms[t++],out,span)
            var i=0; while (i<span) crc=crcMono(crc,out[i++])
        } else {
            var t=0; while (t<nterm) decorrStereoPass(terms[t++],out,span)
            var i=0
            while (i<span) {
                if (h.flags and JOINT_STEREO != 0) { out[i+1]-=out[i] shr 1; out[i]+=out[i+1] }
                crc=crcStereo(crc,out[i],out[i+1]); i+=2
            }
        }
        fixup(out,span)
        if (crc!=h.crc) malformed("block at sample ${h.blockIndex} fails its CRC")
        if (h.flags and FALSE_STEREO != 0) {
            var i=n-1
            while (i>=0) { val v=out[i]; out[i*2]=v; out[i*2+1]=v; i-- }
        }
        return n
    }
    private fun readMetadata(block: ByteBuffer, off: Int) {
        metadata.reset(block,off,h.size.toInt())
        val m=metadata; val mono=h.mono()
        while (m.next()) {
            when (m.id) {
                2 -> readDecorrTerms(m,mono)
                3 -> readDecorrWeights(m,mono)
                4 -> readDecorrSamples(m,mono)
                5 -> readEntropyVars(m,mono)
                9 -> {
                    if (m.size!=4) malformed("int32 info of ${m.size} bytes, want 4")
                    int32Sent=m.byte(0) and 31; int32Zeros=m.byte(1) and 31; int32Ones=m.byte(2) and 31; int32Dups=m.byte(3) and 31
                }
                10 -> {
                    if (m.size==0 || m.size and 1 != 0) malformed("wv bitstream of ${m.size} bytes")
                    wv.reset(block,m.offset,m.size)
                }
                12,44 -> {
                    if (m.size<=4 || m.size and 1 != 0) malformed("wvx bitstream of ${m.size} bytes")
                    crcWVX=m.int(0); wvx.reset(block,m.offset+4,m.size-4)
                    if (m.id==44) int32MaxWidth=wvx.getBits(5) and 31
                }
            }
        }
    }
    private fun readDecorrTerms(m: Metadata, mono: Boolean) {
        if (m.size>MAX_TERMS) malformed("${m.size} decorrelation terms exceeds $MAX_TERMS")
        nterm=m.size
        var i=0
        while (i<nterm) {
            val b=m.byte(i); val d=terms[nterm-1-i]; d.term=(b and 31)-5; d.delta=(b ushr 5) and 7
            if (d.term==0 || d.term < -3 || (d.term>8 && d.term<17) || d.term>18 || (mono && d.term<0))
                malformed("decorrelation term ${d.term}")
            i++
        }
    }
    private fun readDecorrWeights(m: Metadata, mono: Boolean) {
        val n=if (mono) m.size else m.size/2
        if (n>nterm) malformed("$n decorrelation weights for $nterm terms")
        var p=0; var j=0
        while (j<n) {
            val d=terms[nterm-1-j]; d.weightA=restoreWeight(m.byte(p++).toByte().toInt())
            if (!mono) d.weightB=restoreWeight(m.byte(p++).toByte().toInt())
            j++
        }
    }
    private fun readDecorrSamples(m: Metadata, mono: Boolean) {
        var p=0; var j=0
        while (j<nterm && p<m.size) {
            val d=terms[nterm-1-j]
            when {
                d.term>8 -> {
                    val need=if (mono) 4 else 8
                    if (p+need>m.size) malformed("decorrelation samples truncated")
                    d.samplesA[0]=exp2s(m.short(p).toShort().toInt()); p+=2
                    d.samplesA[1]=exp2s(m.short(p).toShort().toInt()); p+=2
                    if (!mono) {
                        d.samplesB[0]=exp2s(m.short(p).toShort().toInt()); p+=2
                        d.samplesB[1]=exp2s(m.short(p).toShort().toInt()); p+=2
                    }
                }
                d.term<0 -> {
                    if (p+4>m.size) malformed("decorrelation samples truncated")
                    d.samplesA[0]=exp2s(m.short(p).toShort().toInt()); p+=2
                    d.samplesB[0]=exp2s(m.short(p).toShort().toInt()); p+=2
                }
                else -> {
                    var k=0
                    while (k<d.term) {
                        if (p+(if (mono) 2 else 4)>m.size) malformed("decorrelation samples truncated")
                        d.samplesA[k]=exp2s(m.short(p).toShort().toInt()); p+=2
                        if (!mono) { d.samplesB[k]=exp2s(m.short(p).toShort().toInt()); p+=2 }
                        k++
                    }
                }
            }
            j++
        }
        if (p!=m.size) malformed("${m.size-p} unread decorrelation sample bytes")
    }
    private fun readEntropyVars(m: Metadata, mono: Boolean) {
        val want=if (mono) 6 else 12
        if (m.size!=want) malformed("entropy vars of ${m.size} bytes, want $want")
        var ch=0
        while (ch<(if (mono) 1 else 2)) {
            var i=0
            while (i<3) { w.c[ch].median[i]=exp2s(m.short((ch*3+i)*2)); i++ }; ch++
        }
    }
    private fun fixup(buf: IntArray, length: Int) {
        var shift=h.shift()
        if (h.flags and INT32_DATA != 0) {
            val sent=int32Sent; val zeros=int32Zeros; val ones=int32Ones; val dups=int32Dups
            when {
                wvx.open() -> {
                    var crc=-1; var i=0
                    while (i<length) {
                        var v=buf[i]
                        if (sent!=0) v=sendBits(v,sent)
                        v=synthesizeLowBits(v,zeros,ones,dups); buf[i++]=v; crc=crcExtension(crc,v)
                    }
                    if (crc!=crcWVX) malformed("block at sample ${h.blockIndex} fails its extension CRC")
                }
                sent==0 && zeros+ones+dups!=0 -> {
                    var i=0; while (i<length) { buf[i]=synthesizeLowBits(buf[i],zeros,ones,dups); i++ }
                }
                else -> shift+=zeros+sent+ones+dups
            }
        }
        shift=shift and 31
        if (shift!=0) { var i=0; while (i<length) { buf[i]=buf[i] shl shift; i++ } }
    }
    private fun sendBits(v: Int, sent: Int): Int {
        if (int32MaxWidth==0) return (v shl sent) or wvx.getBits(sent)
        val pos=if (v<0) v.inv() else v
        val width=32-Integer.numberOfLeadingZeros(pos)+sent
        var read=sent
        if (width>int32MaxWidth) {
            if (width-int32MaxWidth>=sent) return v shl sent
            read=sent-(width-int32MaxWidth)
        }
        return ((v shl read) or wvx.getBits(read)) shl (sent-read)
    }
}
internal fun synthesizeLowBits(v: Int, zeros: Int, ones: Int, dups: Int): Int = when {
    zeros!=0 -> v shl zeros
    ones!=0 -> ((v+1) shl ones)-1
    dups!=0 -> ((v+(v and 1)) shl dups)-(v and 1)
    else -> v
}
