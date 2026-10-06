package me.misa198.airmedy.sync

import android.content.ContentResolver
import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.ext.SdkExtensions
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.text.Normalizer
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import me.misa198.airmedy.library.LocalAlbumRef
import me.misa198.airmedy.library.LocalArtistRef
import me.misa198.airmedy.library.LocalComposer
import me.misa198.airmedy.library.LocalGenre
import me.misa198.airmedy.library.LocalLibrarySnapshot
import me.misa198.airmedy.library.LocalTrack

/** Audio asset row resolved for a scanned track, keyed by track id in the scan result. */
internal data class LocalScanAudio(
    val trackId: String,
    val absolutePath: String,
    val sha256: String,
    val size: Long,
)

/**
 * The one place an album's artwork key is derived. Every track of the album
 * ([LocalTrack.artworkKey] and its album ref) and the album's [LocalScanArtwork]
 * row must carry this same value: `writeLocalLibrary` keeps a track's key only
 * when an artwork row has it, and the track query joins on it.
 */
internal fun albumArtworkKey(albumKey: String): String = "album-$albumKey"

/** `sync_assets` id of an artwork row; the track query joins on this same `'artwork:' || artworkKey` form. */
internal fun artworkAssetId(artworkKey: String): String = "artwork:$artworkKey"

/** Inverse of [artworkAssetId]. */
internal fun artworkKeyOfAssetId(assetId: String): String = assetId.removePrefix("artwork:")

/** Copied album artwork, persisted under the library store's artwork directory. */
internal data class LocalScanArtwork(
    val artworkKey: String,
    val relativePath: String,
    val sha256: String,
    val size: Long,
)

internal data class LocalLibraryScanResult(
    val snapshot: LocalLibrarySnapshot,
    val audio: Map<String, LocalScanAudio>,
    val artwork: List<LocalScanArtwork>,
    val skipped: SkippedFilesSummary = SkippedFilesSummary(0L, emptyList()),
)

/** A previously scanned track's identity/metadata, read back before a rescan so
 *  unchanged files can skip re-parsing their embedded tags. */
internal data class PriorTrackScanState(
    val identityHash: String,
    val schemaVersion: Int,
    val year: Int,
    val releaseDate: String,
    val bpm: Int,
    val label: String,
    val isrc: String,
    val copyright: String,
    val explicit: Boolean = false,
    /** The codec [MediaStoreLibraryScanner] sniffed for this file (see realCodec). */
    val codec: String = "",
    /** Raw embedded artist/album-artist/album tags, kept so unchanged files keep the
     *  MediaStore-missing fallback without re-parsing. */
    val tagArtist: String = "",
    val tagAlbumArtist: String = "",
    val tagAlbum: String = "",
)

/** A previously copied album artwork file, read back so unchanged albums skip
 *  re-decoding/re-encoding their artwork. */
internal data class PriorArtworkScanState(
    val sha256: String,
    val size: Long,
    val relativePath: String,
)

internal data class PriorLibraryScanState(
    val tracksByTrackId: Map<String, PriorTrackScanState> = emptyMap(),
    /** Keyed by [albumArtworkKey]. */
    val artworkByArtworkKey: Map<String, PriorArtworkScanState> = emptyMap(),
)

/**
 * The artist/album-artist/album a scanned track resolves to. [albumFromTag] is true when
 * [album] came from the file's own tag, so the scanner can ignore MediaStore's shared
 * ALBUM_ID for that folder (see [albumKey]).
 */
internal data class ScanTagFields(
    val artist: String,
    val albumArtist: String,
    val album: String,
    val albumFromTag: Boolean,
)

/**
 * Resolves a track's artist/album-artist/album from MediaStore, falling back to the file's
 * own embedded tags only when MediaStore has no value. A non-blank MediaStore value is never
 * overridden, with one deliberate exception for the album: MediaStore reports the parent
 * folder name (e.g. "Music") as the album for files it has no album tag for, so when the
 * MediaStore album exactly equals [parentFolderName] and the file has its own non-blank
 * album tag, the file's tag wins. A truly untagged file keeps the folder name. Album artist
 * additionally falls back to the resolved artist, preserving the existing "album artist
 * defaults to the track artist" behaviour.
 *
 * [ScanTagFields.albumFromTag] is true when the album came from the tag (the folder-name
 * override or the MediaStore-null fallback): MediaStore gives every file in such a folder
 * one shared ALBUM_ID, so the scanner derives the album key from the name instead.
 *
 * The tag inputs may be null or blank; blanks are ignored.
 */
