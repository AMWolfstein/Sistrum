// SPDX-License-Identifier: GPL-3.0-or-later
// WaxFlow b7857aff88820ad37936026421d1641e64611dbe, Cole Springer (MIT).
package me.misa198.airmedy.dsp.gain
import kotlin.math.*

object Gain {
 const val VERSION="gain-1"
 fun fromDB(db:Double):Double=10.0.pow(db/20)
 fun apply(samples:FloatArray,factor:Float,offset:Int=0,frames:Int=samples.size-offset) {
  require(offset>=0 && frames>=0 && offset<=samples.size-frames)
  if(factor==1f)return
  var i=offset;while(i<offset+frames){samples[i]*=factor;i++}
 }
 internal fun checkFrames(slices:Array<FloatArray>,channels:Int,offset:Int,frames:Int) {
  require(slices.size==channels && offset>=0 && frames>=0)
  for(c in slices.indices)require(slices[c].size==slices[0].size && offset<=slices[c].size-frames)
 }
}

enum class Preset(val id:String) {
 OFF(""),VOICE("voice");
 companion object {
  fun presets():List<Preset> = listOf(VOICE)
  fun parse(id:String):Preset = entries.firstOrNull{it.id==id} ?: error("Unknown dynamics preset: $id")
 }
}

/** Linked peak detector, soft knee, float64 gain envelope and float32 application.
 * OFF is the absence of a node; no compressor is constructed for it. No lookahead.
 * Makeup can exceed full scale; this port does not wire dynamics into playback. */
class Compressor(rate:Int,val channels:Int,preset:Preset=Preset.VOICE) {
 private val kneeStart=Gain.fromDB(-23.0)
 private val makeup=Gain.fromDB(6.0)
 private val aAtk:Double
 private val aRel:Double
 private var reduction=0.0
 val horizonNanos=10_000_000_000L
 init { require(rate>0 && channels>0 && preset==Preset.VOICE);aAtk=1-exp(-1/(.010*rate));aRel=1-exp(-1/(.250*rate)) }
 fun reset() { reduction=0.0 }
 /** Pure static detector curve, at most zero dB; exposed for oracle/boundary checks. */
 fun targetDB(peak:Double):Double {
  if(peak<=kneeStart)return 0.0
  val capped=if(peak>2)2.0 else peak
  val over=20*log10(capped)-(-20.0)
  if(over>=3)return over*(1/2.5-1)
  val x=over+3
  return (1/2.5-1)*x*x/(2*6.0)
 }
 fun process(dst:Array<FloatArray>,src:Array<FloatArray>,srcOffset:Int=0,dstOffset:Int=0,
             frames:Int=min(src[0].size-srcOffset,dst[0].size-dstOffset)):Int {
  Gain.checkFrames(src,channels,srcOffset,frames);Gain.checkFrames(dst,channels,dstOffset,frames)
  for(o in dst.indices)for(i in src.indices)require(dst[o]!==src[i] || (o==i && srcOffset==dstOffset)) { "Cross-channel or shifted alias" }
  var i=0
  while(i<frames) {
   var peak=0f;var ch=0
   while(ch<channels){var v=src[ch][srcOffset+i];if(v<0)v=-v;if(v>peak)peak=v;ch++}
   val target=targetDB(peak.toDouble())
   if(target<reduction)reduction+=(target-reduction)*aAtk else reduction+=(target-reduction)*aRel
   val g=(exp(reduction*(ln(10.0)/20))*makeup).toFloat()
   ch=0;while(ch<channels){dst[ch][dstOffset+i]=src[ch][srcOffset+i]*g;ch++};i++
  };return frames
 }
 fun drain(dst:Array<FloatArray>):Int { require(dst.size==channels);return 0 }
 companion object { const val VERSION="compressor-1" }
}
