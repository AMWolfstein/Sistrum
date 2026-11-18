import 'package:audio_service/audio_service.dart' show MediaItem;
import 'package:sistrum/models/library_database_models.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/services/music_player_runtime.dart';
import 'package:sistrum/services/playback_metadata.dart';
import 'package:sistrum/utils/file_access.dart' show isContentUri;
import 'package:sistrum/utils/int_utils.dart';
import 'package:sistrum/utils/ios_container_paths.dart';
import 'package:sistrum/utils/string_utils.dart';

class PlayableMedia {
  final String id;
  final String source;
  final String? originSource;
  final String title;
  final String artist;
  final String album;
  final String? artUri;
  final Duration? duration;
  final int? bitDepth;
  final int? sampleRate;
  final int? bitrate;
  final String? format;
  final bool explicit;
  final String? genre;
  final bool autoplay;
  final Map<String, dynamic> displayMetadata;

  const PlayableMedia({
    required this.id,
    required this.source,
    this.originSource,
    required this.title,
    required this.artist,
    this.album = '',
    this.artUri,
    this.duration,
    this.bitDepth,
    this.sampleRate,
    this.bitrate,
    this.format,
    this.explicit = false,
    this.genre,
    this.autoplay = false,
    this.displayMetadata = const {},
  });

  /// This song as one the listener queued: an Autoplay addition loses its
  /// [autoplay] mark, so Autoplay never replaces or reorders the copy.
  PlayableMedia get chosenByListener => !autoplay
      ? this
      : PlayableMedia(
          id: id,
          source: source,
          originSource: originSource,
          title: title,
          artist: artist,
          album: album,
          artUri: artUri,
          duration: duration,
          bitDepth: bitDepth,
          sampleRate: sampleRate,
          bitrate: bitrate,
          format: format,
          explicit: explicit,
          genre: genre,
          displayMetadata: displayMetadata,
        );

  bool get isContentUri => source.startsWith('content://');
  bool get isNetwork =>
      const {'network', 'http', 'https'}.contains(Uri.tryParse(source)?.scheme);

  Map<String, dynamic> toJson() => {
    'id': id,
    'source': source,
    if (originSource != null) 'originSource': originSource,
    'title': title,
    'artist': artist,
    'album': album,
    if (artUri != null && artUri!.isNotEmpty) 'artUri': artUri,
    if (duration != null) 'durationMs': duration!.inMilliseconds,
    if (bitDepth != null && bitDepth! > 0) 'bitDepth': bitDepth,
    if (sampleRate != null && sampleRate! > 0) 'sampleRate': sampleRate,
    if (bitrate != null && bitrate! > 0) 'bitrate': bitrate,
    if (format != null && format!.trim().isNotEmpty) 'format': format,
    if (explicit) 'explicit': true,
    if (genre != null) 'genre': genre,
    if (autoplay) 'autoplay': true,
    if (displayMetadata.isNotEmpty) 'displayMetadata': displayMetadata,
  };

  static PlayableMedia? fromJson(
    Map<String, dynamic> json, {
    String? iosDocumentsPath,
  }) {
    final id = json['id'] as String?;
    final source = json['source'] as String?;
    if (id == null || id.isEmpty || source == null || source.isEmpty) {
      return null;
    }
    final durationMs = (json['durationMs'] as num?)?.toInt();
    var artwork = json['artUri'] as String?;
    if (artwork != null && iosDocumentsPath != null) {
      final uri = Uri.tryParse(artwork);
      if (uri != null && uri.scheme == 'file' && uri.host.isEmpty) {
        try {
          final path = uri.toFilePath();
          final rebased = rebaseIosSandboxPath(path, iosDocumentsPath);
          if (rebased != path) artwork = Uri.file(rebased).toString();
        } on UnsupportedError {
          // Leave malformed or non-local artwork URIs to the image fallback.
        }
      }
    }
    return PlayableMedia(
      id: id,
      source: source,
      originSource: json['originSource'] as String?,
      title: json['title'] as String? ?? '',
      artist: json['artist'] as String? ?? '',
      album: json['album'] as String? ?? '',
      artUri: artwork,
      duration: (durationMs != null && durationMs > 0)
          ? Duration(milliseconds: durationMs)
          : null,
      bitDepth: readPositiveInt(json['bitDepth']),
      sampleRate: readPositiveInt(json['sampleRate']),
      bitrate: readPositiveInt(json['bitrate']),
      format: json['format']?.toString(),
      explicit: parseExplicitFlag(json['explicit']) == true,
      genre: json['genre'] as String?,
      autoplay: json['autoplay'] == true,
      displayMetadata: playbackDisplayMetadata(json['displayMetadata']),
    );
  }

  /// The item players show.
  MediaItem toMediaItem({
    String? resolvedSource,
    String? resolvedArtwork,
    String? unknownTitle,
    String? unknownArtist,
  }) {
    final artwork = resolvedArtwork ?? artUri;
    return MediaItem(
      id: id,
      title: title.isEmpty
          ? unknownTitle ?? musicPlayerRuntime.unknownTitle
          : title,
      artist: artist.isEmpty
          ? unknownArtist ?? musicPlayerRuntime.unknownArtist
          : artist,
      album: album.isEmpty ? null : album,
      duration: duration,
      artUri: artwork != null && artwork.isNotEmpty
          ? Uri.tryParse(artwork)
          : null,
      extras: {
        'source': source,
        if (originSource != null) 'originSource': originSource,
        if (displayMetadata.isNotEmpty) 'displayMetadata': displayMetadata,
        if (resolvedSource != null && resolvedSource.isNotEmpty)
          'resolvedSource': resolvedSource,
        if (bitDepth != null && bitDepth! > 0) 'bit_depth': bitDepth,
        if (sampleRate != null && sampleRate! > 0) 'sample_rate': sampleRate,
        if (bitrate != null && bitrate! > 0) 'bitrate': bitrate,
        if (format != null && format!.trim().isNotEmpty)
          'format': format!.trim(),
        if (explicit) 'explicit': true,
        if (autoplay) 'autoplay': true,
      },
    );
  }
}

/// A cover path or URL as a media artwork URI.
String? normalizeArtUri(String cover) {
  final value = cover.trim();
  if (value.isEmpty) return null;
  if (value.startsWith('http') ||
      isContentUri(value) ||
      value.startsWith('file://')) {
    return value;
  }
  return Uri.file(value).toString();
}

/// A catalog [track] that plays the local file at [path].
PlayableMedia playableFromTrack(Track track, String path) {
  final cover = track.coverUrl ?? '';
  return PlayableMedia(
    id: path,
    source: path,
    title: track.name,
    artist: track.artistName,
    album: track.albumName,
    artUri: normalizeArtUri(cover),
    duration: track.duration > 0 ? Duration(seconds: track.duration) : null,
    explicit: track.isExplicit,
    genre: track.genre,
  );
}

PlayableMedia playableFromLocal(LocalLibraryItem item) {
  return PlayableMedia(
    id: item.id,
    source: item.filePath,
    title: item.trackName,
    artist: item.artistName,
    album: item.albumName,
    artUri: normalizeArtUri(item.coverPath ?? ''),
    duration: (item.duration != null && item.duration! > 0)
        ? Duration(seconds: item.duration!)
        : null,
    bitDepth: item.bitDepth,
    sampleRate: item.sampleRate,
    bitrate: item.bitrate,
    format: item.format,
    explicit: item.explicit,
    genre: item.genre,
  );
}
