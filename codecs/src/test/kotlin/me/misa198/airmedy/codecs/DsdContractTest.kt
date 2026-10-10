// SPDX-License-Identifier: GPL-3.0-or-later
// Contract tests for pinned Flick 79da4ed76557c8ddf534e898480dde66bcc90334 (MIT),
// dsf-meta 0.3.0 and dff-meta 0.2.0 (MIT OR Apache-2.0); see docs/dsd/ORACLE.md.
package me.misa198.airmedy.codecs

import java.io.IOException
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test
import me.misa198.airmedy.codecs.container.*
import me.misa198.airmedy.codecs.container.dsd.Dsd
import me.misa198.airmedy.codecs.codec.dsd.DopPacker

class DsdContractTest {
 @Test fun openingAndRewindingDoNotDecodeAudio(){val f=DsdCorpus.rows.first{it.name=="dsd256-2ch.dsf"};val delegate=ByteBufferSource(ByteBuffer.wrap(f.source.readBytes()));var reads=0;var readBytes=0L
 val source=object:RandomAccessSource{override val length=delegate.length;override fun read(position:Long,buffer:ByteBuffer):Int {reads++;return delegate.read(position,buffer).also{if(it>0)readBytes+=it}}}
 val stream=Dsd.open(source);assertEquals(96L,readBytes);val openedReads=reads;stream.seekSample(0);assertEquals(openedReads,reads);stream.decodeBlock();assertTrue(reads>openedReads);val decodedReads=reads;stream.seekSample(0);assertEquals(decodedReads,reads)}
 @Test fun invalidPcmTargetHasClearRefusal(){val f=DsdCorpus.rows.first{it.status=="ok"};assertEquals("dsd: unsupported PCM target",assertThrows(IOException::class.java){Dsd.open(ByteBuffer.wrap(f.source.readBytes()),48000)}.message)}
 @Test fun dopLsbReverseMatchesNormalizedInput(){val lsb=byteArrayOf(1,2,4,8,16,32,64,(-128).toByte());val msb=ByteArray(lsb.size){(Integer.reverse(lsb[it].toInt() and 255) ushr 24).toByte()};val a=IntArray(4);val b=IntArray(4);val offsets=intArrayOf(0)
 DopPacker(2822400,1,true).packToI32(lsb,offsets,lsb.size,a);DopPacker(2822400,1).packToI32(msb,offsets,msb.size,b);assertArrayEquals(a,b)}
}