internal fun scanTagFields(
    mediaStoreArtist: String?,
    mediaStoreAlbumArtist: String?,
    mediaStoreAlbum: String?,
    tagArtist: String?,
    tagAlbumArtist: String?,
    tagAlbum: String?,
    parentFolderName: String?,
): ScanTagFields {
    fun nonBlank(value: String?): String? = value?.trim()?.takeIf(String::isNotEmpty)
    val artist = mediaStoreArtist ?: nonBlank(tagArtist) ?: ""
    val albumArtist = mediaStoreAlbumArtist ?: nonBlank(tagAlbumArtist) ?: artist
    val tagAlbumNonBlank = nonBlank(tagAlbum)
    // True only when the resolved album is the tag's own non-blank value: the MediaStore-null
    // fallback, or the folder-name override.
    val albumFromTag = tagAlbumNonBlank != null && (mediaStoreAlbum == null || mediaStoreAlbum == parentFolderName)
    val album = if (albumFromTag) tagAlbumNonBlank else mediaStoreAlbum ?: ""
    return ScanTagFields(artist, albumArtist, album, albumFromTag)
}

/** Representative track chosen to source an album's artwork, plus whether that
 *  specific track was found unchanged since the prior scan. */
private data class AlbumArtworkCandidate(val mediaUri: Uri, val absolutePath: String, val unchanged: Boolean)

/**
 * Reads the device MediaStore audio collection and synthesizes the platform-neutral
 * [LocalLibrarySnapshot] plus the audio/artwork rows [AndroidLibrarySyncStore] persists.
 *
 * Track ids are `local:<mediaStore _id>` so favorites and listening stats survive
 * rescans and re-installs. Album ids and all name-based reference ids are derived
 * deterministically so rescans do not churn the search index or favorite overlay.
 */
