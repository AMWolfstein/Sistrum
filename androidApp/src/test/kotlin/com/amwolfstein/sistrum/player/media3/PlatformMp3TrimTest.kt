package com.amwolfstein.sistrum.player.media3

import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.CommentFrame
import androidx.media3.extractor.metadata.id3.InternalFrame
import org.junit.Assert.assertEquals
import org.junit.Test

/** Expected assertion failures until the provenance-based correction is implemented in T062j. */
@OptIn(UnstableApi::class)
class PlatformMp3TrimTest {
    @Test
    fun aospCodecUsesRawLameDelayAndPadding() {
        val input = trimFormat("audio/mpeg", 1105, 815, LameGaplessInfo("LAME3.100", 576, 1344))
        val actual = platformMp3Trim(input, "c2.android.mp3.decoder")

        assertEquals("raw LAME delay for the AOSP codec", 576, actual.encoderDelay)
        assertEquals("raw LAME padding for the AOSP codec", 1344, actual.encoderPadding)
        assertEquals(
            "all fields except delay/padding must equal the input",
            input.buildUpon().setEncoderDelay(576).setEncoderPadding(1344).build(),
            actual,
        )
    }

    @Test
    fun googleCodecRecoversLavfPaddingBelow529() {
        val input = trimFormat("audio/mpeg", 1105, 0, LameGaplessInfo("Lavf58.76", 576, 300))
        val actual = platformMp3Trim(input, "OMX.google.mp3.decoder")

        assertEquals("raw Lavf delay for the Google codec", 576, actual.encoderDelay)
        assertEquals("raw padding lost by Media3 clamping must be restored", 300, actual.encoderPadding)
        assertEquals(
            "all fields except delay/padding must equal the input",
            input.buildUpon().setEncoderDelay(576).setEncoderPadding(300).build(),
            actual,
        )
    }
}

/** ID3 precedence and missing/unverified provenance must preserve the entire input Format. */
@OptIn(UnstableApi::class)
class PlatformMp3TrimGuardTest {
    @Test
    fun iTunSmpbCommentTakesPrecedenceOverLame() {
        val input = trimFormat(
            "audio/mpeg", 1105, 815,
            LameGaplessInfo("LAME3.100", 576, 1344),
            CommentFrame("eng", "iTunSMPB", ITUN_SMPB),
        )
        assertEquals(input, platformMp3Trim(input, "c2.android.mp3.decoder"))
    }

    @Test
    fun iTunSmpbInternalFrameTakesPrecedenceOverLame() {
        val input = trimFormat(
            "audio/mpeg", 1105, 815,
            InternalFrame("com.apple.iTunes", "iTunSMPB", ITUN_SMPB),
            LameGaplessInfo("LAME3.100", 576, 1344),
        )
        assertEquals(input, platformMp3Trim(input, "c2.android.mp3.decoder"))
    }

    @Test
    fun gogoEncoderRemainsUnchanged() {
        val input = trimFormat("audio/mpeg", 1105, 815, LameGaplessInfo("GOGO", 576, 1344))
        assertEquals(input, platformMp3Trim(input, "c2.android.mp3.decoder"))
    }

    @Test
    fun xingEncoderRemainsUnchanged() {
        val input = trimFormat("audio/mpeg", 1105, 815, LameGaplessInfo("Xing", 576, 1344))
        assertEquals(input, platformMp3Trim(input, "c2.android.mp3.decoder"))
    }

    @Test
    fun vendorCodecRemainsUnchanged() {
        val input = trimFormat("audio/mpeg", 1105, 815, LameGaplessInfo("LAME3.100", 576, 1344))
        assertEquals(input, platformMp3Trim(input, "c2.qti.mp3.decoder"))
    }

    @Test
    fun missingLameProvenanceRemainsUnchanged() {
        val input = trimFormat("audio/mpeg", 1105, 815, CommentFrame("eng", "note", "No LAME tag"))
        assertEquals(input, platformMp3Trim(input, "c2.android.mp3.decoder"))
    }

    @Test
    fun flacWithLameMetadataRemainsUnchanged() {
        val input = trimFormat("audio/flac", 0, 0, LameGaplessInfo("LAME3.100", 576, 1344))
        assertEquals(input, platformMp3Trim(input, "c2.android.mp3.decoder"))
    }

    @Test
    fun aacWithLameMetadataRemainsUnchanged() {
        val input = trimFormat("audio/mp4a-latm", 2112, 1000, LameGaplessInfo("LAME3.100", 576, 1344))
        assertEquals(input, platformMp3Trim(input, "c2.android.mp3.decoder"))
    }

    @Test
    fun mpegLayerTwoWithLameMetadataRemainsUnchanged() {
        val input = trimFormat("audio/mpeg-L2", 1105, 815, LameGaplessInfo("LAME3.100", 576, 1344))
        assertEquals(input, platformMp3Trim(input, "c2.android.mp3.decoder"))
    }
}

private const val ITUN_SMPB = " 00000000 00000451 0000032F 000000000019A280"

@OptIn(UnstableApi::class)
private fun trimFormat(
    mimeType: String,
    delay: Int,
    padding: Int,
    vararg entries: Metadata.Entry,
): Format = Format.Builder()
    .setId("platform-trim-track")
    .setLabel("Gapless track")
    .setLanguage("ar")
    .setSampleMimeType(mimeType)
    .setSampleRate(48000)
    .setChannelCount(2)
    .setAverageBitrate(192000)
    .setPeakBitrate(256000)
    .setInitializationData(listOf(byteArrayOf(1, 2, 3)))
    .setEncoderDelay(delay)
    .setEncoderPadding(padding)
    .setMetadata(Metadata(*entries))
    .build()
