package com.amwolfstein.sistrum.dsp
import com.amwolfstein.sistrum.dsp.resample.*
import org.junit.Test
import org.junit.Assert.assertEquals
class AllocationTest {
 @Test fun resample() {
  for(p in Profile.entries) {
   val r=Resampler(44100,48000,2,p);val src=Array(2){FloatArray(64)};val dst=Array(2){FloatArray(128)}
   assertEquals(0L,DspBenchmark.measure(p.id,64){r.reset();r.process(dst,src);while(r.drain(dst)>0){}})
  }
 }
}
