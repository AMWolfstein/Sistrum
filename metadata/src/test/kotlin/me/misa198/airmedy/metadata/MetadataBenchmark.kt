// SPDX-License-Identifier: GPL-3.0-or-later
// Sistrum benchmark for WaxLabel cfbb45d13135a76f5df6781fdeec75f5cfe63bf9.
// Copyright (c) 2026 Cole Springer (MIT); see metadata/THIRD-PARTY-NOTICES.
package me.misa198.airmedy.metadata

import me.misa198.airmedy.codecs.container.FileChannelSource
import java.lang.management.ManagementFactory
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path

object MetadataBenchmark {
    @Volatile private var sink: Document? = null
    @JvmStatic fun main(args: Array<String>) {
        val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        check(bean.isThreadAllocatedMemorySupported)
        bean.isThreadAllocatedMemoryEnabled = true
        val thread = Thread.currentThread().threadId()
        val rows = mutableListOf("# SPDX-License-Identifier: GPL-3.0-or-later; WaxLabel MIT, Cole Springer",
            "# JDK ${System.getProperty("java.version")}; ${System.getProperty("os.arch")}; warm filesystem cache; METADATA_ONLY; 25 warmups, 101 trials per file",
            "format\tfile\tmedian_ns\tmedian_allocated_bytes")
        for(row in fixtures(System.getProperty("waxlabel.step","1").toInt())) {
            val path = row["path"].asString
            FileChannel.open(cache.resolve(path)).use { channel ->
                val source = FileChannelSource(channel)
                val options = ReadOptions(formatHint=path.substringAfterLast('.'))
                repeat(25) { sink = Metadata.read(source,options) }
                val times = mutableListOf<Long>()
                val allocations = mutableListOf<Long>()
                repeat(101) {
                    val beforeAlloc = bean.getThreadAllocatedBytes(thread)
                    val before = System.nanoTime()
                    sink = Metadata.read(source,options)
                    times += System.nanoTime()-before
                    allocations += bean.getThreadAllocatedBytes(thread)-beforeAlloc
                }
                val format = if(row.has("oracle")) row["oracle"].asJsonObject["format"].asString else "error"
                rows += "$format\t$path\t${times.sorted()[50]}\t${allocations.sorted()[50]}"
            }
        }
        val output = Path.of(args.single())
        Files.createDirectories(output.parent)
        Files.write(output,rows)
        println("${rows.size-3} files benchmarked -> $output")
    }
}
