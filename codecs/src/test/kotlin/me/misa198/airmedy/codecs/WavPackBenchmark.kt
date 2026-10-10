package me.misa198.airmedy.codecs

import java.io.File
import java.lang.management.ManagementFactory
import java.util.Locale
import me.misa198.airmedy.codecs.container.wv.Wv
import me.misa198.airmedy.codecs.codec.wavpack.WavPackException

@Volatile private var sink=0L

/** Full JVM decoder/block walk, excluding file loading, open, PCM packing and hashing.
 * Reuse one already-open stream and seek before timing. Five warmups and five timed
 * full decodes per file. RTF = wall decode seconds / audio seconds (lower is faster).
 */
fun main(args: Array<String>) {
    val lib=LibWavPackCorpus.rows.filter { !it.correction && LibWavPackCorpus.active(it) }
    fun fixture(f: LibWavPackFixture)=Fixture(mapOf("file" to (f.name+if (f.correction) " + wvc.wv" else ""),"status" to if (f.success) "ok" else "refused: ${f.fields["error"]}","frames" to f.info[5],"rate" to f.info[0],"channels" to f.info[3],"sample_format" to f.info[2],"bits" to f.info[1]),f.source,f.correctionSource)
    val allRows=OracleCorpus.rows.map { old -> lib.find { it.name==old.name }?.let(::fixture) ?: old }+
        lib.filter { it.name.startsWith("generated/") }.map(::fixture)+
        LibWavPackCorpus.rows.filter { it.correction && LibWavPackCorpus.active(it) }.map(::fixture)
    val feature=System.getProperty("wavpack.benchmarkFeature","all")
    val rows=allRows.filter { when (feature) {
        "float" -> it.fields["sample_format"]=="float"
        "multichannel" -> (it.fields["channels"]?.toIntOrNull() ?: 0)>2
        "dsd" -> it.fields["bits"]=="1"
        else -> true
    } }
    val output=File(args.single()); output.parentFile.mkdirs()
    val alloc=ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
    if (alloc?.isThreadAllocatedMemorySupported==true) alloc.isThreadAllocatedMemoryEnabled=true
    val thread=Thread.currentThread().threadId()
    output.bufferedWriter().use { out ->
        out.appendLine("# ${System.getProperty("java.vm.name")} ${System.getProperty("java.version")}; ${System.getProperty("os.arch")}; ${Runtime.getRuntime().availableProcessors()} CPUs")
        out.appendLine("# Warmups=5; measured decodes=5; median RTF; file loading/open/seek/hash excluded; bounded memory reads included; allocation bytes per full decode")
        out.appendLine("file\tstatus\taudio_seconds\tdecode_seconds\treal_time_factor\tallocated_bytes")
        for (fixture in rows.filter { it.name.endsWith(".wv") }) {
            if (fixture.status!="ok") { out.appendLine("${fixture.name}\t${fixture.status}\t\t\t\t"); continue }
            val stream=try { Wv.open(fixture.byteBuffer(),fixture.correctionSource?.let { me.misa198.airmedy.codecs.container.ByteBufferSource(java.nio.ByteBuffer.wrap(it.readBytes())) }) } catch (e: WavPackException) {
                val line="${fixture.name}\toracle mismatch: ${e.message}\t\t\t\t"
                out.appendLine(line); println(line); continue
            }
            fun decode() {
                var sum=0L
                if (stream.info.isFloat) {
                    while (true) { val b=stream.decodeFloatBlock() ?: break; sum+=b.frames; sum+=b.samples[0].toRawBits() }
                } else {
                    while (true) { val b=stream.decodeBlock() ?: break; sum+=b.frames; sum+=b.samples[0] }
                }
                sink=sum
            }
            repeat(5) { stream.seekSample(0); decode() }
            val nanos=LongArray(5); val allocations=LongArray(5)
            repeat(5) { i ->
                stream.seekSample(0)
                val before=alloc?.getThreadAllocatedBytes(thread) ?: -1
                val start=System.nanoTime(); decode(); nanos[i]=System.nanoTime()-start
                allocations[i]=if (before>=0) alloc!!.getThreadAllocatedBytes(thread)-before else -1
            }
            nanos.sort(); allocations.sort()
            val seconds=nanos[2]/1e9
            val duration=fixture.value("frames").toDouble()/fixture.value("rate").toDouble()
            val line=String.format(Locale.ROOT,"%s\tok\t%.6f\t%.6f\t%.6f\t%d",fixture.name,duration,seconds,seconds/duration,allocations[2])
            out.appendLine(line); println(line)
        }
    }
    println("Benchmark: ${output.absolutePath}")
}
