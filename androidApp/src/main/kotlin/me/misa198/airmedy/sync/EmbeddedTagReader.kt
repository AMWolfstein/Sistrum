package me.misa198.airmedy.sync

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.RandomAccessFile

/**
 * Reads embedded tags directly from the audio file's own bytes. Purely JVM
 * (no Android APIs) so the parsers are host-testable.
 *
 * Covered formats:
 * - FLAC (`fLaC`): METADATA_BLOCK_PICTURE (front cover) artwork and the
 *   Vorbis-comment `LYRICS`/`UNSYNCEDLYRICS` tags. The `LYRICS` tag commonly
 *   holds LRC-formatted synced text. Only the needed metadata blocks are read,
 *   found by walking the block headers.
 * - OGG Opus/Vorbis (`OggS`): Vorbis-comment `LYRICS`/`UNSYNCEDLYRICS` text and
 *   `METADATA_BLOCK_PICTURE` artwork (base64 per RFC 7845). The comment header
 *   packet is reassembled across pages, so it is read in full (up to a sanity cap).
 * - M4A/MP4 (`ftyp`): `moov > udta > meta > ilst` `covr` artwork, the
 *   `\u00A9lyr` lyrics atom, and the iTunes `----:com.apple.iTunes:LYRICS`
 *   free-form lyrics atom. Only the `moov` box is read, located by walking the
 *   top-level box headers, so it is found after a leading `mdat` too.
 * - MP3 ID3v2.2/2.3/2.4: `APIC`/`PIC` artwork and `USLT`/`ULT` lyrics frames. The
 *   tag is read at the size its header declares, not as a fixed prefix.
 * - WAV (`RIFF`/`WAVE`) and AIFF (`FORM`/`AIFF`|`AIFC`): `id3 `/`ID3 `
 *   chunks carrying an embedded ID3v2 tag, found wherever it sits by walking the
 *   chunk headers; WAV files with a tag prepended directly to the file are handled
 *   by the ID3 branch.
 *
 * Each format reads only its tag data, located from the sizes the file itself
 * declares; the byte limits are guards against corrupt sizes.
 */
/**
 * Supplementary tag values extracted alongside artwork/lyrics. [releaseDate] is an
 * ISO date string only when the source tag carries more than a bare year (e.g. ID3
 * `TDRC`/legacy `TYER`+`TDAT`+`TIME`, or Vorbis-comment `DATE`); otherwise it is null
 * and [year] alone is populated.
 */
internal data class EmbeddedTrackTags(
    val releaseDate: String? = null,
    val year: Int? = null,
    val bpm: Int? = null,
    val label: String? = null,
    val isrc: String? = null,
    val copyright: String? = null,
    /** Content advisory: true explicit, false clean, null when the file carries no advisory. */
    val explicit: Boolean? = null,
    /** Artist tag, used only when MediaStore has no artist for the track. */
    val artist: String? = null,
    /** Album-artist tag, used only when MediaStore has no album artist. */
    val albumArtist: String? = null,
    /** Album-title tag, used only when MediaStore has no album. */
    val album: String? = null,
)

/**
 * Advisory text value, as SpotiFLAC and iTunes-style taggers write `ITUNESADVISORY`:
 * "1" explicit, "0"/"2" clean. Anything unrecognised counts as not explicit.
 */
internal fun parseAdvisoryText(value: String?): Boolean? {
    val v = value?.trim()?.lowercase()?.takeIf(String::isNotEmpty) ?: return null
    return v == "1" || v == "true" || v == "yes" || v == "explicit"
}

/**
 * MP4 `rtng` payload. 1 is explicit: it is what SpotiFLAC writes and reads (Apple's
 * convention is 0 none, 1 explicit, 2 clean). Every other value counts as not explicit.
 */
internal fun parseRtngValue(value: Int?): Boolean? = value?.let { it == 1 }

/**
 * Total size of the ID3v2 tag starting at [header] (its first 10 bytes): the header,
 * the synchsafe body size it declares, and the v2.4 footer when flagged. Null when
 * [header] is not an ID3v2 header.
 */
internal fun id3TagSize(header: ByteArray): Long? {
    if (header.size < 10 || header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) return null
    if (header.sliceArray(6..9).any { it.toInt() and 0x80 != 0 }) return null
    val major = header[3].toInt() and 0xFF
    val hasFooter = major == 4 && header[5].toInt() and 0x10 != 0
    return 10L + header.syncsafeInt(6) + if (hasFooter) 10 else 0
}

internal object EmbeddedTagReader {

    /** Guard on the metadata blocks read from one FLAC file (generous for large covers). */
    private const val FlacReadLimit = 24 * 1024 * 1024
    private const val FlacVorbisComment = 4
    private const val FlacPicture = 6
    /** Guard against a corrupt ID3 size field; a real tag is read at its declared size. */
    private const val Id3ReadLimit = 24 * 1024 * 1024
    /** Cap on bytes walked to reassemble the OGG header packets (same bound as ID3). */
    private const val OggReadLimit = 24 * 1024 * 1024
    private const val OggPageHeaderSize = 27
    private val OpusHeadMagic = "OpusHead".toByteArray(Charsets.US_ASCII)
    private val OpusTagsMagic = "OpusTags".toByteArray(Charsets.US_ASCII)
    private val VorbisIdMagic = byteArrayOf(0x01) + "vorbis".toByteArray(Charsets.US_ASCII)
    private val VorbisCommentMagic = byteArrayOf(0x03) + "vorbis".toByteArray(Charsets.US_ASCII)
    /** Guard on the size of the one top-level box read (`moov`), not a file prefix. */
    private const val Mp4ReadLimit = 24 * 1024 * 1024
    /** Guard against a corrupt ID3 size field inside a WAV/AIFF chunk. */
    private const val RiffReadLimit = 24 * 1024 * 1024
    private const val MagicSize = 12

    /** Embedded album-art bytes (front cover when available), or null. */
    fun embeddedArtworkBytes(path: String): ByteArray? = runCatching {
        openForRead(path).use { file ->
            val head = file.head()
            when {
                head.isFlac() -> flacPicture(readFlacBlocks(file, FlacPicture) ?: return null)
                head.isId3() -> id3Apic(readId3Tag(file) ?: return null)
                head.isOgg() -> oggPicture(file)
                head.isMp4() -> mp4Covr(readTopLevelMp4Box(file, "moov") ?: return null)
                head.isRiff() || head.isForm() -> riffId3Picture(file)
                else -> null
            }
        }
    }.getOrNull()

    /** Embedded lyrics text (`LYRICS` -> `UNSYNCEDLYRICS` -> `USLT` -> M4A/AIFF), or null. */
    fun embeddedLyricsText(path: String): String? = runCatching {
        openForRead(path).use { file ->
            val head = file.head()
            when {
                head.isId3() -> id3Uslt(readId3Tag(file) ?: return null)
                head.isFlac() -> flacVorbisLyrics(readFlacBlocks(file, FlacVorbisComment) ?: return null)
                head.isOgg() -> oggVorbisLyrics(file)
                head.isMp4() -> mp4Lyrics(readTopLevelMp4Box(file, "moov") ?: return null)
                head.isRiff() || head.isForm() -> riffId3Lyrics(file)
                else -> null
            }
        }
    }.getOrNull()

    /** Release date/year, BPM, label, ISRC, copyright, artist, album artist and album read
     *  from the file's own tags. The artist/album fields are only a fallback for containers
     *  (e.g. WAV with an `id3 ` chunk) whose tags MediaStore does not report. */
    fun embeddedTrackTags(path: String): EmbeddedTrackTags? = runCatching {
        openForRead(path).use { file ->
            val head = file.head()
            when {
                head.isId3() -> id3ExtractedTags(readId3Tag(file) ?: return null)
                head.isFlac() -> flacExtractedTags(readFlacBlocks(file, FlacVorbisComment) ?: return null)
                head.isOgg() -> oggExtractedTags(file)
                head.isMp4() -> mp4ExtractedTags(readTopLevelMp4Box(file, "moov") ?: return null)
                head.isRiff() || head.isForm() -> riffExtractedTags(file)
                else -> null
            }
        }
    }.getOrNull()

