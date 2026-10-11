// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxLabel cfbb45d13135a76f5df6781fdeec75f5cfe63bf9, internal/wav/parse.go, internal/aiff/comm.go, internal/core/properties.go.
// Copyright (c) 2026 Cole Springer, MIT; see metadata/THIRD-PARTY-NOTICES.
package com.amwolfstein.sistrum.metadata

import java.util.Locale

internal object IffProperties {
    fun waveCodec(n: Int)=when(n) {1->"PCM";2->"ADPCM";3->"IEEE float";6->"A-law";7->"mu-law";10->"WMA Voice";17->"IMA ADPCM";80->"MP2";85->"MP3";255->"AAC";0x160->"WMA v1";0x161->"WMA v2";0x162->"WMA Pro";0x163->"WMA Lossless";65534->"PCM (extensible)";else->"WAVE format 0x%04X".format(n)}
    fun wav(f: ByteArray?,bytes: Long,fact: Long?,container: String): AudioProperties {
        val h=f?.takeIf {it.size>=16} ?: ByteArray(16)
        val tag=h.le(0,2).toInt();val rate=h.le(4,4).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val byteRate=h.le(8,4);val align=h.le(12,2);val constant=tag in listOf(1,3,6,7,65534)
        var duration=0L;var samples=0L
        if(!constant && fact!=null && fact>0 && bytes>0) {
            val ns=samplesToNs(fact,rate);val secs=ns/1e9;val estimate=if(byteRate>0) bytes.toDouble()/byteRate else 0.0
            if(ns>0 && averageBitrate(bytes,ns)>=1000 && (byteRate==0L || secs<=estimate*8 && secs*8>=estimate)) {duration=ns;samples=fact}
        }
        if(duration==0L && byteRate>0) {val secs=bytes.toDouble()/byteRate;if(secs>0 && secs<Long.MAX_VALUE/1e9) duration=(secs*1e9).toLong()}
        if(constant && align>0) samples=bytes/align
        val bitrate=if(constant) (byteRate*8).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() else averageBitrate(bytes,duration)
        return AudioProperties(container,waveCodec(tag),sampleRate=rate,channels=h.le(2,2).toInt(),bitsPerSample=h.le(14,2).toInt(),totalSamples=samples,durationNs=duration,bitrate=bitrate)
    }
    data class Aiff(val properties: AudioProperties,val raw: String,val overstates: Boolean)
    fun aiff(f: ByteArray?,bytes: Long,form: String): Aiff {
        val h=f?.takeIf {it.size>=18} ?: ByteArray(18)
        val channels=h.be(0,2).toInt();val declared=h.be(2,4);var bits=h.be(6,2).toInt()
        val exp=(h.u(8) and 127 shl 8) or h.u(9);var mant=0.0
        for(i in 10..17) mant=mant*256+h.u(i)
        val value=if(mant==0.0 || exp==32767) 0.0 else Math.scalb(mant,exp-16383-63)*if(h.u(8) and 128!=0) -1 else 1
        val rawRate=if(value>0 && value<0xffffffffL) Math.round(value) else 0
        val rate=rawRate.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val comp=if(form=="AIFC" && h.size>=22) h.latin(18,4) else "\u0000\u0000\u0000\u0000"
        val up=comp.uppercase(Locale.ROOT)
        var known=true;var frames=1L;var packet=0L
        when(up) {
            "IN24"->bits=24;"IN32","FL32"->bits=32;"FL64"->bits=64;"ULAW","ALAW"->bits=8
            "IMA4"->{bits=4;frames=64;packet=34};"MAC3"->{frames=6;packet=2};"MAC6"->{frames=6;packet=1}
            "LPCM","IPCM","FPCM","SOWT","TWOS","RAW ","NONE","\u0000\u0000\u0000\u0000"->Unit
            else->{if(comp.startsWith("ms")) when(h.be(20,2).toInt()) {1,3->Unit;6,7->bits=8;else->known=false} else known=false}
        }
        var raw=if(form!="AIFC" || comp.all {it=='\u0000'}) "PCM" else if(comp.startsWith("ms")) waveCodec(h.be(20,2).toInt()) else comp.map {if(it.code in 32..126) it else '?'}.joinToString("")
        val codec=when(raw.uppercase(Locale.ROOT)) {"LPCM","IPCM","SOWT","TWOS","IN24","IN32","RAW ","NONE"->"PCM";"FL32","FPCM"->"IEEE float";"FL64"->"IEEE float64";"ULAW"->"mu-law";"ALAW"->"A-law";"IMA4"->"IMA ADPCM";"MAC3"->"MAC3";"MAC6"->"MAC6";"MP4A"->"AAC";"ALAC"->"ALAC";"FLAC"->"FLAC";"AC-3"->"AC-3";"EC-3","EAC3"->"E-AC-3";".MP3"->"MP3";".MP2"->"MP2";".MP1"->"MP1";else->raw}
        val bpp=channels*(if(packet>0) packet else (bits+7)/8L)
        known=known && bpp>0
        val present=if(known) maxOf(0,bytes)/bpp else 0
        val samples=if(known) minOf(declared,present)*frames else declared
        val bitrate=if(known) ((minOf(rawRate*bpp,Long.MAX_VALUE/16)*8+frames/2)/frames).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() else 0
        return Aiff(AudioProperties(form,codec,if(raw!=codec) raw else "",rate,channels,bits,samples,samplesToNs(samples,rate),bitrate),raw,known && bytes>0 && present<declared)
    }
}
