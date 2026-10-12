package me.misa198.airmedy.dsp
import com.google.gson.Gson
import me.misa198.airmedy.dsp.gain.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
class GainTest {
 class Vector {var Kind="";var Rate=0;var Channels=0;var Ceiling=0.0;var DB=0.0;var Linear=0.0;lateinit var Input:Array<FloatArray>;lateinit var Output:Array<FloatArray>}
 @Test fun oracle() {
  val vectors=Gson().fromJson(File(System.getProperty("dsp.repo"),"docs/dsp/gain-oracle.json").readText(),Array<Vector>::class.java)
  for(v in vectors) {
   if(v.Kind=="scalar") {
    assertEquals(v.Linear,Gain.fromDB(v.DB),1e-12*maxOf(1.0,v.Linear));val result=v.Input[0].copyOf();Gain.apply(result,Gain.fromDB(v.DB).toFloat());assertArrayEquals(v.Output[0],result,0f);continue
   }
   var baseline:Array<FloatArray>?=null
   for(chunk in intArrayOf(1,31,2049)) {
    val k=if(v.Kind=="compressor")Compressor(v.Rate,v.Channels)else null
    val l=if(v.Kind=="limiter")Limiter(v.Rate,v.Channels,v.Ceiling)else null
    val dst=Array(v.Channels){FloatArray(73)};val actual=Array(v.Channels){FloatArray(v.Output[0].size)}
    var at=0;var out=0
    fun record(n:Int){for(c in dst.indices)System.arraycopy(dst[c],0,actual[c],out,n);out+=n}
    while(at<v.Input[0].size){val size=minOf(chunk,v.Input[0].size-at)
     if(k!=null){val n=k.process(dst,v.Input,at,0,minOf(size,73));at+=n;record(n)}
     else {val n=l!!.process(dst,v.Input,at,size);at+=l.consumed;record(n)}
    }
    while(true){val n=if(k!=null)k.drain(dst)else l!!.drain(dst);if(n==0)break;record(n)}
    assertEquals(v.Output[0].size,out)
    for(c in actual.indices) {
     for(i in actual[c].indices)assertEquals("${v.Kind} rate=${v.Rate} channels=${v.Channels} c=$c i=$i chunk=$chunk",v.Output[c][i].toDouble(),actual[c][i].toDouble(),4e-7)
     if(baseline!=null)assertArrayEquals("Chunk invariance",baseline[c],actual[c],0f)
    };baseline=actual
   }
  }
 }
 @Test fun compressorBoundariesAndRecovery() {
  val c=Compressor(48000,2)
  assertEquals(0.0,c.targetDB(0.0),0.0);assertEquals(c.targetDB(2.0),c.targetDB(Double.POSITIVE_INFINITY),0.0)
  val src=arrayOf(floatArrayOf(Float.POSITIVE_INFINITY,Float.NaN,0f,1f),FloatArray(4));val dst=Array(2){FloatArray(4)}
  c.process(dst,src);assertTrue(dst[0][0].isInfinite());assertTrue(dst[0][1].isNaN());assertTrue(dst[0][3].isFinite());assertEquals(0f,dst[1][3],0f)
  assertEquals(10_000_000_000L,c.horizonNanos)
  assertThrows(IllegalArgumentException::class.java){Compressor(48000,2,Preset.OFF)}
  assertEquals(listOf(Preset.VOICE),Preset.presets())
 }
 @Test fun limiterIdentityCeilingAndBounds() {
  val l=Limiter(48000,2);val input=Array(2){FloatArray(2048){if(it%17==0).125f else -.25f}};val output=Array(2){FloatArray(2048)}
  var produced=l.process(output,input);while(produced<2048)produced+=l.drain(output,produced)
  for(c in input.indices)assertArrayEquals(input[c],output[c],0f)
  assertEquals(0,l.clamped)
  l.reset();input[0].fill(1.75f);input[1].fill(-1.75f)
  produced=l.process(output,input);while(produced<2048)produced+=l.drain(output,produced)
  val ceiling=Gain.fromDB(-1.0).toFloat()
  for(c in output.indices)for(v in output[c])assertTrue(kotlin.math.abs(v)<=ceiling)
  assertEquals(0,l.clamped)
  assertThrows(IllegalStateException::class.java){l.process(output,input)}
  assertThrows(IllegalArgumentException::class.java){Limiter(0,1)}
  assertThrows(IllegalArgumentException::class.java){Limiter(48000,1,Double.NaN)}
 }
 @Test fun settleHorizonsAndInvalidRates() {
  assertThrows(IllegalArgumentException::class.java){Limiter(Int.MAX_VALUE,1)}
  val rate=8000
  val prefix=arrayOf(FloatArray(1000){1.75f})
  val input=arrayOf(FloatArray(rate*10+128){.5f})
  val continuous=Compressor(rate,1);val restarted=Compressor(rate,1)
  continuous.process(arrayOf(FloatArray(1000)),prefix)
  val a=arrayOf(FloatArray(input[0].size));val b=arrayOf(FloatArray(input[0].size))
  continuous.process(a,input);restarted.process(b,input)
  for(i in rate*10 until input[0].size)assertEquals(a[0][i].toRawBits(),b[0][i].toRawBits())
  fun limit(k:Limiter,src:Array<FloatArray>):FloatArray {
   val dst=arrayOf(FloatArray(src[0].size+1000));var at=0;var written=0
   while(at<src[0].size){written+=k.process(dst,src,at,src[0].size-at,written);at+=k.consumed}
   while(true){val n=k.drain(dst,written);if(n==0)break;written+=n}
   assertEquals(src[0].size,written);return dst[0].copyOf(written)
  }
  val whole=arrayOf(prefix[0]+input[0]);val wholeOut=limit(Limiter(rate,1),whole)
  val segmentOut=limit(Limiter(rate,1),input)
  for(i in rate*2 until input[0].size)assertEquals(wholeOut[prefix[0].size+i].toRawBits(),segmentOut[i].toRawBits())
 }
 @Test fun allocations() {
  val c=Compressor(48000,2);val l=Limiter(48000,2);val src=Array(2){FloatArray(256){if(it%16==0)1.5f else .25f}};val dst=Array(2){FloatArray(256)}
  assertEquals(0L,DspBenchmark.measure("compressor",256){c.process(dst,src)})
  assertEquals(0L,DspBenchmark.measure("limiter",256){l.reset();var at=0;while(at<256){l.process(dst,src,at,256-at);at+=l.consumed};while(l.drain(dst)>0){}})
 }
}
