// SPDX-License-Identifier: GPL-3.0-or-later
// Benchmark of pinned Flick 79da4ed76557c8ddf534e898480dde66bcc90334 (MIT),
// dsf-meta 0.3.0 and dff-meta 0.2.0 (MIT OR Apache-2.0); see docs/dsd/ORACLE.md.
package me.misa198.airmedy.codecs

import java.io.File
import java.lang.management.ManagementFactory
import java.util.Locale

@Volatile private var dsdSink=0L
object DsdBenchmark {
 @JvmStatic fun main(args:Array<String>){
  val allocation=ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean;allocation.isThreadAllocatedMemoryEnabled=true;val thread=Thread.currentThread().threadId()
  val output=File(args.single());output.parentFile.mkdirs();output.bufferedWriter().use{out->
   out.appendLine("# ${System.getProperty("java.vm.name")} ${System.getProperty("java.version")}; ${System.getProperty("os.arch")}; ${Runtime.getRuntime().availableProcessors()} CPUs")
   out.appendLine("# 20 warmups, five measured full decodes; median RTF and allocation bytes; open/seek/hash excluded; bounded input reads included")
   out.appendLine("file\tmode\tstatus\taudio_seconds\tdecode_seconds\treal_time_factor\tallocated_bytes")
   for(f in DsdCorpus.rows){if(f.status!="ok"){out.appendLine("${f.name}\t\t${f.status}\t\t\t\t");continue}
    for(dop in listOf(false,true)){val stream=f.open()
     fun seek(){if(dop)stream.seekDopSample(0)else stream.seekSample(0)}
     fun decode(){var sum=0L;if(dop){while(true){val b=stream.decodeDopBlock()?:break;sum+=b.frames;sum+=b.samples[0]}}else{while(true){val b=stream.decodeBlock()?:break;sum+=b.frames;sum+=b.samples[0].toRawBits()}};dsdSink=sum}
     repeat(20){seek();decode()};val ns=LongArray(5);val bytes=LongArray(5)
     repeat(5){i->seek();val before=allocation.getThreadAllocatedBytes(thread);val start=System.nanoTime();decode();ns[i]=System.nanoTime()-start;bytes[i]=allocation.getThreadAllocatedBytes(thread)-before};ns.sort();bytes.sort()
     val seconds=ns[2]/1e9;val duration=f.value("frames").toDouble()/f.value("pcm_rate").toInt();val line=String.format(Locale.ROOT,"%s\t%s\tok\t%.6f\t%.6f\t%.6f\t%d",f.name,if(dop)"dop"else "pcm",duration,seconds,seconds/duration,bytes[2]);out.appendLine(line);println(line)
    }
   }
  };println("Benchmark: ${output.absolutePath}")
 }
}