    /** Test-only: how many files [openForRead] has opened. */
    internal val fileOpenCount = java.util.concurrent.atomic.AtomicInteger()

    /**
     * Each public read opens its file once and hands the open file to format detection
     * and the format's reader. Opening a file under shared storage (FUSE) costs several
     * milliseconds, far more than reading its tags, so it must not be repeated per step.
     */
    private fun openForRead(path: String): RandomAccessFile =
        RandomAccessFile(path, "r").also { fileOpenCount.incrementAndGet() }

    /** The first bytes of the file, enough for every magic check below (12). */
    private fun RandomAccessFile.head(): ByteArray {
        seek(0)
        val head = ByteArray(minOf(MagicSize.toLong(), length()).toInt())
        readFully(head)
        return head
    }

    /** The leading ID3v2 tag, read at the size its header declares (capped by [Id3ReadLimit]). */
    private fun readId3Tag(file: RandomAccessFile): ByteArray? = run {
        file.seek(0)
        val length = file.length()
        if (length < 10L) return null
        val header = ByteArray(10)
        file.readFully(header)
        val tagSize = id3TagSize(header) ?: return null
        val bytes = ByteArray(minOf(tagSize, length, Id3ReadLimit.toLong()).toInt())
        file.seek(0)
        file.readFully(bytes)
        bytes
    }

    /** Loops raw reads so bounded prefix reads work on every supported API level. */
    private fun readUpTo(input: InputStream, maxBytes: Int): ByteArray {
        val buffer = ByteArray(maxBytes)
        var total = 0
        while (total < maxBytes) {
            val read = input.read(buffer, total, maxBytes - total)
            if (read < 0) break
            total += read
        }
        return if (total == maxBytes) buffer else buffer.copyOf(total)
    }

    // ---------------------------------------------------------------- FLAC

    /**
     * The FLAC metadata blocks of [type], re-framed as `fLaC` + blocks (the final one
     * flagged last) for the block parsers below. Block headers are walked by seeking,
     * so padding, seek tables and other large blocks before them are skipped, not read.
     * A block that would take the total past [FlacReadLimit] is skipped.
     */
    private fun readFlacBlocks(file: RandomAccessFile, type: Int): ByteArray? = run {
        val length = file.length()
        val header = ByteArray(4)
        if (length < 8L) return null
        file.seek(0)
        file.readFully(header)
        if (!header.isFlac()) return null
        val out = ByteArrayOutputStream()
        out.write(header)
        var lastHeaderOffset = -1
        var pos = 4L
        var block = 0
        while (pos + 4 <= length && block < 256) {
            file.seek(pos)
            file.readFully(header)
            val blockType = header[0].toInt() and 0x7F
            val last = header[0].toInt() and 0x80 != 0
            val blockLength = header.readUInt24BE(1)
            val payloadStart = pos + 4
            if (payloadStart + blockLength > length) break
            if (blockType == type && out.size() + 4 + blockLength <= FlacReadLimit) {
                lastHeaderOffset = out.size()
                header[0] = blockType.toByte()
                out.write(header)
                val payload = ByteArray(blockLength)
                file.readFully(payload)
                out.write(payload)
            }
            if (last) break
            pos = payloadStart + blockLength
            block++
        }
        if (lastHeaderOffset < 0) return null
        out.toByteArray().also { it[lastHeaderOffset] = (it[lastHeaderOffset].toInt() or 0x80).toByte() }
    }

    private fun flacPicture(bytes: ByteArray): ByteArray? {
        var offset = 4
        var block = 0
        var firstPicture: ByteArray? = null
        while (offset + 4 <= bytes.size && block < 256) {
            val type = bytes[offset].toInt() and 0x7F
            val last = bytes[offset].toInt() and 0x80 != 0
            val length = bytes.readUInt24BE(offset + 1)
            val payloadStart = offset + 4
            val payloadEnd = payloadStart + length
            if (payloadEnd > bytes.size) break
            if (type == 6) {
                val picture = bytes.copyOfRange(payloadStart, payloadEnd).flacPicturePayload()
                if (picture != null) {
                    // Prefer the front-cover block; remember any other picture as a fallback.
                    if (bytes[payloadStart + 3].toInt() and 0xFF == 3) return picture
                    if (firstPicture == null) firstPicture = picture
                }
            }
            if (last) break
            offset = payloadEnd
            block++
        }
        return firstPicture
    }

    /** METADATA_BLOCK_PICTURE layout (all lengths big-endian). */
    private fun ByteArray.flacPicturePayload(): ByteArray? {
        if (size < 32) return null
        var pos = 0
        pos += 4 // picture type (prefer returning the first block; FLAC permits one front cover)
        val mimeLength = readUInt32BE(pos); pos += 4
        if (mimeLength < 0 || pos + mimeLength > size) return null
        pos += mimeLength
        val descriptionLength = readUInt32BE(pos); pos += 4
        if (descriptionLength < 0 || pos + descriptionLength > size) return null
        pos += descriptionLength
        pos += 16 // width, height, colour depth, colour count
        if (pos + 4 > size) return null
        val dataLength = readUInt32BE(pos); pos += 4
        if (dataLength <= 0 || pos + dataLength > size) return null
        return copyOfRange(pos, pos + dataLength)
    }

    private fun flacVorbisLyrics(bytes: ByteArray): String? {
        var offset = 4
        var block = 0
        while (offset + 4 <= bytes.size && block < 256) {
            val type = bytes[offset].toInt() and 0x7F
            val last = bytes[offset].toInt() and 0x80 != 0
            val length = bytes.readUInt24BE(offset + 1)
            val payloadStart = offset + 4
            val payloadEnd = payloadStart + length
            if (payloadEnd > bytes.size) return null
            if (type == 4) bytes.vorbisComment(payloadStart, payloadEnd, wantPicture = false)?.let {
                return it.lyrics ?: it.unsynced
            }
            if (last) return null
            offset = payloadEnd
            block++
        }
        return null
    }

    private fun flacExtractedTags(bytes: ByteArray): EmbeddedTrackTags? {
        var offset = 4
        var block = 0
        while (offset + 4 <= bytes.size && block < 256) {
            val type = bytes[offset].toInt() and 0x7F
            val last = bytes[offset].toInt() and 0x80 != 0
            val length = bytes.readUInt24BE(offset + 1)
            val payloadStart = offset + 4
            val payloadEnd = payloadStart + length
            if (payloadEnd > bytes.size) return null
            if (type == 4) bytes.vorbisComment(payloadStart, payloadEnd, wantPicture = false)?.let {
                return it.tags.toVorbisExtractedTags()
            }
            if (last) return null
            offset = payloadEnd
            block++
        }
        return null
    }

    // ---------------------------------------------------------------- OGG

    private fun oggVorbisLyrics(file: RandomAccessFile): String? = oggCommentText(file)?.let { it.lyrics ?: it.unsynced }

    private fun oggPicture(file: RandomAccessFile): ByteArray? = oggVorbisComment(file, wantPicture = true)?.picture

    private fun oggExtractedTags(file: RandomAccessFile): EmbeddedTrackTags? = oggCommentText(file)?.tags?.toVorbisExtractedTags()

    /**
     * Parses the comment header of an Opus ("OpusHead" + "OpusTags") or Vorbis
     * (0x01/0x03 + "vorbis") stream. The header packets are reassembled from
     * the Ogg page structure first: large comment packets (embedded artwork,
     * long lyrics) span many pages, and each page boundary interleaves a page
     * header that a flat byte scan would splice into the comment data.
     * Picture entries (almost all of a typical packet) are decoded only when
     * [wantPicture] is set.
     */
    private fun oggVorbisComment(file: RandomAccessFile, wantPicture: Boolean): VorbisComment? {
        // Reads through the already-open file; closing the stream also closes the file,
        // which the caller would do next anyway.
        val input = java.nio.channels.Channels.newInputStream(file.channel.position(0)).buffered()
        val (identification, comment) = input.use { oggHeaderPackets(it) } ?: return null
        val magicSize = when {
            identification.startsWith(OpusHeadMagic) && comment.startsWith(OpusTagsMagic) -> OpusTagsMagic.size
            identification.startsWith(VorbisIdMagic) && comment.startsWith(VorbisCommentMagic) -> VorbisCommentMagic.size
            else -> return null
        }
        return comment.vorbisComment(magicSize, comment.size, wantPicture)
    }

