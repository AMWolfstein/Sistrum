// SPDX-License-Identifier: GPL-3.0-or-later
package com.amwolfstein.sistrum.codecs

import java.io.File as DiskFile
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.AfterClass
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import com.amwolfstein.sistrum.codecs.cue.*

@RunWith(Parameterized::class)
class CueOracleTest(private val name: String, private val bytes: ByteArray) {
    companion object {
        private lateinit var work: DiskFile
        private lateinit var binary: DiskFile
        @JvmStatic @Parameterized.Parameters(name="{0}") fun cases(): List<Array<Any>> {
            val basic = """
                REM GENRE "Rock"
                REM DATE 2026
                REM REPLAYGAIN_ALBUM_GAIN "-4.5 dB"
                REM REPLAYGAIN_ALBUM_PEAK 0.98
                CATALOG 1234567890123
                TITLE "An album"
                PERFORMER "An artist"
                SONGWRITER "A writer"
                FILE "owned audio.wav" WAVE
                  TRACK 01 AUDIO
                    TITLE "First song"
                    PERFORMER "Track artist"
                    SONGWRITER "Track writer"
                    ISRC XXABC2600001
                    FLAGS DCP PRE
                    REM REPLAYGAIN_TRACK_GAIN "-3.1 dB"
                    REM REPLAYGAIN_TRACK_PEAK 0.9
                    INDEX 00 00:00:00
                    INDEX 01 00:02:00
                    INDEX 02 00:03:07
                    POSTGAP 00:00:15
                  TRACK 02 AUDIO
                    TITLE "Second song"
                    PREGAP 00:01:00
                    INDEX 01 03:04:05
            """.trimIndent()
            val cases = linkedMapOf(
                "metadata" to basic,
                "crlf" to basic.replace("\n", "\r\n"),
                "classic-cr" to basic.replace("\n", "\r"),
                "multiple-files" to "FILE a.wav WAVE\nTRACK 1 AUDIO\nINDEX 01 00:00:00\nFILE b.flac WAVE\nREM DATE 2025\nTRACK 2 AUDIO\nINDEX 01 00:00:00\nTRACK 3 AUDIO\nINDEX 01 01:00:00",
                "data-file-then-audio" to "FILE data.bin BINARY\nTRACK 1 MODE1/2352\nINDEX 01 00:02:00\nFILE audio.wav WAVE\nTRACK 2 AUDIO\nINDEX 01 00:02:00\nTRACK 3 AUDIO\nINDEX 01 01:00:00",
                "audio-then-data" to "FILE audio.wav WAVE\nTRACK 1 AUDIO\nINDEX 01 00:00:00\nTRACK 2 MODE1/2352\nINDEX 00 01:00:00\nINDEX 01 01:02:00",
                "raw-data" to "FILE mixed.bin BINARY\nTRACK 1 MODE1/2352\nINDEX 01 00:02:00\nTRACK 2 AUDIO\nINDEX 01 00:02:00\nTRACK 3 AUDIO\nINDEX 01 01:00:00",
                "cooked-data" to "FILE mixed.bin BINARY\nTRACK 1 MODE1/2048\nINDEX 01 00:02:00\nTRACK 2 AUDIO\nINDEX 01 00:02:00",
                "empty-data-span" to "TRACK 1 MODE1/2352\nINDEX 01 00:00:00\nTRACK 2 AUDIO\nINDEX 01 00:00:00\nTRACK 3 CDG\nINDEX 01 01:00:00",
                "missing-index-01" to "FILE a.wav WAVE\nTRACK 1 AUDIO\nTITLE first\nTRACK 2 AUDIO\nINDEX 01 01:00:00",
                "data-missing-index" to "TRACK 1 AUDIO\nINDEX 01 00:00:00\nTRACK 2 MODE1/2352",
                "implied-file" to "TRACK 2 AUDIO\nINDEX 01 00:00:00\nTRACK 4 AUDIO\nINDEX 01 100:01:74",
                "unquoted-file-and-inner-quotes" to "FILE 12 inch single.flac WAVE\nTRACK 1 AUDIO\nTITLE \"The \"Best\" Of\"\nINDEX 01 00:00:00\nFILE \"12\" Single.flac\" WAVE\nTRACK 2 AUDIO\nINDEX 01 00:00:00",
                "duplicate-index" to "TRACK 1 AUDIO\nINDEX 01 00:00:00\nTRAK 2 AUDIO\nINDEX 01 01:00:00",
                "index-outside-track" to "TRAK 1 AUDIO\nINDEX 01 00:00:00",
                "unreadable-track" to "FILE a.wav WAVE\nTRACK AUDIO\nINDEX 01 00:00:00\nTRACK -2 AUDIO\nINDEX 01 01:00:00",
                "bad-times" to "TRACK 1 AUDIO\nINDEX 01 00:60:00\nPREGAP 00:00:75\nPOSTGAP 6001:00:00\nINDEX 02 +0:00:00",
                "bad-quote" to "FILE \"unterminated\nTRACK 1 AUDIO\nINDEX 01 00:00:00",
                "nonascending" to "TRACK 1 AUDIO\nINDEX 01 01:00:00\nTRACK 2 AUDIO\nINDEX 01 00:00:00",
                "large-numbers" to "TRACK 2147483648 AUDIO\nINDEX 01 2049638230412173:00:00\nINDEX 02 00:00:00",
                "escaped-filename" to "FILE \"C:\\\\rip\\\\audio.wav\" WAVE\nTRACK 1 AUDIO\nINDEX 01 00:00:00",
                "unicode-error-operand" to "TRACK 曲 AUDIO\nINDEX 01 00:00:00",
                "unicode-quoted-file-error" to "FILE \"a\u0081\u00a0\u200b.wav\" WAVE\nTRACK 1 AUDIO",
                "empty" to "",
                "single-track" to "FILE a.wav WAVE\nTRACK 1 AUDIO\nINDEX 01 00:00:00",
                "unknown-command" to "TITLE Album\nREM DATE\nREM date 2026\nFOOBAR abc\nTRACK 1 UNKNOWN\nINDEX 01 00:00:00\nTRACK 2 AUDIO\nINDEX 01 00:00:01"
            )
            val result = cases.map { arrayOf<Any>(it.key, it.value.toByteArray()) }.toMutableList()
            result += arrayOf<Any>("utf8-bom", byteArrayOf(0xef.toByte(),0xbb.toByte(),0xbf.toByte())+basic.toByteArray())
            result += arrayOf<Any>("utf8-unicode", basic.replace("An album", "موسيقى Café").toByteArray())
            result += arrayOf<Any>("legacy-cp1252", basic.replace("An album", "Café – music").toByteArray(charset("windows-1252")))
            result += arrayOf<Any>("fallback-holes", "TITLE \"".toByteArray()+byteArrayOf(0x81.toByte(),0x8d.toByte(),0x8f.toByte(),0x90.toByte(),0x9d.toByte())+"\"".toByteArray())
            result += arrayOf<Any>("warning-cap", ("INDEX 01 00:00:00\n".repeat(70)).toByteArray())
            return result
        }
        private fun command(args: List<String>, cwd: DiskFile? = null): String {
            val output = DiskFile(work, "command-output")
            val builder = ProcessBuilder(args).redirectErrorStream(true).redirectOutput(output)
            if(cwd != null) builder.directory(cwd)
            builder.environment()["GOCACHE"] = DiskFile(work,"go-cache").path
            builder.environment()["GOTOOLCHAIN"] = "local"
            builder.environment()["GOPROXY"] = "off"
            val process = builder.start()
            if(!process.waitFor(120, TimeUnit.SECONDS)) { process.destroyForcibly(); error("CUE oracle command timed out") }
            val text = output.readText()
            check(process.exitValue() == 0) { "CUE oracle command failed: $text" }
            return text
        }
        @JvmStatic @BeforeClass fun buildOracle() {
            work = Files.createTempDirectory("sistrum-owned-cue-").toFile()
            val root = DiskFile(System.getProperty("oracle.owned")).parentFile
            val source = DiskFile(root,"oracle-work/src")
            assertEquals("b7857aff88820ad37936026421d1641e64611dbe", command(listOf("git","rev-parse","HEAD"),source).trim())
            for((name, expected) in mapOf("cue.go" to "2b400dd41e2ff77753dc4c6add6536b41eca73abc11321007a457c7d75bde517", "decode.go" to "90cc2a55dbfc91d8242c5fc280dc14b351245394dfe1c3e0d7a34ef9849b8da1")) {
                val actual = MessageDigest.getInstance("SHA-256").digest(DiskFile(source,"cue/$name").readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) }
                assertEquals(expected, actual)
            }
            binary = DiskFile(work,"cue-oracle")
            command(listOf("go","build","-o",binary.path,DiskFile("tools/cue-oracle.go").absolutePath),source)
        }
        @JvmStatic @AfterClass fun cleanup() { if(::work.isInitialized) work.deleteRecursively() }
    }

    @Test fun matchesPinnedGoMetadataBoundariesAndRefusals() {
        val input = DiskFile(work,"$name.cue").apply { writeBytes(bytes) }
        for(rate in listOf(44100,48000,32000)) for(total in listOf(-1L, 44100L*360, 44100L)) {
            val expected = command(listOf(binary.path,input.path,rate.toString(),total.toString()))
            assertEquals("$name rate=$rate total=$total", expected, serialize(bytes,rate,total))
        }
    }
    private fun serialize(bytes: ByteArray, rate: Int, total: Long): String = buildString {
        fun row(vararg values: Any) { append(values.joinToString("\t") { Base64.getEncoder().encodeToString(it.toString().toByteArray()) }); append('\n') }
        val s = try { Cue.parse(bytes).also { row("OK") } } catch(e: CueException) { row("ERROR",e.message!!); Cue.parseTolerant(bytes) }
        row("SHEET",s.catalog,s.title,s.performer)
        s.rems.forEach { row("REM",it.key,it.value) }
        s.warnings.forEach { row("WARNING",it.line,it.message) }
        s.files.forEach { f ->
            row("FILE",f.name,f.type,f.precededByData)
            f.tracks.forEach { t ->
                row("TRACK",t.number,t.type,t.title,t.performer,t.isrc,t.pregap,t.postgap,t.isAudio)
                t.indexes.forEach { row("INDEX",it.number,it.frame) }
                t.rems.forEach { row("TREM",it.key,it.value) }
                t.flags.forEach { row("FLAG",it) }
            }
            try { f.starts(rate).forEach { row("START",it) } } catch(e: CueException) { row("STARTERROR",e.message!!) }
            try {
                val pieces = f.pieces(rate,total)
                pieces.forEach { row("PIECE",it.from,it.to,it.track,it.audio) }
                val cuts = Cue.cutpoints(pieces)
                cuts.cuts.forEach { row("PCUT",it) }; cuts.skip.forEach { row("SKIP",it) }
            } catch(e: CueException) { row("PIECEERROR",e.message!!) }
            try { f.cuts(rate).forEach { row("CUT",it) } } catch(e: CueException) { row("CUTERROR",e.message!!) }
        }
        try { row("SINGLE",s.files.indexOf(s.singleFile())) } catch(e: CueException) { row("SINGLEERROR",e.message!!) }
    }
}
