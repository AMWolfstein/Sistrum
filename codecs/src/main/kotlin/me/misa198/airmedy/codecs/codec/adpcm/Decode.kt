// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/adpcm/decode.go, codec/adpcm/ima.go and codec/adpcm/ms.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.adpcm

import java.nio.ByteBuffer

/** One borrowed interleaved block. WAV IMA belongs to Media3 and is not duplicated. */
class Decoder(val cfg:Config) {
 init{cfg.validate()}
 val samples=IntArray(cfg.samplesPerBlock*cfg.channels);private val ima=Array(cfg.channels){ImaState()};private val ms=Array(cfg.channels){MsState()}
 fun decodeBlock(block:ByteBuffer):IntArray {val off=block.position();if(block.remaining()!=cfg.blockAlign)throw malformed("packet of ${block.remaining()} bytes is not a whole number of ${cfg.blockAlign}-byte blocks")
 if(cfg.layout==Layout.IMAQuickTime)decodeIMAQuickTime(block,off) else decodeMS(block,off);return samples}
 private fun u8(b:ByteBuffer,p:Int)=b.get(p).toInt() and 255
 private fun le16(b:ByteBuffer,p:Int)=(u8(b,p) or (u8(b,p+1) shl 8)).toShort().toInt()
 private fun decodeIMAQuickTime(b:ByteBuffer,off:Int){for(c in 0 until cfg.channels){val at=off+c*34;val v=((u8(b,at) shl 8) or u8(b,at+1)).toShort().toInt();val index=v and 127;val predictor=v and -128;if(index>88)throw malformed("step index $index in a block header (max 88)")
 val st=ima[c];if(index!=st.index||kotlin.math.abs(st.predictor-predictor)>127){st.predictor=predictor;st.index=index}
 for(i in 0 until 32){val by=u8(b,at+2+i);samples[(2*i)*cfg.channels+c]=st.next(by and 15);samples[(2*i+1)*cfg.channels+c]=st.next(by ushr 4)}}}
 private fun decodeMS(b:ByteBuffer,off:Int){val ch=cfg.channels;for(c in 0 until ch){val idx=u8(b,off+c);if(idx>=7)throw malformed("predictor index $idx in a block header (the table holds 7)");val st=ms[c];st.coef1=cfg.coefs[idx][0].toShort().toLong();st.coef2=cfg.coefs[idx][1].toShort().toLong();st.delta=le16(b,off+ch+2*c).toLong();st.sample1=le16(b,off+3*ch+2*c).toLong();st.sample2=le16(b,off+5*ch+2*c).toLong();samples[c]=st.sample2.toInt();samples[ch+c]=st.sample1.toInt()}
 var c=0;var n=2;for(at in off+7*ch until off+cfg.blockAlign){val by=u8(b,at);for(half in 0..1){val nib=if(half==0)by ushr 4 else by and 15;if(n>=cfg.samplesPerBlock)throw malformed("block of ${cfg.blockAlign} bytes decodes past the ${cfg.samplesPerBlock} samples its geometry states");samples[n*ch+c]=ms[c].next(nib);c++;if(c==ch){c=0;n++}}}}
 fun reset(){for(st in ima){st.predictor=0;st.index=0};for(st in ms){st.coef1=0;st.coef2=0;st.delta=0;st.sample1=0;st.sample2=0}}
}
