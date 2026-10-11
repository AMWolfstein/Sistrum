// SPDX-License-Identifier: GPL-3.0-or-later
package me.misa198.airmedy.analysis
import org.junit.Test
import org.junit.Assert.*
class CheckpointTest {
    @Test fun cancelAndResumeMatchesUninterruptedAcrossDspWindows() {
        val w=OracleSupport.wave("owned/generated/dynamic-2ch.wav")
        val full=PcmAnalyzer(w.channels,w.rate);full.feed(w.pcm,w.channels,w.rate)
        val partial=PcmAnalyzer(w.channels,w.rate)
        val first=w.pcm.copyOfRange(0,12346);val rest=w.pcm.copyOfRange(12346,w.pcm.size)
        assertTrue(partial.feed(first,w.channels,w.rate));partial.cancel()
        assertFalse(partial.feed(rest,w.channels,w.rate));assertEquals(6173L,partial.frames)
        val resumed=PcmAnalyzer.resume(partial.checkpoint())
        assertTrue(resumed.feed(rest,w.channels,w.rate));assertEquals(full.finish(),resumed.finish())
        assertSame(resumed.finish(),resumed.finish())
    }
    @Test fun checkpointsAreIndependentOfTheLiveAnalyzer() {
        val w=OracleSupport.wave("owned/generated/sine-44100-997.wav")
        val a=PcmAnalyzer(1,44100);a.feed(w.pcm.copyOfRange(0,5001),1,44100)
        val saved=a.checkpoint();a.feed(w.pcm.copyOfRange(5001,w.pcm.size),1,44100)
        val b=PcmAnalyzer.resume(saved);assertEquals(5001L,b.frames)
        b.feed(w.pcm.copyOfRange(5001,w.pcm.size),1,44100);assertEquals(a.finish(),b.finish())
    }
}
