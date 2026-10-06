package me.misa198.airmedy.player.dsp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

internal data class GoldenBand(
    val active: Boolean,
    val b0: Float,
    val b1: Float,
    val b2: Float,
    val a1: Float,
    val a2: Float,
)

internal data class GoldenResponsePoint(val hz: Double, val db: Double)

internal data class GoldenEqEntry(
    val sampleRate: Int,
    val gainSet: String,
    val gainsDb: FloatArray,
    val bands: List<GoldenBand>,
    val response: List<GoldenResponsePoint>,
)

internal data class GoldenWidthPreamp(
    val left: Float,
    val right: Float,
    val preampDb: Float,
    val width: Float,
    val outLeft: Float,
    val outRight: Float,
)

internal data class GoldenData(
    val frequenciesHz: IntArray,
    val q: Double,
    val eq: List<GoldenEqEntry>,
    val widthPreamp: List<GoldenWidthPreamp>,
)

/** Loads and parses androidApp/src/test/resources/golden/eq_golden.json. */
internal object Golden {
    val data: GoldenData by lazy {
        val text = javaClass.classLoader!!.getResourceAsStream("golden/eq_golden.json")!!
            .bufferedReader()
            .use { it.readText() }
        parseGolden(text)
    }
}

private fun parseGolden(text: String): GoldenData {
    val root = Json.parseToJsonElement(text).jsonObject
    val frequencies = root.getValue("frequenciesHz").jsonArray.map { it.jsonPrimitive.int }.toIntArray()
    val q = root.getValue("q").jsonPrimitive.double
    val eq = root.getValue("eq").jsonArray.map { element ->
        val entry = element.jsonObject
        GoldenEqEntry(
            sampleRate = entry.getValue("sampleRate").jsonPrimitive.int,
            gainSet = entry.getValue("gainSet").jsonPrimitive.content,
            gainsDb = entry.getValue("gainsDb").jsonArray.map { it.jsonPrimitive.float }.toFloatArray(),
            bands = entry.getValue("bands").jsonArray.map { bandElement ->
                val band = bandElement.jsonObject
                GoldenBand(
                    active = band.getValue("active").jsonPrimitive.boolean,
                    b0 = band.getValue("b0").jsonPrimitive.float,
                    b1 = band.getValue("b1").jsonPrimitive.float,
                    b2 = band.getValue("b2").jsonPrimitive.float,
                    a1 = band.getValue("a1").jsonPrimitive.float,
                    a2 = band.getValue("a2").jsonPrimitive.float,
                )
            },
            response = entry.getValue("response").jsonArray.map { pointElement ->
                val point = pointElement.jsonObject
                GoldenResponsePoint(
                    hz = point.getValue("hz").jsonPrimitive.double,
                    db = point.getValue("db").jsonPrimitive.double,
                )
            },
        )
    }
    val widthPreamp = root.getValue("widthPreamp").jsonArray.map { element ->
        val case = element.jsonObject
        GoldenWidthPreamp(
            left = case.getValue("left").jsonPrimitive.float,
            right = case.getValue("right").jsonPrimitive.float,
            preampDb = case.getValue("preampDb").jsonPrimitive.float,
            width = case.getValue("width").jsonPrimitive.float,
            outLeft = case.getValue("outLeft").jsonPrimitive.float,
            outRight = case.getValue("outRight").jsonPrimitive.float,
        )
    }
    return GoldenData(frequencies, q, eq, widthPreamp)
}

internal fun sineBuffer(rate: Int, frames: Int, hz: Double, amplitude: Double): FloatArray {
    val out = FloatArray(frames)
    for (n in 0 until frames) {
        out[n] = (amplitude * sin(2.0 * PI * hz * n / rate)).toFloat()
    }
    return out
}

/** Seeded interleaved stereo noise in [-amplitude, amplitude], independent per sample. */
internal fun randomStereo(frames: Int, random: Random, amplitude: Double): FloatArray {
    val out = FloatArray(frames * 2)
    for (i in out.indices) {
        out[i] = ((random.nextDouble() * 2.0 - 1.0) * amplitude).toFloat()
    }
    return out
}

internal fun peak(buffer: FloatArray, from: Int = 0, until: Int = buffer.size): Float {
    var m = 0f
    for (n in from until until) {
        val a = abs(buffer[n])
        if (a > m) m = a
    }
    return m
}

internal fun maxStep(buffer: FloatArray, from: Int, until: Int): Float {
    var m = 0f
    val start = max(1, from)
    for (n in start until until) {
        val d = abs(buffer[n] - buffer[n - 1])
        if (d > m) m = d
    }
    return m
}

internal fun maxAbsDiff(a: FloatArray, b: FloatArray): Float {
    var m = 0f
    for (i in a.indices) {
        val d = abs(a[i] - b[i])
        if (d > m) m = d
    }
    return m
}

