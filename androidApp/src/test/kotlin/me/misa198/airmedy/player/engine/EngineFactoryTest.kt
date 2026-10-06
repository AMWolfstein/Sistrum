package me.misa198.airmedy.player.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import me.misa198.airmedy.player.fakes.FakePlayerEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class EngineFactoryTest {
    @Test
    fun `parseEngineKind maps exact enum names`() {
        assertEquals(EngineKind.Native, parseEngineKind("Native"))
        assertEquals(EngineKind.Media3, parseEngineKind("Media3"))
    }

    @Test
    fun `parseEngineKind falls back to Native for null unknown or wrong case`() {
        assertEquals(EngineKind.Native, parseEngineKind(null))
        assertEquals(EngineKind.Native, parseEngineKind(""))
        assertEquals(EngineKind.Native, parseEngineKind("media3"))
        assertEquals(EngineKind.Native, parseEngineKind("Exo"))
    }

    @Test
    fun `fresh selection uses the native builder`() = runTest {
        var nativeCalls = 0
        var media3Calls = 0
        val factory = EngineFactory(
            selection = MutableStateFlow(EngineKind.Native),
            builders = mapOf(
                EngineKind.Native to { nativeCalls++; FakePlayerEngine() },
                EngineKind.Media3 to { media3Calls++; FakePlayerEngine() },
            ),
        )

        val engine = factory.create()

        assertEquals(EngineKind.Native, engine.kind)
        assertEquals(1, nativeCalls)
        assertEquals(0, media3Calls)
    }

    @Test
    fun `media3 selection uses the media3 builder when present`() = runTest {
        var nativeCalls = 0
        var media3Calls = 0
        val factory = EngineFactory(
            selection = MutableStateFlow(EngineKind.Media3),
            builders = mapOf(
                EngineKind.Native to { nativeCalls++; FakePlayerEngine() },
                EngineKind.Media3 to { media3Calls++; FakePlayerEngine() },
            ),
        )

        factory.create()

        assertEquals(0, nativeCalls)
        assertEquals(1, media3Calls)
    }

    @Test
    fun `media3 selection without a media3 builder falls back to native`() = runTest {
        var nativeCalls = 0
        val factory = EngineFactory(
            selection = MutableStateFlow(EngineKind.Media3),
            builders = mapOf(EngineKind.Native to { nativeCalls++; FakePlayerEngine() }),
        )

        val engine = factory.create()

        assertEquals(EngineKind.Native, engine.kind)
        assertEquals(1, nativeCalls)
    }

    @Test
    fun `changing the selection takes effect only on the next create`() = runTest {
        val selection = MutableStateFlow(EngineKind.Native)
        var nativeCalls = 0
        var media3Calls = 0
        val factory = EngineFactory(
            selection = selection,
            builders = mapOf(
                EngineKind.Native to { nativeCalls++; FakePlayerEngine() },
                EngineKind.Media3 to { media3Calls++; FakePlayerEngine() },
            ),
        )

        factory.create()
        assertEquals(1, nativeCalls)
        assertEquals(0, media3Calls)

        selection.value = EngineKind.Media3
        assertEquals(1, nativeCalls)
        assertEquals(0, media3Calls)

        factory.create()
        assertEquals(1, nativeCalls)
        assertEquals(1, media3Calls)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `factory requires a native builder`() {
        EngineFactory(
            selection = MutableStateFlow(EngineKind.Native),
            builders = mapOf(EngineKind.Media3 to { FakePlayerEngine() }),
        )
    }

    @Test
    fun `playbackEngineBuilders maps exactly Native and Media3 to their lambdas`() {
        var nativeCalls = 0
        var media3Calls = 0
        val builders = playbackEngineBuilders(
            native = { nativeCalls++; FakePlayerEngine() },
            media3 = { media3Calls++; FakePlayerEngine() },
        )

        assertEquals(setOf(EngineKind.Native, EngineKind.Media3), builders.keys)

        builders.getValue(EngineKind.Native)()
        assertEquals(1, nativeCalls)
        assertEquals(0, media3Calls)

        builders.getValue(EngineKind.Media3)()
        assertEquals(1, nativeCalls)
        assertEquals(1, media3Calls)
    }

    @Test
    fun `media3 selection through playbackEngineBuilders runs only the media3 lambda`() = runTest {
        var nativeCalls = 0
        var media3Calls = 0
        val factory = EngineFactory(
            selection = MutableStateFlow(EngineKind.Media3),
            builders = playbackEngineBuilders(
                native = { nativeCalls++; FakePlayerEngine() },
                media3 = { media3Calls++; FakePlayerEngine() },
            ),
        )

        factory.create()

        assertEquals(0, nativeCalls)
        assertEquals(1, media3Calls)
    }

    @Test
    fun `native selection through playbackEngineBuilders runs only the native lambda`() = runTest {
        var nativeCalls = 0
        var media3Calls = 0
        val factory = EngineFactory(
            selection = MutableStateFlow(EngineKind.Native),
            builders = playbackEngineBuilders(
                native = { nativeCalls++; FakePlayerEngine() },
                media3 = { media3Calls++; FakePlayerEngine() },
            ),
        )

        factory.create()

        assertEquals(1, nativeCalls)
        assertEquals(0, media3Calls)
    }

    @Test
    fun `default selection uses the native builder`() = runTest {
        var nativeCalls = 0
        var media3Calls = 0
        val factory = EngineFactory(
            selection = flowOf(parseEngineKind(null)),
            builders = playbackEngineBuilders(
                native = { nativeCalls++; FakePlayerEngine() },
                media3 = { media3Calls++; FakePlayerEngine() },
            ),
        )

        val engine = factory.create()

        assertEquals(EngineKind.Native, engine.kind)
        assertEquals(1, nativeCalls)
        assertEquals(0, media3Calls)
    }
}
