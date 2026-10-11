// SPDX-License-Identifier: GPL-3.0-or-later
// Port of WaxFlow cue/ at b7857aff88820ad37936026421d1641e64611dbe (MIT).
package me.misa198.airmedy.codecs.cue

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale

class CueException(message: String) : IOException("cue: $message")
data class Rem(val key: String, val value: String)
data class Index(val number: Int, val frame: Int)
data class Warning(val line: Int, val message: String)
/** A source-compatible split span; negative to means EOF, even with a known total. */
data class Piece(val from: Long, var to: Long, val track: Int, val audio: Boolean)
data class Cutpoints(val cuts: List<Long>, val skip: List<Int>)
enum class Encoding { UTF8, UTF8_BOM, UTF16_LE, UTF16_BE, WINDOWS_1252 }

data class Track(
    val number: Long, val type: String,
    var title: String = "", var performer: String = "", var songwriter: String = "", var isrc: String = "",
    var pregap: Int = 0, var postgap: Int = 0,
    val indexes: MutableList<Index> = mutableListOf(), val rems: MutableList<Rem> = mutableListOf(),
    val flags: MutableList<String> = mutableListOf()
) {
    val isAudio get() = !type.uppercase(Locale.ROOT).let { it.startsWith("MODE") || it.startsWith("CDI") }
    val start get() = indexes.firstOrNull { it.number == 1 }?.frame
    fun rem(key: String) = rems.firstOrNull { it.key.equals(key, true) }?.value
}

data class File(val name: String, val type: String, val precededByData: Boolean = false,
                val tracks: MutableList<Track> = mutableListOf()) {
    private val label get() = if (name.isEmpty()) "the implied file" else "file ${quote(clip(name, 255))}"
    fun starts(rate: Int): LongArray {
        require(rate > 0) { "sample rate must be positive" }
        if (tracks.isEmpty()) throw CueException("$label names no tracks")
        var floor = 0
        var previous: Track? = null
        return LongArray(tracks.size) { i ->
            val t = tracks[i]
            if (t.number < 0) throw CueException("$label track ${t.number} has no readable number, so a split cannot number its piece")
            val start = if(t.isAudio) t.start ?: throw CueException("$label track ${t.number} has no INDEX 01, so it has no start")
                else if(i == 0) 0 else t.indexes.firstOrNull { it.number <= 1 }?.frame
                    ?: throw CueException("$label track ${t.number} is ${quote(clip(t.type))}, a data track with no INDEX 00 or 01, so nothing says where the audio before it ends")
            if(previous != null && (start < floor || (start == floor && previous!!.isAudio)))
                throw CueException("$label track ${t.number} starts at frame $start, at or before track ${previous!!.number} at $floor; a file's tracks have to ascend")
            previous = t
            floor = if(!t.isAudio) maxOf(start, t.start ?: start) else start
            Cue.samples(start, rate)
        }
    }
    fun pieces(rate: Int, total: Long = -1): List<Piece> {
        if(tracks.none { it.isAudio }) throw CueException("$label names no audio track")
        val starts = starts(rate)
        if(tracks.size == 1) throw CueException("$label names one track, so there is nothing to cut")
        val all = mutableListOf<Piece>()
        if(starts[0] > 0) all += Piece(0, starts[0], -1, !precededByData)
        tracks.forEachIndexed { i, t -> all += Piece(starts[i], starts.getOrElse(i+1) { -1 }, i, t.isAudio) }
        val lastAudio = all.indexOfLast { it.audio }
        val out = mutableListOf<Piece>()
        all.forEachIndexed { i, p ->
            if(p.audio && total >= 0 && p.from >= total) {
                val what = if(p.track < 0) "a lead-in" else "track ${tracks[p.track].number}"
                throw CueException("$label names $what starting at sample ${p.from}, past the file's $total: this sheet does not describe this file")
            }
            if(!p.audio && (p.to == p.from || (total >= 0 && p.from >= total))) {
                out.lastOrNull()?.to = p.to
            } else {
                if(!p.audio && p.track >= 0 && i < lastAudio && !tracks[p.track].type.endsWith("/2352")) {
                    val t = tracks[p.track]
                    throw CueException("$label track ${t.number} is ${quote(clip(t.type))}, a data track occupying the file ahead of audio; only 2352-byte sectors keep the sheet's frames on samples past it")
                }
                out += p
            }
        }
        if(out.size < 2) throw CueException("$label divides into one piece, so there is nothing to cut")
        return out
    }
    fun cuts(rate: Int): List<Long> {
        val pieces = pieces(rate)
        tracks.firstOrNull { !it.isAudio }?.let {
            throw CueException("$label track ${it.number} is ${quote(clip(it.type))}, a data track, which a cut list cannot skip")
        }
        if(precededByData && !pieces[0].audio)
            throw CueException("$label follows a data track, whose pregap opens it; a cut list cannot skip that")
        return Cue.cutpoints(pieces).cuts
    }
}

