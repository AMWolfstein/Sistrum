import 'package:sistrum/utils/audio_format_utils.dart';
import 'package:sistrum/utils/string_utils.dart';

const highQualityBadgeBitrateThresholdKbps = 900;

final RegExp _bitDepthQualityPattern = RegExp(
  r'\b\d+\s*(?:-\s*)?bit\b',
  caseSensitive: false,
);
final RegExp _storedBitratePattern = RegExp(
  r'\b(\d+(?:\.\d+)?)\s*(k(?:bps)?|mbps)\b',
  caseSensitive: false,
);
final RegExp _storedBitDepthPattern = RegExp(
  r'\b(\d{1,3})(?:\s*[- ]?\s*bit\b|(?=/))',
  caseSensitive: false,
);
final RegExp _leadingDotPattern = RegExp(r'^\.');
final RegExp _gridBitrateTextPattern = RegExp(
  r'(\d+)\s*k(?:bps)?',
  caseSensitive: false,
);
final RegExp _gridBitrateIdPattern = RegExp(r'_(\d+)$');
final RegExp _highlightBitDepthPattern = RegExp(r'\b24(?:\s*[- ]?\s*bit|/)');
final RegExp _highlightKbpsPattern = RegExp(r'\b(\d+(?:\.\d+)?)\s*k(?:bps)?\b');
final RegExp _highlightMbpsPattern = RegExp(r'\b(\d+(?:\.\d+)?)\s*mbps\b');

String? _libraryFileFormatLabel(String? format) {
  final rawFormat = normalizeOptionalString(format);
  if (rawFormat == null) return null;

  return switch (normalizeAudioFormatValue(rawFormat)) {
    'flac' => 'FLAC',
    'alac' => 'ALAC',
    'wav' => 'WAV',
    'aiff' => 'AIFF',
    'aac' => 'AAC',
    'eac3' => 'EAC3',
    'ac3' => 'AC3',
    'ac4' => 'AC4',
    'mp3' => 'MP3',
    'opus' => 'OPUS',
    'm4a' => 'M4A',
    _ => rawFormat.replaceFirst(_leadingDotPattern, '').toUpperCase(),
  };
}

/// Builds the Library badge from metadata already held in memory: the bit
/// depth for lossless audio, the bitrate for lossy formats, whose bit depth is
/// not a useful quality signal.
String? buildLibraryAudioQualityLabel({
  String? format,
  String? filePath,
  String? fileName,
  int? bitrateKbps,
  int? bitDepth,
  String? storedQuality,
}) {
  final effectiveFormat =
      normalizeOptionalString(format) ??
      audioFormatForPath(filePath, fileName: fileName);
  final stored = normalizeOptionalString(storedQuality);
  final storedBitrate = _bitrateFromStoredQuality(stored);
  final storedBitDepth = _bitDepthFromStoredQuality(stored);
  final effectiveBitrate = bitrateKbps != null && bitrateKbps > 0
      ? bitrateKbps
      : storedBitrate;
  final effectiveBitDepth = bitDepth != null && bitDepth > 0
      ? bitDepth
      : storedBitDepth;
  final bitrateLabel = effectiveBitrate != null
      ? buildDisplayAudioQuality(
          bitrateKbps: effectiveBitrate,
          format: effectiveFormat,
        )
      : null;
  final bitDepthOnlyLabel = effectiveBitDepth != null
      ? '$effectiveBitDepth-bit'
      : null;
  if (isLossyAudioFormat(effectiveFormat)) {
    // Older rows may know the codec before their bitrate has been backfilled.
    // Keep a useful badge without showing stale lossless specs or inventing a
    // bitrate from the user's current download/conversion setting.
    return bitrateLabel ?? _libraryFileFormatLabel(effectiveFormat);
  }

  return bitDepthOnlyLabel ?? bitrateLabel ?? stored;
}

int? _bitrateFromStoredQuality(String? quality) {
  final match = _storedBitratePattern.firstMatch(quality ?? '');
  final value = double.tryParse(match?.group(1) ?? '');
  if (value == null || value <= 0) return null;
  final unit = match?.group(2)?.toLowerCase();
  return unit == 'mbps' ? (value * 1000).round() : value.round();
}

int? _bitDepthFromStoredQuality(String? quality) {
  final match = _storedBitDepthPattern.firstMatch(quality ?? '');
  final value = int.tryParse(match?.group(1) ?? '');
  return value != null && value > 0 ? value : null;
}

/// Keeps detailed bit-depth labels intact in Library grid badges while
/// shortening bitrate-only labels that already include a codec name.
String formatLibraryGridAudioQualityLabel(String quality) {
  final normalized = quality.trim().toLowerCase();
  if (_bitDepthQualityPattern.hasMatch(normalized)) return quality;

  final bitrateTextMatch = _gridBitrateTextPattern.firstMatch(quality);
  if (bitrateTextMatch != null) {
    return '${bitrateTextMatch.group(1)}k';
  }

  final bitrateIdMatch = _gridBitrateIdPattern.firstMatch(normalized);
  if (bitrateIdMatch != null) {
    return '${bitrateIdMatch.group(1)}k';
  }

  return quality.split(' ').first;
}

bool isDetailedLibraryAudioQualityLabel(String quality) {
  final normalized = quality.trim().toLowerCase();
  return _bitDepthQualityPattern.hasMatch(normalized) &&
      normalized.contains('/');
}

/// Preserves the highlighted color used by legacy 24-bit Library badges while
/// also supporting newer labels that display a measured bitrate instead.
bool shouldHighlightAudioQualityBadge(String quality) {
  final normalized = quality.trim().toLowerCase();
  if (_highlightBitDepthPattern.hasMatch(normalized)) {
    return true;
  }

  final kbpsMatch = _highlightKbpsPattern.firstMatch(normalized);
  final kbps = double.tryParse(kbpsMatch?.group(1) ?? '');
  if (kbps != null) {
    return kbps > highQualityBadgeBitrateThresholdKbps;
  }

  final mbpsMatch = _highlightMbpsPattern.firstMatch(normalized);
  final mbps = double.tryParse(mbpsMatch?.group(1) ?? '');
  return mbps != null && mbps * 1000 > highQualityBadgeBitrateThresholdKbps;
}
