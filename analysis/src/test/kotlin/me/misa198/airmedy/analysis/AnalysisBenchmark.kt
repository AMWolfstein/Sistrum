// SPDX-License-Identifier: GPL-3.0-or-later
package me.misa198.airmedy.analysis
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import java.io.File
import kotlin.math.*
object AnalysisBenchmark {
    @JvmStatic fun main(args: Array<String>) {
        val bean=ManagementFactory.getThreadMXBean() as ThreadMXBean
        bean.isThreadAllocatedMemoryEnabled=true
        val id=Thread.currentThread().threadId()
        repeat(32) { bean.getThreadAllocatedBytes(id);System.nanoTime() }
        val rate=44100;val channels=2
        val block=FloatArray(8192*channels) { .6f*sin(2*PI*997*(it/channels)/rate).toFloat() }
        val rows=mutableListOf("group\tframes\tRTF\tfeed_allocated_bytes\tbytes_per_frame")
        for((name,group) in listOf("dynamics" to PcmAnalyzer.DYNAMICS,"loudness_true_peak" to PcmAnalyzer.LOUDNESS,"spectral" to PcmAnalyzer.SPECTRAL,"tempo_onset" to PcmAnalyzer.RHYTHM)) {
            repeat(3) { val warm=PcmAnalyzer(channels,rate,group); repeat(64){warm.feed(block,channels,rate)} }
            val analyzer=PcmAnalyzer(channels,rate,group)
            val before=bean.getThreadAllocatedBytes(id);val start=System.nanoTime()
            repeat(128) { analyzer.feed(block,channels,rate) }
            val elapsed=System.nanoTime()-start;val allocated=bean.getThreadAllocatedBytes(id)-before
            val frames=128L*8192;val rtf=elapsed/1e9/(frames.toDouble()/rate)
            rows+="$name\t$frames\t$rtf\t$allocated\t${allocated.toDouble()/frames}"
            analyzer.finish()
        }
        val output=File(args.single());output.parentFile.mkdirs();output.writeText(rows.joinToString("\n",postfix="\n"))
        println(rows.joinToString("\n"))
        println("JVM: ${System.getProperty("java.vm.name")} ${System.getProperty("java.version")}")
        println("OS: ${System.getProperty("os.name")} ${System.getProperty("os.arch")}; CPUs: ${Runtime.getRuntime().availableProcessors()}")
    }
}