    /**
     * Reassembles the first two packets (identification + comment header) of the
     * file's first logical stream by walking each page's segment table. Pages of
     * other multiplexed streams are skipped. Reading stops once the comment packet
     * completes, or returns null past [OggReadLimit] or on a malformed page.
     */
    private fun oggHeaderPackets(input: InputStream): Pair<ByteArray, ByteArray>? {
        val packets = ArrayList<ByteArray>(2)
        var current = ByteArrayOutputStream()
        var serial: Int? = null
        var consumed = 0L
        while (packets.size < 2) {
            val header = readUpTo(input, OggPageHeaderSize)
            if (header.size < OggPageHeaderSize || !header.isOgg()) return null
            val segmentCount = header[26].toInt() and 0xFF
            val segments = readUpTo(input, segmentCount)
            if (segments.size < segmentCount) return null
            val bodySize = segments.sumOf { it.toInt() and 0xFF }
            consumed += OggPageHeaderSize + segmentCount + bodySize
            if (consumed > OggReadLimit) return null
            val body = readUpTo(input, bodySize)
            if (body.size < bodySize) return null
            val pageSerial = header.readUInt32LE(14)
            if (serial == null) serial = pageSerial
            if (pageSerial != serial) continue
            var offset = 0
            for (segment in segments) {
                val length = segment.toInt() and 0xFF
                current.write(body, offset, length)
                offset += length
                // A lacing value below 255 terminates the packet; 255 continues it,
                // possibly onto the next page.
                if (length < 255) {
                    packets += current.toByteArray()
                    current = ByteArrayOutputStream()
                    if (packets.size == 2) break
                }
            }
        }
        return packets[0] to packets[1]
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    /** Parsed Vorbis-comment block; any of the fields may be null when absent. [tags] holds
     *  every key=value pair (first occurrence per key) for callers that need other fields. */
    private class VorbisComment(val lyrics: String?, val unsynced: String?, val picture: ByteArray?, val tags: Map<String, String>)

    /**
     * Vorbis-comment block in [start]..[end]: vendor (LE), vendor data, count (LE), then
     * "key=value" pairs. Keys are read in place; `METADATA_BLOCK_PICTURE` values are
     * skipped (not copied, decoded or kept in [VorbisComment.tags]) unless [wantPicture].
     */
    private fun ByteArray.vorbisComment(start: Int, end: Int, wantPicture: Boolean): VorbisComment? {
        if (end - start < 8) return null
        var pos = start
        val vendorLength = readUInt32LE(pos); pos += 4
        if (vendorLength < 0 || pos + vendorLength > end) return null
        pos += vendorLength
        if (pos + 4 > end) return null
        val count = readUInt32LE(pos).coerceIn(0, 4096); pos += 4
        var lyrics: String? = null
        var unsynced: String? = null
        var picture: ByteArray? = null
        val tags = linkedMapOf<String, String>()
        repeat(count) {
            if (pos + 4 > end) return@repeat
            val length = readUInt32LE(pos); pos += 4
            if (length < 0 || pos + length > end) {
                pos = end
                return@repeat
            }
            val entryStart = pos
            val entryEnd = pos + length
            pos = entryEnd
            val equals = indexOfByte('='.code.toByte(), entryStart, entryEnd)
            if (equals > entryStart) {
                val key = String(this, entryStart, equals - entryStart, Charsets.ISO_8859_1).trim().uppercase()
                if (key == "METADATA_BLOCK_PICTURE") {
                    if (wantPicture && picture == null) {
                        picture = String(this, equals + 1, entryEnd - equals - 1, Charsets.ISO_8859_1).decodePictureBlock()
                    }
                    return@repeat
                }
                // Values are UTF-8 in the Vorbis-comment spec; decode separately so
                // non-Latin lyrics (e.g. Korean) survive past the ASCII key scan.
                val entryValue = String(this, equals + 1, entryEnd - equals - 1, Charsets.UTF_8)
                when (key) {
                    // LYRICS and SYNCEDLYRICS (e.g. written by Mp3tag) are both treated as
                    // synced text and take priority over UNSYNCEDLYRICS below.
                    "LYRICS", "SYNCEDLYRICS" -> if (lyrics == null) lyrics = entryValue
                    "UNSYNCEDLYRICS" -> if (unsynced == null) unsynced = entryValue
                }
                if (key.isNotEmpty() && key !in tags) tags[key] = entryValue
            }
        }
        return VorbisComment(lyrics, unsynced, picture, tags)
    }

    /**
     * Text entries of an Ogg Opus/Vorbis comment header, for tags and lyrics. Unlike
     * [oggVorbisComment] (the artwork path) the comment packet is never reassembled:
     * [OggCommentTextReader] parses it straight from the page stream and skips
     * `METADATA_BLOCK_PICTURE` entries (almost all of a typical packet) without copying
     * them. Page walking, guards and entry handling match [oggHeaderPackets] and
     * [vorbisComment], so the result equals `oggVorbisComment(path, false)`.
     */
    private fun oggCommentText(file: RandomAccessFile): VorbisComment? {
        val input = java.nio.channels.Channels.newInputStream(file.channel.position(0)).buffered()
        return input.use { OggCommentTextReader(it, file.length()).read() }
    }

    /** Test-only: bytes the tags-only Ogg path copied out of the header packets. */
    internal val oggTextBytesCopied = java.util.concurrent.atomic.AtomicLong()

    /** Test-only: an Ogg file's comment text through the streaming tags path, or through
     *  the full reassembly that the artwork path uses, for comparing the two. */
    internal fun oggCommentTextForTest(path: String, streaming: Boolean): Triple<String?, String?, Map<String, String>>? =
        openForRead(path).use { file -> if (streaming) oggCommentText(file) else oggVorbisComment(file, wantPicture = false) }
            ?.let { Triple(it.lyrics, it.unsynced, it.tags) }

    /**
     * Reads the first two packets of the file's first logical Ogg stream as a byte stream
     * that continues across pages, without assembling them. Pages are validated as
     * [oggHeaderPackets] does: `OggS` header, full segment table, the [OggReadLimit]
     * budget counted per page, the whole body present in the file, and other streams'
     * pages skipped. Skipped bytes are passed over with [InputStream.skip], never copied.
     */
    private class OggCommentTextReader(private val input: InputStream, private val fileLength: Long) {
        private var position = 0L
        private var consumed = 0L
        private var serial: Int? = null
        private var segments = IntArray(0)
        private var segmentIndex = 0
        private var segmentRemaining = 0
        private var lastLacing = 255
        private var pageBodyRemaining = 0
        /** Set when the page stream is malformed, truncated or over budget: the result is null. */
        private var failed = false

        fun read(): VorbisComment? {
            val identification = readPacketPrefix(8) ?: return null
            if (!skipRestOfPacket()) return null
            val commentMagic = when {
                identification.startsWith(OpusHeadMagic) -> OpusTagsMagic
                identification.startsWith(VorbisIdMagic) -> VorbisCommentMagic
                else -> return null
            }
            startPacket()
            val magic = readInPacket(commentMagic.size) ?: return null
            if (!magic.startsWith(commentMagic)) return null
            val comment = parseComment()
            // As with the reassembled packet, the comment header must complete.
            if (!skipRestOfPacket()) return null
            return comment
        }

        /** Mirrors [vorbisComment] over the packet stream; null where it returns null. */
        private fun parseComment(): VorbisComment? {
            val vendorLength = readIntLE() ?: return null
            if (vendorLength < 0 || skipInPacket(vendorLength.toLong()) < vendorLength) return null
            val count = (readIntLE() ?: return null).coerceIn(0, 4096)
            var lyrics: String? = null
            var unsynced: String? = null
            val tags = linkedMapOf<String, String>()
            for (index in 0 until count) {
                val length = readIntLE() ?: break
                if (length < 0) break
                // The key runs up to the first '='. The first chunk holds "METADATA_BLOCK_PICTURE="
                // (23 bytes), so a picture entry costs one byte of its payload.
                var head = ByteArray(0)
                var equals = -1
                while (equals < 0 && head.size < length) {
                    val chunkSize = if (head.isEmpty()) 24 else 64
                    val chunk = readInPacket(minOf(chunkSize, length - head.size)) ?: return VorbisComment(lyrics, unsynced, null, tags)
                    val searchFrom = head.size
                    head += chunk
                    equals = head.indexOfByte('='.code.toByte(), searchFrom, head.size)
                }
                if (equals <= 0) {
                    if (skipInPacket((length - head.size).toLong()) < length - head.size) break
                    continue
                }
                val key = String(head, 0, equals, Charsets.ISO_8859_1).trim().uppercase()
                if (key == "METADATA_BLOCK_PICTURE") {
                    if (skipInPacket((length - head.size).toLong()) < length - head.size) break
                    continue
                }
                val rest = readInPacket(length - head.size) ?: break
                val value = head.copyOfRange(equals + 1, head.size) + rest
                val entryValue = String(value, Charsets.UTF_8)
                when (key) {
                    "LYRICS", "SYNCEDLYRICS" -> if (lyrics == null) lyrics = entryValue
                    "UNSYNCEDLYRICS" -> if (unsynced == null) unsynced = entryValue
                }
                if (key.isNotEmpty() && key !in tags) tags[key] = entryValue
            }
            return VorbisComment(lyrics, unsynced, null, tags)
        }

        private fun readIntLE(): Int? = readInPacket(4)?.readUInt32LE(0)

        /** Up to [limit] leading bytes of the current packet (fewer if it is shorter). */
        private fun readPacketPrefix(limit: Int): ByteArray? {
            val out = ByteArray(limit)
            var total = 0
            while (total < limit && available()) total += copy(out, total, limit - total)
            return if (failed) null else out.copyOf(total)
        }

        /** Exactly [count] bytes of the current packet, or null if it ends first. */
        private fun readInPacket(count: Int): ByteArray? {
            val out = ByteArray(count)
            var total = 0
            while (total < count) {
                if (!available()) return null
                total += copy(out, total, count - total)
            }
            return out
        }

        /** Skips up to [count] bytes of the current packet; returns how many were skipped. */
        private fun skipInPacket(count: Long): Long {
            var skipped = 0L
            while (skipped < count && available()) {
                val step = minOf(count - skipped, segmentRemaining.toLong()).toInt()
                if (!skipFully(step)) return skipped
                segmentRemaining -= step
                pageBodyRemaining -= step
                skipped += step
            }
            return skipped
        }

        /** Skips to the end of the current packet; false if the stream fails first. */
        private fun skipRestOfPacket(): Boolean {
            while (available()) skipInPacket(segmentRemaining.toLong())
            return !failed
        }

        private fun startPacket() {
            lastLacing = 255
        }

        private fun copy(out: ByteArray, offset: Int, max: Int): Int {
            val count = minOf(max, segmentRemaining)
            var total = 0
            while (total < count) {
                val read = input.read(out, offset + total, count - total)
                if (read < 0) {
                    failed = true
                    segmentRemaining = 0
                    return 0
                }
                total += read
            }
            position += count
            segmentRemaining -= count
            pageBodyRemaining -= count
            oggTextBytesCopied.addAndGet(count.toLong())
            return count
        }

        /** True while the current packet has bytes left; moves on to the next segment or page. */
        private fun available(): Boolean {
            while (!failed) {
                if (segmentRemaining > 0) return true
                if (lastLacing < 255) return false
                if (segmentIndex >= segments.size) {
                    if (!nextPage()) failed = true
                    continue
                }
                lastLacing = segments[segmentIndex++]
                segmentRemaining = lastLacing
            }
            return false
        }

        private fun nextPage(): Boolean {
            if (pageBodyRemaining > 0 && !skipFully(pageBodyRemaining)) return false
            while (true) {
                val header = readUpTo(input, OggPageHeaderSize)
                position += header.size
                if (header.size < OggPageHeaderSize || !header.isOgg()) return false
                val segmentCount = header[26].toInt() and 0xFF
                val table = readUpTo(input, segmentCount)
                position += table.size
                if (table.size < segmentCount) return false
                val bodySize = table.sumOf { it.toInt() and 0xFF }
                consumed += OggPageHeaderSize + segmentCount + bodySize
                if (consumed > OggReadLimit) return false
                if (position + bodySize > fileLength) return false
                val pageSerial = header.readUInt32LE(14)
                if (serial == null) serial = pageSerial
                if (pageSerial != serial) {
                    if (!skipFully(bodySize)) return false
                    continue
                }
                segments = IntArray(segmentCount) { table[it].toInt() and 0xFF }
                segmentIndex = 0
                pageBodyRemaining = bodySize
                return true
            }
        }

        private fun skipFully(count: Int): Boolean {
            var remaining = count.toLong()
            while (remaining > 0) {
                val skipped = input.skip(remaining)
                if (skipped <= 0) return false
                remaining -= skipped
            }
            position += count
            return true
        }
    }

    /** Vorbis-comment DATE/YEAR (FLAC/OGG/Opus) -> the shared [EmbeddedTrackTags]. */
    private fun Map<String, String>.toVorbisExtractedTags(): EmbeddedTrackTags {
        fun firstOf(vararg keys: String): String? = keys.firstNotNullOfOrNull { key -> this[key]?.trim()?.takeIf(String::isNotEmpty) }
        val dateRaw = firstOf("DATE", "YEAR")
        return EmbeddedTrackTags(
            releaseDate = dateRaw?.takeIf { it.length > 4 },
            year = dateRaw?.let(::parseTagYear),
            bpm = firstOf("BPM")?.let(::parseTagBpm),
            label = firstOf("LABEL", "PUBLISHER"),
            isrc = firstOf("ISRC"),
            copyright = firstOf("COPYRIGHT"),
            explicit = parseAdvisoryText(firstOf("ITUNESADVISORY")),
            artist = firstOf("ARTIST"),
            albumArtist = firstOf("ALBUMARTIST", "ALBUM ARTIST", "ALBUM_ARTIST"),
            album = firstOf("ALBUM"),
        )
    }

    /** `METADATA_BLOCK_PICTURE` values are base64-encoded FLAC-style pictures (RFC 7845). */
    /** Test-only: how many `METADATA_BLOCK_PICTURE` values have been base64-decoded. */
    internal val pictureDecodeCount = java.util.concurrent.atomic.AtomicInteger()

    private fun String.decodePictureBlock(): ByteArray? = try {
        pictureDecodeCount.incrementAndGet()
        val base64 = trim()
        if (base64.isBlank()) null else java.util.Base64.getDecoder().decode(base64).flacPicturePayload()
    } catch (_: IllegalArgumentException) {
        null
    }

    // ---------------------------------------------------------------- MP4

    /** A well-formed MP4 box region: header bytes already skipped. */
    private class Mp4Box(val type: String, val start: Int, val end: Int)

    /** Enumerates the sibling boxes in [start, end), skipping extended-size boxes safely. */
    private fun ByteArray.mp4Boxes(start: Int, end: Int): List<Mp4Box> {
        val out = ArrayList<Mp4Box>(4)
        var pos = start
        while (pos + 8 <= end) {
            val size32 = readUInt32BE(pos)
            val type = String(this, pos + 4, 4, Charsets.ISO_8859_1)
            var header = 8
            var boxEnd: Long
            if (size32 == 1) {
                if (pos + 16 > end) break
                val large = readUInt64BE(pos + 8)
                if (large < 16) break
                header = 16
                boxEnd = pos + large
            } else if (size32 == 0) {
                boxEnd = end.toLong()
            } else {
                boxEnd = pos.toLong() + size32
            }
            if (boxEnd < pos + header || boxEnd > end) break
            out += Mp4Box(type, pos + header, boxEnd.toInt())
            pos = boxEnd.toInt()
        }
        return out
    }

    /**
     * Locates `udta > meta > ilst` in a `moov` payload. The `meta` box is formally a
     * full box, but many iTunes-authored files omit its version/flags quartet, so
     * both layouts are accepted.
     */
    private fun ByteArray.mp4IlstRegion(): Pair<Int, Int>? {
        for (udta in mp4Boxes(0, size)) {
            if (udta.type != "udta") continue
            for (meta in mp4Boxes(udta.start, udta.end)) {
                if (meta.type != "meta") continue
                for (ilst in mp4Boxes(metaChildrenStart(meta), meta.end)) {
                    if (ilst.type == "ilst") return ilst.start to ilst.end
                }
            }
        }
        return null
    }

    private fun ByteArray.metaChildrenStart(meta: Mp4Box): Int {
        fun plausible(offset: Int): Boolean =
            offset + 8 <= meta.end && readUInt32BE(offset) in 8..(meta.end - offset)
        if (plausible(meta.start)) return meta.start
        if (plausible(meta.start + 4)) return meta.start + 4
        return meta.start
    }

    private fun mp4Covr(bytes: ByteArray): ByteArray? {
        val (start, end) = bytes.mp4IlstRegion() ?: return null
        for (item in bytes.mp4Boxes(start, end)) {
            if (item.type != "covr") continue
            for (data in bytes.mp4Boxes(item.start, item.end)) {
                if (data.type != "data") continue
                val payloadStart = data.start + 8 // version/flags + locale
                if (payloadStart >= data.end) continue
                val picture = bytes.copyOfRange(payloadStart, data.end)
                if (picture.isNotEmpty()) return picture
            }
        }
        return null
    }

    private fun mp4Lyrics(bytes: ByteArray): String? {
        val (start, end) = bytes.mp4IlstRegion() ?: return null
        var lyricText: String? = null
        var itunesLyrics: String? = null
        for (item in bytes.mp4Boxes(start, end)) {
            when (item.type) {
                "\u00A9lyr" -> if (lyricText == null) bytes.mp4DataText(item.start, item.end)?.let { lyricText = it }
                "----" -> {
                    // iTunes free-form: mean=com.apple.iTunes, name=LYRICS.
                    val key = bytes.mp4FreeformKey(item.start, item.end)
                    val value = bytes.mp4DataText(item.start, item.end)
                    if (key != null && value != null && (key == "com.apple.iTunes.LYRICS" || key == "LYRICS")) {
                        if (itunesLyrics == null) itunesLyrics = value
                    }
                }
            }
        }
        return lyricText ?: itunesLyrics
    }

    private fun mp4ExtractedTags(bytes: ByteArray): EmbeddedTrackTags? {
        val (start, end) = bytes.mp4IlstRegion() ?: return null
        var dateRaw: String? = null
        var bpmRaw: String? = null
        var label: String? = null
        var isrc: String? = null
        var copyright: String? = null
        var explicit: Boolean? = null
        var artist: String? = null
        var albumArtist: String? = null
        var album: String? = null
        for (item in bytes.mp4Boxes(start, end)) {
            when (item.type) {
                // Content rating: an integer atom, not a text tag.
                "rtng" -> if (explicit == null) explicit = parseRtngValue(bytes.mp4DataInt(item.start, item.end))
                "©day" -> if (dateRaw == null) dateRaw = bytes.mp4DataText(item.start, item.end)
                "tmpo" -> if (bpmRaw == null) bpmRaw = bytes.mp4TmpoText(item.start, item.end)
                "cprt" -> if (copyright == null) copyright = bytes.mp4DataText(item.start, item.end)
                "©ART" -> if (artist == null) artist = bytes.mp4DataText(item.start, item.end)
                "aART" -> if (albumArtist == null) albumArtist = bytes.mp4DataText(item.start, item.end)
                "©alb" -> if (album == null) album = bytes.mp4DataText(item.start, item.end)
                "----" -> {
                    // iTunes free-form: mean=com.apple.iTunes, name=LABEL/PUBLISHER/ISRC/COPYRIGHT.
                    val key = bytes.mp4FreeformKey(item.start, item.end)?.substringAfterLast('.')?.uppercase()
                    val value = bytes.mp4DataText(item.start, item.end)
                    if (key != null && value != null) {
                        when (key) {
                            "LABEL", "PUBLISHER" -> if (label == null) label = value
                            "ISRC" -> if (isrc == null) isrc = value
                            "COPYRIGHT" -> if (copyright == null) copyright = value
                            "ITUNESADVISORY" -> if (explicit == null) explicit = parseAdvisoryText(value)
                        }
                    }
                }
            }
        }
        return EmbeddedTrackTags(
            releaseDate = dateRaw?.takeIf { it.length > 4 },
            year = dateRaw?.let(::parseTagYear),
            bpm = bpmRaw?.let(::parseTagBpm),
            label = label,
            isrc = isrc,
            copyright = copyright,
            explicit = explicit,
            artist = artist,
            albumArtist = albumArtist,
            album = album,
        )
    }

    /** Big-endian integer payload (1-4 bytes) of an integer `data` atom such as `rtng`. */
    private fun ByteArray.mp4DataInt(start: Int, end: Int): Int? {
        for (data in mp4Boxes(start, end)) {
            if (data.type != "data") continue
            val payloadStart = data.start + 8
            val length = data.end - payloadStart
            if (length !in 1..4) continue
            var value = 0
            for (i in payloadStart until data.end) value = (value shl 8) or (this[i].toInt() and 0xFF)
            return value
        }
        return null
    }

    /** `tmpo`'s `data` atom holds a big-endian 16-bit integer BPM, not text. */
    private fun ByteArray.mp4TmpoText(start: Int, end: Int): String? {
        for (data in mp4Boxes(start, end)) {
            if (data.type != "data") continue
            val payloadStart = data.start + 8
            if (payloadStart + 2 > data.end) continue
            val value = ((this[payloadStart].toInt() and 0xFF) shl 8) or (this[payloadStart + 1].toInt() and 0xFF)
            if (value > 0) return value.toString()
        }
        return null
    }

    /** First non-empty utf-8 text from a `data` atom (skips version/flags + locale). */
    private fun ByteArray.mp4DataText(start: Int, end: Int): String? {
        for (data in mp4Boxes(start, end)) {
            if (data.type != "data") continue
            val payloadStart = data.start + 8
            if (payloadStart >= data.end) continue
            val text = String(copyOfRange(payloadStart, data.end), Charsets.UTF_8).trim()
            if (text.isNotEmpty()) return text
        }
        return null
    }

    /** `mean.name` key of a `----` atom (values are padded with NULs/spaces). */
    private fun ByteArray.mp4FreeformKey(start: Int, end: Int): String? {
        var mean: String? = null
        var name: String? = null
        for (atom in mp4Boxes(start, end)) {
            val value = atomText(atom)
            when (atom.type) {
                "mean" -> if (mean == null) mean = value
                "name" -> if (name == null) name = value
            }
        }
        return if (mean == null && name == null) null else listOfNotNull(mean, name).joinToString(".")
    }

    /** `mean`/`name` atoms are laid out like `data` atoms minus the locale field:
     *  a 4-byte version/flags header precedes the string payload. */
    private fun ByteArray.atomText(box: Mp4Box): String? {
        val payloadStart = box.start + 4
        if (payloadStart >= box.end) return null
        return String(copyOfRange(payloadStart, box.end), Charsets.UTF_8).trim('\u0000', ' ')
    }

    /**
     * Length of a fragmented MP4/M4A (e.g. a saved DASH/CMAF stream) from `moov > mvex > mehd`:
     * `fragment_duration` in the `mvhd` timescale. Such files carry zero `mvhd`/`mdhd`
     * durations with the samples in `moof`/`mdat` fragments, and Android's own parser
     * (MediaStore and MediaMetadataRetriever) reports their duration as 0. Null when the
     * file is not MP4 or has no `mehd`.
     */
    fun fragmentedMp4DurationMillis(path: String): Long? = runCatching {
        openForRead(path).use { file ->
            if (!file.head().isMp4()) return null
            // TODO: a fragmented file without mehd could fall back to summing the
            // moof > traf > trun sample durations; no such file has turned up yet.
            readTopLevelMp4Box(file, "moov")?.mehdDurationMillis()
        }
    }.getOrNull()

    /** Payload of the first top-level box of [type], found by walking box headers only,
     *  so `mdat` and any `moof` fragments are skipped, not read. This finds `moov`
     *  wherever it sits: at the front of fast-start files or after `mdat` in the rest. */
    private fun readTopLevelMp4Box(file: RandomAccessFile, type: String): ByteArray? =
        run {
            val length = file.length()
            val header = ByteArray(16)
            var pos = 0L
            while (pos + 8 <= length) {
                file.seek(pos)
                file.readFully(header, 0, 8)
                val size32 = header.readUInt32BE(0).toLong() and 0xFFFFFFFFL
                var headerSize = 8
                val size = when (size32) {
                    1L -> {
                        if (pos + 16 > length) return null
                        file.readFully(header, 8, 8)
                        headerSize = 16
                        header.readUInt64BE(8)
                    }
                    0L -> length - pos
                    else -> size32
                }
                if (size < headerSize || pos + size > length) return null
                if (String(header, 4, 4, Charsets.ISO_8859_1) == type) {
                    val payloadSize = size - headerSize
                    if (payloadSize > Mp4ReadLimit) return null
                    return ByteArray(payloadSize.toInt()).also { payload ->
                        file.seek(pos + headerSize)
                        file.readFully(payload)
                    }
                }
                pos += size
            }
            null
        }

    /** `mehd` fragment_duration (32-bit in version 0, 64-bit in version 1) over the `mvhd` timescale. */
    private fun ByteArray.mehdDurationMillis(): Long? {
        val children = mp4Boxes(0, size)
        val mvhd = children.firstOrNull { it.type == "mvhd" } ?: return null
        // version/flags, then creation + modification times (32-bit in v0, 64-bit in v1).
        val timescaleOffset = mvhd.start + if (this[mvhd.start].toInt() == 1) 20 else 12
        if (timescaleOffset + 4 > mvhd.end) return null
        val timescale = readUInt32BE(timescaleOffset).toLong() and 0xFFFFFFFFL
        val mvex = children.firstOrNull { it.type == "mvex" } ?: return null
        val mehd = mp4Boxes(mvex.start, mvex.end).firstOrNull { it.type == "mehd" } ?: return null
        val fragmentDuration = if (this[mehd.start].toInt() == 1) {
            if (mehd.start + 12 > mehd.end) return null
            readUInt64BE(mehd.start + 4)
        } else {
            if (mehd.start + 8 > mehd.end) return null
            readUInt32BE(mehd.start + 4).toLong() and 0xFFFFFFFFL
        }
        if (timescale <= 0 || fragmentDuration <= 0) return null
        return fragmentDuration * 1000 / timescale
    }

    // ---------------------------------------------------------------- RIFF / AIFF

    /**
     * Embedded ID3v2 tag carried by a WAV `id3 ` or AIFF `ID3 ` chunk, or null. The
     * chunk usually follows the audio `data`/`SSND` chunk, so chunk headers are walked
     * by seeking past each body; only the ID3 tag itself is read, at its declared size.
     */
    private fun readRiffId3Tag(file: RandomAccessFile): ByteArray? = run {
        val length = file.length()
        val header = ByteArray(12)
        if (length < 12L) return null
        file.seek(0)
        file.readFully(header)
        val bigEndian = header.isForm() // AIFF is big-endian; WAV is little-endian.
        var pos = 12L
        while (pos + 8 <= length) {
            file.seek(pos)
            file.readFully(header, 0, 8)
            val chunkSize = (if (bigEndian) header.readUInt32BE(4) else header.readUInt32LE(4)).toLong() and 0xFFFFFFFFL
            val payloadStart = pos + 8
            val chunkEnd = payloadStart + chunkSize
            if (chunkEnd > length) return null
            val id = String(header, 0, 4, Charsets.ISO_8859_1)
            if (id.equals("id3 ", ignoreCase = true) && chunkSize >= 10) {
                file.readFully(header, 0, 10)
                val tagSize = id3TagSize(header)
                if (tagSize != null) {
                    val tag = ByteArray(minOf(tagSize, chunkSize, RiffReadLimit.toLong()).toInt())
                    file.seek(payloadStart)
                    file.readFully(tag)
                    return tag
                }
            }
            // RIFF chunks are word-aligned.
            pos = chunkEnd + (chunkSize and 1L)
        }
        null
    }

    private fun riffId3Picture(file: RandomAccessFile): ByteArray? = readRiffId3Tag(file)?.let { id3Apic(it) }

    private fun riffId3Lyrics(file: RandomAccessFile): String? = readRiffId3Tag(file)?.let { id3Uslt(it) }

    private fun riffExtractedTags(file: RandomAccessFile): EmbeddedTrackTags? = readRiffId3Tag(file)?.let { id3ExtractedTags(it) }

    // ---------------------------------------------------------------- ID3v2

    private fun id3Apic(bytes: ByteArray): ByteArray? {
        val (body, major) = bytes.id3Body() ?: return null
        var pos = 0
        var frames = 0
        if (major == 2) {
            while (pos + 6 <= body.size && frames < 512) {
                val id = String(body, pos, 3, Charsets.ISO_8859_1)
                val length = body.readUInt24BE(pos + 3)
                val dataStart = pos + 6
                val dataEnd = dataStart + length
                if (dataEnd > body.size) return null
                if (id == "PIC") body.copyOfRange(dataStart, dataEnd).parsePic()?.let { return it }
                pos = dataEnd
                frames++
            }
        } else {
            while (pos + 10 <= body.size && frames < 512) {
                val id = String(body, pos, 4, Charsets.ISO_8859_1)
                if (id == "\u0000\u0000\u0000\u0000") break
                val syncSafe = major == 4
                val length = if (syncSafe) body.syncsafeInt(pos + 4) else body.readUInt32BE(pos + 4)
                val flags = ((body[pos + 8].toInt() and 0xFF) shl 8) or (body[pos + 9].toInt() and 0xFF)
                val dataStart = pos + 10
                val rawEnd = dataStart + length.coerceAtMost(body.size - dataStart)
                var data = body.copyOfRange(dataStart, rawEnd)
                if (major == 4 && flags and 0x0002 != 0) data = data.unsync()
                if (id == "APIC") data.parseApic()?.let { return it }
                pos = rawEnd
                frames++
            }
        }
        return null
    }

    // TODO: ID3v2 SYLT (synchronised lyrics/text) frames are not parsed here — MP3-only,
    // a distinct binary structure from USLT, and lower priority. Falls back to USLT for now.
    private fun id3Uslt(bytes: ByteArray): String? {
        val (body, major) = bytes.id3Body() ?: return null
        var pos = 0
        var frames = 0
        if (major == 2) {
            while (pos + 6 <= body.size && frames < 512) {
                val id = String(body, pos, 3, Charsets.ISO_8859_1)
                val length = body.readUInt24BE(pos + 3)
                val dataStart = pos + 6
                val dataEnd = dataStart + length
                if (dataEnd > body.size) return null
                if (id == "ULT") body.copyOfRange(dataStart, dataEnd).parseUslt()?.let { return it }
                pos = dataEnd
                frames++
            }
        } else {
            while (pos + 10 <= body.size && frames < 512) {
                val id = String(body, pos, 4, Charsets.ISO_8859_1)
                if (id == "\u0000\u0000\u0000\u0000") break
                val syncSafe = major == 4
                val length = if (syncSafe) body.syncsafeInt(pos + 4) else body.readUInt32BE(pos + 4)
                val flags = ((body[pos + 8].toInt() and 0xFF) shl 8) or (body[pos + 9].toInt() and 0xFF)
                val dataStart = pos + 10
                val rawEnd = dataStart + length.coerceAtMost(body.size - dataStart)
                var data = body.copyOfRange(dataStart, rawEnd)
                if (major == 4 && flags and 0x0002 != 0) data = data.unsync()
                if (id == "USLT") data.parseUslt()?.let { return it }
                pos = rawEnd
                frames++
            }
        }
        return null
    }

    /** v2.3/2.4 (4-char) text-information frame id -> canonical key used by [EmbeddedTrackTags]. */
    private val Id3TextFrameKeysV24 = setOf(
        "TDRC", "TYER", "TDAT", "TIME", "TBPM", "TPUB", "TSRC", "TCOP",
        "TPE1", "TPE2", "TALB",
    )

    /** v2.2 (3-char) text-information frame id -> its v2.3/2.4 canonical equivalent. */
    private val Id3TextFrameKeysV22 = mapOf(
        "TYE" to "TYER", "TDA" to "TDAT", "TIM" to "TIME",
        "TBP" to "TBPM", "TPB" to "TPUB", "TRC" to "TSRC", "TCR" to "TCOP",
        "TP1" to "TPE1", "TP2" to "TPE2", "TAL" to "TALB",
    )

    private fun id3ExtractedTags(bytes: ByteArray): EmbeddedTrackTags? {
        val (body, major) = bytes.id3Body() ?: return null
        val values = linkedMapOf<String, String>()
        // TXXX user-defined text frames, keyed by upper-cased description.
        val userValues = linkedMapOf<String, String>()
        var pos = 0
        var frames = 0
        if (major == 2) {
            while (pos + 6 <= body.size && frames < 512) {
                val id = String(body, pos, 3, Charsets.ISO_8859_1)
                val length = body.readUInt24BE(pos + 3)
                val dataStart = pos + 6
                val dataEnd = dataStart + length
                if (dataEnd > body.size) break
                val canonical = Id3TextFrameKeysV22[id]
                if (canonical != null && canonical !in values) {
                    body.copyOfRange(dataStart, dataEnd).parseId3TextFrame()?.let { values[canonical] = it }
                }
                if (id == "TXX") body.copyOfRange(dataStart, dataEnd).parseTxxx()?.let { (key, value) -> userValues.putIfAbsent(key, value) }
                pos = dataEnd
                frames++
            }
        } else {
            while (pos + 10 <= body.size && frames < 512) {
                val id = String(body, pos, 4, Charsets.ISO_8859_1)
                if (id == "\u0000\u0000\u0000\u0000") break
                val syncSafe = major == 4
                val length = if (syncSafe) body.syncsafeInt(pos + 4) else body.readUInt32BE(pos + 4)
                val flags = ((body[pos + 8].toInt() and 0xFF) shl 8) or (body[pos + 9].toInt() and 0xFF)
                val dataStart = pos + 10
                val rawEnd = dataStart + length.coerceAtMost(body.size - dataStart)
                var data = body.copyOfRange(dataStart, rawEnd)
                if (major == 4 && flags and 0x0002 != 0) data = data.unsync()
                if (id in Id3TextFrameKeysV24 && id !in values) {
                    data.parseId3TextFrame()?.let { values[id] = it }
                }
                if (id == "TXXX") data.parseTxxx()?.let { (key, value) -> userValues.putIfAbsent(key, value) }
                pos = rawEnd
                frames++
            }
        }
        val tdrc = values["TDRC"]
        val legacyDate = legacyDateFrom(values["TYER"], values["TDAT"], values["TIME"])
        val dateRaw = tdrc ?: legacyDate
        return EmbeddedTrackTags(
            releaseDate = when {
                tdrc != null && tdrc.length > 4 -> tdrc
                legacyDate != null -> legacyDate
                else -> null
            },
            year = dateRaw?.let(::parseTagYear),
            bpm = values["TBPM"]?.let(::parseTagBpm),
            label = values["TPUB"],
            isrc = values["TSRC"],
            copyright = values["TCOP"],
            explicit = parseAdvisoryText(userValues["ITUNESADVISORY"]),
            artist = joinId3PersonValues(values["TPE1"]),
            albumArtist = joinId3PersonValues(values["TPE2"]),
            album = joinId3PersonValues(values["TALB"]),
        )
    }

    /**
     * A v2.4 text frame may hold several NUL-separated values. For the person/album fields
     * that is a multi-value list ("A\u0000B" -> "A, B"); blank entries are dropped. Null
     * when nothing non-blank remains.
     */
    private fun joinId3PersonValues(raw: String?): String? =
        raw?.split('\u0000')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString(", ")

    /** TXXX/TXX: `<encoding><description>\0<value>` -> (upper-cased description, value). */
    private fun ByteArray.parseTxxx(): Pair<String, String>? {
        if (isEmpty()) return null
        val encoding = this[0].toInt() and 0xFF
        if (encoding > 3) return null
        val (description, valueStart) = id3TextField(this, 1, encoding)
        if (valueStart > size) return null
        val value = decodeUsltText(copyOfRange(valueStart, size), encoding).trim('\u0000', ' ')
        return description.trim('\u0000', ' ').uppercase() to value
    }

    /** Text-information frame: `<encoding byte><text>`, no length-prefixed terminator required. */
    private fun ByteArray.parseId3TextFrame(): String? {
        if (isEmpty()) return null
        val encoding = this[0].toInt() and 0xFF
        if (encoding > 3) return null
        val text = decodeUsltText(copyOfRange(1, size), encoding)
        return text.trim('\u0000', ' ').takeIf(String::isNotEmpty)
    }

    /** Legacy v2.3 date split across TYER (year) + TDAT (DDMM) [+ TIME (HHMM)]. */
    private fun legacyDateFrom(year: String?, date: String?, time: String?): String? {
        val y = year?.trim()?.takeIf { it.length == 4 && it.all(Char::isDigit) } ?: return null
        val d = date?.trim()?.takeIf { it.length == 4 && it.all(Char::isDigit) } ?: return null
        val day = d.substring(0, 2)
        val month = d.substring(2, 4)
        val t = time?.trim()?.takeIf { it.length == 4 && it.all(Char::isDigit) }
        return if (t != null) "$y-$month-${day}T${t.substring(0, 2)}:${t.substring(2, 4)}:00" else "$y-$month-$day"
    }

    /** Splits the tag into its frame payload, honouring v2.3 tag-level unsynchronisation. */
    private fun ByteArray.id3Body(): Pair<ByteArray, Int>? {
        if (size < 10 || this[0] != 'I'.code.toByte() || this[1] != 'D'.code.toByte() || this[2] != '3'.code.toByte()) return null
        val major = this[3].toInt() and 0xFF
        if (major != 2 && major != 3 && major != 4) return null
        val flags = this[5].toInt() and 0xFF
        val tagLength = this.syncsafeInt(6)
        if (tagLength <= 0) return null
        var body = copyOfRange(10, (10 + tagLength).coerceAtMost(size))
        // v2.2/2.3 apply this single flag to the whole tag body; v2.4 instead uses a
        // per-frame flag (handled where each frame is read) since its syncsafe frame
        // lengths stay readable without unsyncing first.
        if ((major == 2 || major == 3) && flags and 0x80 != 0) body = body.unsync()
        return body to major
    }

    /** v2.2 `PIC`: encoding, image format (PNG/JPG), picture type, description, data. */
    private fun ByteArray.parsePic(): ByteArray? {
        if (size < 6) return null
        val encoding = this[0].toInt() and 0xFF
        if (encoding > 3) return null
        var pos = 1
        pos += 3 // image format
        pos += 1 // picture type
        if (pos >= size) return null
        val (_, end) = id3TextField(this, pos, encoding)
        return copyOfRange(end, size).takeIf { it.isNotEmpty() }
    }

    /** v2.3/2.4 `APIC`: encoding, mime, picture type, description, data. */
    private fun ByteArray.parseApic(): ByteArray? {
        if (size < 4) return null
        val encoding = this[0].toInt() and 0xFF
        if (encoding > 3) return null
        val mimeEnd = indexOfByte(0, 1)
        if (mimeEnd < 0) return null
        var pos = mimeEnd + 1
        if (pos >= size) return null
        pos += 1 // picture type
        if (pos >= size) return null
        val (_, end) = id3TextField(this, pos, encoding)
        return copyOfRange(end, size).takeIf { it.isNotEmpty() }
    }

    /** v2.2/2.3/2.4 `USLT`/`ULT`: encoding, language, content descriptor, text. */
    private fun ByteArray.parseUslt(): String? {
        if (size < 4) return null
        val encoding = this[0].toInt() and 0xFF
        if (encoding > 3) return null
        var pos = 1
        if (encoding == 1 && pos + 1 < size && isUtf16Bom(pos)) pos += 2
        pos += 3 // language
        if (pos >= size) return null
        val (_, afterDescriptor) = id3TextField(this, pos, encoding)
        if (afterDescriptor >= size) return null
        val text = decodeUsltText(copyOfRange(afterDescriptor, size), encoding)
        return text.trim().takeIf(String::isNotEmpty)
    }

    /** Lyrics text after the descriptor, using the frame's declared encoding. */
    private fun decodeUsltText(bytes: ByteArray, encoding: Int): String = when (encoding) {
        3 -> bytes.toString(Charsets.UTF_8)
        2 -> bytes.toString(Charsets.UTF_16BE)
        1 -> when {
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16LE)
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16BE)
            else -> bytes.toString(Charsets.UTF_16BE)
        }
        else -> bytes.toString(Charsets.ISO_8859_1)
    }

    private fun ByteArray.isUtf16Bom(pos: Int): Boolean =
        (this[pos] == 0xFF.toByte() && this[pos + 1] == 0xFE.toByte()) ||
            (this[pos] == 0xFE.toByte() && this[pos + 1] == 0xFF.toByte())

    /** Reads an encoded, null-terminated ID3 text field, returning (text, indexAfterTerminator). */
    private fun id3TextField(bytes: ByteArray, start: Int, encoding: Int): Pair<String, Int> {
        var pos = start
        when (encoding) {
            1, 2 -> {
                while (pos + 1 < bytes.size) {
                    if (bytes[pos] == 0.toByte() && bytes[pos + 1] == 0.toByte()) {
                        return decodeId3Text(bytes.copyOfRange(start, pos), encoding) to pos + 2
                    }
                    pos += 2
                }
                return decodeId3Text(bytes.copyOfRange(start, bytes.size), encoding) to bytes.size
            }
            else -> {
                while (pos < bytes.size) {
                    if (bytes[pos] == 0.toByte()) {
                        return String(bytes, start, pos - start, if (encoding == 3) Charsets.UTF_8 else Charsets.ISO_8859_1) to pos + 1
                    }
                    pos++
                }
                return String(bytes, start, bytes.size - start, if (encoding == 3) Charsets.UTF_8 else Charsets.ISO_8859_1) to bytes.size
            }
        }
    }

    private fun decodeId3Text(bytes: ByteArray, encoding: Int): String = when {
        encoding >= 2 -> bytes.toString(Charsets.UTF_16BE)
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
            bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16LE)
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
            bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16BE)
        else -> bytes.toString(Charsets.UTF_16LE)
    }

    private fun ByteArray.unsync(): ByteArray {
        if (isEmpty()) return this
        val out = ByteArrayOutputStream(size)
        var pos = 0
        while (pos < size) {
            if (this[pos] == 0xFF.toByte() && pos + 1 < size && this[pos + 1] == 0.toByte()) {
                out.write(this[pos].toInt())
                pos += 2
            } else {
                out.write(this[pos].toInt())
                pos += 1
            }
        }
        return out.toByteArray()
    }

    // ---------------------------------------------------------------- magic

    private fun ByteArray.isFlac(): Boolean {
        if (size < 4) return false
        return this[0] == 'f'.code.toByte() && this[1] == 'L'.code.toByte() && this[2] == 'a'.code.toByte() && this[3] == 'C'.code.toByte()
    }

    private fun ByteArray.isOgg(): Boolean {
        if (size < 4) return false
        return this[0] == 'O'.code.toByte() && this[1] == 'g'.code.toByte() && this[2] == 'g'.code.toByte() && this[3] == 'S'.code.toByte()
    }

    private fun ByteArray.isId3(): Boolean {
        if (size < 10) return false
        return this[0] == 'I'.code.toByte() && this[1] == 'D'.code.toByte() && this[2] == '3'.code.toByte()
    }

    private fun ByteArray.isMp4(): Boolean {
        if (size < 12) return false
        return this[4] == 'f'.code.toByte() && this[5] == 't'.code.toByte() && this[6] == 'y'.code.toByte() && this[7] == 'p'.code.toByte()
    }

    private fun ByteArray.isRiff(): Boolean {
        if (size < 12) return false
        return this[0] == 'R'.code.toByte() && this[1] == 'I'.code.toByte() && this[2] == 'F'.code.toByte() && this[3] == 'F'.code.toByte() &&
            this[8] == 'W'.code.toByte() && this[9] == 'A'.code.toByte() && this[10] == 'V'.code.toByte() && this[11] == 'E'.code.toByte()
    }

    private fun ByteArray.isForm(): Boolean {
        if (size < 12) return false
        return this[0] == 'F'.code.toByte() && this[1] == 'O'.code.toByte() && this[2] == 'R'.code.toByte() && this[3] == 'M'.code.toByte() &&
            this[8] == 'A'.code.toByte() && this[9] == 'I'.code.toByte() && this[10] == 'F'.code.toByte() &&
            (this[11] == 'F'.code.toByte() || this[11] == 'C'.code.toByte())
    }
}

