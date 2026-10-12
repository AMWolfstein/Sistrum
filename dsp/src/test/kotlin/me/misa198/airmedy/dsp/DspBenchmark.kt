// SPDX-License-Identifier: GPL-3.0-or-later
package me.misa198.airmedy.dsp
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import me.misa198.airmedy.dsp.resample.*
import me.misa198.airmedy.dsp.silence.Detector
import me.misa198.airmedy.dsp.mix.*

object DspBenchmark {
    @JvmStatic fun main(args:Array<String>) {
        println("kernel\tframes\tns_per_frame\tallocated_bytes")
        for(p in Profile.entries) {
            val r=Resampler(44100,48000,2,p)
            val src=Array(2){FloatArray(1024){((it%127)-63)/128f}};val dst=Array(2){FloatArray(1200)}
            val run={ var at=0;while(at<1024){r.process(dst,src,at,1024-at);at+=r.consumed} }
            measure("resample-${p.id}",1024,run)
        }
        val detector=Detector(48000,2,-55.0,1,1024)
        val silenceInput=Array(2){FloatArray(1024){if(it%16<8)0f else .25f}}
        measure("silence",1024){detector.reset();detector.process(silenceInput);detector.flush()}
        val matrix=Matrix.forLayouts(Speakers.SURROUND_51,Speakers.STEREO)
        val mixInput=Array(6){FloatArray(1024){((it%127)-63)/128f}};val mixOutput=Array(2){FloatArray(1024)}
        measure("mix-5.1-stereo",1024){matrix.apply(mixOutput,mixInput,1024)}
    }
    fun measure(name:String,frames:Int,run:()->Unit):Long {
        val bean=ManagementFactory.getThreadMXBean() as ThreadMXBean
        val id=Thread.currentThread().threadId()
        repeat(20000){run()}
        val before=bean.getThreadAllocatedBytes(id);val start=System.nanoTime()
        repeat(10000){run()}
        val elapsed=System.nanoTime()-start;val allocated=bean.getThreadAllocatedBytes(id)-before
        println("$name\t${frames.toLong()*10000}\t${elapsed.toDouble()/(frames.toLong()*10000)}\t$allocated")
        return allocated
    }
}
