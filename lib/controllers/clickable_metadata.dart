import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/providers/library_tag_credits_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/screens/album_screen.dart';
import 'package:sistrum/screens/artist_screen.dart';
import 'package:sistrum/services/library_detail_data.dart';
import 'package:sistrum/services/library_tag_credits.dart';
import 'package:sistrum/services/shell_navigation_service.dart';
import 'package:sistrum/widgets/app_bottom_sheet.dart';

/// Opens the library artist behind [artistNames]: one credited artist opens
/// directly, several offer a choice.
Future<void> navigateToArtistCredits(
  BuildContext context, {
  required String artistNames,
}) async {
  final rules = ProviderScope.containerOf(
    context,
    listen: false,
  ).read(tagCreditRulesProvider);
  final names = _creditedArtistNames(artistNames, rules);
  if (names.isEmpty) return;
  final selected = names.length == 1
      ? names.single
      : await showAppBottomSheet<String>(
          context: context,
          useRootNavigator: true,
          title: context.l10n.sistrumGoToArtist,
          maxHeightFactor: 0.6,
          builder: (context) => ListView(
            shrinkWrap: true,
            children: [
              for (final name in names)
                AppSheetOption(
                  leading: const Icon(Icons.person_outline),
                  title: Text(name),
                  onTap: () => Navigator.pop(context, name),
                ),
            ],
          ),
        );
  if (!context.mounted || selected == null) return;
  openLibraryArtist(context, selected);
}

/// Opens the library's artist page: its albums and songs come from the
/// library's own files.
void openLibraryArtist(BuildContext context, String artistName) {
  pushViaPreferredNavigator(
    context,
    (context) => ArtistScreen(artistName: artistName),
  );
}

/// Opens the library artist [artistName]; a name the library does not hold
/// shows a message instead. Only the library is read.
Future<void> navigateToArtist(
  BuildContext context, {
  required String artistName,
}) async {
  if (artistName.isEmpty) return;
  final container = ProviderScope.containerOf(context, listen: false);
  final found = await libraryHasArtist(
    artistName,
    credits: container.read(tagCreditScopeProvider),
    includeLocal: container.read(settingsProvider).localLibraryEnabled,
  );
  if (!context.mounted) return;
  if (!found) {
    _showUnavailable(context, context.l10n.trackArtist);
    return;
  }
  openLibraryArtist(context, artistName);
}

/// Opens the library album named [albumName] (by [artistName] when several
/// share the title); a name the library does not hold shows a message.
Future<void> navigateToAlbum(
  BuildContext context, {
  required String albumName,
  String? artistName,
}) async {
  if (albumName.isEmpty) return;
  final container = ProviderScope.containerOf(context, listen: false);
  final albumKey = await libraryAlbumKeyFor(
    albumName,
    artistName: artistName,
    includeLocal: container.read(settingsProvider).localLibraryEnabled,
  );
  if (!context.mounted) return;
  if (albumKey == null) {
    _showUnavailable(context, context.l10n.trackAlbum);
    return;
  }
  pushViaPreferredNavigator(
    context,
    (_) => AlbumScreen.library(albumKey: albumKey),
  );
}

/// Opens the library album of [track] by the album name it carries.
Future<void> navigateToTrackAlbum(BuildContext context, Track track) async {
  final albumName = track.albumName.trim();
  if (albumName.isEmpty) {
    _showUnavailable(context, context.l10n.trackAlbum);
    return;
  }
  await navigateToAlbum(
    context,
    albumName: albumName,
    artistName: _preferredAlbumArtist(track),
  );
}

String _preferredAlbumArtist(Track track) {
  final albumArtist = (track.albumArtist ?? '').trim();
  return albumArtist.isNotEmpty ? albumArtist : track.artistName;
}

