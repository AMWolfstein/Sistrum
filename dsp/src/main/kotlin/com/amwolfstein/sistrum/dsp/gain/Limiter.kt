// SPDX-License-Identifier: GPL-3.0-or-later
// WaxFlow b7857aff88820ad37936026421d1641e64611dbe, Cole Springer (MIT).
package com.amwolfstein.sistrum.dsp.gain
import kotlin.math.*

/** Four-times true-peak detector, min-hold lookahead and exact fixed-point boxes.
 * All buffers/deque entries are primitive arrays allocated at construction. */
class Limiter(rate:Int,val channels:Int,ceilingDB:Double=DEFAULT_CEILING_DB) {
 private val validatedRate=rate.also { require(it in 1..(1 shl 20) && channels>0 && ceilingDB<=0 && !ceilingDB.isNaN()) }
 private val ceiling=Gain.fromDB(ceilingDB)
 private val look=max(validatedRate/200,32)
 private val aRel=1-exp(-1/(.050*validatedRate))
 private val interp=Array(3){FloatArray(16)}
 private val buf:Array<FloatArray>
 private val peaks:FloatArray
 private val indices=LongArray(2*(look+2))
 private val values=FloatArray(indices.size)
 private var head=0
 private var size=0
 private var start=0L
 private var base=0L
 private var have=0
 private var pk=0L
 private var pushed=0L
 private var gRel=1.0
 private var primed=false
 private var draining=false
 private val box1=Box(look/2+1)
 private val box2=Box(look/2+1)
 var consumed=0;private set
 var clamped=0;private set
 val horizonNanos=2_000_000_000L
 init {
  val capacity=look+16+4096
  buf=Array(channels){FloatArray(capacity)};peaks=FloatArray(capacity)
  val beta=3.67;val i0=bessel(beta)
  for(p in 1..3) {
   var sum=0.0
   for(t in 0 until 16) {
    val x=p/4.0-(t-8+1)
    val w=bessel(beta*sqrt(1-(x/8)*(x/8)))/i0
    interp[p-1][t]=((if(x==0.0)1.0 else sin(PI*x)/(PI*x))*w).toFloat();sum+=interp[p-1][t]
   }
   for(t in 0 until 16)interp[p-1][t]=(interp[p-1][t].toDouble()/sum).toFloat()
  }
 }
 fun reset() { start=0;base=0;have=0;pk=0;pushed=0;head=0;size=0;gRel=1.0;primed=false;clamped=0;draining=false;consumed=0 }
 fun process(dst:Array<FloatArray>,src:Array<FloatArray>,srcOffset:Int=0,frames:Int=src[0].size-srcOffset,
             dstOffset:Int=0,capacity:Int=dst[0].size-dstOffset):Int {
  check(!draining)
  Gain.checkFrames(src,channels,srcOffset,frames);Gain.checkFrames(dst,channels,dstOffset,capacity)
  consumed=0;var produced=0
  while(true) {
   compact()
   val take=min(buf[0].size-have,frames-consumed)
   for(c in 0 until channels)System.arraycopy(src[c],srcOffset+consumed,buf[c],have,take)
   have+=take;consumed+=take
   detect(false)
   produced+=emit(dst,dstOffset+produced,dstOffset+capacity,false)
   if(produced>=capacity || consumed>=frames)return produced
  }
 }
 fun drain(dst:Array<FloatArray>,dstOffset:Int=0,capacity:Int=dst[0].size-dstOffset):Int {
  Gain.checkFrames(dst,channels,dstOffset,capacity);draining=true;consumed=0;detect(true)
  return emit(dst,dstOffset,dstOffset+capacity,true)
 }
 private fun compact() {
  val drop=(base-start).toInt();if(drop<=0)return
  for(c in 0 until channels)System.arraycopy(buf[c],drop,buf[c],0,have-drop)
  System.arraycopy(peaks,drop,peaks,0,have-drop)
  have-=drop;start=base
 }
 private fun detect(tail:Boolean) {
  val end=if(tail)have else have-8
  var j=(pk-start).toInt()
  while(j<end) {
   var peak=0f;var c=0
   while(c<channels) {
    val input=buf[c];val v=abs32(input[j]);if(v>peak)peak=v
    val lo=j-8+1;var p=0
    while(p<3) {
     var acc=0f;var t=0
     if(lo>=0 && j+8<have) { while(t<16){acc+=interp[p][t]*input[lo+t];t++} }
     else { while(t<16){val at=lo+t;if(at>=0 && at<have)acc+=interp[p][t]*input[at];t++} }
     val a=abs32(acc);if(a>peak)peak=a;p++
    };c++
   }
   peaks[j]=peak;pk++;j++
  }
 }
 private fun emit(dst:Array<FloatArray>,offset:Int,end:Int,tail:Boolean):Int {
  var produced=0;val ceil=ceiling.toFloat()
  while(offset+produced<end) {
   val n=base;val winEnd=n+look
   if(!tail && pk<=winEnd)return produced
   if(tail && n>=pk)return produced
   while(pushed<min(winEnd+1,pk)) {
    val v=peaks[(pushed-start).toInt()]
    while(size>head && values[size-1]<=v)size--
    check(size<indices.size)
    indices[size]=pushed;values[size]=v;size++;pushed++
   }
   while(head<size && indices[head]<n)head++
   if(head>0 && head*2>size) {
    System.arraycopy(indices,head,indices,0,size-head);System.arraycopy(values,head,values,0,size-head)
    size-=head;head=0
   }
   var m=1.0;val env=values[head].toDouble();if(env>ceiling)m=ceiling/env
   val rel=gRel+(1-gRel)*aRel;gRel=if(rel<m)rel else m
   if(!primed){box1.fill(gRel);box2.fill(gRel);primed=true}
   val i=(n-start).toInt();val g=box2.step(box1.step(gRel)).toFloat()
   var c=0;while(c<channels) {
    var v=buf[c][i]*g
    if(v>ceil){v=ceil;clamped++}else if(v< -ceil){v= -ceil;clamped++}
    dst[c][offset+produced]=v;c++
   };produced++;base++
  };return produced
 }
 private class Box(n:Int) {
  val ring=LongArray(n);var sum=0L;var idx=0
  fun fill(v:Double){val q=quantize(v);ring.fill(q);sum=q*ring.size;idx=0}
  fun step(v:Double):Double {val q=quantize(v);sum+=q-ring[idx];ring[idx]=q;idx++;if(idx==ring.size)idx=0;return sum.toDouble()/(ring.size*SCALE).toDouble()}
 }
 companion object {
  const val VERSION="limiter-3"
  const val DEFAULT_CEILING_DB= -1.0
  private const val SCALE=1L shl 40
  private fun quantize(v:Double):Long=when {v<=0 -> 0;v>=1 -> SCALE;else -> (v*SCALE).toLong()}
  private fun abs32(v:Float)=if(v<0)-v else v
  private fun bessel(x:Double):Double {var sum=1.0;var term=1.0;val half=x/2;var k=1;while(true){term*=(half/k)*(half/k);sum+=term;if(term<sum*1e-17)return sum;k++}}
 }
}
