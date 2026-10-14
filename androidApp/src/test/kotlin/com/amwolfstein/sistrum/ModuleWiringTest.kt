package com.amwolfstein.sistrum

import com.amwolfstein.sistrum.analysis.Mood
import com.amwolfstein.sistrum.codecs.audio.Buffer
import com.amwolfstein.sistrum.dsp.mix.Matrix
import com.amwolfstein.sistrum.dsp.mix.Speakers
import com.amwolfstein.sistrum.metadata.TagVocabulary
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Smoke checks that the app's host runtime can load and use all four JVM modules. */
class ModuleWiringTest {
    @Test
    fun codecsBufferIsAvailable() {
        val buffer = Buffer(channels = 2, bits = 16, capacityFrames = 4)

        assertEquals(2, buffer.channels)
        assertEquals(16, buffer.bits)
        assertEquals(4, buffer.capacityFrames)
        assertEquals(8, buffer.samples.size)
    }

    @Test
    fun metadataVocabularyIsAvailable() {
        assertEquals("TITLE", TagVocabulary.parseKey(" title "))
    }

    @Test
    fun analysisMoodIsAvailable() {
        assertEquals(1.0, Mood.tempoScore(115.0), 0.0)
    }

    @Test
    fun dspMatrixIsAvailable() {
        val matrix = Matrix.forLayouts(Speakers.MONO, Speakers.STEREO)
        val input = floatArrayOf(0.25f, -0.5f)
        val output = Array(2) { FloatArray(2) }

        matrix.apply(output, arrayOf(input), frames = 2)

        assertArrayEquals(input, output[0], 0f)
        assertArrayEquals(input, output[1], 0f)
    }
}
