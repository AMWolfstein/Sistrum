// SPDX-License-Identifier: GPL-3.0-only
// Port of aubio 0.4.9 tempo/tempo.c, tempo/beattracking.c, onset/onset.c,
// onset/peakpicker.c, spectral/phasevoc.c, spectral/specdesc.c, cvec.c,
// temporal/filter.c and mathutils.c.
// tempo.c: Copyright (C) 2006-2009 Paul Brossier <piem@aubio.org>.
// beattracking.c: Copyright (C) 2005-2009 Matthew Davies and Paul Brossier.
// onset.c: Copyright (C) 2006-2013 Paul Brossier.
// peakpicker.c, specdesc.c, cvec.c, filter.c: Copyright (C) 2003-2009 Paul Brossier.
// phasevoc.c, mathutils.c: Copyright (C) 2003-2014 Paul Brossier.
// Aggregation follows Airmedy 7d3a08d0eaa2c88d3518f19665eaffc4762dd6a1.
package com.amwolfstein.sistrum.analysis
import com.amwolfstein.sistrum.dsp.resample.AnalysisResampler
import java.io.Serializable
import kotlin.math.*

internal class Rhythm(private val channels: Int, private val rate: Int): Serializable {
    private val enabled = channels != 9 // libswresample cannot downmix an unspecified nine-channel layout.
    private val resampler = if(rate != 44100 && enabled) AnalysisResampler(rate,44100,1,true) else null
    private val matrix = FloatArray(channels) { ch -> when {
        channels == 1 -> 1f
        ch < 2 -> .7071067811865476f
        channels == 3 && ch == 2 || channels >= 6 && ch == 3 -> 0f
        ch == 2 -> 1f
        else -> .5f
    } }
    private val hop = FloatArray(512)
    private var fill = 0
    private val pv = AubioSpectrum()
    private val oldMagnitude = FloatArray(513)
    private val tempoPicker = PeakPicker(.3f)
    private val onsetPicker = PeakPicker(.058f)
    private val beat = BeatTracking()
    private val detectionFrame = FloatArray(512)
    private val beats = FloatArray(128)
    private var blockPosition = 0
    private var bpmSum = 0.0
    private var bpmCount = 0L
    private var totalSamples = 0L
    private var onsetUnsignedSamples = 0L
    private var lastOnset = 0L
    private var lastVariancePosition = -1L
    private var intervalCount = 0L
    private var intervalMean = 0.0
    private var intervalM2 = 0.0
    fun frame(pcm: FloatArray, offset: Int) {
        if(!enabled) return
        var mono = 0f;var ch=0
        while(ch<channels) { if(matrix[ch]!=0f) mono += pcm[offset+ch]*matrix[ch];ch++ }
        val r=resampler
        if(r==null) put(mono) else { r.putMono(mono);while(r.next()) put(r.output[0].toFloat()) }
    }
    private fun put(sample: Float) {
        hop[fill++] = sample
        if(fill != 512) return
        fill=0
        val magnitude=pv.transform(hop)
        var flux=0f;var hfc=0f;var energy=0f;var i=0
        while(i<513) {
            val m=magnitude[i]
            if(m>oldMagnitude[i]) flux += m-oldMagnitude[i]
            oldMagnitude[i]=m
            hfc += (i+1)*ln((m+1f).toDouble()).toFloat()
            i++
        }
        i=0;while(i<512) { energy += hop[i]*hop[i];i++ }
        val level=(10.0*log10((energy/512).toDouble()).toFloat()).toFloat()
        if(blockPosition==127) {
            beat.process(detectionFrame,beats)
            detectionFrame.copyInto(detectionFrame,0,128,512)
            detectionFrame.fill(0f,384,512);blockPosition=-1
        }
        blockPosition++
        tempoPicker.process(flux)
        detectionFrame[384+blockPosition]=tempoPicker.thresholded
        var tactus=0f;i=1
        while(i<beats[0]) {
            if(blockPosition==floor(beats[i]).toInt()) {
                tactus=beats[i]-floor(beats[i])
                if(level < -90f) tactus=0f
            }
            i++
        }
        if(tactus != 0f) {
            val bpm=beat.bpm()
            if(bpm>0) { bpmSum+=bpm;bpmCount++ }
        }
        var onset=onsetPicker.process(hfc)
        if(onset>0f) {
            if(level < -70f) onset=0f else {
                val newOnset=(onsetUnsignedSamples+floor(onset*512+.5f).toLong()) and UINT_MASK
                if(((lastOnset+2205) and UINT_MASK) < newOnset) {
                    if(lastOnset>0 && 2201>newOnset) onset=0f
                    else lastOnset=max(2201L,newOnset)
                } else onset=0f
            }
        } else if(onsetUnsignedSamples<=2201 && !(level < -70f)) {
            if(onsetUnsignedSamples==0L || ((lastOnset+2205) and UINT_MASK)<onsetUnsignedSamples) {
                onset=4f;lastOnset=(onsetUnsignedSamples+2201) and UINT_MASK
            }
        }
        if(onset != 0f && totalSamples>=1024) {
            if(lastVariancePosition>=0) {
                val interval=(totalSamples-lastVariancePosition).toDouble()
                intervalCount++
                val delta=interval-intervalMean
                intervalMean+=delta/intervalCount
                intervalM2+=delta*(interval-intervalMean)
            }
            lastVariancePosition=totalSamples
        }
        totalSamples+=512;onsetUnsignedSamples=(onsetUnsignedSamples+512) and UINT_MASK
    }
    fun tempo() = if(bpmCount>0) bpmSum/bpmCount else 0.0
    fun variance() = if(intervalCount>1) intervalM2/(intervalCount-1) else 0.0
    companion object { private const val UINT_MASK=0xffffffffL }
}

