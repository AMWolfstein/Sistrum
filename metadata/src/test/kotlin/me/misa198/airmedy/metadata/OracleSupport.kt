// SPDX-License-Identifier: GPL-3.0-or-later
// Sistrum parity harness; WaxLabel cfbb45d13135a76f5df6781fdeec75f5cfe63bf9.
// Copyright (c) 2026 Cole Springer (MIT); see metadata/THIRD-PARTY-NOTICES.
package me.misa198.airmedy.metadata

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

internal val cache: Path = Path.of(System.getProperty("waxlabel.cache"))
internal val repository: Path = Path.of(System.getProperty("waxlabel.repo"))
internal fun fixtures(step: Int=1): List<JsonObject> = JsonParser.parseString(Files.readString(repository.resolve("docs/waxlabel/oracle-step$step.json")))
    .asJsonObject.getAsJsonArray("files").map { it.asJsonObject }
internal fun fixtureBytes(row: JsonObject): ByteArray {
    val data = Files.readAllBytes(cache.resolve(row["path"].asString))
    check(MessageDigest.getInstance("SHA-256").digest(data).hex() == row["sha256"].asString) { "changed fixture" }
    return data
}
private val gson = Gson()
internal fun jsonProjection(doc: Document): JsonElement {
    val result = linkedMapOf<String,Any>("schemaVersion" to 1,"format" to doc.format,
        "subformat" to doc.properties.container.ifEmpty { doc.format })
    val p = doc.properties
    val props = linkedMapOf<String,Any>()
    fun field(key: String, value: Any) { if(value != "" && value != 0 && value != 0L && value != 0.0) props[key] = value }
    field("container",p.container); field("codec",p.codec); field("codecProfile",p.profile)
    field("sampleRate",p.sampleRate); field("channels",p.channels)
    if(p.codec.uppercase() !in listOf("AAC","MP1","MP2","MP3","OPUS","VORBIS","AC-3","E-AC-3","MPC","MUSEPACK","WMA V1","WMA V2","WMA PRO","WMA VOICE")) field("bitsPerSample",p.bitsPerSample)
    field("durationMs",p.durationMs); if(p.durationMs != 0L) field("bitrateBps",p.bitrate)
    field("paddingBytes",p.paddingBytes); field("outputGainDb",p.opusOutputGain.toDouble()/256)
    result["properties"] = props
    result["tags"] = doc.tags.map { (key,values) -> linkedMapOf<String,Any>("key" to key,"values" to values).apply {
        if(key in TagVocabulary.knownKeys && key !in TagVocabulary.multivaluedKeys && values.size > 1)
            put("cardinality",if(values.map { AsfText.fold(it) }.distinct().size == 1) "duplicate" else "conflict")
    } }
    result["pictures"] = doc.pictures.map { pic -> linkedMapOf<String,Any>("type" to pic.typeName,"mime" to pic.mime,"bytes" to pic.size).apply {
        if(pic.width != 0) put("width",pic.width); if(pic.height != 0) put("height",pic.height)
        if(pic.depth != 0) put("depth",pic.depth); if(pic.colors != 0) put("colors",pic.colors)
        if(pic.description.isNotEmpty()) put("description",pic.description)
    } }
    result["chapters"] = doc.chapters.map { c -> linkedMapOf<String,Any>("startMs" to c.startMs).apply {
        if(c.endMs != 0L) put("endMs",c.endMs); if(c.title.isNotEmpty()) put("title",c.title)
    } }
    result["syncedLyrics"] = doc.syncedLyrics.map { sl -> linkedMapOf<String,Any>("lines" to sl.lines.map { mapOf("timeMs" to it.timeMs,"text" to it.text) }).apply {
        if(sl.language.isNotEmpty()) put("language",sl.language); if(sl.description.isNotEmpty()) put("description",sl.description)
    } }
    result["warnings"] = doc.warnings.map { mapOf("code" to it.code,"message" to it.message) }
    if(doc.legacyOnlyKeys.isNotEmpty()) result["legacyOnly"] = doc.legacyOnlyKeys
    if(doc.native.isNotEmpty()) result["native"] = doc.native.map { n -> linkedMapOf<String,Any>("kind" to n.kind,"size" to n.size).apply { if(n.note.isNotEmpty()) put("note",n.note) } }
    if(doc.families.isNotEmpty()) result["sources"] = doc.families.map { mapOf("key" to it.key,"family" to it.family,"scope" to it.scope,"values" to it.values,"selected" to it.selected) }
    return gson.toJsonTree(result)
}