internal class MediaStoreLibraryScanner(
    private val contentResolver: ContentResolver,
    private val artworkDir: File,
    private val separators: TagSeparatorSettings = TagSeparatorSettings(),
) {
    fun scan(
        prior: PriorLibraryScanState = PriorLibraryScanState(),
        filter: MediaScanFilter = MediaScanFilter(),
        gate: ScanGate = AdmitAllGate,
    ): LocalLibraryScanResult {
        val genresByTrack = genresByTrackId()
        val audio = linkedMapOf<String, LocalScanAudio>()
        val tracks = mutableListOf<LocalTrack>()
        val albumRepresentatives = linkedMapOf<String, AlbumArtworkCandidate>()
        val skipped = SkippedFilesCounter()

        queryWithAudioFormatFallback(deviceHasAudioFormatColumns()) { projection ->
            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                Selection,
                null,
                SortOrder,
            )
        }?.use { cursor ->
            // Columns missing from this cursor (e.g. audio-format ones on an older
            // MediaProvider) resolve to -1 and read as null.
            val columns = (BaseProjection + AudioFormatColumns).associateWith { name -> cursor.getColumnIndex(name) }
            fun text(name: String): String? = columns[name]?.takeIf { it >= 0 }?.let(cursor::getString)?.trim()
            fun number(name: String): Long? = columns[name]?.takeIf { it >= 0 }?.let(cursor::getLong)
            fun tag(name: String): String? = mediaStoreTagValue(text(name))

            while (cursor.moveToNext()) {
                val mediaId = number(ColumnId) ?: continue
                val data = text(ColumnData) ?: continue
                if (!filter.allows(data)) continue
                val title = text(ColumnTitle) ?: ""
                if (title.isBlank()) continue
                val trackId = "local:$mediaId"
                val dateAdded = number(ColumnDateAdded) ?: 0L
                val dateModified = number(ColumnDateModified) ?: 0L
                val size = number(ColumnSize) ?: 0L
                val mime = text(ColumnMimeType) ?: ""

                val newIdentityHash = identityHash("$data|$size|$dateModified")
                val priorTrack = prior.tracksByTrackId[trackId]
                val format = audioFormatOf(mime, data)
                val codec = reusableCodec(priorTrack, newIdentityHash) ?: realCodec(format, mime, data)
                // FR-065a: with the Media3 engine a file is admitted only when the
                // decoder registry has a provider for its format/codec. A skipped file is
                // hidden, not deleted: its tags are never read, but its row is kept so it
                // reappears with its history intact once a decoder becomes available.
                val admission = gate.admit(format, codec) { count -> readHeaderBytes(data, count) }
                if (admission is Admission.Skipped) skipped.add(admission)
                val skipReason = (admission as? Admission.Skipped)?.reason
                val hidden = skipReason != null
                val hiddenBasis = if (hidden) hiddenTrackBasis(priorTrack, newIdentityHash) else HiddenTrackBasis(false, 0)
                // Skip re-parsing embedded tags (the expensive file-read work) when the
                // file itself hasn't changed and it was already parsed at the current
                // schema version; otherwise a bumped schema forces one full re-parse so
                // newly added fields get backfilled without a manual rescan. A hidden file
                // is never parsed at all: it reuses its prior values when unchanged.
                val unchanged = !hidden &&
                    priorTrack != null &&
                    priorTrack.identityHash == newIdentityHash &&
                    priorTrack.schemaVersion >= CurrentMetadataSchemaVersion
                val embeddedTags = if (hidden || unchanged) null else EmbeddedTagReader.embeddedTrackTags(data)
                // MediaStore reports no artist/album-artist/album for some containers (e.g.
                // WAV with an `id3 ` chunk), so fall back to the file's own tags when
                // MediaStore has no value. The raw tag values are persisted (see LocalTrack)
                // so an unchanged file keeps the fallback without another file read.
                val reusePrior = unchanged || hiddenBasis.usePrior
                val tagArtist = when {
                    reusePrior -> priorTrack?.tagArtist.orEmpty()
                    hidden -> ""
                    else -> embeddedTags?.artist.orEmpty()
                }
                val tagAlbumArtist = when {
                    reusePrior -> priorTrack?.tagAlbumArtist.orEmpty()
                    hidden -> ""
                    else -> embeddedTags?.albumArtist.orEmpty()
                }
                val tagAlbum = when {
                    reusePrior -> priorTrack?.tagAlbum.orEmpty()
                    hidden -> ""
                    else -> embeddedTags?.album.orEmpty()
                }
                val tagFields = scanTagFields(
                    mediaStoreArtist = tag(ColumnArtist),
                    mediaStoreAlbumArtist = tag(ColumnAlbumArtist),
                    mediaStoreAlbum = tag(ColumnAlbum),
                    tagArtist = tagArtist,
                    tagAlbumArtist = tagAlbumArtist,
                    tagAlbum = tagAlbum,
                    parentFolderName = File(data).parentFile?.name,
                )
                val artistName = tagFields.artist
                val albumArtistName = tagFields.albumArtist
                val albumName = tagFields.album
                // A tagged album gets its key from the name, not MediaStore's shared folder ALBUM_ID.
                val key = albumKey(if (tagFields.albumFromTag) null else number(ColumnAlbumId), albumArtistName, albumName)
                val artworkKey = albumArtworkKey(key)

                val year = when {
                    reusePrior -> priorTrack?.year ?: 0
                    hidden -> 0
                    else -> embeddedTags?.year ?: 0
                }
                val releaseDate = when {
                    reusePrior -> priorTrack?.releaseDate.orEmpty()
                    hidden -> ""
                    else -> embeddedTags?.releaseDate.orEmpty()
                }
                val bpm = when {
                    reusePrior -> priorTrack?.bpm ?: 0
                    hidden -> 0
                    else -> embeddedTags?.bpm ?: 0
                }
                val label = when {
                    reusePrior -> priorTrack?.label.orEmpty()
                    hidden -> ""
                    else -> embeddedTags?.label.orEmpty()
                }
                val isrc = when {
                    reusePrior -> priorTrack?.isrc.orEmpty()
                    hidden -> ""
                    else -> embeddedTags?.isrc.orEmpty()
                }
                val copyright = when {
                    reusePrior -> priorTrack?.copyright.orEmpty()
                    hidden -> ""
                    else -> embeddedTags?.copyright.orEmpty()
                }
                val explicit = when {
                    reusePrior -> priorTrack?.explicit ?: false
                    hidden -> false
                    else -> embeddedTags?.explicit == true
                }

                // Some OEMs report MediaStore.Audio.Media.TRACK as discNumber * 1000 +
                // trackNumber instead of the plain track number (e.g. 1001..1009 for a
                // single-disc, 12-track album).
                val rawTrack = number(ColumnTrackNumber) ?: 0L
                val trackNumber = if (rawTrack > 1000) (rawTrack % 1000).toInt() else rawTrack.toInt()
                if (size > 0L) {
                    audio[trackId] = LocalScanAudio(
                        trackId = trackId,
                        absolutePath = data,
                        sha256 = newIdentityHash,
                        size = size,
                    )
                }
                if (!hidden) {
                    albumRepresentatives.putIfAbsent(
                        key,
                        AlbumArtworkCandidate(
                            mediaUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId),
                            absolutePath = data,
                            unchanged = unchanged,
                        ),
                    )
                }
                tracks += LocalTrack(
                    id = trackId,
                    title = title,
                    sortTitle = text(ColumnTitleKey) ?: "",
                    artists = artistsOf(artistName),
                    album = LocalAlbumRef(
                        id = "local:album:$key",
                        title = albumName,
                        artworkKey = artworkKey,
                        year = year,
                        copyright = copyright,
                        createdAt = isoDate(dateAdded),
                    ),
                    albumArtists = artistsOf(albumArtistName),
                    composers = composersOf(tag(ColumnComposer) ?: ""),
                    genres = localGenresOf(genresByTrack[mediaId].orEmpty(), separators),
                    durationMillis = durationMillisOf(number(ColumnDuration), data),
                    discNumber = number(ColumnDiscNumber)?.toInt() ?: 0,
                    trackNumber = trackNumber,
                    createdAt = isoDate(dateAdded),
                    updatedAt = isoDate(dateModified),
                    addedAt = isoDate(dateAdded),
                    artworkKey = artworkKey,
                    archived = false,
                    format = format,
                    bitrate = number(ColumnBitrate)?.toInt() ?: 0,
                    sampleRate = number(ColumnSampleRate)?.toInt() ?: 0,
                    bitDepth = number(ColumnBitsPerSample)?.toInt() ?: 0,
                    // Sniffing an M4A's codec opens the file with MediaExtractor; an
                    // unchanged file keeps the codec found last time.
                    codec = codec,
                    fileSize = size,
                    releaseDate = releaseDate,
                    bpm = bpm,
                    label = label,
                    isrc = isrc,
                    explicit = explicit,
                    schemaVersion = if (hidden) hiddenBasis.schemaVersion else CurrentMetadataSchemaVersion,
                    tagArtist = tagArtist,
                    tagAlbumArtist = tagAlbumArtist,
                    tagAlbum = tagAlbum,
                    decoderUnavailable = skipReason.orEmpty(),
                )
            }
        }

        // Albums are independent (each writes its own <albumKey>.jpg), so their covers are
        // extracted on a few threads; results keep the album order.
        val artworkPool = Executors.newFixedThreadPool(ArtworkParallelism)
        val artwork = try {
            albumRepresentatives.map { (key, candidate) ->
                artworkPool.submit(Callable { copyArtworkOrReuse(key, candidate, prior.artworkByArtworkKey[albumArtworkKey(key)]) })
            }.mapNotNull { future ->
                try {
                    future.get()
                } catch (e: ExecutionException) {
                    throw e.cause ?: e
                }
            }
        } finally {
            artworkPool.shutdownNow()
        }
        val sorted = tracks.sortedWith(
            compareBy<LocalTrack> { it.album.title.lowercase() }
                .thenBy { it.discNumber }
                .thenBy { it.trackNumber }
                .thenBy { it.title.lowercase() },
        )
        return LocalLibraryScanResult(
            snapshot = LocalLibrarySnapshot(scannedAtMillis = System.currentTimeMillis(), tracks = sorted),
            audio = audio,
            artwork = artwork,
            skipped = skipped.summary(System.currentTimeMillis()),
        )
    }

    /** Up to the first [count] bytes of [path] (fewer for a shorter file), or null when it can't be read. */
    private fun readHeaderBytes(path: String, count: Int): ByteArray? = runCatching {
        RandomAccessFile(path, "r").use { file ->
            val bytes = ByteArray(minOf(count.toLong(), file.length()).toInt())
            file.readFully(bytes)
            bytes
        }
    }.getOrNull()

    /** Some .opus encoders never finalize the Ogg granule/seek position, so MediaStore
     *  reports 0 (or an implausible sub-1s value) for DURATION; fall back to decoding
     *  the file's own duration in that case. Fragmented MP4/M4A files read as 0 through
     *  both MediaStore and MediaMetadataRetriever, so their `mehd` box is read directly. */
    private fun durationMillisOf(mediaStoreDurationMillis: Long?, path: String): Long {
        val reported = mediaStoreDurationMillis?.coerceAtLeast(0L) ?: 0L
        if (reported >= MinimumPlausibleDurationMillis) return reported
        return runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(path)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            }
        }.getOrNull()?.takeIf { it > 0 }
            ?: EmbeddedTagReader.fragmentedMp4DurationMillis(path)
            ?: reported
    }

    /**
     * The M4A/MP4 container's mime type ("audio/mp4") doesn't distinguish the actual
     * codec inside it (e.g. AAC vs. ALAC), which previously made every M4A file
     * misreport as Lossy quality. Sniff the real per-track codec via MediaExtractor's
     * own demuxer for m4a/mp4; other formats name their one codec, so it is the format.
     */
    private fun realCodec(format: String, mime: String, path: String): String {
        if (format != "mp4" && format != "m4a") return format
        val fallback = mimeSubtype(mime)
        return runCatching {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(path)
                for (index in 0 until extractor.trackCount) {
                    val trackMime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME) ?: continue
                    if (!trackMime.startsWith("audio/")) continue
                    return@runCatching when (trackMime) {
                        // Android has no MediaFormat.MIMETYPE_AUDIO_ALAC constant; ALAC's
                        // own registered mime string is "audio/alac".
                        "audio/alac" -> "alac"
                        MediaFormat.MIMETYPE_AUDIO_AAC -> "aac"
                        else -> trackMime.substringAfter("audio/", trackMime)
                    }
                }
                fallback
            } finally {
                extractor.release()
            }
        }.getOrDefault(fallback)
    }

    private fun artistsOf(raw: String): List<LocalArtistRef> = localArtistsOf(raw, separators)

    private fun composersOf(raw: String): List<LocalComposer> = localComposersOf(raw, separators)

    /** MediaStore has no per-track genre projection; read the genre membership table once. */
    private fun genresByTrackId(): Map<Long, List<String>> {
        val names = mutableMapOf<Long, String>()
        contentResolver.query(
            MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(MediaStore.Audio.Genres._ID)
            val nameIndex = cursor.getColumnIndex(MediaStore.Audio.Genres.NAME)
            while (cursor.moveToNext()) {
                val id = if (idIndex >= 0) cursor.getLong(idIndex) else continue
                val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                if (id >= 0L && !name.isNullOrBlank()) names[id] = name.trim()
            }
        }
        val members = mutableMapOf<Long, MutableList<String>>()
        names.forEach { (genreId, name) ->
            val memberUri = MediaStore.Audio.Genres.Members.getContentUri(MediaStore.VOLUME_EXTERNAL, genreId)
            contentResolver.query(memberUri, arrayOf(MediaStore.Audio.Genres.Members._ID), null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndex(MediaStore.Audio.Genres.Members._ID)
                if (idIndex >= 0) while (cursor.moveToNext()) {
                    val trackId = cursor.getLong(idIndex)
                    if (trackId >= 0L) members.getOrPut(trackId) { mutableListOf() }.add(name)
                }
            }
        }
        return members
    }

    /** Reuses the previously copied artwork file when the representative track hasn't
     *  changed and that file still exists, instead of re-decoding/re-encoding it. */
    private fun copyArtworkOrReuse(albumKey: String, candidate: AlbumArtworkCandidate, reuse: PriorArtworkScanState?): LocalScanArtwork? {
        if (candidate.unchanged && reuse != null) {
            val file = File(artworkDir, "$albumKey.jpg")
            if (file.isFile && file.length() == reuse.size) {
                return LocalScanArtwork(artworkKey = albumArtworkKey(albumKey), relativePath = reuse.relativePath, sha256 = reuse.sha256, size = reuse.size)
            }
        }
        return copyArtwork(albumKey, candidate.mediaUri, candidate.absolutePath)
    }

    private fun copyArtwork(albumKey: String, mediaUri: Uri, absolutePath: String): LocalScanArtwork? {
        // Prefer the file's own tag bytes (some MediaStore providers return a
        // generic generated thumbnail instead of FLAC/ID3 embedded art), then
        // MediaStore's embedded picture, then a generated representative.
        val bitmap = embeddedArtworkFromFile(absolutePath)
            ?: embeddedArtwork(mediaUri)
            ?: generatedArtwork(mediaUri)
            ?: return null
        val file = File(artworkDir, "$albumKey.jpg")
        file.parentFile?.mkdirs()
        val wrote = file.outputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream) }
        bitmap.recycle()
        if (!wrote || !file.isFile || file.length() <= 0L) return null
        return LocalScanArtwork(
            artworkKey = albumArtworkKey(albumKey),
            relativePath = "artwork/${file.name}",
            sha256 = fileSha256(file),
            size = file.length(),
        )
    }

    /** A 1x1 request returns the file's embedded artwork without scaling, or fails when absent. */
    private fun embeddedArtwork(mediaUri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching {
            contentResolver.loadThumbnail(mediaUri, EmbeddedThumbnailRequestSize, null)
        }.getOrNull()?.takeIf { it.width > 1 && it.height > 1 }
    }

    /** Reads FLAC METADATA_BLOCK_PICTURE / ID3 APIC bytes directly from the audio file. */
    private fun embeddedArtworkFromFile(path: String): Bitmap? =
        EmbeddedTagReader.embeddedArtworkBytes(path)?.let { decodeSampled(it, ArtworkTargetPx) }

    private fun decodeSampled(bytes: ByteArray, targetPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetPx && bounds.outHeight / (sample * 2) >= targetPx) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun generatedArtwork(mediaUri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching {
            contentResolver.loadThumbnail(mediaUri, ArtworkTargetSize, null)
        }.getOrNull()
    }

    companion object {
        /** Bump whenever EmbeddedTagReader extraction gains/changes fields (e.g. the
         *  release date/BPM/label/ISRC/copyright extraction added alongside this
         *  constant) so the incremental-scan check forces one full re-parse per track
         *  to backfill them, instead of skipping unchanged files forever.
         *  3: the content advisory (explicit) flag.
         *  4: WAV/AIFF ID3 chunks after the audio data (past the old 24 MB prefix).
         *  5: M4A `moov` after `mdat` (past the old 24 MB prefix).
         *  6: FLAC Vorbis comments past the old 8 MB prefix (after large padding or covers).
         *  7: artist/album-artist/album embedded-tag fallback for containers MediaStore
         *     reports no tags for (e.g. WAV with an `id3 ` chunk). */
        const val CurrentMetadataSchemaVersion = 7

        const val ColumnId = MediaStore.Audio.Media._ID
        const val ColumnData = MediaStore.Audio.Media.DATA
        const val ColumnTitle = MediaStore.Audio.Media.TITLE
        const val ColumnTitleKey = MediaStore.Audio.Media.TITLE_KEY
        const val ColumnArtist = MediaStore.Audio.Media.ARTIST
        const val ColumnAlbumArtist = MediaStore.Audio.Media.ALBUM_ARTIST
        const val ColumnAlbum = MediaStore.Audio.Media.ALBUM
        const val ColumnAlbumId = MediaStore.Audio.Media.ALBUM_ID
        const val ColumnComposer = MediaStore.Audio.Media.COMPOSER
        const val ColumnDuration = MediaStore.Audio.Media.DURATION
        const val ColumnDiscNumber = MediaStore.Audio.Media.DISC_NUMBER
        const val ColumnTrackNumber = MediaStore.Audio.Media.TRACK
        const val ColumnDateAdded = MediaStore.Audio.Media.DATE_ADDED
        const val ColumnDateModified = MediaStore.Audio.Media.DATE_MODIFIED
        const val ColumnSize = MediaStore.Audio.Media.SIZE
        const val ColumnBitrate = MediaStore.Audio.Media.BITRATE
        // Only requested when available; see AudioFormatColumns.
        @android.annotation.SuppressLint("InlinedApi")
        const val ColumnSampleRate = MediaStore.Audio.AudioColumns.SAMPLERATE
        @android.annotation.SuppressLint("InlinedApi")
        const val ColumnBitsPerSample = MediaStore.Audio.Media.BITS_PER_SAMPLE
        const val ColumnMimeType = MediaStore.Audio.Media.MIME_TYPE

        /** Columns every supported MediaStore (API 31+) provides. */
        val BaseProjection: Array<String> = arrayOf(
            ColumnId,
            ColumnData,
            ColumnTitle,
            ColumnTitleKey,
            ColumnArtist,
            ColumnAlbumArtist,
            ColumnAlbum,
            ColumnAlbumId,
            ColumnComposer,
            ColumnDuration,
            ColumnDiscNumber,
            ColumnTrackNumber,
            ColumnDateAdded,
            ColumnDateModified,
            ColumnSize,
            ColumnBitrate,
            ColumnMimeType,
        )

        /**
         * SAMPLERATE / BITS_PER_SAMPLE exist from API 36, or on API 33-35 with T-extension
         * level 15 (a MediaProvider module update); never on API 31-32. MediaProvider
         * rejects the *whole* query for an unknown column ("Invalid column samplerate"),
         * so they are only requested where available (see [hasAudioFormatColumns]).
         */
        val AudioFormatColumns: Array<String> = arrayOf(ColumnSampleRate, ColumnBitsPerSample)

        /** Guards against very short clips (voice memos, notification sounds) that
         *  IS_MUSIC alone doesn't reliably exclude on every OEM. Durations under 1s are
         *  let through unfiltered: some .opus encoders leave DURATION unset/zero, and
         *  that case is corrected by a MediaMetadataRetriever fallback during the scan
         *  rather than excluded here. */
        const val MinimumDurationMillis = 30_000L

        /** Below this, MediaStore's DURATION is treated as unreliable (e.g. an
         *  un-finalized .opus seek table) and re-read via MediaMetadataRetriever. */
        const val MinimumPlausibleDurationMillis = 1_000L

        const val Selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND (" +
            "${MediaStore.Audio.Media.DURATION} IS NULL OR " +
            "${MediaStore.Audio.Media.DURATION} < $MinimumPlausibleDurationMillis OR " +
            "${MediaStore.Audio.Media.DURATION} >= $MinimumDurationMillis)"
        const val SortOrder = "${MediaStore.Audio.Media.ALBUM} COLLATE NOCASE, ${MediaStore.Audio.Media.DISC_NUMBER}, ${MediaStore.Audio.Media.TRACK}, ${MediaStore.Audio.Media.TITLE} COLLATE NOCASE"
        val EmbeddedThumbnailRequestSize = Size(1, 1)
        val ArtworkTargetSize = Size(1024, 1024)

        /** Covers extracted at once in a full scan. Each can hold a decoded bitmap of up
         *  to ~16 MB (2048 px ARGB), so this stays small against a 384 MB heap. */
        const val ArtworkParallelism = 4
    }
}

