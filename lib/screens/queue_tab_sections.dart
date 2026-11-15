part of 'queue_tab.dart';

final _letterPattern = RegExp(r'[\p{L}]', unicode: true);

final _monthYearFormats = <String, DateFormat>{};

DateFormat _monthYearFormat(BuildContext context) {
  final locale = Localizations.localeOf(context).toLanguageTag();
  return _monthYearFormats.putIfAbsent(locale, () => DateFormat.yMMMM(locale));
}

/// First letter of [text] for an alphabetical index; digits and symbols
/// share `#`.
String _sectionInitial(String text) {
  final trimmed = text.trimLeft();
  for (final character in trimmed.characters) {
    final upper = character.toUpperCase();
    if (upper != character.toLowerCase()) return upper;
    if (_letterPattern.hasMatch(character)) return upper;
    return '#';
  }
  return '#';
}

const _alphabeticalSorts = {'a-z', 'z-a'};
const _artistSorts = {'artist-asc', 'artist-desc'};
const _albumSorts = {'album-asc', 'album-desc'};
const _dateSorts = {'latest', 'oldest'};

extension _QueueTabSections on _QueueTabState {
  Iterable<String> get _availableLibrarySortModes {
    final mode = widget.librarySection;
    return mode == 'playlists'
        ? librarySortModes.where((sort) => !sort.startsWith('size-'))
        : librarySortModes;
  }

  String _sortLabel(BuildContext context, String mode) {
    final l10n = context.l10n;
    return switch (mode) {
      'oldest' => l10n.libraryFilterSortOldest,
      'size-desc' => l10n.libraryFilterSortSizeLargest,
      'size-asc' => l10n.libraryFilterSortSizeSmallest,
      'a-z' => l10n.searchSortTitleAZ,
      'z-a' => l10n.searchSortTitleZA,
      'artist-asc' => l10n.searchSortArtistAZ,
      'artist-desc' => l10n.searchSortArtistZA,
      'album-asc' => l10n.libraryFilterSortAlbumAsc,
      'album-desc' => l10n.libraryFilterSortAlbumDesc,
      'release-newest' => l10n.searchSortDateNewest,
      'release-oldest' => l10n.searchSortDateOldest,
      'genre-asc' => l10n.libraryFilterSortGenreAsc,
      'genre-desc' => l10n.libraryFilterSortGenreDesc,
      _ => l10n.libraryFilterSortLatest,
    };
  }

  /// Section label of [item] under the current sort, or null when the sort
  /// has no readable sections (genre and release dates are not loaded).
  String? _trackSectionLabel(BuildContext context, UnifiedLibraryItem item) {
    if (_alphabeticalSorts.contains(_sortMode)) {
      return _sectionInitial(item.trackName);
    }
    if (_artistSorts.contains(_sortMode)) {
      return _sectionInitial(item.artistName);
    }
    if (_albumSorts.contains(_sortMode)) {
      return _sectionInitial(item.albumName);
    }
    if (_dateSorts.contains(_sortMode)) {
      return _monthYearFormat(context).format(item.addedAt);
    }
    return null;
  }

  String? _albumSectionLabel(
    BuildContext context, {
    required String album,
    required String artist,
    required DateTime added,
  }) {
    if (_alphabeticalSorts.contains(_sortMode) ||
        _albumSorts.contains(_sortMode)) {
      return _sectionInitial(album);
    }
    if (_artistSorts.contains(_sortMode)) return _sectionInitial(artist);
    if (_dateSorts.contains(_sortMode)) {
      return _monthYearFormat(context).format(added);
    }
    return null;
  }

  Future<T?> _showLibraryOptionsSheet<T>(
    BuildContext context, {
    required ScrollableWidgetBuilder builder,
  }) => showAppModalBottomSheet<T>(
    context: context,
    useRootNavigator: true,
    isScrollControlled: true,
    builder: (context) => AppScrollableSheet(builder: builder),
  );
}
