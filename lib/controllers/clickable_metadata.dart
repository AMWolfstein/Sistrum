import 'package:material_ui/material_ui.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/screens/library_artist_screen.dart';
import 'package:sistrum/screens/local_album_screen.dart';
import 'package:sistrum/services/shell_navigation_service.dart';
import 'package:sistrum/utils/artist_utils.dart';
import 'package:sistrum/widgets/app_bottom_sheet.dart';

/// Opens a local artist page for [artistName] (Library artist, not online).
Future<void> navigateToArtist(
  BuildContext context, {
  required String artistName,
}) async {
  if (artistName.trim().isEmpty) return;
  pushViaPreferredNavigator(
    context,
    (_) => LibraryArtistScreen(artist: artistName),
  );
}

/// Picks one credited artist when a song names several, then opens it.
Future<void> navigateToArtistCredits(
  BuildContext context, {
  required String artistNames,
}) async {
  final artists = splitArtistNames(artistNames);
  if (artists.isEmpty) return;
  final selected = artists.length == 1
      ? artists.single
      : await showAppBottomSheet<String>(
          context: context,
          useRootNavigator: true,
          title: context.l10n.mornyeGoToArtist,
          maxHeightFactor: 0.6,
          builder: (context) => ListView(
            shrinkWrap: true,
            children: [
              for (final artist in artists)
                AppSheetOption(
                  leading: const Icon(Icons.person_outline),
                  title: Text(artist),
                  onTap: () => Navigator.pop(context, artist),
                ),
            ],
          ),
        );
  if (!context.mounted || selected == null) return;
  await navigateToArtist(context, artistName: selected);
}

/// Opens the local album page of [albumName] by [artistName].
void navigateToAlbum(
  BuildContext context, {
  required String albumName,
  String? artistName,
  String? coverPath,
}) {
  if (albumName.trim().isEmpty) return;
  pushViaPreferredNavigator(
    context,
    (_) => LocalAlbumScreen(
      albumName: albumName,
      artistName: artistName ?? '',
      coverPath: coverPath,
    ),
  );
}

class DetailPage {
  const DetailPage.album(this.id, {this.providerId}) : kind = 'album';

  final String kind;
  final String id;
  final String? providerId;

  bool matches(DetailPage other) =>
      kind == other.kind &&
      id == other.id &&
      (providerId == null ||
          other.providerId == null ||
          providerId == other.providerId);

  static final _routes = Expando<DetailPage>('detail page');

  static void mark(BuildContext context, DetailPage page) {
    final route = ModalRoute.of(context);
    if (route != null) _routes[route] = page;
  }

  static bool _isOnTop(NavigatorState navigator, DetailPage page) {
    Route<dynamic>? top;
    // Reads the top route without popping anything.
    navigator.popUntil((route) {
      top = route;
      return true;
    });
    final shown = top == null ? null : _routes[top!];
    return shown != null && shown.matches(page);
  }
}

/// Pushes detail pages into the active tab navigator when invoked from a root
/// modal such as Now Playing. The root modal is dismissed first so the album
/// is immediately visible instead of being hidden behind it. A [page] that
/// is already on top is not pushed again.
void pushViaPreferredNavigator(
  BuildContext context,
  WidgetBuilder builder, {
  DetailPage? page,
}) {
  final currentNavigator = Navigator.of(context);
  final rootNavigator = Navigator.of(context, rootNavigator: true);
  final activeTabNavigator = ShellNavigationService.activeTabNavigator();

  void push(NavigatorState navigator) {
    if (page != null && DetailPage._isOnTop(navigator, page)) return;
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

Future<T?> pushPage<T>(BuildContext context, WidgetBuilder builder) =>
    Navigator.push(context, MaterialPageRoute<T>(builder: builder));

class ClickableArtistName extends StatelessWidget {
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
  Widget build(BuildContext context) {
    final artists = splitArtistNames(artistName);
    if (artists.length <= 1) {
      return GestureDetector(
        onTap: artists.isEmpty
            ? null
            : () => navigateToArtist(context, artistName: artists.single),
        child: Text(
          artistName,
          style: style,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          textAlign: textAlign,
        ),
      );
    }
    return GestureDetector(
      onTap: () => navigateToArtistCredits(context, artistNames: artistName),
      child: Text(
        artistName,
        style: style,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        textAlign: textAlign ?? TextAlign.start,
      ),
    );
  }
}

class ClickableAlbumName extends StatelessWidget {
  final String albumName;
  final String? artistName;
  final String? coverPath;
  final TextStyle? style;
  final int? maxLines;
  final TextOverflow? overflow;
  final TextAlign? textAlign;

  const ClickableAlbumName({
    super.key,
    required this.albumName,
    this.artistName,
    this.coverPath,
    this.style,
    this.maxLines,
    this.overflow,
    this.textAlign,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: () => navigateToAlbum(
        context,
        albumName: albumName,
        artistName: artistName,
        coverPath: coverPath,
      ),
      child: Text(
        albumName,
        style: style,
        maxLines: maxLines,
        overflow: overflow,
        textAlign: textAlign,
      ),
    );
  }
}