/** Positions are file-relative; end is exclusive, -1 when file length is unknown. */
data class SampleTrack(val file: Int, val track: Track, val start: Long, val end: Long)
data class Sheet(
    var catalog: String = "", var title: String = "", var performer: String = "", var songwriter: String = "",
    val rems: MutableList<Rem> = mutableListOf(), val files: MutableList<File> = mutableListOf(),
    val warnings: MutableList<Warning> = mutableListOf(),
    val encoding: Encoding = Encoding.UTF8, val encodingWarning: String? = null
) {
    fun rem(key: String) = rems.firstOrNull { it.key.equals(key, true) }?.value
    fun singleFile(): File {
        if(files.size == 1) return files[0]
        if(files.isEmpty()) throw CueException("the sheet indexes no files")
        val audio = files.filter { f -> f.tracks.any { it.isAudio } }
        if(audio.size == 1) return audio[0]
        if(audio.isEmpty()) throw CueException("the sheet indexes ${files.size} files and none holds an audio track")
        throw CueException("the sheet indexes ${audio.size} files with audio tracks, so those tracks are already separate; there is nothing to cut")
    }
    fun trackList(rate: Int, fileLengths: List<Long> = List(files.size) { -1 }): List<SampleTrack> {
        require(fileLengths.size == files.size) { "one length per FILE is required" }
        return files.flatMapIndexed { fi, f ->
            val starts = f.starts(rate)
            f.tracks.mapIndexed { ti, t ->
                val end = starts.getOrElse(ti+1) { fileLengths[fi] }
                if(fileLengths[fi] >= 0 && (starts[ti] > fileLengths[fi] || end > fileLengths[fi]))
                    throw CueException("file ${quote(f.name)} track ${t.number} is past the file's ${fileLengths[fi]} samples")
                SampleTrack(fi, t, starts[ti], end)
            }
        }
    }
}

object Cue {
    const val FRAMES_PER_SECOND = 75
    /** Interior boundaries and indices of pieces that contain no audio. */
    fun cutpoints(pieces: List<Piece>) = Cutpoints(pieces.drop(1).map { it.from },
        pieces.indices.filter { !pieces[it].audio })
    fun samples(frames: Int, rate: Int): Long = frames.toLong() * rate / FRAMES_PER_SECOND
    fun parseTime(value: String): Int = try { time(value) } catch(e: IllegalArgumentException) { throw CueException(e.message!!) }
    fun parse(bytes: ByteArray): Sheet = parse(bytes, true)
    fun parseTolerant(bytes: ByteArray): Sheet = parse(bytes, false)