/** Leading 4-digit year from a date-ish tag value ("2023", "2023-05-01", ...). */
private fun parseTagYear(raw: String): Int? {
    val trimmed = raw.trim()
    if (trimmed.length < 4) return null
    return trimmed.take(4).toIntOrNull()?.takeIf { it in 1000..9999 }
}

/** BPM tag value, tolerating a fractional form ("128.00"). */
private fun parseTagBpm(raw: String): Int? = raw.trim().toDoubleOrNull()?.toInt()?.takeIf { it > 0 }

// ---------------------------------------------------------------- primitives

private fun ByteArray.readUInt32BE(offset: Int): Int =
    ((this[offset].toInt() and 0xFF) shl 24) or
        ((this[offset + 1].toInt() and 0xFF) shl 16) or
        ((this[offset + 2].toInt() and 0xFF) shl 8) or
        (this[offset + 3].toInt() and 0xFF)

private fun ByteArray.readUInt24BE(offset: Int): Int =
    ((this[offset].toInt() and 0xFF) shl 16) or
        ((this[offset + 1].toInt() and 0xFF) shl 8) or
        (this[offset + 2].toInt() and 0xFF)

private fun ByteArray.readUInt32LE(offset: Int): Int =
    (this[offset].toInt() and 0xFF) or
        ((this[offset + 1].toInt() and 0xFF) shl 8) or
        ((this[offset + 2].toInt() and 0xFF) shl 16) or
        ((this[offset + 3].toInt() and 0xFF) shl 24)

private fun ByteArray.syncsafeInt(offset: Int): Int {
    var value = 0
    for (i in 0 until 4) {
        value = (value shl 7) or (this[offset + i].toInt() and 0x7F)
    }
    return value
}

private fun ByteArray.readUInt64BE(offset: Int): Long {
    var value = 0L
    for (i in 0 until 8) {
        value = (value shl 8) or (this[offset + i].toLong() and 0xFF)
    }
    return value
}

private fun ByteArray.indexOfByte(element: Byte, startIndex: Int, endIndex: Int = size): Int {
    for (pos in startIndex until endIndex) if (this[pos] == element) return pos
    return -1
}