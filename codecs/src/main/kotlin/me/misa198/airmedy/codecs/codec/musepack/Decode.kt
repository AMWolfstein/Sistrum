// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/musepack/decode.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.musepack

class Decoder(val cfg:Config) {
 internal val st=FrameState();private val y=Array(2){Array(36){FloatArray(32)}};private val syn=Array(2){Synthesis()}
 internal val r=BitReader();val samples=FloatArray(1152*cfg.channels)
 private var frames=0;private var frame=0;private var sticky:MusepackException?=null
 init{cfg.validate();if(cfg.streamVersion==7)Books7.hdr else Books8.bands}
 internal fun startPayload(data:ByteArray,offset:Int,bitLen:Int,frames:Int){sticky?.let{throw it};this.frames=frames;frame=0;r.reset(data,offset,bitLen)}
 fun decodeFrame():FloatArray {sticky?.let{throw it};try {
 if(frame>=frames)throw malformed("packet has no remaining frames")
 if(cfg.streamVersion==7){val maxUsed=st.readSV7Header(r,cfg);st.readSV7Samples(r,maxUsed);if(r.pos!=r.end)throw malformed("frame consumed ${r.pos} bits of the ${r.end} it declared")} else st.readSV8(r,cfg,frame==0)
 requantize(st,cfg,y);for(ch in 0 until cfg.channels)syn[ch].frame(y[ch],samples,ch,cfg.channels)
 frame++;if(cfg.streamVersion==8&&frame==frames&&r.end-r.pos>=8)throw malformed("block has ${r.end-r.pos} bits past its last frame, more than padding")
 return samples
 }catch(e:MusepackException){sticky=e;throw e}}
 fun reset(){st.reset();for(s in syn)s.reset();frames=0;frame=0;sticky=null}
}