internal fun assertWithinTolerance(message: String, expected: Float, actual: Float) {
    val tolerance = 1e-6f * max(abs(expected), 1e-6f) + 1e-7f
    assertTrue("$message: expected $expected but was $actual (tol $tolerance)", abs(expected - actual) <= tolerance)
}

internal fun assertCoefficientWithinRounding(message: String, expected: Float, actual: Float) {
    val tolerance = 1e-6 * max(abs(expected.toDouble()), 1e-6)
    assertTrue("$message: expected $expected but was $actual (tol $tolerance)", abs(expected - actual) <= tolerance)
}

class EqualizerGoldenTest {
    @Test
    fun frequenciesMatchGolden() {
        val golden = Golden.data
        assertEquals(golden.frequenciesHz.size, BiquadDesign.FrequenciesHz.size)
        for (i in golden.frequenciesHz.indices) {
            assertEquals(
                "frequency at index $i",
                golden.frequenciesHz[i].toFloat(),
                BiquadDesign.FrequenciesHz[i],
                0f,
            )
        }
        assertEquals("Q", golden.q, BiquadDesign.Q.toDouble(), 0.0)
        assertNotNull("peaking(1000 Hz, +12 dB, 48000) must be designed", BiquadDesign.peaking(1000f, 12f, 48000))
    }

    @Test
    fun coefficientsMatchGolden() {
        for (entry in Golden.data.eq) {
            for (i in 0 until BiquadDesign.FrequenciesHz.size) {
                val band = entry.bands[i]
                val coefficients = BiquadDesign.peaking(BiquadDesign.FrequenciesHz[i], entry.gainsDb[i], entry.sampleRate)
                val context = "rate=${entry.sampleRate} gainSet=${entry.gainSet} band=$i gain=${entry.gainsDb[i]}"
                assertEquals("$context active mismatch", band.active, coefficients != null)
                if (band.active) {
                    assertNotNull("$context coefficients missing", coefficients)
                    assertCoefficientWithinRounding("$context b0", band.b0, coefficients!!.b0)
                    assertCoefficientWithinRounding("$context b1", band.b1, coefficients.b1)
                    assertCoefficientWithinRounding("$context b2", band.b2, coefficients.b2)
                    assertCoefficientWithinRounding("$context a1", band.a1, coefficients.a1)
                    assertCoefficientWithinRounding("$context a2", band.a2, coefficients.a2)
                }
            }
        }
    }

    @Test
    fun responseMatchesGoldenWithinTenthDb() {
        val impulseLength = 65536
        var maxDeviation = 0.0
        for (entry in Golden.data.eq) {
            val equalizer = BiquadEqualizer(1, entry.sampleRate)
            equalizer.setGains(entry.gainsDb)
            val impulse = FloatArray(impulseLength)
            impulse[0] = 1f
            equalizer.process(impulse, 0, impulseLength)
            val (positions, values) = nonZeroTerms(impulse)
            for (point in entry.response) {
                val db = responseDb(positions, values, entry.sampleRate, point.hz)
                val deviation = abs(db - point.db)
                if (deviation > maxDeviation) maxDeviation = deviation
            }
        }
        println("EqualizerGoldenTest: max response deviation = $maxDeviation dB (limit 0.1 dB)")
        assertTrue("max response deviation $maxDeviation dB exceeds 0.1 dB", maxDeviation <= 0.1)
    }

    @Test
    fun isActiveOnlyWhenABandIsNonZero() {
        val withBand = BiquadEqualizer(1, 48000)
        withBand.setGains(floatArrayOf(0f, 0f, 0f, 0f, 0f, 3f, 0f, 0f, 0f, 0f))
        assertTrue("a non-zero band must make the equalizer active", withBand.isActive)

        val flat = BiquadEqualizer(1, 48000)
        flat.setGains(FloatArray(10))
        assertFalse("all-zero gains must be inactive", flat.isActive)

        val random = Random(4242L)
        val buffer = FloatArray(2048) { (random.nextDouble() * 2.0 - 1.0).toFloat() }
        val before = buffer.copyOf()
        flat.process(buffer, 0, 1024)
        assertArrayEquals("inactive equalizer must be a bit-exact passthrough", before, buffer, 0f)
    }

