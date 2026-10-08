// SPDX-License-Identifier: GPL-3.0-or-later
// Parsing ported from Rhythm (cromaguy/Rhythm) ReplayGainUtil.kt at ef16e7bd, Copyright 2024-2026 Anjishnu Nandi, GPL-3.0-or-later; changed per ADR-005 (see comments).
@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package me.misa198.airmedy.player.normalization

import androidx.media3.common.Format
import androidx.media3.common.Metadata
import androidx.media3.extractor.metadata.id3.BinaryFrame
import androidx.media3.extractor.metadata.id3.CommentFrame
import androidx.media3.extractor.metadata.id3.InternalFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import androidx.media3.extractor.mp3.Mp3InfoReplayGain
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import me.misa198.airmedy.player.PlaybackItem

/**
 * Reads ReplayGain/R128/Sound Check tags from a Media3 [Format.metadata] and turns them into a
 * [GainInfo]. Each frame is parsed defensively: a malformed binary frame (truncated RVA2/RGAD/RVAD,
 * channel index > 8, bit length 0) is skipped rather than throwing.
 *
 * Parsing ported from Rhythm's ReplayGainUtil.kt; the deviations from Rhythm required by ADR-005 are
 * marked with `ADR-005:` in the code.
 */
internal class TagGainSource : GainSource {

    override fun gainFor(item: PlaybackItem, format: Format?): GainInfo? {
        val metadata = format?.metadata ?: return null

        collectReplayGain(metadata)?.let { return it }
        collectR128(metadata)?.let { return it }
        return collectSoundCheck(metadata)
    }

    // ------------------------------------------------------------------------------------------
    // ReplayGain form: text tags -> RVA2 -> RGAD/LAME -> RVAD, each later source only filling the
    // values the earlier ones left missing.
    // ------------------------------------------------------------------------------------------

    private fun collectReplayGain(metadata: Metadata): GainInfo? {
        val values = ReplayGainValues()

        val referenceDiff = referenceLoudnessAdjustment(metadata)
        fillTextTags(metadata, values, referenceDiff)
        fillFromRva2(metadata, values)
        fillFromRgadAndMp3(metadata, values)
        fillFromRvad(metadata, values)

        return values.toGainInfo()
    }

    private class ReplayGainValues {
        var trackGain: Float? = null
        var albumGain: Float? = null
        var trackPeak: Float? = null
        var albumPeak: Float? = null

        fun toGainInfo(): GainInfo? =
            if (trackGain == null && albumGain == null) null
            else GainInfo(trackGain, albumGain, trackPeak, albumPeak, GainForm.ReplayGain)
    }

    private fun fillTextTags(metadata: Metadata, values: ReplayGainValues, diff: Float) {
        for (i in 0 until metadata.length()) {
            val entry = metadata[i] as? TextInformationFrame ?: continue
            if (entry.id != "TXXX" && entry.id != "TXX") continue
            entry.description?.let { applyReplayGainText(it, entry.value, diff, values) }
        }
        for (i in 0 until metadata.length()) {
            val entry = metadata[i] as? VorbisComment ?: continue
            applyReplayGainText(entry.key, entry.value, diff, values)
        }
        for (i in 0 until metadata.length()) {
            val entry = metadata[i] as? InternalFrame ?: continue
            if (entry.domain != "com.apple.iTunes" && entry.domain != "org.hydrogenaudio.replaygain") continue
            applyReplayGainText(entry.description, entry.text, diff, values)
        }
    }

    private fun applyReplayGainText(description: String, value: String, diff: Float, values: ReplayGainValues) {
        when (description.uppercase()) {
            "REPLAYGAIN_TRACK_GAIN" -> if (values.trackGain == null) values.trackGain = parseGainText(value, diff)
            "REPLAYGAIN_ALBUM_GAIN" -> if (values.albumGain == null) values.albumGain = parseGainText(value, diff)
            "REPLAYGAIN_TRACK_PEAK" -> if (values.trackPeak == null) values.trackPeak = parsePeakText(value)
            "REPLAYGAIN_ALBUM_PEAK" -> if (values.albumPeak == null) values.albumPeak = parsePeakText(value)
        }
    }

