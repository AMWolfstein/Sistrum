// SPDX-License-Identifier: GPL-3.0-or-later
// Adapted from WaxLabel cfbb45d13135a76f5df6781fdeec75f5cfe63bf9, internal/wav/, internal/aiff/, internal/iff/.
// Copyright (c) 2026 Cole Springer, MIT; see metadata/THIRD-PARTY-NOTICES.
package com.amwolfstein.sistrum.metadata

/** Framing for embedded ID3. Extended RIFF (RF64/bext) is reserved for step 5. */
internal object Iff {
    private data class Chunk(val id: String, val offset: Long, val size: Long, val truncated: Boolean)
    private data class Text(val key: String?, val id: String, val data: ByteArray) {
        val value get() = data.toString(Charsets.UTF_8)
    }
    private val infoKeys = mapOf("INAM" to "TITLE", "IART" to "ARTIST", "IPRD" to "ALBUM", "ICRD" to "RECORDINGDATE",
        "IGNR" to "GENRE", "ICMT" to "COMMENT", "ICOP" to "COPYRIGHT", "IPRT" to "TRACKNUMBER", "ITRK" to "TRACKNUMBER",
        "ISFT" to "ENCODER", "ITCH" to "ENCODEDBY", "IENG" to "ENGINEER")
    private val aiffKeys = mapOf("NAME" to "TITLE", "AUTH" to "ARTIST", "(c) " to "COPYRIGHT", "ANNO" to "COMMENT")
    fun read(input: Input, b: Builder, aiff: Boolean) {
        val h = input.bytes(0,12)
        val form = h.latin(8,4)
        b.format = if(aiff) "AIFF" else "WAV"
        val declared = if(aiff) h.be(4,4) else h.le(4,4)
        var end = declared+8
        if (end < 12 || end > input.length) end = input.length
        val chunks = mutableListOf<Chunk>()
        var pos = 12L
        while (end-pos >= 8) {
            input.cap(chunks.size)
            val ch = input.bytes(pos,8)
            val id = ch.latin(0,4)
            val size = if(aiff) ch.be(4,4) else ch.le(4,4)
            val available = input.length-pos-8
            val actual = minOf(size,available)
            chunks += Chunk(id,pos+8,actual,size > available)
            val next = pos+8+actual+(actual and 1)
            if(next <= pos) break
            pos = next
        }
        val audioID = if(aiff) "SSND" else "data"
        val audio = chunks.firstOrNull { it.id == audioID }
        var audioBytes = audio?.size ?: 0
        if(aiff && audio != null && audio.size >= 8) {
            val offset = input.bytes(audio.offset,4).be(0,4)
            audioBytes = maxOf(0,audio.size-8-offset)
        }
        val format = chunks.firstOrNull { it.id == if(aiff) "COMM" else "fmt " }
        b.properties = if(aiff) aiffProperties(input,format,audioBytes,form) else wavProperties(input,format,audioBytes)
        if (audio?.truncated == true) b.warn("truncated-audio","${if(aiff) "the SSND chunk" else "the data chunk"} declares more bytes than the file holds; file may be truncated")
        val nativeValues = mutableListOf<Text>()
        val descriptions = mutableMapOf<Chunk,List<NativeBlock>>()
        for (chunk in chunks) {
            if (aiff && chunk.id in aiffKeys) {
                val raw = input.bytes(chunk.offset,checkedSize(chunk.size,input))
                val clean = raw.copyOf(raw.indexOf(0).let { if(it < 0) raw.size else it })
                nativeValues += Text(aiffKeys[chunk.id],chunk.id,clean)
                descriptions[chunk] = listOf(NativeBlock(chunk.id,chunk.size,"text"))
            } else if (!aiff && chunk.id == "LIST" && chunk.size >= 4 && input.bytes(chunk.offset,4).latin() == "INFO") {
                val items = mutableListOf<Text>()
                var p = 4L
                while (chunk.size-p >= 8) {
                    input.cap(items.size)
                    val header = input.bytes(chunk.offset+p,8)
                    val n = header.le(4,4)
                    if (n > chunk.size-p-8) break
                    val raw = input.bytes(chunk.offset+p+8,checkedSize(n,input))
                    val clean = raw.copyOf(raw.indexOf(0).let { if(it < 0) raw.size else it })
                    val id = header.latin(0,4)
                    items += Text(infoKeys[id],id,clean)
                    p += 8+n+(n and 1)
                }
                nativeValues += items
                descriptions[chunk] = listOf(NativeBlock("LIST/INFO",chunk.size,"${items.size} items")) + items.map { NativeBlock("  ${it.id}",it.data.size.toLong()) }
            }
        }
        var id3Chunk: Chunk? = null
        var tag: Id3Tag? = null
        val candidates = chunks.filter { it.id == "ID3 " || it.id == "id3 " }
        for (chunk in candidates) {
            try { tag = Id3.parse(input,chunk.offset,chunk.size); id3Chunk = chunk; break }
            catch (e: ReadFailure) { if (e.kind == ErrorClass.SIZE_TOO_LARGE || e.kind == ErrorClass.IO) throw e }
        }
        if (candidates.size > 1 && tag != null) b.warn("duplicate-tag-block","more than one ID3 chunk; the first that parses is authoritative and the rest are dropped on rewrite")
        var numeric = false
        if (tag != null) {
            numeric = Id3.project(tag,b,input.options.pictures)
            descriptions[id3Chunk!!] = listOf(NativeBlock(if(aiff) "ID3 chunk" else "id3 chunk",id3Chunk.size,"ID3v2.${tag.version}, ${tag.note}"))+tag.blocks()
        }
        val authoritativeKeys = b.tags.keys.toSet()
        for (t in nativeValues) {
            val key = t.key ?: continue
            if (t.value.isEmpty()) continue
            if (key !in authoritativeKeys) {
                val values = b.tags.getOrPut(key) { mutableListOf() }
                if (key !in listOf("TRACKNUMBER","TRACKTOTAL","DISCNUMBER","DISCTOTAL") || values.isEmpty()) values += t.value
            }
        }
        for(t in nativeValues) {
            val key = t.key ?: continue
            if(t.value.isNotEmpty()) b.families += FamilyValue(key,if(aiff) "aiff" else "riff",listOf(t.value),b.tags[key]?.any { it.trim().equals(t.value.trim(),true) } ?: true)
        }
        if(numeric) b.warn("numeric-genre","a numeric genre reference was resolved to a name")
        if(!aiff) nativeValues.filter { it.id == "ISFT" && transcoder(it.value) }.forEach { b.warn("inherited-encoder","inherited encoder stamp: ${snippet(it.value)}") }
        if(tag != null) b.warnings += Id3.encoderWarnings(tag)
        for(chunk in chunks) {
            b.native += descriptions[chunk] ?: listOf(NativeBlock(chunk.id,chunk.size,when {
                chunk == audio -> if(aiff) "sound data" else rawCodec(b.properties)
                aiff && chunk == format -> rawCodec(b.properties)
                else -> "preserved"
            }))
        }
        if (input.length > end) b.warn("trailing-bytes","${input.length-end} byte(s) after the ${if(aiff) "FORM" else "RIFF"} container belong to no chunk or page; preserved verbatim")
        finish(b,audioBytes)
    }
    private fun checkedSize(size: Long,input: Input): Int {
        if(size > input.options.maxTagBytes) throw ReadFailure(ErrorClass.SIZE_TOO_LARGE,"metadata chunk allocation limit")
        return size.toInt()
    }
    private fun rawCodec(p: AudioProperties) = p.profile.ifEmpty { p.codec }
    private fun wavProperties(input: Input, chunk: Chunk?, bytes: Long): AudioProperties {
        if(chunk == null || chunk.size < 16) return AudioProperties(container="WAV",codec="WAVE format 0x0000")
        val f = input.bytes(chunk.offset,16)
        val tag = f.le(0,2).toInt()
        val channels = f.le(2,2).toInt()
        val rate = f.le(4,4).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val byteRate = f.le(8,4)
        val align = f.le(12,2)
        val bits = f.le(14,2).toInt()
        val codec = when(tag) { 1 -> "PCM"; 3 -> "IEEE float"; 6 -> "A-law"; 7 -> "mu-law"; 65534 -> "WAVE extensible"; else -> "WAVE format 0x%04X".format(tag) }
        val duration = if(byteRate > 0) (bytes.toDouble()/byteRate*1e9).toLong() else 0
        return AudioProperties("WAV",codec,sampleRate=rate,channels=channels,bitsPerSample=bits,
            totalSamples=if(align > 0) bytes/align else 0,durationNs=duration,bitrate=(byteRate*8).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }
    private fun aiffProperties(input: Input, chunk: Chunk?, bytes: Long, form: String): AudioProperties {
        if(chunk == null || chunk.size < 18) return AudioProperties(container=form,codec="PCM")
        val f = input.bytes(chunk.offset,minOf(chunk.size,22).toInt())
        val channels = f.be(0,2).toInt()
        val declaredSamples = f.be(2,4)
        val bits = f.be(6,2).toInt()
        val exponent = (f.u(8) and 127 shl 8) or f.u(9)
        var mantissa = 0.0
        for(i in 10..17) mantissa = mantissa*256+f.u(i)
        val r = Math.scalb(mantissa,exponent-16383-63) * if(f.u(8) and 128 != 0) -1 else 1
        val rate = if(r > 0 && r < 0xffffffffL && exponent != 32767) Math.round(r).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() else 0
        val raw = if(form == "AIFC" && f.size >= 22) f.latin(18,4) else "PCM"
        val codec = when(raw) { "NONE", "sowt", "twos", "PCM" -> "PCM"; "fl32", "FL32", "fl64", "FL64" -> "IEEE float"; else -> raw }
        val profile = if(codec != raw) raw else ""
        val align = channels.toLong()*((bits+7)/8)
        val samples = if(align > 0) minOf(declaredSamples,bytes/align) else declaredSamples
        return AudioProperties(form,codec,profile,rate,channels,bits,samples,samplesToNs(samples,rate),
            (rate.toLong()*align*8).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }
}