/** The codec found when [prior] was scanned, if the file is byte-for-byte the same one. */
internal fun reusableCodec(prior: PriorTrackScanState?, identityHash: String): String? =
    prior?.codec?.takeIf { prior.identityHash == identityHash && it.isNotBlank() }

/** Whether a hidden file should reuse its prior scan values (tags are not re-read). */
internal data class HiddenTrackBasis(val usePrior: Boolean, val schemaVersion: Int)

internal fun hiddenTrackBasis(prior: PriorTrackScanState?, identityHash: String): HiddenTrackBasis {
    val usePrior = prior != null && prior.identityHash == identityHash
    val schemaVersion = if (usePrior) prior.schemaVersion else 0
    return HiddenTrackBasis(usePrior, schemaVersion)
}

/**
 * A MediaStore tag column's value, or null when the file has no such tag. MediaStore
 * reports a missing artist as the literal "<unknown>" (MediaStore.UNKNOWN_STRING), which
 * would otherwise be shown and even listed as an artist; missing values are left blank
 * so the app's own "Unknown artist" fallback applies.
 */
internal fun mediaStoreTagValue(value: String?): String? =
    value?.trim()?.takeUnless { it.isEmpty() || it == MediaStore.UNKNOWN_STRING }

/**
 * Canonical format name ("mp3", "wav", "aiff", "m4a", ...) for a MediaStore MIME type.
 * MediaStore's subtype is often not a format name ("mpeg", "x-wav", "alac") and is
 * sometimes uninformative ("audio/ffmpeg" for anything its own parser doesn't know,
 * e.g. AIFF), so any subtype that isn't a known format falls back to the file extension.
 */
