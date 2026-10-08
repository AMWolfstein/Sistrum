// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/ape/decode.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.ape

import java.nio.ByteBuffer
import java.util.zip.CRC32

/** A full independently decodable frame is checked before its samples are exposed. */
class Decoder(private val cfg: Header) {
    private val rd=RangeDecoder(cfg.fileVersion)
    private val state=Array(cfg.channels) { EntropyState() }; private val pred=Array(cfg.channels) { Predictor(cfg) }
    val samples=IntArray(cfg.blocksPerFrame*cfg.channels)
    private val pack=ByteArray(3072+8); private val crc=CRC32(); private var interim=false
    var blocks=0; private set
    fun decode(packet: ByteBuffer) {
        if (packet.limit()<12) malformed("frame header of ${packet.limit()} bytes, want 12")
        blocks=le32(packet,0); val skip=le32(packet,4); val bytes=le32(packet,8); val dataLength=packet.limit()-12
        when {
            blocks<=0 || blocks>MAX_BLOCKS -> malformed("frame of $blocks blocks")
            skip !in 0..3 -> malformed("frame alignment skip of $skip bytes, want 0..3")
            bytes<=0 || bytes>dataLength-skip -> malformed("frame of $bytes bytes past a $skip-byte skip, in a $dataLength-byte packet")
            blocks>cfg.blocksPerFrame -> malformed("frame of $blocks blocks exceeds the stream's ${cfg.blocksPerFrame}")
        }
        try { frame(packet,skip) } catch (first: ApeException) {
            if (interim || cfg.bitsPerSample!=24 || !first.message!!.startsWith("ape: frame fails its CRC")) throw first
            setInterim(true)
            try { frame(packet,skip) } catch (retry: ApeException) { setInterim(false); throw first }
        }
    }
    private fun setInterim(on: Boolean) { interim=on; for (p in pred) p.setInterim(on) }
    private fun frame(packet: ByteBuffer,skip: Int) {
        rd.br.reset(packet,12,skip)
        var stored=rd.br.bits(32); val special=if (stored and 0x80000000L!=0L) rd.br.bits(32).toInt() else 0
        stored=stored and 0x7fffffff
        for (c in 0 until cfg.channels) { pred[c].flush(); state[c].flush() }; rd.start()
        decodeBlocks(special)
        val computed=unprepare()
        if (computed!=stored) malformed("frame fails its CRC (0x${computed.toString(16).padStart(8,'0')}, want 0x${stored.toString(16).padStart(8,'0')})")
    }
    private fun decodeBlocks(special: Int) {
        if ((cfg.channels==1 && special and 1!=0) || (cfg.channels==2 && special and 3==3)) {
            java.util.Arrays.fill(samples,0,blocks*cfg.channels,0); return
        }
        if (cfg.channels==1) { var i=0; while (i<blocks) { samples[i]=pred[0].decompress(rd.decodeValue(state[0]),0); i++ }; return }
        if (special and 4!=0) {
            var i=0; while (i<blocks) { samples[2*i]=pred[0].decompress(rd.decodeValue(state[0]),0); samples[2*i+1]=0; i++ }; return
        }
        var lastX=0; var i=0
        while (i<blocks) {
            val ny=rd.decodeValue(state[1]); val nx=rd.decodeValue(state[0])
            val y=pred[1].decompress(ny,lastX); val x=pred[0].decompress(nx,y); lastX=x
            samples[2*i]=x; samples[2*i+1]=y; i++
        }
    }
    private fun unprepare(): Long {
        crc.reset(); var size=0; var i=0
        while (i<blocks*cfg.channels) {
            var v0=samples[i]; var v1=0
            if (cfg.channels==2) { val y=samples[i+1]; v0-=y/2; v1=v0+y }
            if (cfg.bitsPerSample==16 && cfg.channels==2 && (v0 !in -32768..32767 || v1 !in -32768..32767)) malformed("16-bit sample ${maxOf(v0,v1)} outside its range")
            var c=0
            while (c<cfg.channels) {
                val value=if (c==0) v0 else v1
                val packed=when(cfg.bitsPerSample) { 8 -> value+128; 16 -> value.toShort().toInt(); else -> if (value<0) (value+0x800000) or 0x800000 else value }
                samples[i+c]=when(cfg.bitsPerSample) { 8 -> value.toByte().toInt(); 16 -> value.toShort().toInt(); else -> (packed shl 8) shr 8 }
                var shift=0; while (shift<cfg.bitsPerSample) { pack[size++]=(packed ushr shift).toByte(); shift+=8 }; c++
            }
            if (size>=3072) { crc.update(pack,0,size); size=0 }
            i+=cfg.channels
        }
        crc.update(pack,0,size); return crc.value ushr 1
    }
}