    @Test
    fun bandChangeMidStreamKeepsFilterStateAndBoundsTheStep() {
        val rate = 48000
        val frames = rate * 2
        val block = 480
        val input = sineBuffer(rate, frames, 64.0, 0.25)
        val gains6 = FloatArray(10).also { it[1] = 6f }
        val gains9 = FloatArray(10).also { it[1] = 9f }

        val equalizer = BiquadEqualizer(1, rate)
        equalizer.setGains(gains6)
        val output = processInBlocks(equalizer, input, block)

        val stepEarly = maxStep(output, rate, rate + rate / 5)
        val stepLate = maxStep(output, (1.8 * rate).toInt(), frames)
        assertTrue(
            "gain change must not reset state: step $stepEarly vs steady $stepLate",
            stepEarly <= 1.5f * stepLate,
        )

        val target = 0.25 * 10.0.pow(9.0 / 20.0)
        val settled = peak(output, (1.75 * rate).toInt(), frames).toDouble()
        assertTrue("settled peak must be non-zero", settled > 0.0)
        val peakErrorDb = abs(20.0 * log10(settled / target))
        assertTrue("settled peak $settled must be within 0.2 dB of $target (was $peakErrorDb dB)", peakErrorDb <= 0.2)

        val fresh = BiquadEqualizer(1, rate)
        fresh.setGains(gains9)
        val reference = processInBlocks(fresh, input, block)
        val from = (1.5 * rate).toInt()
        var maxDiff = 0f
        for (n in from until frames) {
            val d = abs(output[n] - reference[n])
            if (d > maxDiff) maxDiff = d
        }
        // Float rounding noise of a 64 Hz biquad at 48 kHz is ~1e-4 here; a state reset is caught by the step check above.
        assertTrue("late output must match steady +9 dB equalizer (max diff $maxDiff)", maxDiff <= 1e-3f)
    }

    @Test
    fun settingTheSameGainsChangesNothing() {
        val rate = 48000
        val frames = rate * 2
        val block = 480
        val input = sineBuffer(rate, frames, 64.0, 0.25)
        val gains = FloatArray(10).also { it[1] = 6f }

        val x = BiquadEqualizer(1, rate)
        val y = BiquadEqualizer(1, rate)
        x.setGains(gains)
        y.setGains(gains)

        val outX = FloatArray(frames)
        val outY = FloatArray(frames)
        var frame = 0
        while (frame < frames) {
            x.setGains(gains)
            val count = minOf(block, frames - frame)
            val bx = input.copyOfRange(frame, frame + count)
            val by = input.copyOfRange(frame, frame + count)
            x.process(bx, 0, count)
            y.process(by, 0, count)
            System.arraycopy(bx, 0, outX, frame, count)
            System.arraycopy(by, 0, outY, frame, count)
            frame += count
        }
        assertArrayEquals("re-setting identical gains must be a no-op", outX, outY, 0f)

        val target = 0.25 * 10.0.pow(6.0 / 20.0)
        val settled = peak(outX, frames - rate / 4, frames).toDouble()
        assertTrue("settled peak must be non-zero", settled > 0.0)
        val peakErrorDb = abs(20.0 * log10(settled / target))
        assertTrue("settled peak $settled must be within 0.2 dB of $target (was $peakErrorDb dB)", peakErrorDb <= 0.2)
    }

    private fun processInBlocks(equalizer: BiquadEqualizer, input: FloatArray, block: Int): FloatArray {
        val output = FloatArray(input.size)
        var frame = 0
        var blockIndex = 0
        while (frame < input.size) {
            if (blockIndex == 100) equalizer.setGains(FloatArray(10).also { it[1] = 9f })
            val count = minOf(block, input.size - frame)
            val chunk = input.copyOfRange(frame, frame + count)
            equalizer.process(chunk, 0, count)
            System.arraycopy(chunk, 0, output, frame, count)
            frame += count
            blockIndex++
        }
        return output
    }
}

private fun nonZeroTerms(buffer: FloatArray): Pair<IntArray, DoubleArray> {
    var count = 0
    for (value in buffer) {
        if (value.toDouble() != 0.0) count++
    }
    val positions = IntArray(count)
    val values = DoubleArray(count)
    var k = 0
    for (i in buffer.indices) {
        val value = buffer[i].toDouble()
        if (value != 0.0) {
            positions[k] = i
            values[k] = value
            k++
        }
    }
    return positions to values
}

private fun responseDb(positions: IntArray, values: DoubleArray, sampleRate: Int, hz: Double): Double {
    val w = 2.0 * PI * hz / sampleRate
    val c = cos(w)
    val s = sin(w)
    var cr = 1.0
    var ci = 0.0
    var re = 0.0
    var im = 0.0
    var next = 0
    for (k in positions.indices) {
        val index = positions[k]
        val gap = index - next
        if (gap > 0) {
            val gc = cos(w * gap)
            val gs = sin(w * gap)
            val ncr = cr * gc + ci * gs
            ci = ci * gc - cr * gs
            cr = ncr
        }
        val x = values[k]
        re += x * cr
        im += x * ci
        val ncr = cr * c + ci * s
        ci = ci * c - cr * s
        cr = ncr
        next = index + 1
    }
    val magnitude = sqrt(re * re + im * im)
    assertTrue("non-finite response magnitude at $hz Hz", magnitude.isFinite() && magnitude > 0.0)
    return 20.0 * log10(magnitude)
}