internal fun audioFormatOf(mime: String, path: String): String {
    val extension = path.substringAfterLast('/').substringAfterLast('.', "").lowercase()
    val subtype = mimeSubtype(mime)
    val fromMime = FormatBySubtype[subtype]
    // An MP4-family MIME type says nothing about .mp4 vs .m4a; keep the file's own name.
    if (fromMime == "m4a" && extension == "mp4") return "mp4"
    return fromMime
        ?: extension.takeIf(String::isNotEmpty)?.let { FormatByExtension[it] ?: it }
        ?: subtype.takeUnless { it in GenericAudioMimeSubtypes }?.removePrefix("x-")
        ?: ""
}

private fun mimeSubtype(mime: String): String =
    mime.lowercase().substringBefore(';').trim().let { if (it.startsWith("audio/")) it.removePrefix("audio/") else "" }

/** Subtypes never used as a last-resort format name: MediaStore's catch-all, generic binaries. */
private val GenericAudioMimeSubtypes = setOf("", "ffmpeg", "unknown", "octet-stream", "*")

private val FormatBySubtype = mapOf(
    "mpeg" to "mp3", "mp3" to "mp3", "mpeg3" to "mp3", "x-mpeg" to "mp3", "x-mp3" to "mp3", "mpg" to "mp3",
    "wav" to "wav", "x-wav" to "wav", "wave" to "wav", "vnd.wave" to "wav",
    "aiff" to "aiff", "x-aiff" to "aiff",
    "flac" to "flac", "x-flac" to "flac",
    "mp4" to "m4a", "m4a" to "m4a", "x-m4a" to "m4a", "alac" to "m4a",
    "aac" to "aac", "x-aac" to "aac", "aacp" to "aac",
    "x-ape" to "ape", "ape" to "ape", "x-wavpack" to "wv", "wavpack" to "wv",
    "x-dsf" to "dsf", "dsf" to "dsf", "x-dff" to "dff", "dff" to "dff",
    // Kept as "ogg" (not "opus"): existing libraries store Opus-in-Ogg this way.
    "ogg" to "ogg", "x-ogg" to "ogg", "vorbis" to "ogg", "opus" to "opus",
    "x-ms-wma" to "wma",
)