private class AubioSpectrum: Serializable {
    private val window=FloatArray(1024)
    private val hann=FloatArray(1024) {
        val angle=(2*PI*it/1024).toFloat()
        (.5*(1.0-cos(angle.toDouble()).toFloat())).toFloat()
    }
    private val re=FloatArray(1024)
    private val im=FloatArray(1024)
    private val cosine=FloatArray(512) { cos(-2*PI*it/1024).toFloat() }
    private val sine=FloatArray(512) { sin(-2*PI*it/1024).toFloat() }
    private val magnitude=FloatArray(513)
    fun transform(hop: FloatArray): FloatArray {
        window.copyInto(window,0,512,1024);hop.copyInto(window,512)
        var i=0
        while(i<1024) {
            val source=(i+512) and 1023 // fvec_shift, after periodic Hann weighting.
            val dest=Integer.reverse(i) ushr 22
            re[dest]=window[source]*hann[source];im[dest]=0f;i++
        }
        var size=2
        while(size<=1024) {
            val half=size/2;val step=1024/size;var start=0
            while(start<1024) {
                var j=0
                while(j<half) {
                    val a=start+j;val b=a+half;val t=j*step
                    val r=re[b]*cosine[t]-im[b]*sine[t]
                    val m=re[b]*sine[t]+im[b]*cosine[t]
                    re[b]=re[a]-r;im[b]=im[a]-m;re[a]+=r;im[a]+=m;j++
                }
                start+=size
            }
            size*=2
        }
        i=0;while(i<513) { magnitude[i]=sqrt((re[i]*re[i]+im[i]*im[i]).toDouble()).toFloat();i++ }
        return magnitude
    }
}

private class PeakPicker(private val threshold: Float): Serializable {
    private val keep=FloatArray(7)
    private val process=FloatArray(7)
    private val scratch=FloatArray(7)
    private val peek=FloatArray(3)
    var thresholded=0f;private set
    fun process(value: Float): Float {
        keep.copyInto(keep,0,1,7);keep[6]=value;keep.copyInto(process)
        filter(process)
        var i=0;while(i<7) { scratch[6-i]=process[i];i++ }
        filter(scratch)
        i=0;var sum=0f
        while(i<7) { process[i]=scratch[6-i];sum+=process[i];i++ }
        process.copyInto(scratch)
        i=1
        while(i<7) { val v=scratch[i];var j=i-1;while(j>=0 && scratch[j]>v){scratch[j+1]=scratch[j];j--};scratch[j+1]=v;i++ }
        thresholded=process[5]-scratch[3]-(sum/7)*threshold
        peek[0]=peek[1];peek[1]=peek[2];peek[2]=thresholded
        return if(peek[1]>peek[0] && peek[1]>peek[2] && peek[1]>0f) quadratic(peek,1) else 0f
    }
    private fun filter(data: FloatArray) {
        var x1=0.0;var x2=0.0;var y1=0.0;var y2=0.0;var j=0
        while(j<data.size) {
            val input=if(abs(data[j].toDouble())<2e-42) 0.0 else data[j].toDouble()
            var output=.15998789*input
            output+=.31997577*x1;output-=.23484048*y1
            output+=.15998789*x2;output-=0.0*y2
            data[j]=output.toFloat();x2=x1;x1=input;y2=y1;y1=output;j++
        }
    }
}

private fun maximumIndex(data: FloatArray): Int {
    var index=0;var i=0;var value=0f
    while(i<data.size) { if(!(value>data[i])) { index=i;value=data[i] };i++ }
    return index
}
private fun quadratic(data: FloatArray, position: Int): Float {
    if(position==0 || position==data.lastIndex) return position.toFloat()
    val a=data[position-1];val b=data[position];val c=data[position+1]
    return position+.5f*(a-c)/(a-2f*b+c)
}
private fun expf(x: Double)=exp(x.toFloat().toDouble()).toFloat()