/// Pushes detail pages into the active tab navigator when invoked from a root
/// modal such as Now Playing. The root modal is dismissed first so the album
/// is immediately visible instead of being hidden behind it.
void pushViaPreferredNavigator(BuildContext context, WidgetBuilder builder) {
  final currentNavigator = Navigator.of(context);
  final rootNavigator = Navigator.of(context, rootNavigator: true);
  final activeTabNavigator = ShellNavigationService.activeTabNavigator();

  void push(NavigatorState navigator) {
    navigator.push(MaterialPageRoute<void>(builder: builder));
  }

  final shouldRouteToTabNavigator =
      identical(currentNavigator, rootNavigator) && activeTabNavigator != null;

  if (!shouldRouteToTabNavigator) {
    push(currentNavigator);
    return;
  }

  final currentRoute = ModalRoute.of(context);
  final shouldPopCurrentRoute =
      currentRoute != null && currentRoute.isFirst == false;

  if (shouldPopCurrentRoute && currentNavigator.canPop()) {
    currentNavigator.pop();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!activeTabNavigator.mounted) return;
      push(activeTabNavigator);
    });
    return;
  }

  push(activeTabNavigator);
}

/// Pushes a [MaterialPageRoute] built by [builder] onto the nearest navigator.
Future<T?> pushPage<T>(BuildContext context, WidgetBuilder builder) =>
    Navigator.push(context, MaterialPageRoute<T>(builder: builder));

void _showUnavailable(BuildContext context, String type) {
  ScaffoldMessenger.of(context).showSnackBar(
    SnackBar(content: Text(context.l10n.clickableInformationUnavailable(type))),
  );
}

class ClickableArtistName extends StatefulWidget {
  final String artistName;
  final TextStyle? style;
  final TextAlign? textAlign;

  const ClickableArtistName({
    super.key,
    required this.artistName,
    this.style,
    this.textAlign,
  });

  @override
  State<ClickableArtistName> createState() => _ClickableArtistNameState();
}

class _ClickableArtistNameState extends State<ClickableArtistName> {
  List<String> _artistNames = const [];
  final List<TapGestureRecognizer> _recognizers = [];

  @override
  void initState() {
    super.initState();
    _rebuildArtistNames();
  }

  @override
  void didUpdateWidget(covariant ClickableArtistName oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.artistName != widget.artistName) _rebuildArtistNames();
  }

  @override
  void dispose() {
    _disposeRecognizers();
    super.dispose();
  }

  void _disposeRecognizers() {
    for (final recognizer in _recognizers) {
      recognizer.dispose();
    }
    _recognizers.clear();
  }

  void _rebuildArtistNames() {
    _disposeRecognizers();
    _artistNames = _creditedArtistNames(
      widget.artistName,
      ProviderScope.containerOf(
        context,
        listen: false,
      ).read(tagCreditRulesProvider),
    );
    if (_artistNames.length <= 1) return;

    for (final name in _artistNames) {
      _recognizers.add(
        TapGestureRecognizer()
          ..onTap = () => navigateToArtist(context, artistName: name),
      );
    }
  }

  List<InlineSpan> _buildMultiArtistSpans() {
    final spans = <InlineSpan>[];
    for (var i = 0; i < _artistNames.length; i++) {
      spans.add(
        TextSpan(
          text: _artistNames[i],
          style: widget.style,
          recognizer: _recognizers[i],
        ),
      );
      if (i < _artistNames.length - 1) {
        spans.add(TextSpan(text: ', ', style: widget.style));
      }
    }
    return spans;
  }

  @override
  Widget build(BuildContext context) {
    if (_artistNames.isEmpty) {
      return Text(
        widget.artistName,
        style: widget.style,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        textAlign: widget.textAlign,
      );
    }

    if (_artistNames.length == 1) {
      final name = _artistNames.first;
      return GestureDetector(
        onTap: () => navigateToArtist(context, artistName: name),
        child: Text(
          name,
          style: widget.style,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          textAlign: widget.textAlign,
        ),
      );
    }

    return Text.rich(
      TextSpan(style: widget.style, children: _buildMultiArtistSpans()),
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      textAlign: widget.textAlign ?? TextAlign.start,
    );
  }
}

/// The artist names of [rawNames] split by the tag credit rules, each once.
List<String> _creditedArtistNames(String rawNames, TagCreditRules rules) {
  final names = <String>[];
  final seen = <String>{};
  for (final parsed in splitTagCredits(
    rawNames,
    TagCreditField.artist,
    rules,
  )) {
    final key = parsed.toLowerCase().replaceAll(RegExp(r'\s+'), ' ').trim();
    if (key.isEmpty || !seen.add(key)) continue;
    names.add(parsed);
  }
  return names;
}