private val FormatByExtension = mapOf(
    "wave" to "wav", "aif" to "aiff", "aifc" to "aiff", "m4b" to "m4a", "oga" to "ogg", "mpga" to "mp3",
)

/**
 * Deterministic album key. MediaStore's ALBUM_ID distinguishes same-named albums (it hashes
 * the album name with the album artist, or the folder when there is none); ALBUM_KEY, used
 * before, is the album name alone and merged e.g. two artists' "Greatest Hits". Without an
 * ALBUM_ID, falls back to the album artist (the track artist when untagged) + album name.
 */
internal fun albumKey(mediaStoreAlbumId: Long?, albumArtist: String, album: String): String =
    mediaStoreAlbumId?.takeIf { it != 0L }?.toString() ?: sha256Hex("$albumArtist|$album").take(12)

/**
 * Splits an artist (or album artist) tag into artists with the configured delimiters. IDs
 * come from each resulting name, so "Björk" and "Bjork" still share one artist.
 */
internal fun localArtistsOf(raw: String, separators: TagSeparatorSettings): List<LocalArtistRef> =
    ArtistSeparator.splitArtistNames(raw, separators.delimiters, separators.enabled)
        .map { name -> LocalArtistRef(id = artistId(name), name = name, sortName = "") }
        .ifEmpty { listOf(LocalArtistRef(id = artistId(raw), name = raw, sortName = "")) }

