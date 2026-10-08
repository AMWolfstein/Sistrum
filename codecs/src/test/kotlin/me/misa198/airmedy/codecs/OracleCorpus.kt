package me.misa198.airmedy.codecs

import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import me.misa198.airmedy.codecs.audio.Buffer

internal data class Fixture(val fields: Map<String,String>, val source: File) {
    override fun toString() = name
    val name get() = fields.getValue("file")
    val status get() = fields.getValue("status")
    fun value(name: String) = fields.getValue(name)
}
internal object OracleCorpus {
    const val PIN="b7857aff88820ad37936026421d1641e64611dbe"
    val rows: List<Fixture> by lazy { allRows.filter { it.name.endsWith(".wv") || it.name.endsWith(".wvc") } }
    val allRows: List<Fixture> by lazy {
        val fixtureFile=File(checkNotNull(System.getProperty("waxflow.fixtures")) { "Missing waxflow.fixtures path" })
        check(fixtureFile.isFile) { "Oracle fixtures missing: $fixtureFile; refusing to skip parity tests" }
        val root=File(checkNotNull(System.getProperty("waxflow.corpus")) { "Missing waxflow.corpus path" })
        check(root.isDirectory) { "Oracle corpus missing: $root; set WAXFLOW_CORPUS or -PwaxflowCorpus" }
        val lines=fixtureFile.readLines()
        check(lines.contains("# waxflow_fork_commit\t$PIN")) { "Oracle pin does not match port" }
        val data=lines.filterNot { it.startsWith("#") }
        val columns=data.first().split('\t')
        val result=data.drop(1).filter { it.isNotEmpty() }.map {
            val values=it.split('\t')
            check(values.size==columns.size) { "Malformed fixture row: $it" }
            val fields=columns.zip(values).toMap()
            Fixture(fields,File(root,fields.getValue("file")))
        }
        check(result.isNotEmpty()) { "No oracle entries" }
        result.forEach {
            check(it.source.isFile) { "Corpus file missing: ${it.source}" }
            val hash=MessageDigest.getInstance("SHA-256").digest(it.source.readBytes()).hex()
            check(hash==it.value("file_sha256")) { "Corpus drift: ${it.name}" }
        }
        result
    }
}
internal fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 255) }
/** Same packed little-endian bytes as the oracle WAV data, including unsigned 8-bit PCM. */
internal fun pcmBytes(buffer: Buffer): ByteArray {
    val bytes=ByteArray(buffer.frames*buffer.channels*(buffer.bits/8))
    var p=0; var i=0
    while (i<buffer.frames*buffer.channels) {
        val sample=buffer.samples[i++]+if (buffer.bits==8) 128 else 0
        var shift=0
        while (shift<buffer.bits) { bytes[p++]=(sample ushr shift).toByte(); shift+=8 }
    }
    return bytes
}
internal fun Fixture.byteBuffer(): ByteBuffer = ByteBuffer.wrap(source.readBytes())