    private fun parse(bytes: ByteArray, strict: Boolean): Sheet {
        val decoded = decode(bytes)
        val sheet = Sheet(encoding = decoded.second, encodingWarning = if(decoded.second == Encoding.WINDOWS_1252)
            "Invalid UTF-8; decoded with the pinned Windows-1252 single-byte fallback" else null)
        var file: File? = null
        var track: Track? = null
        var culprit: Pair<Int, String>? = null
        fun unread(): String = culprit?.let { "line ${it.first} ${asciiQuote(it.second)} is not a command this reads" } ?: ""
        fun warn(line: Int, message: String) {
            if(strict) throw CueException("line $line: $message")
            if(sheet.warnings.size < 64) sheet.warnings += Warning(line, message)
        }
        fun outside(cmd: String) = "$cmd outside a TRACK" + if(culprit != null) "; ${unread()}" else ""
        val sep = if(decoded.first.contains('\n')) '\n' else '\r'
        decoded.first.split(sep).forEachIndexed { i, raw ->
            val lineNo = i+1
            val line = raw.trimEnd('\r')
            if(line.trim(' ', '\t').isEmpty()) return@forEachIndexed
            val command = token(line)
            if(!command.closed) { warn(lineNo, "${quote(clip(command.value))} is missing its closing quote"); return@forEachIndexed }
            val cmd = command.value.uppercase(Locale.ROOT)
            val rest = command.rest
            val t = track
            when(cmd) {
                "TITLE" -> if(t != null) t.title = operand(rest) else sheet.title = operand(rest)
                "PERFORMER" -> if(t != null) t.performer = operand(rest) else sheet.performer = operand(rest)
                "SONGWRITER" -> if(t != null) t.songwriter = operand(rest) else sheet.songwriter = operand(rest)
                "CATALOG" -> if(t == null) sheet.catalog = operand(rest)
                "ISRC" -> if(t != null) t.isrc = operand(rest)
                "CDTEXTFILE" -> Unit
                "FILE" -> {
                    val preceded = file?.tracks?.lastOrNull()?.isAudio == false
                    val parsed = fileOperands(rest)
                    if(parsed.third.isNotEmpty()) warn(lineNo, parsed.third)
                    file = File(parsed.first, parsed.second, preceded).also { sheet.files += it }
                    track = null; culprit = null
                }
                "TRACK" -> {
                    if(file == null) file = File("", "").also { sheet.files += it }
                    var number = token(rest)
                    var type = token(number.rest).value
                    if(type.isEmpty() && datatype(number.value)) { type = number.value; number = Token("", "", true) }
                    val n = digits(number.value)
                    when {
                        number.value.isEmpty() -> warn(lineNo, "TRACK takes a number")
                        n == null -> warn(lineNo, "TRACK number ${quote(clip(number.value))} is not a number")
                        !tokens(rest).second -> warn(lineNo, "TRACK is missing a closing quote")
                    }
                    track = Track(n ?: -1, type.uppercase(Locale.ROOT)).also { file!!.tracks += it }
                    culprit = null
                }
                "INDEX" -> {
                    if(t == null) { warn(lineNo, outside(cmd)); return@forEachIndexed }
                    val nTok = token(rest); val timeTok = token(nTok.rest)
                    fun tw(message: String) = warn(lineNo, "track ${t.number}: $message")
                    if(!tokens(rest).second) { tw("INDEX is missing a closing quote"); return@forEachIndexed }
                    if(timeTok.value.isEmpty()) { tw("INDEX takes a number and a time"); return@forEachIndexed }
                    val n = digits(nTok.value)
                    if(n == null || n > 99) { tw("INDEX number ${quote(clip(nTok.value))} is not 00 to 99"); return@forEachIndexed }
                    val frame = try { time(timeTok.value) } catch(e: IllegalArgumentException) { tw(e.message!!); return@forEachIndexed }
                    val previous = t.indexes.lastOrNull()
                    if(previous != null && n <= previous.number) {
                        val what = if(n == previous.number.toLong()) "INDEX %02d repeats".format(Locale.ROOT, n)
                            else "INDEX %02d follows INDEX %02d".format(Locale.ROOT, n, previous.number)
                        tw("$what; " + if(culprit == null) "the TRACK line between the two is missing or misspelled" else unread())
                        return@forEachIndexed
                    }
                    t.indexes += Index(n.toInt(), frame); culprit = null
                }
                "PREGAP", "POSTGAP" -> {
                    if(t == null) { warn(lineNo, outside(cmd)); return@forEachIndexed }
                    val tok = token(rest)
                    fun tw(message: String) = warn(lineNo, "track ${t.number}: $message")
                    if(!tokens(rest).second) { tw("$cmd is missing a closing quote"); return@forEachIndexed }
                    if(tok.value.isEmpty()) { tw("$cmd takes a time"); return@forEachIndexed }
                    val gap = try { time(tok.value) } catch(e: IllegalArgumentException) { tw(e.message!!); return@forEachIndexed }
                    if(cmd == "PREGAP") t.pregap = gap else t.postgap = gap
                }
                "FLAGS" -> if(t != null) {
                    if(!tokens(rest).second) warn(lineNo, "track ${t.number}: FLAGS is missing a closing quote")
                    else { var tail = rest; while(tail.trim(' ', '\t').isNotEmpty()) { val tok = token(tail); t.flags += tok.value; tail = tok.rest } }
                }
                "REM" -> {
                    val key = token(rest)
                    if(key.value.isNotEmpty()) (t?.rems ?: sheet.rems) += Rem(key.value, operand(key.rest))
                }
                else -> if(culprit == null && ("${command.value}$rest".uppercase(Locale.ROOT).contains("TRACK") ||
                    digits(token(rest).value)?.let { it <= 99 } == true)) culprit = lineNo to clip(command.value)
            }
        }
        return sheet
    }

