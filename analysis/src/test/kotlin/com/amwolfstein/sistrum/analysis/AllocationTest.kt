// SPDX-License-Identifier: GPL-3.0-or-later
package com.amwolfstein.sistrum.analysis
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import org.junit.Assert.assertEquals
import org.junit.Test
class AllocationTest {
    @Test fun warmedFeedAllocatesNothing() {
        val bean=ManagementFactory.getThreadMXBean() as ThreadMXBean
        bean.isThreadAllocatedMemoryEnabled=true
        val block=FloatArray(2048*2) { ((it%101)-50)/100f }
        repeat(3) { val warm=PcmAnalyzer(2,44100);repeat(256){warm.feed(block,2,44100)} }
        val a=PcmAnalyzer(2,44100);val id=Thread.currentThread().threadId()
        val before=bean.getThreadAllocatedBytes(id)
        repeat(128) { a.feed(block,2,44100) }
        val allocated=bean.getThreadAllocatedBytes(id)-before
        assertEquals("feed allocations",0L,allocated)
    }
}
