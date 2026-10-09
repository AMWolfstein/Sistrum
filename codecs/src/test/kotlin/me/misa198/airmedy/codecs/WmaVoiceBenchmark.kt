// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/wmavoice/bench_test.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs

import java.io.File
import java.lang.management.ManagementFactory
import java.util.Locale
import me.misa198.airmedy.codecs.container.asf.WmaVoice
import java.io.IOException

@Volatile private var wmaVoiceSink=0L

/** Full JVM decoder/block walk, excluding file loading, open, PCM packing and hashing.
 * Reuse one already-open stream and seek before timing. Twenty warmups and five timed
 * full decodes per file. RTF = wall decode seconds / audio seconds (lower is faster).
 */
fun wmaVoiceBenchmark(args: Array<String>) {
    val rows=OracleCorpus.allRows
    val output=File(args.single()); output.parentFile.mkdirs()
    val alloc=ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
    if (alloc?.isThreadAllocatedMemorySupported==true) alloc.isThreadAllocatedMemoryEnabled=true
    val thread=Thread.currentThread().threadId()
    output.bufferedWriter().use { out ->
        out.appendLine("# ${System.getProperty("java.vm.name")} ${System.getProperty("java.version")}; ${System.getProperty("os.arch")}; ${Runtime.getRuntime().availableProcessors()} CPUs")
        out.appendLine("# Warmups=20; measured decodes=5; median RTF; file loading/open/seek/hash excluded; bounded memory reads included; allocation bytes per full decode")
        out.appendLine("file\tstatus\taudio_seconds\tdecode_seconds\treal_time_factor\tallocated_bytes")
        for (fixture in rows.filter { it.name.startsWith("waxflow-wmavoice-tests/") }) {
            if (fixture.status!="ok") { out.appendLine("${fixture.name}\t${fixture.status}\t\t\t\t"); continue }
            val stream=try { WmaVoice.open(fixture.byteBuffer()) } catch (e: IOException) {
                val line="${fixture.name}\toracle mismatch: ${e.message}\t\t\t\t"
                out.appendLine(line); println(line); continue
            }
            fun decode() {
                var sum=0L
                while (true) { val b=stream.decodeBlock() ?: break; sum+=b.frames; sum+=b.samples[0].toRawBits() }
                wmaVoiceSink=sum
            }
            // Voice has many small synthesis/postfilter methods: allow tiered JIT to settle.
            repeat(20) { stream.seekSample(0); decode() }
            val nanos=LongArray(5); val allocations=LongArray(5)
            repeat(5) { i ->
                stream.seekSample(0)
                val before=alloc?.getThreadAllocatedBytes(thread) ?: -1
                val start=System.nanoTime(); decode(); nanos[i]=System.nanoTime()-start
                allocations[i]=if (before>=0) alloc!!.getThreadAllocatedBytes(thread)-before else -1
            }
            println("Allocation samples ${fixture.name}: ${allocations.joinToString()}")
            nanos.sort(); allocations.sort()
            val seconds=nanos[2]/1e9
            val duration=fixture.value("frames").toDouble()/fixture.value("rate").toDouble()
            val line=String.format(Locale.ROOT,"%s\tok\t%.6f\t%.6f\t%.6f\t%d",fixture.name,duration,seconds,seconds/duration,allocations[2])
            out.appendLine(line); println(line)
        }
    }
    println("Benchmark: ${output.absolutePath}")
}

object WmaVoiceBenchmark { @JvmStatic fun main(args: Array<String>) = wmaVoiceBenchmark(args) }
