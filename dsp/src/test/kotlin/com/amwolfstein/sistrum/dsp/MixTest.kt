package com.amwolfstein.sistrum.dsp
import com.google.gson.Gson
import com.amwolfstein.sistrum.dsp.mix.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
class MixTest {
 class Vector {var Source=0;var Target=0;var Refused=false;var MaxGain=0.0;var Input:Array<FloatArray>?=null;var Output:Array<FloatArray>?=null}
 @Test fun oracle() {
  val vectors=Gson().fromJson(File(System.getProperty("dsp.repo"),"docs/dsp/mix-oracle.json").readText(),Array<Vector>::class.java)
  for(v in vectors) {
   if(v.Refused){assertThrows(RuntimeException::class.java){Matrix.forLayouts(v.Source,v.Target)};continue}
   val m=Matrix.forLayouts(v.Source,v.Target);val src=v.Input!!;val expected=v.Output!!
   assertEquals(v.MaxGain,m.maxGain,0.0)
   for(chunk in intArrayOf(1,17,128)) {
    val dst=Array(m.outputs){FloatArray(128)};var at=0
    while(at<128){val n=minOf(chunk,128-at);m.apply(dst,src,n,at,at);at+=n}
    for(o in dst.indices) {
     for(i in 0 until m.inputs) assertEquals("Coefficient ${v.Source}->${v.Target}",expected[o][i].toRawBits(),m.coefficient(o,i).toRawBits())
     for(i in 0 until 128)assertEquals("PCM ${v.Source}->${v.Target} row=$o frame=$i",expected[o][i].toDouble(),dst[o][i].toDouble(),2e-7)
    }
   }
  }
 }
 @Test fun namedMedia3Placement() {
  val lfe=Media3Layout.forMask(Speakers.LFE)
  assertEquals(6,lfe.outputs);assertEquals(1f,lfe.coefficient(3,0),0f);assertEquals(0f,lfe.coefficient(2,0),0f)
  val side=Media3Layout.forMask(Speakers.SURROUND_51_SIDE)
  assertEquals(8,side.outputs)
  assertEquals(0f,side.coefficient(4,4),0f);assertEquals(1f,side.coefficient(6,4),0f)
  for(src in 1..Speakers.SURROUND_71) if(src and Speakers.SURROUND_71.inv()==0) {
   val m=Media3Layout.forMask(src)
   assertEquals(Media3Layout.maskForCount(m.outputs),m.targetMask)
   var col=0
   for(bit in 0..10) if(src and (1 shl bit)!=0) {
    var row=0;for(outBit in 0..10)if(m.targetMask and (1 shl outBit)!=0) {
     assertEquals(if(bit==outBit)1f else 0f,m.coefficient(row,col),0f);row++
    };col++
   }
  }
  assertEquals(2,Media3Layout.forMask(Speakers.TFL or Speakers.TFR).outputs)
  assertThrows(IllegalArgumentException::class.java){Media3Layout.forMask(1 shl 20)}
 }
 @Test fun lfeDroppedAndEnergyNormalized() {
  val m=Matrix.forLayouts(Speakers.SURROUND_51,Speakers.STEREO)
  for(o in 0..1){assertEquals(0f,m.coefficient(o,3),0f);var power=0.0;for(i in 0 until 6)power+=m.coefficient(o,i).toDouble()*m.coefficient(o,i);assertEquals(1.0,power,1e-7)}
  assertTrue(m.maxGain>1)
  val x=Array(6){FloatArray(64)};val dst=Array(2){FloatArray(64)}
  assertThrows(IllegalArgumentException::class.java){m.apply(arrayOf(x[0],dst[1]),x,64)}
  assertEquals(0L,DspBenchmark.measure("mix-5.1-stereo",64){m.apply(dst,x,64)})
 }
}