    private fun decode(bytes: ByteArray): Pair<String, Encoding> {
        fun starts(vararg values: Int) = bytes.size >= values.size && values.indices.all { bytes[it].toInt() and 255 == values[it] }
        val encoding = when {
            starts(0xff, 0xfe) -> Encoding.UTF16_LE
            starts(0xfe, 0xff) -> Encoding.UTF16_BE
            starts(0xef, 0xbb, 0xbf) -> Encoding.UTF8_BOM
            else -> Encoding.UTF8
        }
        val offset = when(encoding) { Encoding.UTF16_LE, Encoding.UTF16_BE -> 2; Encoding.UTF8_BOM -> 3; else -> 0 }
        val charset = when(encoding) { Encoding.UTF16_LE -> Charsets.UTF_16LE; Encoding.UTF16_BE -> Charsets.UTF_16BE; else -> Charsets.UTF_8 }
        try {
            val text = charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, offset, bytes.size-offset)).toString()
            return text to encoding
        } catch(e: java.nio.charset.CharacterCodingException) {
            if(encoding == Encoding.UTF16_LE || encoding == Encoding.UTF16_BE) throw CueException("malformed $encoding text")
            val high = intArrayOf(0x20ac,0x81,0x201a,0x192,0x201e,0x2026,0x2020,0x2021,0x2c6,0x2030,0x160,0x2039,0x152,0x8d,0x17d,0x8f,
                0x90,0x2018,0x2019,0x201c,0x201d,0x2022,0x2013,0x2014,0x2dc,0x2122,0x161,0x203a,0x153,0x9d,0x17e,0x178)
            return buildString { for(i in offset until bytes.size) { val b = bytes[i].toInt() and 255; append((if(b in 128..159) high[b-128] else b).toChar()) } } to Encoding.WINDOWS_1252
        }
    }
}

