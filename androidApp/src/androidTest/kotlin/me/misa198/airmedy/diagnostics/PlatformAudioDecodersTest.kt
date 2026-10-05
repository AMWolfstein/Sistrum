package me.misa198.airmedy.diagnostics

import android.media.MediaCodecList
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Diagnostic, not a regression test: reports the platform audio decoders of the device it runs on, as
 * evidence for the Decoder Registry's platform provider (specs/001-media3-migration). Asserts nothing about
 * which codecs exist, because that is device-specific.
 */
@RunWith(AndroidJUnit4::class)
class PlatformAudioDecodersTest {
    @Test
    fun reportPlatformAudioDecoders() {
        val decoders = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
            .filter { !it.isEncoder }
            .flatMap { info ->
                info.supportedTypes.filter { it.startsWith("audio/") }.map { type ->
                    val kind = when {
                        info.isAlias -> "alias"
                        info.isSoftwareOnly -> "software"
                        info.isHardwareAccelerated -> "hardware"
                        else -> "other"
                    }
                    "$type\t${info.name}\t$kind"
                }
            }
            .sorted()
        val types = decoders.map { it.substringBefore('\t') }.toSet()
        val probe = listOf(
            "audio/alac", "audio/flac", "audio/opus", "audio/vorbis", "audio/mpeg", "audio/mp4a-latm",
            "audio/raw", "audio/g711-alaw", "audio/g711-mlaw", "audio/ac3", "audio/eac3",
            "audio/x-ms-wma", "audio/x-ape", "audio/x-wavpack", "audio/x-musepack",
        ).joinToString("\n") { "$it\t${if (it in types) "YES" else "no"}" }
        val report = "DECODERS\n${decoders.joinToString("\n")}\nPROBE\n$probe"
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString("stream", report + "\n") })
    }
}
