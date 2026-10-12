package me.misa198.airmedy.dsp
import com.google.gson.Gson
import me.misa198.airmedy.dsp.silence.Detector
import org.junit.Assert.*
import org.junit.Test
import java.io.File
class SilenceTest {
 class Span { var From=0L;var To=0L }
 class Vector {var Rate=0;var Channels=0;var DB=0.0;var Nanos=0L;lateinit var Input:Array<FloatArray>;var Spans:Array<Span>?=null;var Dropped=0;var DroppedSamples=0L;var TotalSamples=0L;var Samples=0L}
 @Test fun oracle() {
  val vectors=Gson().fromJson(File(System.getProperty("dsp.repo"),"docs/dsp/silence-oracle.json").readText(),Array<Vector>::class.java)
  for(v in vectors) for(chunk in intArrayOf(1,17,257)) {
   val d=Detector(v.Rate,v.Channels,v.DB,v.Nanos)
   var at=0;while(at<v.Input[0].size){val n=minOf(chunk,v.Input[0].size-at);d.process(v.Input,at,n);at+=n};d.flush();d.flush()
   val spans=v.Spans?:emptyArray();assertEquals(spans.size,d.spanCount)
   for(i in spans.indices){assertEquals(spans[i].From,d.from(i));assertEquals(spans[i].To,d.to(i))}
   assertEquals(v.Dropped,d.dropped);assertEquals(v.DroppedSamples,d.droppedSamples);assertEquals(v.TotalSamples,d.totalSamples);assertEquals(v.Samples,d.samples)
   assertThrows(IllegalStateException::class.java){d.process(v.Input)}
  }
 }
 @Test fun exactDurationBoundsAndSpecialSamples() {
  assertEquals(30870L,Detector(44100,1,-55.0,700_000_000).minFrames)
  assertEquals(14686L,Detector(44100,1,-55.0,333_000_000).minFrames)
  val d=Detector(48000,1,-80.0,1,4)
  d.process(arrayOf(floatArrayOf(Float.NaN,0f,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,0f)))
  d.flush();assertEquals(listOf(Detector.Span(0,2),Detector.Span(4,5)),d.spans())
  assertThrows(IllegalArgumentException::class.java){Detector(48000,1,Double.NaN,1)}
  val full=Detector(48000,1,-80.0,1,0)
  assertThrows(IllegalStateException::class.java){full.process(arrayOf(floatArrayOf(0f,1f)))}
 }
 @Test fun allocation() {
  val d=Detector(48000,2,-55.0,1,128);val src=Array(2){FloatArray(64){if(it%4<2)0f else .5f}}
  assertEquals(0L,DspBenchmark.measure("silence",64){d.reset();d.process(src);d.flush()})
 }
}