/**
 * A track's genres from MediaStore's Genres table. Android keeps a tag like
 * "Soundtrack; Hip-Hop/Rap" as one genre, so each entry is split with the genre
 * delimiters (not the artist ones: "/" and "&" occur inside real genre names).
 */
internal fun localGenresOf(rawNames: List<String>, separators: TagSeparatorSettings): List<LocalGenre> =
    rawNames.flatMap { raw -> ArtistSeparator.splitArtistNames(raw, separators.genreDelimiters, separators.enabled) }
        .map { name -> LocalGenre(genreId(name), name) }
        .distinctBy(LocalGenre::id)

/** Splits a composer tag with the same delimiters as artists. */
internal fun localComposersOf(raw: String, separators: TagSeparatorSettings): List<LocalComposer> =
    ArtistSeparator.splitArtistNames(raw, separators.delimiters, separators.enabled)
        .map { name -> LocalComposer(id = composerId(name), name = name) }

internal fun artistId(name: String): String = "local:artist:" + sha256Hex(foldDiacritics(name)).take(16)

internal fun genreId(name: String): String = "local:genre:" + sha256Hex(foldDiacritics(name)).take(16)

internal fun composerId(name: String): String = "local:composer:" + sha256Hex(foldDiacritics(name)).take(16)

/** NFKD-decomposes and strips diacritical marks so "Björk" and "Bjork" hash the same. */
internal fun foldDiacritics(name: String): String =
    Normalizer.normalize(name.trim().lowercase(), Normalizer.Form.NFKD).replace(DiacriticalMarks, "")

