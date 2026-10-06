package me.misa198.airmedy.player.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

class StereoWidthPreampTest {
    @Test
    fun widthAndPreampMatchGolden() {
        var index = 0
        for (case in Golden.data.widthPreamp) {
            val buffer = floatArrayOf(case.left, case.right)
            StereoWidth.apply(buffer, 0, 1, case.width)
            val gain = Preamp.linearGain(case.preampDb)
            buffer[0] *= gain
            buffer[1] *= gain
            assertWithinTolerance(
                "widthPreamp[$index] left (width=${case.width} preampDb=${case.preampDb})",
                case.outLeft,
                buffer[0],
            )
            assertWithinTolerance(
                "widthPreamp[$index] right (width=${case.width} preampDb=${case.preampDb})",
                case.outRight,
                buffer[1],
            )
            index++
        }
    }

    @Test
    fun preampGainMatchesFormula() {
        for (db in listOf(-24f, -12f, -6f, -3f, -0.5f, 0.5f, 3f, 6f, 12f, 24f)) {
            val expected = 10.0.pow(db.toDouble() / 20.0).toFloat()
            val actual = Preamp.linearGain(db)
            assertTrue(
                "linearGain($db) expected $expected but was $actual",
                abs(actual - expected) <= 1e-6f * max(abs(expected), 1e-6f),
            )
        }
        assertEquals(1f, Preamp.linearGain(0f), 0f)
    }

    @Test
    fun widthOneIsIdentityAndZeroIsMono() {
        val frames = 1024
        val input = randomStereo(frames, Random(13579L), 1.0)

        val identity = input.copyOf()
        StereoWidth.apply(identity, 0, frames, 1f)
        for (i in input.indices) {
            assertTrue("width 1 must be identity at $i", abs(identity[i] - input[i]) <= 1e-7f)
        }

        val mono = input.copyOf()
        StereoWidth.apply(mono, 0, frames, 0f)
        for (frame in 0 until frames) {
            val left = input[frame * 2]
            val right = input[frame * 2 + 1]
            val mid = (left + right) * 0.5f
            assertTrue("width 0 left at frame $frame", abs(mono[frame * 2] - mid) <= 1e-7f)
            assertTrue("width 0 right at frame $frame", abs(mono[frame * 2 + 1] - mid) <= 1e-7f)
        }

        val wide = input.copyOf()
        StereoWidth.apply(wide, 0, frames, 2f)
        for (frame in 0 until frames) {
            val left = input[frame * 2]
            val right = input[frame * 2 + 1]
            val expectedLeft = 1.5f * left - 0.5f * right
            val expectedRight = -0.5f * left + 1.5f * right
            assertTrue("width 2 left at frame $frame", abs(wide[frame * 2] - expectedLeft) <= 1e-6f)
            assertTrue("width 2 right at frame $frame", abs(wide[frame * 2 + 1] - expectedRight) <= 1e-6f)
        }
    }
}
