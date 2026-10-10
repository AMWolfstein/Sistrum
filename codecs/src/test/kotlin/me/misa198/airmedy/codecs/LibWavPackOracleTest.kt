package me.misa198.airmedy.codecs

import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import me.misa198.airmedy.codecs.codec.wavpack.*
import me.misa198.airmedy.codecs.container.wv.Wv

internal data class LibWavPackFixture(val fields: Map<String,String>,val source: File) {
    val name get()=fields.getValue("file")
    val correction get()=fields.getValue("correction")=="true"
    val success get()=fields.getValue("exit")=="0"
    override fun toString()=name+if (correction) " + wvc" else " lossy"
    val info get()=fields.getValue("info").split(';')
    val flags get()=if (source.readBytes().take(4)==listOf(119.toByte(),118.toByte(),112.toByte(),107.toByte())) ByteBuffer.wrap(source.readBytes()).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt(24) else 0
    val correctionSource: File? get()=if (!correction) null else if (name.startsWith("generated/")) File("src/test/resources/wavpack/generated",fields.getValue("correction_file")) else File(System.getProperty("waxflow.corpus"),fields.getValue("correction_file"))
    fun open(): Wv {
        val companion=correctionSource
        if (companion!=null) check(MessageDigest.getInstance("SHA-256").digest(companion.readBytes()).hex()==fields.getValue("correction_sha256"))
        return Wv.open(ByteBuffer.wrap(source.readBytes()),companion?.let { me.misa198.airmedy.codecs.container.ByteBufferSource(ByteBuffer.wrap(it.readBytes())) })
    }
}
internal object LibWavPackCorpus {
    val rows: List<LibWavPackFixture> by lazy {
        val file=File("src/test/resources/wavpack/libwavpack.tsv")
        val lines=file.readLines()
        check(lines.contains("# libwavpack_commit\t4827b9889665b937b6ed71b9c6c0123152cd7a02"))
        val data=lines.filterNot { it.startsWith('#') };val columns=data.first().split('\t')
        data.drop(1).filter { it.isNotEmpty() && (System.getProperty("wavpack.ownedOnly")!="true" || it.startsWith("generated/")) }.map {
            val values=it.split('\t');check(values.size==columns.size)
            val fields=columns.zip(values).toMap(); val name=fields.getValue("file")
            val path=if (name.startsWith("generated/")) File("src/test/resources/wavpack",name) else File(System.getProperty("waxflow.corpus"),name)
            check(path.isFile) { "Missing libwavpack vector $path" }
            check(MessageDigest.getInstance("SHA-256").digest(path.readBytes()).hex()==fields.getValue("sha256"))
            LibWavPackFixture(fields,path)
        }
    }
    // Feature gate grows with each separately validated port commit.
    fun active(f: LibWavPackFixture)=f.info.size>3 && f.flags and DSD==0
}
@RunWith(Parameterized::class)
internal class LibWavPackOracleTest(private val fixture: LibWavPackFixture) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name="{0}")
        fun corpus(): Collection<Array<Any>> = LibWavPackCorpus.rows.filter { LibWavPackCorpus.active(it) }.map { arrayOf<Any>(it) }
    }
    @Test fun matchesRawOracle() {
        val stream=fixture.open()
        assertEquals(fixture.correction && fixture.flags and HYBRID!=0,stream.usesCorrection)
        assertEquals(!fixture.correction && fixture.flags and HYBRID!=0,stream.lossyFallback)
        if (!fixture.success) {
            try { while (stream.decodeBlock()!=null) { }; fail("Accepted file with wvunpack error: ${fixture.fields["error"]}") }
            catch (e: WavPackException) { assertEquals(ErrorCode.MALFORMED,e.code) }
            return
        }
        assertEquals(fixture.info[3].toInt(),stream.info.channels)
        assertEquals(java.lang.Long.decode(fixture.info[4]).toLong(),stream.info.channelMask)
        val digest=MessageDigest.getInstance("SHA-256"); var bytes=0L
        while (true) { val b=stream.decodeBlock()?:break;val raw=pcmBytes(b);digest.update(raw);bytes+=raw.size }
        assertEquals(fixture.name,fixture.fields.getValue("raw_bytes").toLong(),bytes)
        assertEquals(fixture.name,fixture.fields.getValue("raw_sha256"),digest.digest().hex())
    }
    @Test fun floatApiPreservesIeeeBits() {
        if (!fixture.success || fixture.flags and FLOAT_DATA==0) return
        val stream=fixture.open();assertTrue(stream.info.isFloat)
        val digest=MessageDigest.getInstance("SHA-256");var borrowed:Any?=null;var storage:Any?=null
        while (true) {
            val b=stream.decodeFloatBlock()?:break
            if (borrowed!=null) { assertSame(borrowed,b);assertSame(storage,b.samples) }
            borrowed=b;storage=b.samples;digest.update(pcmBytes(b))
        }
        assertEquals(fixture.fields.getValue("raw_sha256"),digest.digest().hex())
        stream.seekSample(513);val b=stream.decodeFloatBlock()!!;assertEquals(513L,b.position);assertTrue(b.discontinuity)
    }
    @Test fun exactSeeking() {
        if (!fixture.success) return
        val stream=fixture.open();val total=stream.info.totalSamples
        val targets=longArrayOf(total-1,total/2,1,total/3,0,total)
        val expected=HashMap<Long,IntArray>()
        while (true) {
            val b=stream.decodeBlock()?:break
            for (target in targets) {
                if (target>=b.position && target<b.position+b.frames) {
                    val at=(target-b.position).toInt()*b.channels
                    expected[target]=b.samples.copyOfRange(at,minOf(at+64*b.channels,b.frames*b.channels))
                }
            }
        }
        for (target in targets) {
            stream.seekSample(target);val b=stream.decodeBlock()
            if (target==total) { assertNull(b);continue }
            b!!;assertEquals(target,b.position);assertTrue(b.discontinuity)
            val wanted=expected.getValue(target)
            assertArrayEquals(wanted,b.samples.copyOfRange(0,wanted.size))
        }
    }
}

