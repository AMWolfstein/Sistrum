import 'package:sistrum/utils/string_utils.dart';

/// Whether a release counts as a single: a stored album type decides, and
/// without one a one-track release, or a release named Single(s) with at most
/// two tracks, is a single. A missing track count counts as at most two.
bool isSingleRelease({String? albumType, String? albumName, int? totalTracks}) {
  final rawType = albumType?.toLowerCase();
  if (rawType == 'single' || rawType == 'ep') return true;
  final storedType = normalizeOptionalString(albumType)?.toLowerCase();
  if (storedType != null && storedType.isNotEmpty) return false;
  if (totalTracks == 1) return true;
  final name = normalizeOptionalString(albumName)?.toLowerCase();
  if (name == 'single' || name == 'singles') {
    return totalTracks == null || totalTracks <= 2;
  }
  return false;
}
