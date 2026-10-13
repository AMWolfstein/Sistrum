package com.amwolfstein.sistrum.player.media3

import androidx.media3.common.Format
import androidx.media3.common.Metadata

/**
 * Original encoder provenance and unadjusted 12-bit delay/padding from a Xing/Info LAME section.
 * Media3's adjusted Format values cannot recover padding that was clamped to zero.
 */
internal data class LameGaplessInfo(
    val encoderId: String,
    val rawDelay: Int,
    val rawPadding: Int,
) : Metadata.Entry

/**
 * Intended parser, implemented in T062j: inspect the first MPEG audio frame, header included.
 * Locate Xing/Info using the header's version/channel mode: MPEG-1 uses offset 36 for stereo,
 * 21 for mono; MPEG-2/2.5 uses 21 for stereo, 13 for mono (Mp3Extractor.java:595-598 in 1.11.1).
 * After the tag and flags, skip frames (4 bytes), bytes (4), TOC (100), and quality (4) only when
 * their respective flag bits 1/2/4/8 are set, as XingFrame.java:95-112 does.
 *
 * Read the 9-byte encoder identifier, skip revision/VBR method (1), lowpass (1), ReplayGain (8),
 * encoding flags/ATH (1), and bitrate (1), then unpack the next 24 bits into raw 12-bit delay
 * and padding (XingFrame.java:117-134). Preserve these raw values without Media3's 529 adjustment.
 * Return null for an absent Xing/Info tag or a LAME section shorter than 24 bytes.
 * This seam currently returns null and has no production caller.
 */
internal fun parseLameGaplessInfo(firstFrame: ByteArray): LameGaplessInfo? = null

/**
 * Intended correction, implemented in T062j: for audio/mpeg decoded by c2.android.mp3.decoder or
 * OMX.google.mp3.decoder, use rawDelay/rawPadding from a metadata [LameGaplessInfo] whose encoderId
 * starts with LAME, Lavf, or Lavc. Those verified AOSP decoders compensate their 529-sample delay.
 * Copy the format with those raw encoderDelay/encoderPadding values, preserving every other field.
 *
 * Do not correct a format with an iTunSMPB CommentFrame (description "iTunSMPB") or InternalFrame
 * (domain "com.apple.iTunes", description "iTunSMPB"): Media3 gives those unadjusted ID3 values
 * precedence over Xing/Info (Mp3Extractor.java:430,605-609). Other MIME types, codec names, encoder
 * identifiers, and formats without provenance remain unchanged. Delay magnitude is not provenance.
 * This seam currently returns the original format and has no production caller.
 */
internal fun platformMp3Trim(format: Format, codecName: String): Format = format
