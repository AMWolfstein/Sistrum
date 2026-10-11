package com.amwolfstein.sistrum.codecs

import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import com.amwolfstein.sistrum.codecs.codec.wavpack.DSD
import com.amwolfstein.sistrum.codecs.container.dsd.Dsd
import com.amwolfstein.sistrum.codecs.container.wv.WavPackDsd

/** Raw bits have independent wvunpack hashes; these checks test the existing DSD adapters. */
@RunWith(Parameterized::class)
internal class WavPackDsdIntegrationTest(private val fixture:LibWavPackFixture,private val input:File) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name="{0}")
        fun corpus():Collection<Array<Any>> {
            val lines=File("../docs/wavpack/corpus-manifest.tsv").readLines();val columns=lines.first().split('\t')
            val inputs=lines.drop(1).filter { it.isNotEmpty() }.associate { val r=columns.zip(it.split('\t')).toMap();r.getValue("path") to r.getValue("input_path") }
            return LibWavPackCorpus.rows.filter { it.name.startsWith("generated/") && it.flags and DSD!=0 && it.success }.map {
                arrayOf<Any>(it,File(System.getProperty("oracle.owned")+"/wavpack/generated",inputs.getValue(it.name.removePrefix("generated/"))))
            }
        }
    }
    private fun packed()=WavPackDsd.open(ByteBuffer.wrap(fixture.source.readBytes()))
    private fun original()=Dsd.open(ByteBuffer.wrap(input.readBytes()))
    @Test fun pcmMatchesExistingDsdDecimator() {
        val actual=packed();val reference=original()
        assertEquals(reference.info.sampleRate,actual.info.sampleRate)
        assertEquals(reference.info.totalSamples,actual.info.totalSamples)
        assertEquals(reference.info.dsdSampleRate,actual.info.dsdSampleRate)
        val wanted=MessageDigest.getInstance("SHA-256");val got=MessageDigest.getInstance("SHA-256")
        while (true) { val b=reference.decodeBlock()?:break;wanted.update(pcmBytes(b)) }
        var borrowed:Any?=null;var storage:Any?=null
        while (true) {
            val b=actual.decodeBlock()?:break
            if (borrowed!=null) { assertSame(borrowed,b);assertSame(storage,b.samples) }
            borrowed=b;storage=b.samples;got.update(pcmBytes(b))
        }
        assertArrayEquals(wanted.digest(),got.digest())
    }
    @Test fun randomPcmSeeksMatchUninterruptedBits() {
        val rate=packed().info.dsdSampleRate
        val targets=when (rate) {
            2822400 -> intArrayOf(44100,88200,176400)
            5644800 -> intArrayOf(44100,88200,176400,352800)
            else -> intArrayOf(88200,176400,352800,705600)
        }
        for (target in targets) {
            val stream=WavPackDsd.open(ByteBuffer.wrap(fixture.source.readBytes()),target)
            val all=java.io.ByteArrayOutputStream()
            while (true) { val b=stream.decodeBlock()?:break;all.write(pcmBytes(b)) }
            val expected=all.toByteArray()
            val random=kotlin.random.Random(27)
            val positions=listOf(0L,1L,stream.info.totalSamples-1,stream.info.totalSamples)+
                List(24) { random.nextLong(stream.info.totalSamples) }
            for (position in positions) {
                stream.seekSample(position)
                val got=java.io.ByteArrayOutputStream();var cursor=position;var first=true
                while (true) {
                    val b=stream.decodeBlock()?:break
                    assertEquals(cursor,b.position);assertEquals(first,b.discontinuity);first=false
                    got.write(pcmBytes(b));cursor+=b.frames
                }
                assertEquals(stream.info.totalSamples,cursor)
                assertArrayEquals(expected.copyOfRange((position*stream.info.channels*4).toInt(),expected.size),got.toByteArray())
            }
        }
    }
    @Test fun dopMatchesExistingPackerAcrossOddBlocks() {
        val actual=packed();val reference=original()
        assertEquals(reference.info.dopSampleRate,actual.info.dopSampleRate)
        val wanted=MessageDigest.getInstance("SHA-256");val got=MessageDigest.getInstance("SHA-256")
        while (true) { val b=reference.decodeDopBlock()?:break;wanted.update(pcmBytes(b)) }
        var frames=0L;var borrowed:Any?=null;var storage:Any?=null
        while (true) {
            val b=actual.decodeDopBlock()?:break
            if (borrowed!=null) { assertSame(borrowed,b);assertSame(storage,b.samples) }
            borrowed=b;storage=b.samples;frames+=b.frames;got.update(pcmBytes(b))
        }
        assertEquals(actual.info.totalDopSamples,frames)
        assertArrayEquals(wanted.digest(),got.digest())
    }
    @Test fun pcmAndDopSeekExactlyWithIndependentCursors() {
        val actual=packed();val reference=original()
        val total=actual.info.totalSamples
        for (sample in longArrayOf(0,1,total/2,total-1,total)) {
            actual.seekSample(sample);reference.seekSample(sample)
            val wanted=java.io.ByteArrayOutputStream();val got=java.io.ByteArrayOutputStream()
            while (true) { val b=reference.decodeBlock()?:break;wanted.write(pcmBytes(b)) }
            var position=sample
            while (true) { val b=actual.decodeBlock()?:break;assertEquals(position,b.position);position+=b.frames;got.write(pcmBytes(b)) }
            assertEquals(total,position);assertArrayEquals(wanted.toByteArray(),got.toByteArray())
        }
        for (sample in longArrayOf(1,actual.info.totalDopSamples/2,0,actual.info.totalDopSamples)) {
            actual.seekDopSample(sample);reference.seekDopSample(sample)
            val wanted=java.io.ByteArrayOutputStream();val got=java.io.ByteArrayOutputStream()
            while (true) { val b=reference.decodeDopBlock()?:break;wanted.write(pcmBytes(b)) }
            var position=sample
            while (true) { val b=actual.decodeDopBlock()?:break;assertEquals(position,b.position);position+=b.frames;got.write(pcmBytes(b)) }
            assertEquals(actual.info.totalDopSamples,position);assertArrayEquals(wanted.toByteArray(),got.toByteArray())
        }
    }
}