class WavPackHybridContractsTest {
    private fun vector()=LibWavPackCorpus.rows.single { it.name=="generated/noise-b2-c.wv" && it.correction }
    @Test fun twoCallerSourcesSupportShortReadsAndBufferRegions() {
        val names=setOf("generated/noise-b2-c.wv","generated/3ch-mask7-b3-c.wv","generated/6ch-mask3f-b3-c.wv","generated/8ch-mask63f-b3-c.wv")
        for (f in LibWavPackCorpus.rows.filter { it.correction && it.name in names }) {
        fun region(bytes:ByteArray):ByteBuffer=ByteBuffer.allocateDirect(bytes.size+13).also { it.position(7);it.put(bytes);it.limit(7+bytes.size);it.position(7) }
        val main=region(f.source.readBytes());val companion=region(f.correctionSource!!.readBytes())
        fun shortReads(b:ByteBuffer)=object:me.misa198.airmedy.codecs.container.RandomAccessSource {
            private val source=me.misa198.airmedy.codecs.container.ByteBufferSource(b)
            override val length=source.length
            override fun read(position:Long,buffer:ByteBuffer):Int {
                val limit=buffer.limit();buffer.limit(minOf(limit,buffer.position()+17))
                return try { source.read(position,buffer) } finally { buffer.limit(limit) }
            }
        }
        val stream=Wv.open(shortReads(main),shortReads(companion))
        assertTrue(stream.usesCorrection);assertFalse(stream.lossyFallback)
        val digest=MessageDigest.getInstance("SHA-256")
        var output:Any?=null;var samples:Any?=null
        while (true) {
            val b=stream.decodeBlock()?:break
            if (output!=null) { assertSame(output,b);assertSame(samples,b.samples) }
            output=b;samples=b.samples;digest.update(pcmBytes(b))
        }
        assertEquals(f.fields.getValue("raw_sha256"),digest.digest().hex())
        assertEquals(7,main.position());assertEquals(7,companion.position())
        stream.seekSample(513);assertEquals(513L,stream.decodeBlock()!!.position)
        val fallback=Wv.open(me.misa198.airmedy.codecs.container.ByteBufferSource(main),null)
        assertTrue(fallback.lossyFallback);assertFalse(fallback.usesCorrection)
        }
    }
    @Test fun suppliedCorrectionMustMatchSampleIndices() {
        val f=vector();val companion=ByteBuffer.wrap(f.correctionSource!!.readBytes()).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        companion.putInt(16,1)
        val stream=Wv.open(ByteBuffer.wrap(f.source.readBytes()),me.misa198.airmedy.codecs.container.ByteBufferSource(companion))
        try { stream.decodeBlock();fail("Mismatched correction accepted") }
        catch (e:WavPackException) { assertEquals(ErrorCode.MALFORMED,e.code);assertTrue(e.message!!.contains("correction")) }
    }
}