    /**
     * The REPLAYGAIN_REFERENCE_LOUDNESS adjustment, applied only to the text ReplayGain gains (never
     * to R128, Sound Check or the binary frames). ADR-005: Rhythm requires a " LUFS" suffix; we also
     * accept a bare number. A "dB" (SPL) value yields no adjustment.
     */
    private fun referenceLoudnessAdjustment(metadata: Metadata): Float {
        for (i in 0 until metadata.length()) {
            when (val entry = metadata[i]) {
                is TextInformationFrame -> {
                    if (entry.id != "TXXX" && entry.id != "TXX") continue
                    if (entry.description?.uppercase() == "REPLAYGAIN_REFERENCE_LOUDNESS") {
                        parseReferenceLoudness(entry.value)?.let { return it }
                    }
                }
                is VorbisComment -> if (entry.key.uppercase() == "REPLAYGAIN_REFERENCE_LOUDNESS") {
                    parseReferenceLoudness(entry.value)?.let { return it }
                }
                is InternalFrame ->
                    if (entry.domain == "com.apple.iTunes" || entry.domain == "org.hydrogenaudio.replaygain") {
                        if (entry.description.uppercase() == "REPLAYGAIN_REFERENCE_LOUDNESS") {
                            parseReferenceLoudness(entry.text)?.let { return it }
                        }
                    }
                else -> Unit
            }
        }
        return 0f
    }

    private fun parseReferenceLoudness(raw: String?): Float? {
        val value = raw?.trim() ?: return null
        val number = when {
            value.endsWith(" LUFS", ignoreCase = true) ->
                value.dropLast(5).replace(',', '.').toFloatOrNull()
            // ADR-005: a "dB" (SPL) reference is not a loudness, so it never shifts the gain.
            value.endsWith("dB", ignoreCase = true) -> null
            // ADR-005: Rhythm requires " LUFS"; we also accept a bare number.
            else -> value.replace(',', '.').toFloatOrNull()
        } ?: return null
        return if (number.isFinite()) -18f - number else null
    }

    private fun parseGainText(raw: String?, diff: Float): Float? {
        var value = raw?.trim() ?: return null
        if (value.endsWith(" dB", ignoreCase = true) || value.endsWith(" LU", ignoreCase = true)) {
            value = value.dropLast(3)
        }
        val gain = value.trim().replace(',', '.').toFloatOrNull() ?: return null
        if (!gain.isFinite() || abs(gain) > 40f) return null
        return gain + diff
    }

    private fun parsePeakText(raw: String?): Float? {
        val peak = raw?.trim()?.replace(',', '.')?.toFloatOrNull() ?: return null
        return if (peak.isFinite() && peak > 0f && peak <= 10f) peak else null
    }

    // ------------------------------------------------------------------------------------------
    // ReplayGain 1.0 binary frames.
    // ------------------------------------------------------------------------------------------

    private fun fillFromRva2(metadata: Metadata, values: ReplayGainValues) {
        for (i in 0 until metadata.length()) {
            val frame = metadata[i] as? BinaryFrame ?: continue
            if (frame.id != "RVA2" && frame.id != "XRV" && frame.id != "XRVA") continue
            val rva2 = runCatching { parseRva2(frame) }.getOrNull() ?: continue
            val identification = rva2.identification
            if (identification.startsWith("track", true) ||
                identification.startsWith("mix", true) ||
                identification.startsWith("radio", true) ||
                identification.equals("normalize", true)
            ) {
                if (values.trackGain == null) values.trackGain = rva2.gain()
                if (values.trackPeak == null) values.trackPeak = rva2.peak()
            } else if (identification.startsWith("album", true) ||
                identification.startsWith("audiophile", true) ||
                identification.startsWith("user", true)
            ) {
                if (values.albumGain == null) values.albumGain = rva2.gain()
                if (values.albumPeak == null) values.albumPeak = rva2.peak()
            }
        }
    }

    private data class Rva2Channel(val type: Int, val gain: Float, val peak: Float?)

    private data class Rva2Info(val identification: String, val channels: List<Rva2Channel>) {
        private val master = channels.firstOrNull { it.type == MASTER_VOLUME }

        fun gain(): Float? = master?.gain ?: channels.maxByOrNull { abs(it.gain) }?.gain

        fun peak(): Float? = master?.peak ?: channels.mapNotNull { it.peak }.maxOrNull()
    }

    private fun parseRva2(frame: BinaryFrame): Rva2Info? {
        val data = frame.data
        val zero = data.indexOf(0.toByte())
        val identEnd = if (zero >= 0) zero else data.size
        val identification = String(data, 0, identEnd, Charsets.ISO_8859_1)
        var i = if (zero >= 0) zero + 1 else data.size

        val channels = mutableListOf<Rva2Channel>()
        while (i < data.size) {
            val type = data[i].toInt() and 0xFF
            i++
            // ADR-005: a channel index outside the 0..8 enum range is malformed; skip the frame.
            if (type !in 0..8) return null
            if (i + 2 > data.size) return null
            val gainRaw = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
            val gain = gainRaw.toShort() / 512f
            if (i >= data.size) return null
            val peakBits = data[i].toInt() and 0xFF
            i++
            val len = (peakBits + 7) / 8
            if (i + len > data.size) return null
            val peak = peakFromBytes(data, i, len)
            i += len
            channels += Rva2Channel(type, gain, peak)
        }
        return Rva2Info(identification, channels)
    }

