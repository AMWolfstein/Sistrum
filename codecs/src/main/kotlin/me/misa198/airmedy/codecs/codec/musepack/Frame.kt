// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/musepack/frame.go and codec/musepack/noise.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.musepack

internal class Prng {
 var r1=1;var r2=1
 fun next():Int {val t1=(Integer.bitCount(r1 and 0xf5) and 1) shl 31;val t2=Integer.bitCount((r2 ushr 25) and 0x63) and 1;r1=(r1 ushr 1) or t1;r2=(r2 shl 1) or t2;return r1 xor r2}
 fun noise():Short {val v=next();return (((v ushr 24) and 255)+((v ushr 16) and 255)+((v ushr 8) and 255)+(v and 255)-510).toShort()}
}
internal class FrameState {
 val res=Array(2){IntArray(32)};val scfi=Array(2){IntArray(32)};val scf=Array(2){Array(32){IntArray(3)}}
 val ms=BooleanArray(32);val q=Array(32){Array(2){ShortArray(36)}};val dscfFlag=Array(2){BooleanArray(32)}
 var lastMaxBand=0;val rng=Prng()
 fun reset(){for(ch in 0..1){res[ch].fill(0);scfi[ch].fill(0);dscfFlag[ch].fill(false);for(n in 0..31){scf[ch][n].fill(0);q[n][ch].fill(0)}};ms.fill(false);lastMaxBand=0;rng.r1=1;rng.r2=1}
 private fun checkOver(r:BitReader){if(r.over)throw malformed("frame reads past the end of its packet")}
 private fun dscf7(r:BitReader,prev:Int):Int {val idx=Books7.dscf.decode(r);return if(idx!=8)prev+idx else r.bits(6)}
 fun readSV7Header(r:BitReader,cfg:Config):Int {
 val b=Books7;var maxUsed=0;res[0][0]=r.bits(4);res[1][0]=r.bits(4)
 if(res[0][0]!=0||res[1][0]!=0){if(cfg.ms)ms[0]=r.bit()!=0;maxUsed=1}
 for(n in 1..cfg.maxBand){for(ch in 0..1){val idx=b.hdr.decode(r);res[ch][n]=if(idx!=4)res[ch][n-1]+idx else r.bits(4)};if(res[0][n]!=0||res[1][n]!=0){if(cfg.ms)ms[n]=r.bit()!=0;maxUsed=n+1}}
 for(n in 0 until maxUsed)for(ch in 0..1)if(res[ch][n] !in -1..17)throw malformed("frame codes a quantiser resolution outside -1..17")
 for(n in 0 until maxUsed)for(ch in 0..1)if(res[ch][n]!=0)scfi[ch][n]=b.scfi.decode(r)
 for(n in 0 until maxUsed)for(ch in 0..1){if(res[ch][n]==0)continue;val s=scf[ch][n]
 when(scfi[ch][n]) {
 1->{s[0]=dscf7(r,s[2]);s[1]=dscf7(r,s[0]);s[2]=s[1]}
 3->{s[0]=dscf7(r,s[2]);s[1]=s[0];s[2]=s[1]}
 2->{s[0]=dscf7(r,s[2]);s[1]=s[0];s[2]=dscf7(r,s[1])}
 else->{s[0]=dscf7(r,s[2]);s[1]=dscf7(r,s[0]);s[2]=dscf7(r,s[1])}}
 for(i in 0..2)if(s[i]>1024)s[i]=0x8080
 };checkOver(r);return maxUsed
 }
 fun readSV7Samples(r:BitReader,maxUsed:Int){val b=Books7
 for(n in 0 until maxUsed)for(ch in 0..1){val a=q[n][ch];val resolution=res[ch][n]
 when {
 resolution !in -1..17->throw malformed("frame codes a quantiser resolution outside -1..17")
 resolution==0->{}
 resolution == -1->for(k in 0..35)a[k]=rng.noise()
 resolution==1->{val t=b.q[0][r.bit()];var k=0;while(k<36){val idx=t.decode(r);a[k]=(idx%3-1).toShort();a[k+1]=(idx/3%3-1).toShort();a[k+2]=(idx/9-1).toShort();k+=3}}
 resolution==2->{val t=b.q[1][r.bit()];var k=0;while(k<36){val idx=t.decode(r);a[k]=(idx%5-2).toShort();a[k+1]=(idx/5-2).toShort();k+=2}}
 resolution<=7->{val t=b.q[resolution-1][r.bit()];for(k in 0..35)a[k]=t.decode(r).toShort()}
 else->{val width=resBit[resolution];val off=dc[resolution+1];for(k in 0..35)a[k]=(r.bits(width)-off).toShort()}
 }};checkOver(r)
 }
 fun readSV8(r:BitReader,cfg:Config,key:Boolean) {
 val b=Books8;var maxUsed=if(key)r.logDec(cfg.maxBand+1) else lastMaxBand+b.bands.decode(r)
 if(!key&&maxUsed>32)maxUsed-=33;lastMaxBand=maxUsed
 if(maxUsed>0){for(ch in 0..1){res[ch][maxUsed-1]=b.res[0].decode(r);if(res[ch][maxUsed-1]>15)res[ch][maxUsed-1]-=17}
 for(n in maxUsed-2 downTo 0)for(ch in 0..1){val t=b.res[if(res[ch][n+1]>2)1 else 0];res[ch][n]=t.decode(r)+res[ch][n+1];if(res[ch][n]>15)res[ch][n]-=17}
 if(cfg.ms){var tot=0;for(n in 0 until maxUsed)if(res[0][n]!=0||res[1][n]!=0)tot++
 val cnt=r.logDec(tot);var flags=if(cnt!=0&&cnt!=tot)r.enumDec(minOf(cnt,tot-cnt),tot) else 0
 if(cnt*2>tot)flags=flags.inv();for(n in maxUsed-1 downTo 0)if(res[0][n]!=0||res[1][n]!=0){ms[n]=flags and 1!=0;flags=flags ushr 1}}
 }
 for(n in maxUsed..cfg.maxBand){res[0][n]=0;res[1][n]=0}
 if(key)for(ch in 0..1)dscfFlag[ch].fill(true)
 for(n in 0 until maxUsed){var cnt=-1;if(res[0][n]!=0)cnt++;if(res[1][n]!=0)cnt++;if(cnt<0)continue
 val v=b.scfi[cnt].decode(r);if(res[0][n]!=0)scfi[0][n]=v shr (2*cnt);if(res[1][n]!=0)scfi[1][n]=v and 3}
 for(n in 0 until maxUsed)for(ch in 0..1){if(res[ch][n]==0)continue;val s=scf[ch][n]
 if(dscfFlag[ch][n]){s[0]=r.bits(7)-6;dscfFlag[ch][n]=false} else {var d=b.dscf[1].decode(r);if(d==64)d+=r.bits(6);s[0]=((s[2]-25+d) and 127)-6}
 for(m in 0..1){if((scfi[ch][n] shl m) and 2==0){var d=b.dscf[0].decode(r);if(d==31)d=64+r.bits(6);s[m+1]=((s[m]-25+d) and 127)-6} else s[m+1]=s[m]}}
 for(n in 0 until maxUsed)for(ch in 0..1){val a=q[n][ch];val resolution=res[ch][n]
 when {
 resolution==0->{}
 resolution==2->{var idx=2*thres[2];var k=0;while(k<36){val t=b.q[0][if(idx>thres[2])1 else 0];val v=t.decode(r);a[k]=(v%5-2).toShort();a[k+1]=(v/5%5-2).toShort();a[k+2]=(v/25-2).toShort();idx=(idx ushr 1)+kotlin.math.abs(a[k].toInt())+kotlin.math.abs(a[k+1].toInt())+kotlin.math.abs(a[k+2].toInt());k+=3}}
 resolution==1->{var k=0;while(k<36){val cnt=b.q1.decode(r);var set=if(cnt>0&&cnt<18)r.enumDec(minOf(cnt,18-cnt),18) else 0;if(cnt>9)set=set.inv();for(i in k until k+18){a[i]=0;if(set and (1 shl 17)!=0)a[i]=((r.bit() shl 1)-1).toShort();set=set shl 1};k+=18}}
 resolution == -1->for(k in 0..35)a[k]=rng.noise()
 resolution<=4->{val t=b.q[1][resolution-3];var k=0;while(k<36){val v=t.decode(r);a[k]=((v shl 4).toByte().toInt() shr 4).toShort();a[k+1]=(v.toByte().toInt() shr 4).toShort();k+=2}}
 resolution<=8->{val pair=b.q[resolution-3];var idx=2*thres[resolution];for(k in 0..35){val v=pair[if(idx>thres[resolution])1 else 0].decode(r);a[k]=v.toShort();idx=(idx ushr 1)+kotlin.math.abs(v)}}
 else->{val extra=resolution-9;val off=dc[resolution+1];for(k in 0..35){var hi=b.q9up.decode(r) and 255;if(extra>0)hi=(hi shl extra) or r.bits(extra);a[k]=(hi-off).toShort()}}
 }};checkOver(r)
 }
 companion object {val thres=intArrayOf(0,0,3,0,0,1,3,4,8)}
}
