// SPDX-License-Identifier: GPL-3.0-or-later
package com.amwolfstein.sistrum.analysis
import com.google.gson.JsonParser
import com.google.gson.JsonObject
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
internal data class Wave(val pcm: FloatArray, val channels: Int, val rate: Int)
internal object OracleSupport {
    val repo = File(System.getProperty("analysis.repo"))
    val cache = File(System.getProperty("analysis.cache"))
    fun rows() = JsonParser.parseString(File(repo,"docs/analysis/oracle.json").readText()).asJsonArray.map { it.asJsonObject }
    fun wave(path: String): Wave {
        val bytes = File(cache,path).readBytes()
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        check(String(bytes,0,4) == "RIFF" && String(bytes,8,4) == "WAVE")
        var offset = 12;var channels = 0;var rate = 0
        while(offset+8<=bytes.size) {
            val tag=String(bytes,offset,4);val size=b.getInt(offset+4)
            if(tag == "fmt ") { check(b.getShort(offset+8).toInt()==3);channels=b.getShort(offset+10).toInt();rate=b.getInt(offset+12) }
            if(tag == "data") { check(channels>0);b.position(offset+8);val pcm=FloatArray(size/4);b.asFloatBuffer().get(pcm);return Wave(pcm,channels,rate) }
            offset += 8+size+(size and 1)
        }
        error("Missing PCM data: $path")
    }
    fun analyze(path: String, groups: Int = PcmAnalyzer.DYNAMICS or PcmAnalyzer.LOUDNESS): AnalysisResult {
        RandomAccessFile(File(cache,path), "r").use { file ->
            // The corpus writer canonicalizes every oracle WAV to a 44-byte float header.
            val header=ByteArray(44);file.readFully(header)
            val h=ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val channels=h.getShort(22).toInt();val rate=h.getInt(24)
            val analyzer=PcmAnalyzer(channels,rate,groups)
            val bytes=ByteArray(4096*channels*4);val floats=FloatArray(4096*channels)
            val buffer=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
            var remaining=h.getInt(40).toLong() and 0xffffffffL
            while(remaining>0) {
                val n=minOf(bytes.size.toLong(),remaining).toInt();file.readFully(bytes,0,n)
                buffer.position(0);buffer.get(floats,0,n/4)
                analyzer.feed(if(n==bytes.size) floats else floats.copyOf(n/4),channels,rate)
                remaining-=n
            }
            return analyzer.finish()
        }
    }
    fun number(row: JsonObject,key: String): Double {
        val s=row.get(key).asString
        return when(s) { "-Infinity" -> Double.NEGATIVE_INFINITY; "+Infinity" -> Double.POSITIVE_INFINITY;"NaN" -> Double.NaN;else -> s.toDouble() }
    }
}
