@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.amwolfstein.sistrum.player.media3

import androidx.media3.common.Format
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.extractor.MpegAudioUtil
import androidx.media3.extractor.metadata.id3.CommentFrame
import androidx.media3.extractor.metadata.id3.InternalFrame

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
 * Inspects the first MPEG audio frame, header included.
 * Locate Xing/Info using the header's version/channel mode: MPEG-1 uses offset 36 for stereo,
 * 21 for mono; MPEG-2/2.5 uses 21 for stereo, 13 for mono (Mp3Extractor.java:595-598 in 1.11.1).
 * After the tag and flags, skip frames (4 bytes), bytes (4), TOC (100), and quality (4) only when
 * their respective flag bits 1/2/4/8 are set, as XingFrame.java:95-112 does.
 *
 * Read the 9-byte encoder identifier, skip revision/VBR method (1), lowpass (1), ReplayGain (8),
 * encoding flags/ATH (1), and bitrate (1), then unpack the next 24 bits into raw 12-bit delay
 * and padding (XingFrame.java:117-134). Preserve these raw values without Media3's 529 adjustment.
 * Return null for an absent Xing/Info tag or a LAME section shorter than 24 bytes.
 */
internal fun parseLameGaplessInfo(firstFrame: ByteArray): LameGaplessInfo? {
    if (firstFrame.size < 4) return null
    fun unsignedByte(index: Int): Int = firstFrame[index].toInt() and 0xFF
    fun intAt(index: Int): Int = (unsignedByte(index) shl 24) or
        (unsignedByte(index + 1) shl 16) or (unsignedByte(index + 2) shl 8) or unsignedByte(index + 3)

    val headerData = intAt(0)
    val header = MpegAudioUtil.Header()
    if (!header.setForHeaderData(headerData) || headerData ushr 17 and 3 != 1) return null
    val tagOffset = if (header.version == 3) {
        if (header.channels == 1) 21 else 36
    } else {
        if (header.channels == 1) 13 else 21
    }
    if (firstFrame.size < tagOffset + 8) return null
    val tag = String(firstFrame, tagOffset, 4, Charsets.US_ASCII)
    if (tag != "Xing" && tag != "Info") return null
    val flags = intAt(tagOffset + 4)
    var lameOffset = tagOffset + 8
    if (flags and 1 != 0) lameOffset += 4
    if (flags and 2 != 0) lameOffset += 4
    if (flags and 4 != 0) lameOffset += 100
    if (flags and 8 != 0) lameOffset += 4
    if (firstFrame.size < lameOffset + 24) return null
    val encoderId = String(firstFrame, lameOffset, 9, Charsets.US_ASCII).trimEnd('\u0000', ' ')
    val packed = (unsignedByte(lameOffset + 21) shl 16) or
        (unsignedByte(lameOffset + 22) shl 8) or unsignedByte(lameOffset + 23)
    return LameGaplessInfo(encoderId, packed ushr 12, packed and 0xFFF)
}

/**
 * For audio/mpeg decoded by c2.android.mp3.decoder or
 * OMX.google.mp3.decoder, use rawDelay/rawPadding from a metadata [LameGaplessInfo] whose encoderId
 * starts with LAME, Lavf, or Lavc. Those verified AOSP decoders compensate their 529-sample delay.
 * Copy the format with those raw encoderDelay/encoderPadding values, preserving every other field.
 *
 * Do not correct a format with an iTunSMPB CommentFrame (description "iTunSMPB") or InternalFrame
 * (domain "com.apple.iTunes", description "iTunSMPB"): Media3 gives those unadjusted ID3 values
 * precedence over Xing/Info (Mp3Extractor.java:430,605-609). Other MIME types, codec names, encoder
 * identifiers, and formats without provenance remain unchanged. Delay magnitude is not provenance.
 */
internal fun platformMp3Trim(format: Format, codecName: String): Format {
    if (format.sampleMimeType != MimeTypes.AUDIO_MPEG ||
        codecName != "c2.android.mp3.decoder" && codecName != "OMX.google.mp3.decoder"
    ) return format
    val metadata = format.metadata ?: return format
    var info: LameGaplessInfo? = null
    for (index in 0 until metadata.length()) {
        when (val entry = metadata[index]) {
            is CommentFrame -> if (entry.description == "iTunSMPB") return format
            is InternalFrame -> if (entry.domain == "com.apple.iTunes" && entry.description == "iTunSMPB") return format
            is LameGaplessInfo -> if (info == null &&
                (entry.encoderId.startsWith("LAME") || entry.encoderId.startsWith("Lavf") || entry.encoderId.startsWith("Lavc"))
            ) info = entry
        }
    }
    val lame = info ?: return format
    if (format.encoderDelay == lame.rawDelay && format.encoderPadding == lame.rawPadding) return format
    return format.buildUpon().setEncoderDelay(lame.rawDelay).setEncoderPadding(lame.rawPadding).build()
}
