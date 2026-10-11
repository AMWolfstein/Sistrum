// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxLabel cfbb45d13135a76f5df6781fdeec75f5cfe63bf9, internal/asf/guid.go and internal/mapping/asf.go.
// Copyright (c) 2026 Cole Springer, MIT; see metadata/THIRD-PARTY-NOTICES.
package me.misa198.airmedy.metadata

internal object AsfGuid {
    private fun guid(hex: String)=hex.chunked(2).map {it.toInt(16).toByte()}.toByteArray().latin()
    val Header=guid("3026b2758e66cf11a6d900aa0062ce6c")
    val FileProps=guid("a1dcab8c47a9cf118ee400c00c205365")
    val StreamProps=guid("9107dcb7b7a9cf118ee600c00c205365")
    val HeaderExt=guid("b503bf5f2ea9cf118ee300c00c205365")
    val ContentDesc=guid("3326b2758e66cf11a6d900aa0062ce6c")
    val ExtContentDesc=guid("40a4d0d207e3d21197f000a0c95ea850")
    val Metadata=guid("eacbf8c5af5b77488467aa8c44fa4cca")
    val MetadataLibrary=guid("941c23449894d149a1411d134e457054")
    val Marker=guid("01cd87f451a9cf118ee600c00c205365")
    val Data=guid("3626b2758e66cf11a6d900aa0062ce6c")
    val CodecList=guid("4052d1861d31d011a3a400a0c90348f6")
    val StreamBitrate=guid("ce75f87b8d46d1118d82006097c9a2b2")
    val ContentEncryption=guid("fbb3112223bdd211b4b700a0c955fc6e")
    val ExtContentEncryption=guid("14e68a292226174cb935dae07ee9289c")
    val AudioMedia=guid("409e69f84d5bcf11a8fd00805f5c442b")
}

internal object AsfNames {
    val names=mapOf(
        "title" to "TITLE",
        "author" to "ARTIST",
        "copyright" to "COPYRIGHT",
        "description" to "COMMENT",
        "wm/albumtitle" to "ALBUM",
        "wm/albumartist" to "ALBUMARTIST",
        "wm/composer" to "COMPOSER",
        "wm/writer" to "LYRICIST",
        "wm/conductor" to "CONDUCTOR",
        "wm/producer" to "PRODUCER",
        "wm/engineer" to "ENGINEER",
        "wm/mixer" to "MIXER",
        "wm/modifiedby" to "REMIXER",
        "wm/genre" to "GENRE",
        "wm/year" to "RECORDINGDATE",
        "wm/originalreleaseyear" to "ORIGINALDATE",
        "wm/originalreleasetime" to "ORIGINALDATE",
        "wm/tracknumber" to "TRACKNUMBER",
        "wm/partofset" to "DISCNUMBER",
        "wm/setsubtitle" to "DISCSUBTITLE",
        "wm/publisher" to "LABEL",
        "wm/isrc" to "ISRC",
        "wm/barcode" to "BARCODE",
        "wm/catalogno" to "CATALOGNUMBER",
        "wm/media" to "MEDIA",
        "wm/lyrics" to "LYRICS",
        "wm/contentgroupdescription" to "GROUPING",
        "wm/encodedby" to "ENCODEDBY",
        "wm/toolname" to "ENCODER",
        "wm/encodingsettings" to "ENCODER",
        "wm/beatsperminute" to "BPM",
        "wm/titlesortorder" to "TITLESORT",
        "wm/artistsortorder" to "ARTISTSORT",
        "wm/albumsortorder" to "ALBUMSORT",
        "wm/albumartistsortorder" to "ALBUMARTISTSORT",
        "wm/composersortorder" to "COMPOSERSORT",
        "wm/mediaclassprimaryid" to "",
        "wm/mediaclasssecondaryid" to "",
        "wm/wmcollectiongroupid" to "",
        "wm/wmcollectionid" to "",
        "wm/wmcontentid" to "",
        "wm/uniquefileidentifier" to "",
        "wm/provider style" to "",
        "wm/encodingtime" to "",
        "wm/mcdi" to "",
        "wm/track" to "",
        "rating" to "",
        "aspectratiox" to "",
        "aspectratioy" to "",
        "isvbr" to "",
        "wmfsdkversion" to "",
        "wmfsdkneeded" to "",
        "deviceconformancetemplate" to "",
        "buffer average" to "",
        "vbr peak" to "",
        "musicbrainz/album id" to "MUSICBRAINZ_ALBUMID",
        "musicbrainz/release group id" to "MUSICBRAINZ_RELEASEGROUPID",
        "musicbrainz/track id" to "MUSICBRAINZ_TRACKID",
        "musicbrainz/release track id" to "MUSICBRAINZ_RELEASETRACKID",
        "musicbrainz/work id" to "MUSICBRAINZ_WORKID",
        "musicbrainz/disc id" to "MUSICBRAINZ_DISCID",
        "musicbrainz/artist id" to "MUSICBRAINZ_ARTISTID",
        "musicbrainz/album artist id" to "MUSICBRAINZ_ALBUMARTISTID",
        "musicbrainz/album status" to "RELEASESTATUS",
        "musicbrainz/album type" to "RELEASETYPE",
        "musicbrainz/album release country" to "RELEASECOUNTRY",
        "acoustid/id" to "ACOUSTID_ID",
        "acoustid/fingerprint" to "ACOUSTID_FINGERPRINT",
        "replaygain_track_gain" to "REPLAYGAIN_TRACK_GAIN",
        "replaygain_track_peak" to "REPLAYGAIN_TRACK_PEAK",
        "replaygain_album_gain" to "REPLAYGAIN_ALBUM_GAIN",
        "replaygain_album_peak" to "REPLAYGAIN_ALBUM_PEAK"
    )
}

/** Go's simple Unicode case mapping and TrimSpace, without JVM full-case expansion. */
internal object AsfText {
    fun trim(value: String)=value.trim {it.code in 9..13 || it.code in listOf(32,0x85,0xa0,0x1680,0x2028,0x2029,0x202f,0x205f,0x3000) || it.code in 0x2000..0x200a}
    private fun mapped(value: String,upper: Boolean): String {
        val out=StringBuilder(value.length)
        value.codePoints().forEach {out.appendCodePoint(if(upper) Character.toUpperCase(it) else Character.toLowerCase(it))}
        return out.toString()
    }
    fun lower(value: String)=mapped(value,false)
    fun upper(value: String)=mapped(value,true)
    fun fold(value: String)=lower(trim(value))
}