private data class Token(val value: String, val rest: String, val closed: Boolean)
private fun token(value: String): Token {
    val s = value.trimStart(' ', '\t')
    if(s.isEmpty()) return Token("", "", true)
    if(s[0] == '"') {
        val end = s.indexOf('"', 1)
        return if(end < 0) Token(s.substring(1), "", false) else Token(s.substring(1,end), s.substring(end+1), true)
    }
    val end = s.indexOfFirst { it == ' ' || it == '\t' }
    return if(end < 0) Token(s, "", true) else Token(s.substring(0,end), s.substring(end), true)
}
private fun tokens(value: String): Pair<Int, Boolean> {
    var rest = value.trimStart(' ', '\t'); var count = 0; var closed = true
    while(rest.isNotEmpty()) { val t = token(rest); count++; closed = closed && t.closed; rest = t.rest.trimStart(' ', '\t') }
    return count to closed
}
private fun operand(value: String): String {
    val s = value.trim(' ', '\t')
    return when { s.length >= 2 && s.first() == '"' && s.last() == '"' -> s.substring(1,s.length-1)
        s.startsWith('"') -> token(s).value; else -> s }
}
private fun fileOperands(value: String): Triple<String, String, String> {
    val s = value.trim(' ', '\t')
    if(s.isEmpty()) return Triple("", "", "FILE takes a name")
    if(!s.startsWith('"')) {
        val cut = s.indexOfLast { it == ' ' || it == '\t' }
        return if(cut < 0 || s.substring(cut+1).contains('.')) Triple(s, "", "")
            else Triple(s.substring(0,cut).trimEnd(' ', '\t'), s.substring(cut+1).uppercase(Locale.ROOT), "")
    }
    val first = token(s)
    if(!first.closed) return Triple(first.value, "", "FILE name is missing its closing quote")
    var name = first.value; var tail = first.rest
    if(tokens(tail).first > 1) {
        val last = s.lastIndexOf('"')
        if(last > s.length-tail.length-1 && token(s.substring(last+1)).value.isNotEmpty()) { name = s.substring(1,last); tail = s.substring(last+1) }
    }
    return Triple(name, token(tail).value.uppercase(Locale.ROOT), if(tokens(tail).second) "" else "FILE is missing a closing quote")
}
private fun digits(value: String): Long? = if(value.isNotEmpty() && value.all { it in '0'..'9' }) value.toLongOrNull() else null
private fun datatype(value: String): Boolean = value.uppercase(Locale.ROOT).let { it == "AUDIO" || it == "CDG" || it.startsWith("MODE") || it.startsWith("CDI") }
private fun time(value: String): Int {
    val q = quote(clip(value)); val p = value.split(':', limit=4)
    if(p.size != 3 || p.any { digits(it) == null }) throw IllegalArgumentException("time $q is not MM:SS:FF")
    val n = p.map { digits(it)!! }
    when {
        n[0] > 6000 -> throw IllegalArgumentException("time $q has ${n[0]} minutes; a sheet addresses at most 6000")
        n[1] > 59 -> throw IllegalArgumentException("time $q has ${n[1]} seconds; a minute holds 60")
        n[2] > 74 -> throw IllegalArgumentException("time $q has ${n[2]} frames; a second holds 75")
    }
    return ((n[0]*60+n[1])*75+n[2]).toInt()
}
private fun clip(value: String, max: Int = 32): String {
    val bytes = value.toByteArray(Charsets.UTF_8)
    if(bytes.size <= max) return value
    var end = max
    while(end > 0 && bytes[end].toInt() and 0xc0 == 0x80) end--
    return String(bytes, 0, end, Charsets.UTF_8)+"..."
}
private val nonPrintingCategories = setOf(Character.CONTROL.toInt(), Character.FORMAT.toInt(),
    Character.SURROGATE.toInt(), Character.PRIVATE_USE.toInt(), Character.UNASSIGNED.toInt(),
    Character.SPACE_SEPARATOR.toInt(), Character.LINE_SEPARATOR.toInt(), Character.PARAGRAPH_SEPARATOR.toInt())
private fun quote(value: String): String = buildString {
    append('"')
    value.codePoints().forEach { c ->
        when(c) {
            92 -> append("\\\\"); 34 -> append("\\\"")
            7 -> append("\\a"); 8 -> append("\\b"); 9 -> append("\\t"); 10 -> append("\\n")
            11 -> append("\\v"); 12 -> append("\\f"); 13 -> append("\\r")
            else -> when {
                c < 32 || c == 127 -> append("\\x%02x".format(Locale.ROOT,c))
                c != 32 && Character.getType(c) in nonPrintingCategories ->
                    append(if(c <= 65535) "\\u%04x".format(Locale.ROOT,c) else "\\U%08x".format(Locale.ROOT,c))
                else -> appendCodePoint(c)
            }
        }
    }
    append('"')
}
private fun asciiQuote(value: String): String = buildString {
    val quoted = quote(value)
    quoted.codePoints().forEach { c -> if(c > 127) append(if(c <= 65535) "\\u%04x".format(Locale.ROOT,c) else "\\U%08x".format(Locale.ROOT,c)) else append(c.toChar()) }
}
