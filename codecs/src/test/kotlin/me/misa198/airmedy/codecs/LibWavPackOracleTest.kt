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
    fun open()=Wv.open(ByteBuffer.wrap(source.readBytes()))
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
    fun active(f: LibWavPackFixture)=!f.correction && f.flags and HYBRID!=0 && f.flags and (DSD or FLOAT_DATA)==0 && f.flags and (INITIAL_BLOCK or FINAL_BLOCK)==INITIAL_BLOCK or FINAL_BLOCK
}
@RunWith(Parameterized::class)
internal class LibWavPackOracleTest(private val fixture: LibWavPackFixture) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name="{0}")
        fun corpus(): Collection<Array<Any>> = LibWavPackCorpus.rows.filter { LibWavPackCorpus.active(it) }.map { arrayOf<Any>(it) }
    }
    @Test fun matchesRawOracle() {
        val stream=fixture.open()
        if (!fixture.success) {
            try { while (stream.decodeBlock()!=null) { }; fail("Accepted file with wvunpack error: ${fixture.fields["error"]}") }
            catch (e: WavPackException) { assertEquals(ErrorCode.MALFORMED,e.code) }
            return
        }
        val digest=MessageDigest.getInstance("SHA-256"); var bytes=0L
        while (true) { val b=stream.decodeBlock()?:break;val raw=pcmBytes(b);digest.update(raw);bytes+=raw.size }
        assertEquals(fixture.name,fixture.fields.getValue("raw_bytes").toLong(),bytes)
        assertEquals(fixture.name,fixture.fields.getValue("raw_sha256"),digest.digest().hex())
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
