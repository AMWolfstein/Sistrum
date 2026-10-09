// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/wma/decode.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.wma

import java.util.Arrays
import java.io.IOException
import kotlin.math.*
import me.misa198.airmedy.codecs.audio.FloatBuffer

fun interface FloatSink { fun emit(buffer:FloatBuffer) }
private class Band(val start:Int,val end:Int)
private class BlockSize(val len:Int,val coefsEnd:Int,val highStart:Int,val bands:IntArray,val high:Array<Band>){val plan=ImdctPlan(len)}
class Decoder(val cfg:Config) {
 val frameLen=cfg.frameLen
 private val frameLenBits=cfg.frameLenBits;private val coefBk=2*cfg.coefBookPair()
 private val sizes:Array<BlockSize>;private val selBits:Int
 private val useNoise=cfg.highFreqMult()!=1.0;private val noise=FloatArray(if(useNoise)8192 else 0);private var noiseAt=0
 private val expTable=FloatArray(156){10.0.pow((it-60)/16.0).toFloat()}
 private val lspCos=if(!cfg.expVLC)DoubleArray(frameLen){2*cos(Math.PI*it/frameLen)} else DoubleArray(0)
 private val lsp=DoubleArray(10);private val coded=BooleanArray(2)
 private val primary=BitReader();private val secondary=BitReader();private var r=primary
 private val carry=BitAppender();private var carried=false
 private var prevBits=0;private var curBits=0;private var nextBits=0;private var resetLens=true;private var blockPos=0
 private val exp=Array(cfg.channels){FloatArray(frameLen)};private val expLen=IntArray(cfg.channels);private val maxExp=FloatArray(cfg.channels)
 private val coefs=Array(cfg.channels){FloatArray(frameLen)};private val spec=Array(cfg.channels){FloatArray(frameLen)};private val acc=Array(cfg.channels){FloatArray(2*frameLen)}
 private val noiseOn:Array<BooleanArray>;private val noiseG:Array<IntArray>
 private val expCur=FloatArray(frameLen);private val block=FloatArray(2*frameLen);private val scratch=ImdctScratch(2*frameLen)
 private var skip=frameLen;private var drained=false;private val out=FloatBuffer(cfg.channels,frameLen)
 private var error:IOException?=null;private var resumed=false;private var noResume=false
 init {cfg.validate();val nb=cfg.nbBlockSizes();selBits=if(nb>1)bitsOf(nb-1)+1 else 0
 sizes=Array(nb){k->val len=frameLen ushr k;val end=cfg.coefsEnd(k);val start=floor(len*cfg.highFreqMult()+0.5).toInt();val bands=expBandWidths(k,len)
 if(bands.isEmpty()||end<=0)malformed("no exponent bands fit a $len-sample block at ${cfg.rate} Hz")
 val high=ArrayList<Band>();var at=0;for(w in bands){val bs=at;at+=w;val lo=maxOf(bs,start);val hi=minOf(at,end);if(lo<hi)high.add(Band(lo,hi))}
 if(at<end)malformed("exponent bands cover $at of a $end-coefficient span at ${cfg.rate} Hz");BlockSize(len,end,start,bands,high.toTypedArray())}
 val maxHigh=sizes.maxOf{it.high.size};noiseOn=Array(cfg.channels){BooleanArray(maxHigh)};noiseG=Array(cfg.channels){IntArray(maxHigh)}
 if(useNoise){val scale=Math.scalb(1.0,-31)*sqrt(3.0)*cfg.noiseMult();var seed=1;for(i in noise.indices){seed=seed*314159+1;noise[i]=(seed.toDouble()*scale).toFloat()}}
 // Resolve immutable Huffman books before the measured decode loop.
 Books.coefs;Books.gain;Books.exponent
 }
 private fun expBandWidths(k:Int,len:Int):IntArray {
 if(cfg.v2&&cfg.rate>=22050){val row=cfg.frameLenBits-7-k;if(row in 0..2)return when{cfg.rate>=44100->expBands44100;cfg.rate>=32000->expBands32000;else->expBands22050}[row]}
 val bands=ArrayList<Int>();var prev=0;for(edge in criticalFreqs){var end=if(cfg.v2)((len*2*edge+2*cfg.rate)/(4*cfg.rate)) shl 2 else floor((len*2*edge).toDouble()/cfg.rate+0.5).toInt();end=minOf(end,len);if(end>prev){bands.add(end-prev);prev=end};if(end>=len)break};return bands.toIntArray()}
 private fun nextNoise():Float{val v=noise[noiseAt];noiseAt=(noiseAt+1) and 8191;return v}
 fun decode(pkt:ByteArray,length:Int,sink:FloatSink){error?.let{throw it};try{if(length==0)malformed("empty packet");if(noResume)unsupported("this stream mixes block lengths without a bit reservoir, so a decode cannot resume mid-file: the three block lengths are decoder state that nothing in the bitstream restates")
 r=primary;r.reset(pkt,length);if(!cfg.reservoir)decodeFrame(sink) else decodeSuperframe(pkt,length,sink)
 }catch(e:IOException){error=e;throw e}}
 private fun decodeSuperframe(pkt:ByteArray,length:Int,sink:FloatSink){val offBits=cfg.offsetBits();r.skip(4);val frames=r.bits(4);val bitOff=r.bits(offBits+3);r.check();val headerBits=8+offBits+3;var n=if(carried)frames else frames-1
 if(n<0){if(resumed){carry.reset();carried=false;return};malformed("superframe states $frames frames with no frame carried in")};resumed=false
 if(headerBits+bitOff>r.n)malformed("superframe bit offset $bitOff runs past the packet");val at=headerBits+bitOff
 if(n==0){val from=if(carried)headerBits else at;if(!carried){carry.reset();resetLens=true};if(r.n-from<=8)malformed("all-continuation superframe carries no payload");carry.appendFrom(pkt,from,r.n-from);carried=true;return}
 if(carried){carry.appendFrom(pkt,headerBits,bitOff);val saved=r;r=secondary;r.reset(carry.buf,(carry.bits+7)/8,carry.bits);try{decodeFrame(sink)}finally{r=saved;carried=false};n--}
 r.pos=at;resetLens=true;repeat(n){decodeFrame(sink)};carry.reset();carried=false;val left=r.n-r.pos;if(left>0){carry.appendFrom(pkt,r.pos,left);carried=true}}
 private fun decodeFrame(sink:FloatSink){blockPos=0;while(blockPos<frameLen)decodeBlock();emitFrame(sink)}
 private fun emitFrame(sink:FloatSink){val off=minOf(skip,frameLen);skip-=off;if(off<frameLen){out.frames=frameLen-off;for(ch in 0 until cfg.channels){var i=off;while(i<frameLen){out.samples[(i-off)*cfg.channels+ch]=acc[ch][i];i++}}}
 for(ch in 0 until cfg.channels){System.arraycopy(acc[ch],frameLen,acc[ch],0,frameLen);Arrays.fill(acc[ch],frameLen,2*frameLen,0f)};if(off<frameLen)sink.emit(out)}
 private fun selector():Int{val v=r.bits(selBits);if(v>=sizes.size)malformed("block-length selector $v, stream has ${sizes.size} block sizes");return frameLenBits-v}
 private fun blockLens(){if(selBits==0){prevBits=frameLenBits;curBits=frameLenBits;nextBits=frameLenBits;return};val prev=if(resetLens)selector() else curBits;val cur=if(resetLens)selector() else nextBits;val next=selector();prevBits=prev;curBits=cur;nextBits=next;resetLens=false}
 private fun decodeBlock(){blockLens();val len=1 shl curBits;if(blockPos+len>frameLen)malformed("a $len-sample block at $blockPos overruns the $frameLen-sample frame");val sz=sizes[frameLenBits-curBits];val ms=cfg.channels==2&&r.bit()!=0;var any=false
 for(ch in 0 until cfg.channels){coded[ch]=r.bit()!=0;any=any||coded[ch]};r.check();if(!any){transform(ms,len);return}
 var gain=1;while(true){val v=r.bits(7);r.check();gain+=v;if(v!=127)break;if(gain>512)malformed("block gain ladder passes 512")};if(gain>512)malformed("block gain $gain passes 512")
 val esc=when{gain<15->13;gain<32->12;gain<40->11;gain<45->10;else->9}
 if(useNoise)readNoiseCoding(sz);val transmit=if(len<frameLen)r.bit()!=0 else true
 for(ch in 0 until cfg.channels){if(!coded[ch])continue;if(transmit){decodeExponents(ch,sz,len);expLen[ch]=len}else if(expLen[ch]==0)malformed("channel $ch reuses exponents that were never transmitted")}
 for(ch in 0 until cfg.channels){if(coded[ch])decodeCoefs(ch,sz,len,esc,ms);if(!cfg.v2&&cfg.channels==2)r.align()};r.check()
 for(ch in 0 until cfg.channels)if(coded[ch])reconstruct(ch,sz,len,gain)
 if(ms&&coded[1]){if(!coded[0]){Arrays.fill(spec[0],0,len,0f);coded[0]=true};val a=spec[0];val b=spec[1];for(i in 0 until len){val left=a[i];a[i]=left+b[i];b[i]=left-b[i]}}
 transform(ms,len)}
 private fun readNoiseCoding(sz:BlockSize){for(ch in 0 until cfg.channels){if(!coded[ch])continue;for(j in sz.high.indices)noiseOn[ch][j]=r.bit()!=0}
 for(ch in 0 until cfg.channels){if(!coded[ch])continue;var gain=0;var first=true;for(j in sz.high.indices){if(!noiseOn[ch][j])continue;if(first){gain=r.bits(7)-19;first=false}else{val sym=Books.gain.decode(r);if(sym<0)malformed("noise-gain book desynchronised");gain+=hgainHuff[sym][0]-18};noiseG[ch][j]=gain}};r.check()}
 private fun decodeExponents(ch:Int,sz:BlockSize,len:Int){expLen[ch]=0;if(!cfg.expVLC){decodeLSPExponents(ch,len);return};val dst=exp[ch];var idx=36;var first=0;if(!cfg.v2){idx=r.bits(5)+10;first=1};var maxIdx= -61;var at=0
 if(first==1){val v=expTable[idx+60];repeat(sz.bands[0]){dst[at++]=v};maxIdx=maxOf(maxIdx,idx)}
 for(b in first until sz.bands.size){val sym=Books.exponent.decode(r);if(sym<0)malformed("exponent book desynchronised");idx+=sym-60;if(idx !in -60..95)malformed("exponent index $idx outside -60..95");val v=expTable[idx+60];repeat(sz.bands[b]){dst[at++]=v};maxIdx=maxOf(maxIdx,idx)}
 Arrays.fill(dst,at,len,0f);maxExp[ch]=expTable[maxIdx+60];r.check()}
 private fun decodeLSPExponents(ch:Int,len:Int){for(j in 0 until 10){val n=if(j==0||j==8||j==9)3 else 4;lsp[j]=lspCodebook[j][r.bits(n)].toDouble()};r.check();var maxv=0f
 for(i in 0 until len){val w=lspCos[i];var p=0.5;var q=0.5;var j=1;while(j<10){q*=w-lsp[j-1];p*=w-lsp[j];j+=2};p*=p*(2-w);q*=q*(2+w);val s=p+q;if(s<=0)malformed("the LSP exponent curve is degenerate at bin $i");val v=(1/sqrt(sqrt(s))).toFloat();exp[ch][i]=v;maxv=maxOf(maxv,v)};maxExp[ch]=maxv}
 private fun decodeCoefs(ch:Int,sz:BlockSize,len:Int,esc:Int,ms:Boolean){val dst=coefs[ch];Arrays.fill(dst,0,len,0f);var nb=sz.coefsEnd-cfg.coefsStart();if(useNoise)for(j in sz.high.indices)if(noiseOn[ch][j])nb-=sz.high[j].end-sz.high[j].start
 if(nb<0)malformed("noise fills more of the block than it codes");val book=Books.coefs[coefBk+if(ms&&ch==1)1 else 0];val mask=len-1;var offset=0
 while(offset<nb){val code=book.vlc.decode(r);if(code<0)malformed("coefficient book desynchronised");if(code==1)break;var level:Int;val run:Int
 if(code==0){level=r.bits(esc);run=r.bits(frameLenBits)}else{run=book.run[code];level=book.level[code]};offset+=run;if(r.bit()==0)level= -level;dst[offset and mask]=level.toFloat();offset++};r.check();if(offset>nb)malformed("run-level coding reaches $offset past the channel's $nb coefficients")}
 private fun reconstruct(ch:Int,sz:BlockSize,len:Int,gain:Int){val ec=expCur;val src=exp[ch];val oldLen=expLen[ch]
 when{oldLen==len->System.arraycopy(src,0,ec,0,len);oldLen>len->{val sh=bitsOf(oldLen/len);for(i in 0 until len)ec[i]=src[i shl sh]};else->{val sh=bitsOf(len/oldLen);for(i in 0 until len)ec[i]=src[i ushr sh]}}
 val norm=mdctNorm(len,cfg.v2)/32768;val mult=10.0.pow(gain/20.0)/maxExp[ch].toDouble()*norm;if(!mult.isFinite())malformed("block gain $gain and exponent scale produce no finite coefficient scale")
 val m=mult.toFloat();val dst=spec[ch];val coef=coefs[ch];val start=cfg.coefsStart();val end=sz.coefsEnd;var ptr=0
 for(i in 0 until start)dst[i]=if(useNoise)nextNoise()*ec[i]*m else 0f
 val lo=minOf(sz.highStart,end);for(i in start until lo){dst[i]=(if(useNoise)coef[ptr]+nextNoise() else coef[ptr])*ec[i]*m;ptr++}
 if(useNoise&&sz.high.isNotEmpty()){var last= -1;for(j in sz.high.indices)if(noiseOn[ch][j])last=j;val refPower=if(last>=0)bandPower(ec,sz.high[last]) else 0.0
 for(j in sz.high.indices){val band=sz.high[j];if(!noiseOn[ch][j]){for(i in band.start until band.end){dst[i]=(coef[ptr]+nextNoise())*ec[i]*m;ptr++};continue}
 val g=sqrt(bandPower(ec,band)/refPower)*10.0.pow(noiseG[ch][j]/20.0)/(maxExp[ch].toDouble()*cfg.noiseMult())*norm
 if(!g.isFinite())malformed("noise gain ${noiseG[ch][j]} produces no finite band scale");val g32=g.toFloat();for(i in band.start until band.end)dst[i]=nextNoise()*ec[i]*g32}}
 if(useNoise){val lastExp=ec[end-1]*m;for(i in end until len)dst[i]=nextNoise()*lastExp}else Arrays.fill(dst,end,len,0f)}
 private fun bandPower(ec:FloatArray,b:Band):Double{var sum=0.0;for(i in b.start until b.end){val v=ec[i].toDouble();sum+=v*v};return sum/(b.end-b.start)}
 private fun transform(ms:Boolean,len:Int){val plan=sizes[frameLenBits-curBits].plan;val prev=sizes[frameLenBits-prevBits].plan;val next=sizes[frameLenBits-nextBits].plan;val at=frameLen/2+blockPos-len/2
 for(ch in 0 until cfg.channels){when{coded[ch]->plan.imdct(spec[ch],block,scratch);ms&&ch==1->{};else->Arrays.fill(block,0,2*len,0f)};overlapAdd(acc[ch],at,block,len,1 shl prevBits,1 shl nextBits,plan,prev,next)};blockPos+=len}
 fun drain(sink:FloatSink){error?.let{throw it};if(drained)return;drained=true;try{emitFrame(sink)}catch(e:IOException){error=e;throw e}}
 fun reset(fromStart:Boolean=false){for(ch in acc.indices){Arrays.fill(acc[ch],0f);expLen[ch]=0;maxExp[ch]=0f};carry.reset();carried=false;resetLens=true;resumed=!fromStart;noResume=!fromStart&&selBits>0&&!cfg.reservoir;noiseAt=0;blockPos=0;skip=frameLen;drained=false;error=null;primary.reset(primary.buf,0);secondary.reset(secondary.buf,0);r=primary}
}