private class BeatTracking: Serializable {
    private val ray=43.06640625f
    private val gVar=3.901f
    private val rw=FloatArray(128)
    private val gw=FloatArray(128)
    private val dfw=FloatArray(512)
    private val reversed=FloatArray(512)
    private val acf=FloatArray(512)
    private val acfOut=FloatArray(128)
    private val phaseWeight=FloatArray(256)
    private val phaseOut=FloatArray(512)
    private var timesig=0
    private var lastBeat=0f
    private var counter=0
    private var flagStep=0
    private var gp=0f
    private var bp=0f
    private var rp=1f
    private var rp1=0f
    private var rp2=0f
    init {
        val logarithm=ln(2.0).toFloat()
        val norm=expf(((logarithm/ray)*514).toDouble())
        var i=0
        while(i<512) { dfw[i]=expf(((logarithm/ray)*(i+1)).toDouble())/norm;i++ }
        i=0
        while(i<128) {
            val a=(i+1).toFloat()
            rw[i]=(a/(ray*ray))*expf(-(a*a).toDouble()/(2.0*(ray*ray)));i++
        }
    }
    fun process(frame: FloatArray, output: FloatArray) {
        var i=0
        while(i<512) { reversed[511-i]=frame[i]*dfw[i];i++ }
        i=0
        while(i<512) {
            var sum=0f;var j=i
            while(j<512) { sum+=frame[j-i]*frame[j];j++ }
            acf[i]=sum/(512-i);i++
        }
        acfOut.fill(0f)
        val elements=if(timesig==0) 4 else timesig
        i=1
        while(i<127) {
            var a=1
            while(a<=elements) {
                var b=1
                while(b<2*a) { acfOut[i]=(acfOut[i]+acf[i*a+b-1].toDouble()/(2.0*a-1)).toFloat();b++ }
                a++
            }
            acfOut[i]*=rw[i];i++
        }
        val maximum=maximumIndex(acfOut)
        rp=if(maximum>0 && maximum<127) quadratic(acfOut,maximum) else ray.toInt().toFloat()
        checkState()
        output.fill(0f)
        if(bp==0f) return
        val kmax=floor(512/bp).toInt()
        phaseOut.fill(0f);i=0
        while(i<bp) {
            var k=0
            while(k<kmax) {
                val index=i+floor(bp*k+.5f).toInt()
                // Native aubio rounds the last comb position one sample beyond dfrev.
                // That position is outside the observation window: use zero, never read past an array.
                if(index<reversed.size) phaseOut[i]+=reversed[index]
                k++
            }
            i++
        }
        i=0;while(i<256) { phaseOut[i]*=phaseWeight[i];i++ }
        val phaseIndex=maximumIndex(phaseOut)
        var phase=if(phaseIndex>=511) 128-lastBeat else quadratic(phaseOut,phaseIndex)
        phase+=1f
        var beat=bp-phase
        if((128-lastBeat-phase)<-.40*bp) beat+=bp
        while(beat+bp<0f) beat+=bp
        i=1
        if(beat>=0f) output[i++]=beat
        while(beat+bp<=128) { beat+=bp;output[i++]=beat }
        lastBeat=beat;output[0]=i.toFloat()
    }
    private fun checkState() {
        if(gp!=0f) {
            acfOut.fill(0f)
            var i=1
            while(i<127) {
                var a=1
                while(a<=timesig) { var b=1;while(b<2*a) { acfOut[i]+=acf[i*a+b-1];b++ };a++ }
                acfOut[i]*=gw[i];i++
            }
            gp=quadratic(acfOut,maximumIndex(acfOut))
        } else gp=0f
        if(counter==0) {
            if(abs(gp-rp)>2.0*gVar) { flagStep=1;counter=3 } else flagStep=0
        }
        var constant=false
        if(counter==1 && flagStep==1) {
            if(abs(2f*rp-rp1-rp2)<gVar) { constant=true;counter=0 } else counter=2
        } else if(counter>0) counter--
        rp2=rp1;rp1=rp
        if(constant) {
            gp=rp;timesig=timeSignature(gp.toInt())
            var j=0
            while(j<128) { val d=(j+1.0-gp).toFloat();gw[j]=expf(-.5*(d*d)/(gVar*gVar));j++ }
            bp=gp;phaseWeight.fill(1f)
        } else if(timesig!=0) {
            bp=gp
            if(128>lastBeat) {
                var j=0
                while(j<256) { val d=(1.0+j-128+lastBeat).toFloat();phaseWeight[j]=expf(-.5*(d*d)/(bp/8.0));j++ }
            } else phaseWeight.fill(1f)
        } else { bp=rp;phaseWeight.fill(1f) }
        while(bp>0 && bp<25) bp*=2f // Original octave behavior is deliberately preserved.
    }
    private fun timeSignature(gp: Int): Int {
        if(gp<2) return 4
        var three=0f;var four=0f;var k=-2
        while(k<2) {
            if(512>6*gp+2) { three+=acfSample(3*gp+k);four+=acfSample(4*gp+k) }
            else { three+=acfSample(3*gp+k)+acfSample(6*gp+k);four+=acfSample(4*gp+k)+acfSample(2*gp+k) }
            k++
        }
        return if(three>four) 3 else 4
    }
    // Harmonics outside the finite observation window contribute no energy.
    private fun acfSample(index: Int) = if(index>=0 && index<acf.size) acf[index] else 0f
    fun bpm(): Double = if(bp!=0f) (60.0/((512*bp)/44100f)).toFloat().toDouble() else 0.0
}