    private fun fillFromRgadAndMp3(metadata: Metadata, values: ReplayGainValues) {
        for (i in 0 until metadata.length()) {
            val frame = metadata[i] as? BinaryFrame ?: continue
            if (frame.id != "RGAD") continue
            val rgad = runCatching { parseRgad(frame) }.getOrNull() ?: continue
            applyGainFields(rgad.peak, rgad.field1, rgad.field2, values)
        }
        for (i in 0 until metadata.length()) {
            val info = metadata[i] as? Mp3InfoReplayGain ?: continue
            applyGainFields(
                info.peak,
                info.field1?.let { GainField(it.name, it.gain) },
                info.field2?.let { GainField(it.name, it.gain) },
                values,
            )
        }
    }

    private data class GainField(val name: Int, val gain: Float)

    private data class RgadInfo(val peak: Float, val field1: GainField?, val field2: GainField?)

    private fun applyGainFields(peak: Float, field1: GainField?, field2: GainField?, values: ReplayGainValues) {
        val fields = listOfNotNull(field1, field2)
        if (fields.any { it.name == TRACK_NAME || it.name == ALBUM_NAME }) {
            if (values.trackPeak == null) values.trackPeak = usablePeak(peak)
        }
        for (field in fields) {
            when (field.name) {
                TRACK_NAME -> if (values.trackGain == null) values.trackGain = field.gain
                ALBUM_NAME -> if (values.albumGain == null) values.albumGain = field.gain
            }
        }
    }

