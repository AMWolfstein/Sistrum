// SPDX-License-Identifier: GPL-3.0-or-later
// Sistrum read contracts for WaxLabel cfbb45d13135a76f5df6781fdeec75f5cfe63bf9.
// Copyright (c) 2026 Cole Springer (MIT); see metadata/THIRD-PARTY-NOTICES.
package me.misa198.airmedy.metadata

import me.misa198.airmedy.codecs.container.ByteBufferSource
import me.misa198.airmedy.codecs.container.RandomAccessSource
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.file.Files
import java.io.IOException
import kotlin.random.Random

class ReadContractsTest {
    private fun bytes(name: String) = Files.readAllBytes(cache.resolve("owned/$name"))
    private class Observed(private val data: ByteArray, private val short: Int = Int.MAX_VALUE,
        private val allowed: ((Long,Int) -> Boolean)? = null) : RandomAccessSource {
        override val length = data.size.toLong()
        var largest = 0
        var total = 0L
        override fun read(position: Long, buffer: ByteBuffer): Int {
            val requested = buffer.remaining()
            check(allowed?.invoke(position,requested) != false) { "Forbidden payload read $position + $requested" }
            largest = maxOf(largest,requested)
            if(position >= length) return -1
            val count = minOf(requested,short,(length-position).toInt())
            buffer.put(data,position.toInt(),count)
            total += count
            return count
        }
    }
    @Test fun defaultPicturePolicyStreamsInsteadOfMaterializing() {
        for(name in listOf("large-picture.mp3","large-tag-unsync.mp3","large-frame-unsync.mp3")) {
            val observed = Observed(bytes(name))
            val doc = Metadata.read(observed)
            assertNull(doc.error)
            assertEquals(1,doc.pictures.size)
            assertTrue(doc.pictures.single().size > 1_000_000)
            assertNull(doc.pictures.single().bytes())
            assertTrue("$name: ${observed.largest}",observed.largest <= 8192)
        }
    }
    @Test fun noneSkipsThePicturePayloadAndItsHash() {
        val data = bytes("large-picture.mp3")
        val tagEnd = 10L+data.sync(6)
        val observed = Observed(data,allowed={ p,n -> p+n <= 39 || p >= tagEnd })
        val doc = Metadata.read(observed,ReadOptions(pictures=Pictures.NONE))
        assertNull(doc.error)
        assertTrue(doc.pictures.isEmpty())
        assertFalse(doc.warnings.any { it.code == "invalid-picture" })
        assertTrue(observed.total < 256)
        assertEquals("APIC",doc.native[1].kind.trim())
    }
    @Test fun fullReadsAreLazyRepeatableAndDetached() {
        for(name in listOf("large-picture.mp3","large-tag-unsync.mp3","large-frame-unsync.mp3")) {
            val observed = Observed(bytes(name))
            val doc = Metadata.read(observed,ReadOptions(pictures=Pictures.FULL))
            assertNull(doc.error)
            assertTrue(observed.largest <= 8192)
            val before = observed.total
            val picture = doc.pictures.single()
            val first = picture.bytes()!!
            assertTrue(observed.total > before)
            val original = first[0]
            first[0] = 0
            assertEquals(original,picture.bytes()!![0])
            assertEquals(picture.sha256,java.security.MessageDigest.getInstance("SHA-256").digest(picture.bytes()!!).hex())
        }
    }
    @Test fun mp3OnlyReadsTagRegionsAndFixedPropertyHeaders() {
        val data = bytes("mutagen-v24.mp3")
        val tagEnd = 10L+data.sync(6)
        val first = Mpeg.header(data.copyOfRange(tagEnd.toInt(),tagEnd.toInt()+4))!!
        val allowed = listOf(0L to tagEnd,tagEnd to tagEnd+4,tagEnd+first.length to tagEnd+first.length+4,
            tagEnd+21 to tagEnd+33,tagEnd+23 to tagEnd+27,tagEnd+36 to tagEnd+40,
            data.size-128L to data.size-125L,data.size-32L to data.size-24L)
        val observed = Observed(data,allowed={ p,n -> allowed.any { p >= it.first && p+n <= it.second } })
        assertNull(Metadata.read(observed).error)
    }
    @Test fun wavAndAiffSkipSamplePayload() {
        for(name in listOf("embedded.wav","embedded.aiff")) {
            val data = bytes(name)
            val aiff = name.endsWith("aiff")
            var pos = 12
            var payloadStart = 0
            var payloadEnd = 0
            while(pos+8 <= data.size) {
                val id = data.latin(pos,4)
                val size = (if(aiff) data.be(pos+4,4) else data.le(pos+4,4)).toInt()
                if(id == "data" || id == "SSND") { payloadStart=pos+8+if(aiff) 8 else 0; payloadEnd=pos+8+size }
                pos += 8+size+(size and 1)
            }
            val observed = Observed(data,allowed={ p,n -> p+n <= payloadStart || p >= payloadEnd })
            assertNull(Metadata.read(observed).error)
        }
    }
    @Test fun positiveShortReadsPreserveProjection() {
        val data = bytes("mutagen-v24.mp3")
        val normal = Metadata.read(ByteBufferSource(ByteBuffer.wrap(data)))
        val short = Metadata.read(Observed(data,short=1))
        assertNull(short.error)
        assertEquals(jsonProjection(normal),jsonProjection(short))
    }
    @Test fun sourceFailuresReturnTypedErrors() {
        for(mode in 0..2) {
            val source = object : RandomAccessSource {
                override val length = 100L
                override fun read(position: Long, buffer: ByteBuffer): Int = when(mode) { 0 -> 0; 1 -> -1; else -> throw IOException("source failure") }
            }
            assertEquals(ErrorClass.IO,Metadata.read(source).error?.errorClass)
        }
    }
    @Test fun corruptSourceContractDoesNotEscape() {
        val source = object : RandomAccessSource {
            override val length = 100L
            override fun read(position: Long, buffer: ByteBuffer) = 1
        }
        assertEquals(ErrorClass.IO,Metadata.read(source).error?.errorClass)
    }
    @Test fun explicitByteAndElementLimitsAreEnforced() {
        val data = bytes("mutagen-v24.mp3")
        for(options in listOf(ReadOptions(maxTagBytes=20),ReadOptions(maxReadBytes=10),ReadOptions(maxElements=1))) {
            assertEquals(ErrorClass.SIZE_TOO_LARGE,Metadata.read(ByteBufferSource(ByteBuffer.wrap(data)),options).error?.errorClass)
        }
    }
    @Test fun oversizedTagsAreRejectedBeforePayloadAllocation() {
        val header = bytes("tag-only.mp3").copyOf(10)
        val declared = 64*1024*1024+1
        for(i in 0..3) header[6+i] = ((declared shr (21-7*i)) and 127).toByte()
        val source = object : RandomAccessSource {
            override val length = declared+10L
            override fun read(position: Long, buffer: ByteBuffer): Int {
                check(position+buffer.remaining() <= header.size) { "payload should not be requested" }
                val n = buffer.remaining()
                buffer.put(header,position.toInt(),n)
                return n
            }
        }
        assertEquals(ErrorClass.SIZE_TOO_LARGE,Metadata.read(source).error?.errorClass)
    }
    @Test fun amplifiedTextValuesAndPictureHeadersAreBounded() {
        fun tagged(id: String, body: ByteArray): ByteArray {
            fun sync(n: Int) = byteArrayOf((n shr 21 and 127).toByte(),(n shr 14 and 127).toByte(),(n shr 7 and 127).toByte(),(n and 127).toByte())
            val frame = id.toByteArray()+sync(body.size)+byteArrayOf(0,0)+body
            val header = bytes("tag-only.mp3").copyOf(10)
            sync(frame.size).copyInto(header,6)
            return header+frame
        }
        val values = byteArrayOf(3)+ByteArray(200_002) { if(it%2 == 0) 'x'.code.toByte() else 0 }
        val header = byteArrayOf(0)+"image/png".toByteArray()+byteArrayOf(0,3)+ByteArray(65537) { 'd'.code.toByte() }+byteArrayOf(0)
        for(data in listOf(tagged("TIT2",values),tagged("APIC",header))) {
            assertEquals(ErrorClass.SIZE_TOO_LARGE,Metadata.read(ByteBufferSource(ByteBuffer.wrap(data))).error?.errorClass)
        }
    }
    @Test fun invalidOptionsAndLengthsDoNotEscape() {
        assertEquals(ErrorClass.INVALID_DATA,Metadata.read(ByteBufferSource(ByteBuffer.wrap(bytes("base.mp3"))),ReadOptions(maxElements=0)).error?.errorClass)
        val source = object : RandomAccessSource {
            override val length = -1L
            override fun read(position: Long, buffer: ByteBuffer): Int = error("must not read")
        }
        assertEquals(ErrorClass.INVALID_DATA,Metadata.read(source).error?.errorClass)
    }
    @Test fun documentsAndNestedCollectionsCannotBeMutated() {
        val doc = Metadata.read(ByteBufferSource(ByteBuffer.wrap(bytes("mutagen-v24.mp3"))))
        fun rejects(block: () -> Unit) { try { block(); fail("mutable collection") } catch (_: UnsupportedOperationException) { } }
        rejects { (doc.tags as MutableMap<String,List<String>>).clear() }
        rejects { (doc.tags.getValue("ARTIST") as MutableList<String>).clear() }
        rejects { (doc.native as MutableList<NativeBlock>).clear() }
        rejects { (doc.pictures as MutableList<Picture>).clear() }
        rejects { (doc.syncedLyrics.single().lines as MutableList<SyncedLine>).clear() }
        rejects { (doc.families.first().values as MutableList<String>).clear() }
        rejects { (doc.chapters as MutableList<Chapter>).clear() }
        rejects { (doc.warnings as MutableList<Warning>).clear() }
        rejects { (TagVocabulary.knownKeys as MutableSet<String>).clear() }
    }
    @Test fun vocabularyAliasesMatchThePinnedKeyRules() {
        assertEquals("RECORDINGDATE",TagVocabulary.aliasKey("date"))
        assertEquals("DATE",TagVocabulary.userTextKey("date")) // ID3 has different alias rules.
        assertEquals("MUSICBRAINZ_ALBUMID",TagVocabulary.userTextKey("MusicBrainz Album Id"))
        assertEquals("CUSTOM.KEY",TagVocabulary.parseKey(" custom.key "))
        assertNull(TagVocabulary.parseKey("bad=key"))
        assertNull(TagVocabulary.parseKey("عنوان"))
        val upstream = Files.readString(cache.resolve("src/tag/keys.go"))
        val known = Regex("(?m)^\\s*\\w+\\s+Key = \"([^\"]+)\"").findAll(upstream).map { it.groupValues[1] }.toSet()
        assertEquals(known,TagVocabulary.knownKeys)
    }
    @Test fun malformedAndTruncatedInputsNeverThrow() {
        val random = Random(198)
        val seed = bytes("mutagen-v24.mp3")
        for(cut in 0..minOf(seed.size,2048)) {
            Metadata.read(ByteBufferSource(ByteBuffer.wrap(seed.copyOf(cut))),ReadOptions(formatHint="mp3"))
        }
        repeat(1000) {
            val data = random.nextBytes(random.nextInt(0,1024))
            if(data.size >= 10 && it%2 == 0) { data[0]=73; data[1]=68; data[2]=51; data[3]=(2+it%3).toByte() }
            val doc = Metadata.read(ByteBufferSource(ByteBuffer.wrap(data)),ReadOptions(maxTagBytes=4096,maxElements=64,formatHint="mp3"))
            assertTrue(doc.error != null || doc.warnings.isNotEmpty() || doc.native.isNotEmpty())
        }
    }
    @Test fun utf16CarryAndGenreDateAreProjectedWithoutFlattening() {
        val carry = Metadata.read(ByteBufferSource(ByteBuffer.wrap(bytes("utf16-carry.mp3"))))
        assertEquals(listOf("one","two"),carry.tags["CUSTOM"])
        val date = Metadata.read(ByteBufferSource(ByteBuffer.wrap(bytes("genre-date.mp3"))))
        assertEquals(listOf("2021-02-03T04:05"),date.tags["RECORDINGDATE"])
        assertEquals(listOf("Rock","Techno-Industrial","Noise","Remix","(999)","(live)"),date.tags["GENRE"])
    }
}
