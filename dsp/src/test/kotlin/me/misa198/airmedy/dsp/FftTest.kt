package me.misa198.airmedy.dsp
import com.google.gson.Gson
import me.misa198.airmedy.dsp.fft.Plan
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
class FftTest {
 class Vector {var N=0;lateinit var Real:FloatArray;lateinit var Imag:FloatArray;lateinit var OutReal:FloatArray;lateinit var OutImag:FloatArray}
 @Test fun goOracle() {
  val vectors=Gson().fromJson(File(System.getProperty("dsp.repo"),"docs/dsp/fft-oracle.json").readText(),Array<Vector>::class.java)
  for(v in vectors){val re=FloatArray(v.N);val im=FloatArray(v.N);Plan(v.N).transform(re,im,v.Real,v.Imag)
   for(i in re.indices){assertEquals(v.OutReal[i].toDouble(),re[i].toDouble(),2e-5);assertEquals(v.OutImag[i].toDouble(),im[i].toDouble(),2e-5)}
  }
 }
 @Test fun unchangedJvmBits() {
  val rows=File(System.getProperty("dsp.repo"),"docs/dsp/fft-jvm-before.tsv").readLines().filter{!it.startsWith("#")&&it.isNotBlank()}
  for(row in rows) {
   val fields=row.split('\t');val n=fields[0].toInt();val signal=fields[1].toInt()
   val re=FloatArray(n);val im=FloatArray(n);val outRe=FloatArray(n);val outIm=FloatArray(n)
   for(i in 0 until n){if(signal==0)re[i]=if(i==0)1f else 0f else {re[i]=((i*31%257)-128)/256f;if(signal==2)im[i]=((i*17%127)-63)/128f}}
   Plan(n).transform(outRe,outIm,re,im)
   val digest=MessageDigest.getInstance("SHA-256")
   for(i in 0 until n)for(v in floatArrayOf(outRe[i],outIm[i])){val bits=v.toRawBits();for(b in 0..3)digest.update((bits ushr (b*8)).toByte())}
   assertEquals("n=$n signal=$signal",fields[2],java.util.HexFormat.of().formatHex(digest.digest()))
  }
 }
 @Test fun allocationsAndContracts() {
  val p=Plan(1024);val src=FloatArray(1024);val out=FloatArray(1024);val imaginary=FloatArray(1024);val imOut=FloatArray(1024)
  assertEquals(0L,DspBenchmark.measure("fft-1024",1024){p.transform(out,imOut,src,imaginary)})
  assertThrows(IllegalArgumentException::class.java){p.transform(src,imOut,src,imaginary)}
  assertThrows(IllegalArgumentException::class.java){Plan(7)}
 }
}
