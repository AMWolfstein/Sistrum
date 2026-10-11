// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxLabel cfbb45d13135a76f5df6781fdeec75f5cfe63bf9, tag/ and internal/mapping/.
// Copyright (c) 2026 Cole Springer, MIT; see metadata/THIRD-PARTY-NOTICES.
package me.misa198.airmedy.metadata

import java.util.Locale

/** WaxLabel vocabulary; ID3 TXXX deliberately uses its own aliases. */
object TagVocabulary {
    const val TITLE = "TITLE"
    const val ARTIST = "ARTIST"
    const val ALBUM = "ALBUM"
    const val ALBUM_ARTIST = "ALBUMARTIST"
    const val COMPOSER = "COMPOSER"
    const val LYRICIST = "LYRICIST"
    const val GENRE = "GENRE"
    const val TRACK_NUMBER = "TRACKNUMBER"
    const val TRACK_TOTAL = "TRACKTOTAL"
    const val DISC_NUMBER = "DISCNUMBER"
    const val DISC_TOTAL = "DISCTOTAL"
    const val RECORDING_DATE = "RECORDINGDATE"
    const val RELEASE_DATE = "RELEASEDATE"
    const val ORIGINAL_DATE = "ORIGINALDATE"
    const val COMMENT = "COMMENT"
    const val LYRICS = "LYRICS"
    const val GROUPING = "GROUPING"
    const val COPYRIGHT = "COPYRIGHT"
    const val TITLE_SORT = "TITLESORT"
    const val ARTIST_SORT = "ARTISTSORT"
    const val ALBUM_SORT = "ALBUMSORT"
    const val ALBUM_ARTIST_SORT = "ALBUMARTISTSORT"
    const val COMPOSER_SORT = "COMPOSERSORT"
    const val ISRC = "ISRC"
    const val BARCODE = "BARCODE"
    const val CATALOG_NUMBER = "CATALOGNUMBER"
    const val LABEL = "LABEL"
    const val MEDIA = "MEDIA"
    const val DISC_SUBTITLE = "DISCSUBTITLE"
    const val RELEASE_COUNTRY = "RELEASECOUNTRY"
    const val RELEASE_STATUS = "RELEASESTATUS"
    const val RELEASE_TYPE = "RELEASETYPE"
    const val CONDUCTOR = "CONDUCTOR"
    const val REMIXER = "REMIXER"
    const val PERFORMER = "PERFORMER"
    const val ENCODED_BY = "ENCODEDBY"
    const val ENCODER = "ENCODER"
    const val PRODUCER = "PRODUCER"
    const val ENGINEER = "ENGINEER"
    const val MIXER = "MIXER"
    const val ARRANGER = "ARRANGER"
    const val WRITER = "WRITER"
    const val DJ_MIXER = "DJMIXER"
    const val ACOUSTID = "ACOUSTID_ID"
    const val ACOUSTID_FINGERPRINT = "ACOUSTID_FINGERPRINT"
    const val COMPILATION = "COMPILATION"
    const val MB_RELEASEID = "MUSICBRAINZ_ALBUMID"
    const val MB_RELEASE_GROUPID = "MUSICBRAINZ_RELEASEGROUPID"
    const val MB_RECORDINGID = "MUSICBRAINZ_TRACKID"
    const val MB_RELEASE_TRACKID = "MUSICBRAINZ_RELEASETRACKID"
    const val MB_WORKID = "MUSICBRAINZ_WORKID"
    const val MB_DISCID = "MUSICBRAINZ_DISCID"
    const val MB_ARTISTID = "MUSICBRAINZ_ARTISTID"
    const val MB_ALBUM_ARTISTID = "MUSICBRAINZ_ALBUMARTISTID"
    const val REPLAY_GAIN_TRACK_GAIN = "REPLAYGAIN_TRACK_GAIN"
    const val REPLAY_GAIN_TRACK_PEAK = "REPLAYGAIN_TRACK_PEAK"
    const val REPLAY_GAIN_ALBUM_GAIN = "REPLAYGAIN_ALBUM_GAIN"
    const val REPLAY_GAIN_ALBUM_PEAK = "REPLAYGAIN_ALBUM_PEAK"
    const val RATING = "RATING"
    const val PLAY_COUNT = "PLAYCOUNT"
    const val SOURCEURL = "SOURCE_URL"
    const val SOURCEID = "SOURCE_ID"
    const val ACQUISITION_DATE = "ACQUISITION_DATE"
    const val ENCODING_HISTORY = "ENCODING_HISTORY"
    const val MEDIA_TYPE = "MEDIATYPE"
    const val DESCRIPTION = "DESCRIPTION"
    const val LONG_DESCRIPTION = "LONGDESCRIPTION"
    const val NARRATOR = "NARRATOR"
    const val I_TUNES_ADVISORY = "ITUNESADVISORY"
    const val I_TUNES_GAPLESS = "ITUNESGAPLESS"
    const val SHOW_MOVEMENT = "SHOWMOVEMENT"
    const val BPM = "BPM"
    const val WORK = "WORK"
    const val MOVEMENT_NAME = "MOVEMENTNAME"
    const val MOVEMENT = "MOVEMENT"
    const val MOVEMENT_TOTAL = "MOVEMENTTOTAL"
    val knownKeys: Set<String> = java.util.Collections.unmodifiableSet(sortedSetOf("ACOUSTID_FINGERPRINT", "ACOUSTID_ID", "ACQUISITION_DATE", "ALBUM", "ALBUMARTIST", "ALBUMARTISTSORT", "ALBUMSORT", "ARRANGER", "ARTIST", "ARTISTSORT", "BARCODE", "BPM", "CATALOGNUMBER", "COMMENT", "COMPILATION", "COMPOSER", "COMPOSERSORT", "CONDUCTOR", "COPYRIGHT", "DESCRIPTION", "DISCNUMBER", "DISCSUBTITLE", "DISCTOTAL", "DJMIXER", "ENCODEDBY", "ENCODER", "ENCODING_HISTORY", "ENGINEER", "GENRE", "GROUPING", "ISRC", "ITUNESADVISORY", "ITUNESGAPLESS", "LABEL", "LONGDESCRIPTION", "LYRICIST", "LYRICS", "MEDIA", "MEDIATYPE", "MIXER", "MOVEMENT", "MOVEMENTNAME", "MOVEMENTTOTAL", "MUSICBRAINZ_ALBUMARTISTID", "MUSICBRAINZ_ALBUMID", "MUSICBRAINZ_ARTISTID", "MUSICBRAINZ_DISCID", "MUSICBRAINZ_RELEASEGROUPID", "MUSICBRAINZ_RELEASETRACKID", "MUSICBRAINZ_TRACKID", "MUSICBRAINZ_WORKID", "NARRATOR", "ORIGINALDATE", "PERFORMER", "PLAYCOUNT", "PRODUCER", "RATING", "RECORDINGDATE", "RELEASECOUNTRY", "RELEASEDATE", "RELEASESTATUS", "RELEASETYPE", "REMIXER", "REPLAYGAIN_ALBUM_GAIN", "REPLAYGAIN_ALBUM_PEAK", "REPLAYGAIN_TRACK_GAIN", "REPLAYGAIN_TRACK_PEAK", "SHOWMOVEMENT", "SOURCE_ID", "SOURCE_URL", "TITLE", "TITLESORT", "TRACKNUMBER", "TRACKTOTAL", "WORK", "WRITER"))
    val multivaluedKeys: Set<String> = java.util.Collections.unmodifiableSet(setOf("ARTIST", "COMPOSER", "LYRICIST", "GENRE", "COMMENT", "PERFORMER", "PRODUCER", "ENGINEER", "MIXER", "ARRANGER", "WRITER", "DJMIXER", "RELEASETYPE", "MUSICBRAINZ_ARTISTID", "MUSICBRAINZ_ALBUMARTISTID"))
    internal val aliases = mapOf(
        "DATE" to "RECORDINGDATE",
        "YEAR" to "RECORDINGDATE",
        "ORIGINALYEAR" to "ORIGINALDATE",
        "TOTALTRACKS" to "TRACKTOTAL",
        "TRACKTOTAL" to "TRACKTOTAL",
        "TOTALDISCS" to "DISCTOTAL",
        "DISCTOTAL" to "DISCTOTAL",
        "ORGANIZATION" to "LABEL",
        "UNSYNCEDLYRICS" to "LYRICS",
        "DISC" to "DISCNUMBER",
        "TRACK" to "TRACKNUMBER",
        "ALBUM ARTIST" to "ALBUMARTIST",
        "ALBUM_ARTIST" to "ALBUMARTIST",
        "DJ MIXER" to "DJMIXER",
        "DJ_MIXER" to "DJMIXER",
        "DJ-MIXER" to "DJMIXER",
        "MUSICBRAINZ_ALBUMSTATUS" to "RELEASESTATUS",
        "MUSICBRAINZ_ALBUMTYPE" to "RELEASETYPE",
        "LEAD_PERFORMER" to "ARTIST",
        "DATE_RECORDED" to "RECORDINGDATE",
        "DATE_RELEASED" to "RELEASEDATE",
        "DATE_RELEASE" to "RELEASEDATE",
        "DATE_ORIGINAL" to "ORIGINALDATE",
        "ORIGINAL_DATE" to "ORIGINALDATE",
        "ENCODED_BY" to "ENCODEDBY",
        "PART_NUMBER" to "TRACKNUMBER",
        "TOTAL_PARTS" to "TRACKTOTAL",
        "TOTAL_DISCS" to "DISCTOTAL",
        "CATALOG_NUMBER" to "CATALOGNUMBER",
        "PUBLISHER" to "LABEL",
        "REMIXED_BY" to "REMIXER",
        "CONTENT_GROUP" to "GROUPING"
    )
    internal val textFrames = mapOf(
        "TIT2" to "TITLE",
        "TPE1" to "ARTIST",
        "TALB" to "ALBUM",
        "TPE2" to "ALBUMARTIST",
        "TCOM" to "COMPOSER",
        "TEXT" to "LYRICIST",
        "TPE3" to "CONDUCTOR",
        "TPE4" to "REMIXER",
        "TCOP" to "COPYRIGHT",
        "TPUB" to "LABEL",
        "TMED" to "MEDIA",
        "TIT1" to "GROUPING",
        "TSST" to "DISCSUBTITLE",
        "TSRC" to "ISRC",
        "TENC" to "ENCODEDBY",
        "TSSE" to "ENCODER",
        "TSOT" to "TITLESORT",
        "TSOP" to "ARTISTSORT",
        "TSOA" to "ALBUMSORT",
        "TSO2" to "ALBUMARTISTSORT",
        "TSOC" to "COMPOSERSORT",
        "TCMP" to "COMPILATION",
        "TBPM" to "BPM",
        "MVNM" to "MOVEMENTNAME"
    )
    internal val txxxAliases = mapOf(
        "MUSICBRAINZ ALBUM ID" to "MUSICBRAINZ_ALBUMID",
        "MUSICBRAINZ ARTIST ID" to "MUSICBRAINZ_ARTISTID",
        "MUSICBRAINZ ALBUM ARTIST ID" to "MUSICBRAINZ_ALBUMARTISTID",
        "MUSICBRAINZ RELEASE GROUP ID" to "MUSICBRAINZ_RELEASEGROUPID",
        "MUSICBRAINZ RELEASE TRACK ID" to "MUSICBRAINZ_RELEASETRACKID",
        "MUSICBRAINZ WORK ID" to "MUSICBRAINZ_WORKID",
        "MUSICBRAINZ DISC ID" to "MUSICBRAINZ_DISCID",
        "ACOUSTID ID" to "ACOUSTID_ID",
        "ACOUSTID FINGERPRINT" to "ACOUSTID_FINGERPRINT",
        "BARCODE" to "BARCODE",
        "CATALOGNUMBER" to "CATALOGNUMBER",
        "REPLAYGAIN_TRACK_GAIN" to "REPLAYGAIN_TRACK_GAIN",
        "REPLAYGAIN_TRACK_PEAK" to "REPLAYGAIN_TRACK_PEAK",
        "REPLAYGAIN_ALBUM_GAIN" to "REPLAYGAIN_ALBUM_GAIN",
        "REPLAYGAIN_ALBUM_PEAK" to "REPLAYGAIN_ALBUM_PEAK",
        "MUSICBRAINZ ALBUM RELEASE COUNTRY" to "RELEASECOUNTRY",
        "MUSICBRAINZ ALBUM STATUS" to "RELEASESTATUS",
        "MUSICBRAINZ ALBUM TYPE" to "RELEASETYPE",
        "MUSICBRAINZ_ALBUMSTATUS" to "RELEASESTATUS",
        "MUSICBRAINZ_ALBUMTYPE" to "RELEASETYPE",
        "TCMP" to "COMPILATION",
        "DJ MIXER" to "DJMIXER",
        "DJ_MIXER" to "DJMIXER",
        "DJ-MIXER" to "DJMIXER",
        "LEAD_PERFORMER" to "ARTIST",
        "DATE_RECORDED" to "RECORDINGDATE",
        "DATE_RELEASED" to "RELEASEDATE",
        "DATE_RELEASE" to "RELEASEDATE",
        "DATE_ORIGINAL" to "ORIGINALDATE",
        "ORIGINAL_DATE" to "ORIGINALDATE",
        "ENCODED_BY" to "ENCODEDBY",
        "PART_NUMBER" to "TRACKNUMBER",
        "TOTAL_PARTS" to "TRACKTOTAL",
        "TOTAL_DISCS" to "DISCTOTAL",
        "CATALOG_NUMBER" to "CATALOGNUMBER",
        "PUBLISHER" to "LABEL",
        "REMIXED_BY" to "REMIXER",
        "CONTENT_GROUP" to "GROUPING"
    )
    internal val v22 = mapOf(
        "BUF" to "RBUF",
        "CNT" to "PCNT",
        "COM" to "COMM",
        "CRA" to "AENC",
        "CRM" to "ENCR",
        "ETC" to "ETCO",
        "EQU" to "EQUA",
        "GEO" to "GEOB",
        "IPL" to "IPLS",
        "LNK" to "LINK",
        "MCI" to "MCDI",
        "MLL" to "MLLT",
        "PIC" to "APIC",
        "POP" to "POPM",
        "REV" to "RVRB",
        "RVA" to "RVAD",
        "SLT" to "SYLT",
        "STC" to "SYTC",
        "TAL" to "TALB",
        "TBP" to "TBPM",
        "TCM" to "TCOM",
        "TCO" to "TCON",
        "TCP" to "TCMP",
        "TCR" to "TCOP",
        "TDA" to "TDAT",
        "TDY" to "TDLY",
        "TEN" to "TENC",
        "TFT" to "TFLT",
        "TIM" to "TIME",
        "TKE" to "TKEY",
        "TLA" to "TLAN",
        "TLE" to "TLEN",
        "TMT" to "TMED",
        "TOA" to "TOPE",
        "TOF" to "TOFN",
        "TOL" to "TOLY",
        "TOR" to "TORY",
        "TOT" to "TOAL",
        "TP1" to "TPE1",
        "TP2" to "TPE2",
        "TP3" to "TPE3",
        "TP4" to "TPE4",
        "TPA" to "TPOS",
        "TPB" to "TPUB",
        "TRC" to "TSRC",
        "TRD" to "TRDA",
        "TRK" to "TRCK",
        "TS2" to "TSO2",
        "TSA" to "TSOA",
        "TSC" to "TSOC",
        "TSI" to "TSIZ",
        "TSP" to "TSOP",
        "TSS" to "TSSE",
        "TST" to "TSOT",
        "TT1" to "TIT1",
        "TT2" to "TIT2",
        "TT3" to "TIT3",
        "TXT" to "TEXT",
        "TXX" to "TXXX",
        "TYE" to "TYER",
        "UFI" to "UFID",
        "ULT" to "USLT",
        "WAF" to "WOAF",
        "WAR" to "WOAR",
        "WAS" to "WOAS",
        "WCM" to "WCOM",
        "WCP" to "WCOP",
        "WPB" to "WPUB",
        "WXX" to "WXXX"
    )
    internal val genres = listOf("Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk", "Grunge", "Hip-Hop", "Jazz", "Metal", "New Age", "Oldies", "Other", "Pop", "R&B", "Rap", "Reggae", "Rock", "Techno", "Industrial", "Alternative", "Ska", "Death Metal", "Pranks", "Soundtrack", "Euro-Techno", "Ambient", "Trip-Hop", "Vocal", "Jazz+Funk", "Fusion", "Trance", "Classical", "Instrumental", "Acid", "House", "Game", "Sound Clip", "Gospel", "Noise", "Alt. Rock", "Bass", "Soul", "Punk", "Space", "Meditative", "Instrumental Pop", "Instrumental Rock", "Ethnic", "Gothic", "Darkwave", "Techno-Industrial", "Electronic", "Pop-Folk", "Eurodance", "Dream", "Southern Rock", "Comedy", "Cult", "Gangsta Rap", "Top 40", "Christian Rap", "Pop/Funk", "Jungle", "Native American", "Cabaret", "New Wave", "Psychedelic", "Rave", "Showtunes", "Trailer", "Lo-Fi", "Tribal", "Acid Punk", "Acid Jazz", "Polka", "Retro", "Musical", "Rock & Roll", "Hard Rock", "Folk", "Folk-Rock", "National Folk", "Swing", "Fast-Fusion", "Bebop", "Latin", "Revival", "Celtic", "Bluegrass", "Avantgarde", "Gothic Rock", "Progressive Rock", "Psychedelic Rock", "Symphonic Rock", "Slow Rock", "Big Band", "Chorus", "Easy Listening", "Acoustic", "Humour", "Speech", "Chanson", "Opera", "Chamber Music", "Sonata", "Symphony", "Booty Bass", "Primus", "Porn Groove", "Satire", "Slow Jam", "Club", "Tango", "Samba", "Folklore", "Ballad", "Power Ballad", "Rhythmic Soul", "Freestyle", "Duet", "Punk Rock", "Drum Solo", "A Cappella", "Euro-House", "Dance Hall", "Goa", "Drum & Bass", "Club-House", "Hardcore", "Terror", "Indie", "BritPop", "Afro-Punk", "Polsk Punk", "Beat", "Christian Gangsta Rap", "Heavy Metal", "Black Metal", "Crossover", "Contemporary Christian", "Christian Rock", "Merengue", "Salsa", "Thrash Metal", "Anime", "JPop", "Synthpop", "Abstract", "Art Rock", "Baroque", "Bhangra", "Big Beat", "Breakbeat", "Chillout", "Downtempo", "Dub", "EBM", "Eclectic", "Electro", "Electroclash", "Emo", "Experimental", "Garage", "Global", "IDM", "Illbient", "Industro-Goth", "Jam Band", "Krautrock", "Leftfield", "Lounge", "Math Rock", "New Romantic", "Nu-Breakz", "Post-Punk", "Post-Rock", "Psytrance", "Shoegaze", "Space Rock", "Trop Rock", "World Music", "Neoclassical", "Audiobook", "Audio Theatre", "Neue Deutsche Welle", "Podcast", "Indie Rock", "G-Funk", "Dubstep", "Garage Rock", "Psybient")
    fun parseKey(name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.any { it.code >= 128 }) return null
        val upper = trimmed.uppercase(Locale.ROOT)
        return upper.takeIf { it.all { c -> c.code in 32..125 && c != '=' && c !in 'a'..'z' } }
    }
    fun aliasKey(name: String): String? = aliases[name.uppercase(Locale.ROOT)]
    internal fun userTextKey(name: String): String? =
        txxxAliases[name.trim().uppercase(Locale.ROOT)] ?: parseKey(name)
}
