// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxLabel cfbb45d13135a76f5df6781fdeec75f5cfe63bf9, parse.go and internal/mp3/.
// Copyright (c) 2026 Cole Springer, MIT; see metadata/THIRD-PARTY-NOTICES.
package com.amwolfstein.sistrum.metadata

import com.amwolfstein.sistrum.codecs.container.RandomAccessSource
import java.io.IOException

/** Read-only metadata entry point. The source belongs to the caller. */
object Metadata {
    fun read(source: RandomAccessSource, options: ReadOptions = ReadOptions()): Document {
        val b = Builder()
        try {
            if (options.maxTagBytes < 10 || options.maxElements <= 0 || options.maxReadBytes < 0)
                throw ReadFailure(ErrorClass.INVALID_DATA,"invalid read limits")
            val input = Input(source,options)
            if (input.length == 0L) throw ReadFailure(ErrorClass.UNSUPPORTED_FORMAT,"empty file")
            if (input.length < 3) throw ReadFailure(ErrorClass.UNSUPPORTED_FORMAT,"file shorter than a format signature")
            var magic = input.bytes(0,minOf(input.length,4).toInt())
            if (magic.size == 4 && magic.latin() in listOf("RIFF","FORM") && input.length >= 12)
                magic = input.bytes(0,12)
            when {
                magic.size >= 12 && magic.latin(0,4) == "RIFF" && magic.latin(8,4) == "WAVE" -> Iff.read(input,b,false)
                magic.size >= 12 && magic.latin(0,4) == "FORM" && magic.latin(8,4) in listOf("AIFF","AIFC") -> Iff.read(input,b,true)
                magic.size >= 3 && magic.latin(0,3) == "ID3" || Mpeg.header(magic) != null || options.formatHint?.lowercase() == "mp3" -> mp3(input,b)
                else -> throw ReadFailure(ErrorClass.UNSUPPORTED_FORMAT,"unrecognized or not-yet-ported container")
            }
            return b.document()
        } catch (e: ReadFailure) { return b.document(MetadataError(e.kind,e.message.orEmpty())) }
        catch (e: IOException) { return b.document(MetadataError(ErrorClass.IO,e.message.orEmpty())) }
        catch (e: RuntimeException) {
            // Malformed data and source-contract failures cannot escape the public read.
            return b.document(MetadataError(ErrorClass.INVALID_DATA,e.message ?: e.javaClass.simpleName))
        }
    }
    private fun mp3(input: Input, b: Builder) {
        b.format = "MP3"
        var start = 0L
        var end = input.length
        var tag: Id3Tag? = null
        var probeStart = 0L
        if (input.length >= 10) {
            val size = Id3.tagSize(input.bytes(0,10))
            if (size != null) {
                if (size > input.length) {
                    b.warn("malformed-tag-entry","the leading ID3v2 tag declares $size bytes but the file holds ${input.length}; the tag could not be read")
                    probeStart = 10 // one header probe past the known damaged tag header
                } else { tag = Id3.parse(input,0,size); start = size; probeStart = size }
            }
        }
        while (end-start >= 128) {
            // Trailer probe is metadata detection; no audio body is scanned.
            if (input.bytes(end-128,3).latin() != "TAG") break
            val trailer = input.bytes(end-128,128)
            if (!looksLikeV1(trailer)) break
            input.cap(((input.length-end)/128).toInt())
            end -= 128
        }
        val trailerSize = input.length-end
        if (trailerSize > 0) b.warn("trailing-id3v1",if (trailerSize == 128L) "legacy ID3v1 tag follows the audio; preserved"
            else "${trailerSize/128} stacked legacy ID3v1 tags follow the audio; preserved")
        b.properties = Mpeg.properties(input,start,end,b,probeStart).copy(paddingBytes=tag?.padding?.toLong() ?: 0)
        if (tag != null) {
            b.native += NativeBlock("ID3v2.${tag.version}",start,tag.note)
            b.native += tag.blocks()
            val warningPosition = b.warnings.size
            if (Id3.project(tag,b,input.options.pictures)) b.warnings.add(warningPosition,Warning("numeric-genre","a numeric genre reference was resolved to a name"))
            b.warnings += Id3.encoderWarnings(tag)
        }
        b.native += NativeBlock("MPEG audio",end-start,b.properties.profile.ifEmpty { b.properties.codec })
        if (trailerSize > 0) {
            b.native += NativeBlock("ID3v1 (trailing)",trailerSize,"preserved")
            if (trailerSize == 128L) v1Families(input.bytes(end,128),b)
        }
        finish(b,end-start)
    }
}
internal fun finish(b: Builder, audioBytes: Long) {
    val duration = b.properties.durationMs
    for (i in b.chapters.indices) if (duration > 0 && b.chapters[i].endMs > duration) b.chapters[i] = b.chapters[i].copy(endMs=0)
    if (audioBytes <= 0) {
        b.warnings.removeAll { it.code == "truncated-audio" }
        b.warn("no-audio","no audio essence found; file may be tag-only or truncated")
    }
}
private fun looksLikeV1(b: ByteArray): Boolean {
    if (b.size != 128 || b.latin(0,3) != "TAG") return false
    if ((93..96).any { b.u(it) !in 48..57 && b.u(it) != 32 && b.u(it) != 0 }) return false
    fun text(p: Int) = b.u(p) !in 1..8 && b.u(p) !in 11..12 && b.u(p) !in 14..31 && b.u(p) != 127
    if ((3..92).any { !text(it) }) return false
    val end = if(b[125] == 0.toByte() && b[126] != 0.toByte()) 125 else 127
    return (97 until end).all(::text)
}
private fun v1Families(raw: ByteArray, b: Builder) {
    fun add(key: String, value: String) {
        if (value.isNotEmpty()) b.families += FamilyValue(key,"id3v1",listOf(value),b.tags[key]?.any { it.trim().equals(value.trim(),true) } ?: true,true)
    }
    fun field(p: Int,n: Int) = raw.latin(p,n).trimEnd('\u0000',' ')
    add("TITLE",field(3,30)); add("ARTIST",field(33,30)); add("ALBUM",field(63,30)); add("RECORDINGDATE",field(93,4))
    val track = if(raw[125] == 0.toByte() && raw[126] != 0.toByte()) raw.u(126) else 0
    add("COMMENT",field(97,if(track > 0) 28 else 30))
    add("GENRE",TagVocabulary.genres.getOrNull(raw.u(127)).orEmpty())
    if (track > 0) add("TRACKNUMBER",track.toString())
}
