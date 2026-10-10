// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from libwavpack 5.8.1 src/unpack.c and src/decorr_utils.c,
// commit 4827b9889665b937b6ed71b9c6c0123152cd7a02.
// Copyright (c) 1998-2013 Conifer Software; 1998-2025 David Bryant.
// BSD-3-Clause; copyright, conditions and disclaimer in THIRD-PARTY-NOTICES.
package me.misa198.airmedy.codecs.codec.wavpack

/** Corrected prediction observes the lossy history before adapting that history. */
internal class HybridReconstruction {
    private val error=IntArray(2)
    private val acc=IntArray(2)
    private val delta=IntArray(2)
    fun reset() { error.fill(0);acc.fill(0);delta.fill(0) }
    fun readShaping(m: Metadata,mono: Boolean) {
        if (m.size==2) {
            acc[0]=restoreWeight(m.byte(0).toByte().toInt()) shl 16
            acc[1]=restoreWeight(m.byte(1).toByte().toInt()) shl 16
            return
        }
        val channels=if (mono) 1 else 2
        if (m.size<channels*4) malformed("truncated shaping information")
        var i=0
        while (i<channels) { error[i]=exp2s(m.short(i*4).toShort().toInt());acc[i]=exp2s(m.short(i*4+2).toShort().toInt());i++ }
        if (m.size==channels*6) {
            i=0;while (i<channels) { delta[i]=exp2s(m.short(channels*4+i*2).toShort().toInt());i++ }
        }
    }
    private fun shape(ch:Int,lossy:Int,exact:Int,flags:Int):Int {
        if (flags and 0x40==0) return exact
        val correction=exact-lossy
        acc[ch]+=delta[ch]
        val weight=acc[ch] shr 16
        var temp= -applyWeight(weight,error[ch])
        if (flags and 0x20000000!=0 && weight<0 && temp!=0) {
            if (temp==error[ch]) temp+=if (temp<0) 1 else -1
            error[ch]=temp-correction
        } else error[ch]= -correction
        return exact-temp
    }
    private fun prediction(history:IntArray,term:Int,m:Int):Int = when {
        term==17 -> 2*history[0]-history[1]
        term==18 -> (3*history[0]-history[1]) shr 1
        else -> history[m]
    }
    fun reconstruct(buf:IntArray,corr:IntArray,n:Int,flags:Int,terms:Array<DecorrPass>,nterm:Int):Int {
        val mono=flags and MONO_DATA!=0
        var crc= -1;var m=0;var frame=0
        while (frame<n) {
            val at=if (mono) frame else frame*2
            var left=buf[at];var right=if (mono) 0 else buf[at+1]
            var exactLeft=left+corr[at];var exactRight=if (mono) 0 else right+corr[at+1]
            if (!mono && flags and 0x20!=0) {
                var t=0
                while (t<nterm) {
                    val d=terms[t++]
                    when {
                        d.term>0 -> {
                            exactLeft+=applyWeight(d.weightA,prediction(d.samplesA,d.term,m))
                            exactRight+=applyWeight(d.weightB,prediction(d.samplesB,d.term,m))
                        }
                        d.term== -1 -> {
                            exactLeft+=applyWeight(d.weightA,d.samplesA[0]);exactRight+=applyWeight(d.weightB,exactLeft)
                        }
                        else -> {
                            exactRight+=applyWeight(d.weightB,d.samplesB[0])
                            exactLeft+=applyWeight(d.weightA,if (d.term== -3) d.samplesA[0] else exactRight)
                        }
                    }
                }
                if (flags and JOINT_STEREO!=0) { exactRight-=exactLeft shr 1;exactLeft+=exactRight }
            }
            var t=0
            while (t<nterm) {
                val d=terms[t++]
                when {
                    d.term>0 -> {
                        val a=prediction(d.samplesA,d.term,m);val b=if (mono) 0 else prediction(d.samplesB,d.term,m)
                        val k=if (d.term>MAX_TERM) 0 else (m+d.term) and 7
                        if (d.term>MAX_TERM) { d.samplesA[1]=d.samplesA[0];if (!mono) d.samplesB[1]=d.samplesB[0] }
                        val nextLeft=left+applyWeight(d.weightA,a)
                        d.weightA=updateWeight(d.weightA,d.delta,a,left);d.samplesA[k]=nextLeft;left=nextLeft
                        if (!mono) {
                            val nextRight=right+applyWeight(d.weightB,b)
                            d.weightB=updateWeight(d.weightB,d.delta,b,right);d.samplesB[k]=nextRight;right=nextRight
                        }
                    }
                    d.term== -1 -> {
                        val nextLeft=left+applyWeight(d.weightA,d.samplesA[0])
                        d.weightA=updateWeightClip(d.weightA,d.delta,d.samplesA[0],left);left=nextLeft
                        val nextRight=right+applyWeight(d.weightB,left)
                        d.weightB=updateWeightClip(d.weightB,d.delta,left,right);d.samplesA[0]=nextRight;right=nextRight
                    }
                    else -> {
                        val nextRight=right+applyWeight(d.weightB,d.samplesB[0])
                        d.weightB=updateWeightClip(d.weightB,d.delta,d.samplesB[0],right);right=nextRight
                        val a=if (d.term== -3) d.samplesA[0] else right
                        if (d.term== -3) d.samplesA[0]=right
                        val nextLeft=left+applyWeight(d.weightA,a)
                        d.weightA=updateWeightClip(d.weightA,d.delta,a,left);d.samplesB[0]=nextLeft;left=nextLeft
                    }
                }
            }
            m=(m+1) and 7
            if (mono) {
                left=shape(0,left,left+corr[at],flags);buf[at]=left;crc=crcMono(crc,left)
            } else {
                if (flags and 0x20==0) {
                    exactLeft=left+corr[at];exactRight=right+corr[at+1]
                    if (flags and JOINT_STEREO!=0) { exactRight-=exactLeft shr 1;exactLeft+=exactRight }
                }
                if (flags and JOINT_STEREO!=0) { right-=left shr 1;left+=right }
                left=shape(0,left,exactLeft,flags);right=shape(1,right,exactRight,flags)
                buf[at]=left;buf[at+1]=right;crc=crcStereo(crc,left,right)
            }
            frame++
        }
        return crc
    }
}