    private fun parseRgad(frame: BinaryFrame): RgadInfo? {
        val data = frame.data
        if (data.size < 8) return null
        val peak = Float.fromBits(
            ((data[0].toInt() and 0xFF) shl 24) or
                ((data[1].toInt() and 0xFF) shl 16) or
                ((data[2].toInt() and 0xFF) shl 8) or
                (data[3].toInt() and 0xFF),
        )
        val field1 = parseGainField(((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF))
        val field2 = parseGainField(((data[6].toInt() and 0xFF) shl 8) or (data[7].toInt() and 0xFF))
        return RgadInfo(peak, field1, field2)
    }

    private fun parseGainField(field: Int): GainField? {
        val name = (field shr 13) and 7
        if (name == 0) return null
        val sign = if ((field and 0x200) != 0) -1 else 1
        val gain = (field and 0x1FF) * sign / 10f
        return GainField(name, gain)
    }

    private fun fillFromRvad(metadata: Metadata, values: ReplayGainValues) {
        for (i in 0 until metadata.length()) {
            val frame = metadata[i] as? BinaryFrame ?: continue
            if (frame.id != "RVAD" && frame.id != "RVA") continue
            val rvad = runCatching { parseRvad(frame) }.getOrNull() ?: continue
            if (values.trackGain == null) {
                values.trackGain = rvad.channels.maxByOrNull { abs(it.gain) }?.gain
            }
            if (values.trackPeak == null) {
                values.trackPeak = rvad.channels.mapNotNull { it.peak }.maxOrNull()
            }
        }
    }

    private data class RvadChannel(val gain: Float, val peak: Float?)

    private data class RvadInfo(val channels: List<RvadChannel>)

    private fun parseRvad(frame: BinaryFrame): RvadInfo? {
        val data = frame.data
        if (data.size < 2) return null
        val signs = data[0].toInt() and 0xFF
        val bitLen = data[1].toInt() and 0xFF
        // ADR-005: a zero bit length yields no usable volume; skip the frame instead of reading
        // empty (zero) bytes as Rhythm does.
        if (bitLen == 0) return null
        val len = (bitLen + 7) / 8
        var i = 2
        val channels = mutableListOf<RvadChannel>()

        fun readVolume(sign: Boolean): Float? {
            if (i + len > data.size) return null
            val volume = adjustVolumeRvad(data, i, len, sign)
            i += len
            return volume
        }

        fun readPeak(): Float? {
            if (i + len > data.size) return null
            val peak = peakFromBytes(data, i, len)
            i += len
            return peak
        }

        // Rhythm reads FR, FL, their peaks, then BR, BL, their peaks, then C, then B.
        val volFR = readVolume(signs and 1 == 0) ?: return null
        val volFL = readVolume((signs shr 1) and 1 == 0) ?: return null
        var peakFR: Float? = null
        var peakFL: Float? = null
        if (i < data.size) {
            peakFR = readPeak()
            peakFL = readPeak()
        }
        channels += RvadChannel(volFR, peakFR)
        channels += RvadChannel(volFL, peakFL)
        if (i < data.size) {
            val volBR = readVolume((signs shr 2) and 1 == 0) ?: return null
            val volBL = readVolume((signs shr 3) and 1 == 0) ?: return null
            var peakBR: Float? = null
            var peakBL: Float? = null
            if (i < data.size) {
                peakBR = readPeak()
                peakBL = readPeak()
            }
            channels += RvadChannel(volBR, peakBR)
            channels += RvadChannel(volBL, peakBL)
            if (i < data.size) {
                val volC = readVolume((signs shr 4) and 1 == 0) ?: return null
                var peakC: Float? = null
                if (i < data.size) peakC = readPeak()
                channels += RvadChannel(volC, peakC)
                if (i < data.size) {
                    val volB = readVolume((signs shr 5) and 1 == 0) ?: return null
                    var peakB: Float? = null
                    if (i < data.size) peakB = readPeak()
                    channels += RvadChannel(volB, peakB)
                }
            }
        }
        return RvadInfo(channels)
    }

    private fun adjustVolumeRvad(data: ByteArray, offset: Int, len: Int, sign: Boolean): Float {
        val bytes = data.copyOfRange(offset, offset + len)
        val volume = BigInteger(bytes)
            .let { if (sign) it.multiply(BigInteger.valueOf(-1)) else it }
            .divide(BigInteger.valueOf(256))
            .toLong()
        return if (volume == -255L) -96f else 20f * ln((volume + 255) / 255f) / ln(10f)
    }

    private fun peakFromBytes(data: ByteArray, offset: Int, len: Int): Float? {
        if (len == 0) return null
        val peak = BigInteger(1, data.copyOfRange(offset, offset + len))
        if (peak.signum() == 0) return null
        return usablePeak(BigDecimal(peak).divide(BigDecimal.valueOf(32768), MathContext.DECIMAL128).toFloat())
    }

    private fun usablePeak(peak: Float): Float? =
        if (peak.isFinite() && peak > 0f && peak <= 10f) peak else null

    // ------------------------------------------------------------------------------------------
    // R128: Vorbis comments only, Q7.8 gain converted to dB (+5); no peaks.
    // ------------------------------------------------------------------------------------------

    private fun collectR128(metadata: Metadata): GainInfo? {
        var trackGain: Float? = null
        var albumGain: Float? = null
        for (i in 0 until metadata.length()) {
            val entry = metadata[i] as? VorbisComment ?: continue
            when (entry.key.uppercase()) {
                "R128_TRACK_GAIN" -> if (trackGain == null) trackGain = parseR128Gain(entry.value)
                "R128_ALBUM_GAIN" -> if (albumGain == null) albumGain = parseR128Gain(entry.value)
            }
        }
        return if (trackGain == null && albumGain == null) null
        else GainInfo(trackGain, albumGain, null, null, GainForm.R128)
    }

    private fun parseR128Gain(value: String): Float? {
        val q = value.trim().replace(',', '.').toIntOrNull() ?: return null
        return q / 256f + 5f
    }

    // ------------------------------------------------------------------------------------------
    // Sound Check / iTunNORM: track values only.
    // ------------------------------------------------------------------------------------------

    private fun collectSoundCheck(metadata: Metadata): GainInfo? {
        for (i in 0 until metadata.length()) {
            val entry = metadata[i]
            val text = when (entry) {
                is InternalFrame ->
                    if (entry.domain.equals("com.apple.iTunes", true) && entry.description.equals("iTunNORM", true)) {
                        entry.text
                    } else {
                        continue
                    }
                is CommentFrame -> if (entry.description.equals("iTunNORM", true)) entry.text else continue
                is VorbisComment -> if (entry.key.equals("iTunNORM", true)) entry.value else continue
                else -> continue
            }
            val soundCheck = parseITunNorm(text) ?: continue
            return GainInfo(soundCheck.gain, null, soundCheck.peak, null, GainForm.SoundCheck)
        }
        return null
    }

    private data class SoundCheckValues(val gain: Float, val peak: Float?)

    private fun parseITunNorm(text: String): SoundCheckValues? {
        // Words are unsigned 32-bit hex ("FFFFFFFF" does not fit an Int); a non-hex word rejects the tag
        // instead of shifting the word positions.
        val words = text.trim().split(Regex("\\s+")).map { it.toLongOrNull(16) ?: return null }
        if (words.size < 10) return null
        // ADR-005: Rhythm averages the L/R gain; we take the louder channel word.
        val gainWord = max(words[0], words[1])
        if (gainWord == 0L) return null
        val gain = -10f * log10(gainWord / 1000f)
        val peakWord = max(words[6], words[7])
        val peak = if (peakWord == 0L) null else usablePeak(peakWord / 32768f)
        return SoundCheckValues(gain, peak)
    }

    private companion object {
        const val MASTER_VOLUME = 1
        const val TRACK_NAME = 1
        const val ALBUM_NAME = 2
    }
}