private val DiacriticalMarks = Regex("\\p{Mn}+")

internal fun isoDate(epochSeconds: Long): String = if (epochSeconds > 0L) Instant.ofEpochSecond(epochSeconds).toString() else ""

internal fun identityHash(value: String): String = sha256Hex(value)

internal fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.encodeToByteArray())
    .joinToString("") { byte -> "%02x".format(byte) }

internal fun fileSha256(file: File): String = file.inputStream().use { input ->
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        if (read > 0) digest.update(buffer, 0, read)
    }
    digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

private val ArtworkTargetPx = 2048

/** Whether MediaStore has SAMPLERATE / BITS_PER_SAMPLE (see [MediaStoreLibraryScanner.AudioFormatColumns]). */
internal fun hasAudioFormatColumns(sdkInt: Int, tiramisuExtensionVersion: Int): Boolean =
    sdkInt >= 36 || (sdkInt >= Build.VERSION_CODES.TIRAMISU && tiramisuExtensionVersion >= 15)

private fun deviceHasAudioFormatColumns(): Boolean {
    val sdkInt = Build.VERSION.SDK_INT
    val tiramisuExtension = if (sdkInt >= Build.VERSION_CODES.TIRAMISU) SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) else 0
    return hasAudioFormatColumns(sdkInt, tiramisuExtension)
}

/** The scan projection, with the audio-format columns only when the device has them. */
internal fun scanProjection(includeAudioFormatColumns: Boolean): Array<String> =
    if (includeAudioFormatColumns) MediaStoreLibraryScanner.BaseProjection + MediaStoreLibraryScanner.AudioFormatColumns
    else MediaStoreLibraryScanner.BaseProjection

/**
 * Runs the scan [query], and if a MediaProvider that doesn't match the documented
 * availability rejects the audio-format columns (IllegalArgumentException "Invalid
 * column"), retries once without them rather than failing the whole scan.
 */
internal fun <T> queryWithAudioFormatFallback(includeAudioFormatColumns: Boolean, query: (Array<String>) -> T): T {
    if (!includeAudioFormatColumns) return query(scanProjection(false))
    return try {
        query(scanProjection(true))
    } catch (error: IllegalArgumentException) {
        Log.w("AirmedyScan", "MediaStore rejected the audio-format columns; scanning without sample rate/bit depth", error)
        query(scanProjection(false))
    }
}
