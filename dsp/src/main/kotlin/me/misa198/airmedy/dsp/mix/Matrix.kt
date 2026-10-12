// SPDX-License-Identifier: GPL-3.0-or-later
// WaxFlow b7857aff88820ad37936026421d1641e64611dbe, Cole Springer (MIT).
package me.misa198.airmedy.dsp.mix
import kotlin.math.*

/** WAVE_FORMAT_EXTENSIBLE bit order, independent of Android's mask numbering. */
object Speakers {
 const val FL=1; const val FR=2; const val FC=4; const val LFE=8
 const val BL=16; const val BR=32; const val FLC=64; const val FRC=128
 const val BC=256; const val SL=512; const val SR=1024
 const val TC=2048; const val TFL=4096; const val TFC=8192; const val TFR=16384
 const val TBL=32768; const val TBC=65536; const val TBR=131072
 const val MONO=FC; const val STEREO=FL or FR
 const val SURROUND_51=FL or FR or FC or LFE or BL or BR
 const val SURROUND_51_SIDE=FL or FR or FC or LFE or SL or SR
 const val SURROUND_71=SURROUND_51 or SL or SR
 internal fun positions(mask: Int): IntArray = IntArray(Integer.bitCount(mask)).also { out ->
  var at=0;for(bit in 0..31) if(mask and (1 shl bit)!=0) out[at++]=1 shl bit
 }
}

/** Immutable gain matrix; apply requires separate caller-owned planar input/output. */
class Matrix private constructor(val sourceMask:Int,val targetMask:Int,private val coef:Array<FloatArray>) {
 val inputs get()=coef[0].size
 val outputs get()=coef.size
 fun coefficient(out:Int,input:Int):Float=coef[out][input]
 val maxGain:Double get() { var worst=0.0;for(row in coef){var sum=0.0;for(g in row)sum+=abs(g.toDouble());worst=max(worst,sum)};return worst }
 fun apply(dst:Array<FloatArray>,src:Array<FloatArray>,frames:Int,srcOffset:Int=0,dstOffset:Int=0) {
  require(src.size==inputs && dst.size==outputs && frames>=0 && srcOffset>=0 && dstOffset>=0)
  for(i in src.indices)require(srcOffset<=src[i].size-frames)
  for(o in dst.indices) {
   require(dstOffset<=dst[o].size-frames)
   for(i in src.indices)require(dst[o]!==src[i]) { "Matrix input/output must not alias" }
   for(p in 0 until o)require(dst[o]!==dst[p])
  }
  var o=0
  while(o<outputs) {
   val out=dst[o];out.fill(0f,dstOffset,dstOffset+frames)
   var i=0
   while(i<inputs) {
    val g=coef[o][i]
    if(g!=0f) { val input=src[i];var j=0;while(j<frames){out[dstOffset+j]+=g*input[srcOffset+j];j++} }
    i++
   };o++
  }
 }
 companion object {
  const val VERSION="mix-1"
  private const val DB3=.7071067811865476
  private const val DB6=.5
  private const val DB9=.3535533905932738
  private val gains=arrayOf(doubleArrayOf(1.0,0.0),doubleArrayOf(0.0,1.0),doubleArrayOf(DB3,DB3),doubleArrayOf(0.0,0.0),
   doubleArrayOf(DB3,0.0),doubleArrayOf(0.0,DB3),doubleArrayOf(DB3,0.0),doubleArrayOf(0.0,DB3),doubleArrayOf(DB6,DB6),
   doubleArrayOf(DB3,0.0),doubleArrayOf(0.0,DB3),doubleArrayOf(DB6,DB6),doubleArrayOf(DB3,0.0),doubleArrayOf(DB6,DB6),
   doubleArrayOf(0.0,DB3),doubleArrayOf(DB6,0.0),doubleArrayOf(DB9,DB9),doubleArrayOf(0.0,DB6))
  fun forLayouts(src:Int,dst:Int):Matrix {
   require(src!=0 && dst!=0 && src!=dst)
   val positions=Speakers.positions(src);val targets=Speakers.positions(dst)
   val rows=Array(targets.size){DoubleArray(positions.size)}
   when {
    positions.size==1 && dst==Speakers.STEREO -> for(row in rows)row[0]=1.0
    dst==Speakers.STEREO || dst==Speakers.MONO -> {
     for(i in positions.indices) {
      val bit=Integer.numberOfTrailingZeros(positions[i]);require(bit<gains.size) { "Unsupported speaker" }
      val g=gains[bit]
      if(dst==Speakers.STEREO) { rows[0][i]=g[0];rows[1][i]=g[1] }
      else rows[0][i]=DB3*(g[0]+g[1])
     }
    }
    positions.size==1 && dst and Speakers.FC!=0 -> for(o in targets.indices)if(targets[o]==Speakers.FC)rows[o][0]=1.0
    positions.size==1 && dst and Speakers.STEREO==Speakers.STEREO -> for(o in targets.indices)if(targets[o] and Speakers.STEREO!=0)rows[o][0]=1.0
    src and dst.inv()==0 -> for(o in targets.indices)for(i in positions.indices)if(targets[o]==positions[i])rows[o][i]=1.0
    else -> error("Target has no place for source speakers")
   }
   val coef=Array(rows.size){o ->
    var energy=0.0;for(g in rows[o])energy+=g*g
    val scale=if(energy>1)1/sqrt(energy) else 1.0
    FloatArray(positions.size){i -> (rows[o][i]*scale).toFloat()}
   }
   return Matrix(src,dst,coef)
  }
  /** Literal named-speaker placement. Unlike WaxFlow's mono convention this never
   * moves a lone LFE (or another named speaker) onto centre. Also permits identity. */
  fun placement(src:Int,dst:Int):Matrix {
   require(src!=0 && dst!=0 && src and dst.inv()==0)
   val positions=Speakers.positions(src);val targets=Speakers.positions(dst)
   return Matrix(src,dst,Array(targets.size){o -> FloatArray(positions.size){i -> if(targets[o]==positions[i])1f else 0f}})
  }
 }
}

/** Media3 count-only PCM layouts (1..8), named in WAVE bit order.
 * Choose the smallest compatible envelope; zero-fill every missing named speaker.
 * Layouts outside these envelopes use the WaxFlow stereo downmix; unknown bits refuse.
 * This API returns reordered PCM's count, not the source count, for Media3 Format. */
object Media3Layout {
 private val masks=intArrayOf(0,Speakers.MONO,Speakers.STEREO,Speakers.STEREO or Speakers.FC,
  Speakers.STEREO or Speakers.BL or Speakers.BR,Speakers.STEREO or Speakers.FC or Speakers.BL or Speakers.BR,
  Speakers.SURROUND_51,Speakers.SURROUND_51 or Speakers.BC,Speakers.SURROUND_71)
 fun maskForCount(count:Int):Int { require(count in 1..8);return masks[count] }
 fun forMask(sourceMask:Int):Matrix {
  require(sourceMask!=0 && sourceMask and ((1 shl 18)-1).inv()==0) { "Unknown speaker mask" }
  for(count in 1..8) if(sourceMask and masks[count].inv()==0) return Matrix.placement(sourceMask,masks[count])
  return Matrix.forLayouts(sourceMask,Speakers.STEREO)
 }
}
