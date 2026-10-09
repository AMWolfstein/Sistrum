// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/wmavoice/lsp.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.wmavoice

import java.util.Arrays
import kotlin.math.cos
internal class DqStage(val size:Int,val mul:Double,val base:Double)
private val stages10i=arrayOf(DqStage(256,5.2187144800e-3,-0.6770823318869794),DqStage(64,1.4626986422e-3,-0.1936666207231964),DqStage(32,9.6179549166e-4,-0.10519937159810781),DqStage(32,1.1325736225e-3,-0.18035255105728284))
private val stages16i=arrayOf(DqStage(256,3.3439586280e-3,-0.40079182437437144),DqStage(64,6.9908173703e-4,-0.07631556874100326),DqStage(128,3.3216608306e-3,-0.40241916936893096),DqStage(64,1.0334960326e-3,-0.10093308877453287),DqStage(128,3.1899104283e-3,-0.4078289919184126))
private val stages10r=arrayOf(DqStage(128,2.5807601174e-3,-0.3375578474429161),DqStage(64,1.2354460219e-3,-0.16558078240010365),DqStage(64,1.1763821673e-3,-0.1622129950754554))
private val stages16r=arrayOf(DqStage(128,1.2232979501e-3,-0.17539511784991815),DqStage(128,1.4062241527e-3,-0.16621538411612877),DqStage(128,1.6114744851e-3,-0.17208387919303453))
internal fun warmLsp(){stages10i.size;dqLSP10i.size}
private fun dequant(out:DoubleArray,at:Int,table:IntArray,num:Int,idx:IntArray,stages:Array<DqStage>,from:Int,count:Int){
    Arrays.fill(out,at,at+num,0.0);var offset=0
    for(s in from until from+count){val st=stages[s];val row=offset+idx[s]*num;for(m in 0 until num)out[at+m]+=st.base+st.mul*table[row+m];offset+=st.size*num}
}
internal fun Decoder.independentLSFs(r:BitReader,out:DoubleArray){
    val idx=lspIndices
    if(lsps==10){idx[0]=r.bits(8);idx[1]=r.bits(6);idx[2]=r.bits(5);idx[3]=r.bits(5);dequant(out,0,dqLSP10i,10,idx,stages10i,0,4);return}
    idx[0]=r.bits(8);idx[1]=r.bits(6);idx[2]=r.bits(7);idx[3]=r.bits(6);idx[4]=r.bits(7)
    dequant(out,0,dqLSP16i1,5,idx,stages16i,0,2);dequant(out,5,dqLSP16i2,5,idx,stages16i,2,2);dequant(out,10,dqLSP16i3,6,idx,stages16i,4,1)
}
internal fun Decoder.residualLSFs(r:BitReader){
    val l=lsps;val mean=meanLSF();val prev=lsfPrevWork;val indep=lsfIndep;val a1=lsfA1;val a2=lsfA2
    for(n in 0 until l)prev[n]=prevLSF[n]-mean[n]
    independentLSFs(r,indep);val interp=r.bits(5);val idx=lspIndices
    idx[0]=r.bits(7);idx[1]=r.bits(if(l==10)6 else 7);idx[2]=r.bits(if(l==10)6 else 7)
    val t=if(l==10){if(cfg.lspQuantiserB)lsp10InterpB[interp]else lsp10InterpA[interp]}else{if(cfg.lspQuantiserB)lsp16InterpB[interp]else lsp16InterpA[interp]}
    for(n in 0 until l){val delta=prev[n]-indep[n];a1[n]=t[0][n].toDouble()*delta+indep[n];a1[l+n]=t[1][n].toDouble()*delta+indep[n]}
    if(l==10)dequant(a2,0,dqLSP10r,20,idx,stages10r,0,3)else{dequant(a2,0,dqLSP16r1,10,idx,stages16r,0,1);dequant(a2,10,dqLSP16r2,10,idx,stages16r,1,1);dequant(a2,20,dqLSP16r3,12,idx,stages16r,2,1)}
    for(n in 0 until l){lsf[0][n]=mean[n]+(a1[n]-a2[2*n]);lsf[1][n]=mean[n]+(a1[l+n]-a2[2*n+1]);lsf[2][n]=mean[n]+indep[n]}
}
internal fun Decoder.independentFrameLSFs(r:BitReader,out:DoubleArray){val mean=meanLSF();independentLSFs(r,out);for(n in 0 until lsps)out[n]+=mean[n]}
private fun Decoder.meanLSF()=if(lsps==10)meanLSF10[cfg.meanIndex]else meanLSF16[cfg.meanIndex]
internal fun stabilise(lsf:DoubleArray,n:Int){
    if(lsf[0]<0.00471238898038469)lsf[0]=0.00471238898038469
    for(i in 1 until n)lsf[i]=maxOf(lsf[i],lsf[i-1]+0.039269908169872414)
    if(lsf[n-1]>3.1368802646094087)lsf[n-1]=3.1368802646094087
    for(i in 1 until n)if(lsf[i]<lsf[i-1]){Arrays.sort(lsf,0,n);return}
}
private fun lspPoly(f:DoubleArray,v:DoubleArray,off:Int,h:Int){
    f[0]=1.0;f[1]= -2*v[off]
    for(i in 2..h){val a= -2*v[off+2*i-2];f[i]=a*f[i-1]+2*f[i-2];for(j in i-1 downTo 2)f[j]+=f[j-1]*a+f[j-2];f[1]+=a}
}
internal fun Decoder.lsfToLPC(from:DoubleArray,to:DoubleArray,weight:Double){
    for(n in 0 until lsps)lspScratch[n]=cos(from[n]+weight*(to[n]-from[n]))
    val h=lsps/2;lspPoly(paScratch,lspScratch,0,h);lspPoly(qaScratch,lspScratch,1,h)
    for(i in h-1 downTo 0){val p=paScratch[i+1]+paScratch[i];val q=qaScratch[i+1]-qaScratch[i];lpc[i]=(0.5*(p+q)).toFloat();lpc[2*h-1-i]=(0.5*(p-q)).toFloat()}
}
