package me.misa198.airmedy.dsp
import me.misa198.airmedy.dsp.resample.*
import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

/** Independent signal measurements of the pinned Go profile guarantees. */
class ResamplerQualityTest {
 private fun convert(inputRate:Int,outputRate:Int,profile:Profile,freq:Double):FloatArray {
  val r=Resampler(inputRate,outputRate,1,profile)
  val src=arrayOf(FloatArray(inputRate/2){(.5*sin(2*PI*freq*it/inputRate)).toFloat()})
  val dst=arrayOf(FloatArray(Resampler.outputLen(src[0].size.toLong(),inputRate,outputRate).toInt()))
  var at=0;var produced=0
  while(at<src[0].size){produced+=r.process(dst,src,at,src[0].size-at,produced);at+=r.consumed}
  while(produced<dst[0].size){val n=r.drain(dst,produced);check(n>0);produced+=n}
  val trim=2*(r.delayNumerator/r.denominator+1)
  return dst[0].copyOfRange(trim,dst[0].size-trim)
 }
 private fun toneAmp(input:FloatArray,rate:Int,freq:Double):Double {
  var a=0.0;var b=0.0;var sum=0.0
  for(i in input.indices){val w=.5-.5*cos(2*PI*i/input.size);val ph=2*PI*freq*i/rate;a+=input[i]*w*cos(ph);b+=input[i]*w*sin(ph);sum+=w}
  return 2*hypot(a,b)/sum
 }
 @Test fun passbandAliasAndImages() {
  for(p in Profile.entries) {
   val pass=if(p==Profile.HQ).91 else .85;val stop=if(p==Profile.HQ)1.09 else 1.15
   val ripple=if(p==Profile.HQ).05 else .10;val reject=if(p==Profile.HQ)110.0 else 70.0
   for(rates in arrayOf(intArrayOf(96000,44100),intArrayOf(48000,44100),intArrayOf(44100,48000),intArrayOf(44100,96000),intArrayOf(192000,48000))) {
    val ir=rates[0];val or=rates[1];val nyq=min(ir,or)/2.0
    for(fraction in doubleArrayOf(.1,.5,1.0)) {
     val freq=fraction*pass*nyq
     val out=convert(ir,or,p,freq)
     assertTrue("$p $ir->$or passband $freq",abs(20*log10(toneAmp(out,or,freq)/.5))<=ripple)
    }
    if(or<ir && stop*nyq<ir/2.0) {
     val freq=(stop*nyq+ir/2.0)/2;val folded=abs(freq-or*round(freq/or))
     val out=convert(ir,or,p,freq)
     assertTrue("$p $ir->$or alias",20*log10(toneAmp(out,or,folded)/.5)<= -reject)
    }
    if(or>ir) {
     val freq=.5*pass*nyq;val image=ir-freq
     if(image<or/2.0){val out=convert(ir,or,p,freq);assertTrue("$p $ir->$or image",20*log10(toneAmp(out,or,image)/.5)<= -reject)}
    }
   }
  }
 }
}
