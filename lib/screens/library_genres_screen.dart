import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/controllers/clickable_metadata.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/providers/library_detail_provider.dart';
import 'package:sistrum/screens/library_genre_screen.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/services/library_detail_data.dart';
import 'package:sistrum/utils/adaptive_layout.dart';
import 'package:sistrum/utils/nav_bar_inset.dart';
import 'package:sistrum/widgets/app_search_field.dart';
import 'package:sistrum/widgets/app_sliver_header.dart';
import 'package:sistrum/widgets/cached_cover_image.dart';

/// The library's genres from the genre credits, each opening its songs.
class LibraryGenresScreen extends ConsumerStatefulWidget {
  const LibraryGenresScreen({super.key});

  @override
  ConsumerState<LibraryGenresScreen> createState() =>
      _LibraryGenresScreenState();
}

class _LibraryGenresScreenState extends ConsumerState<LibraryGenresScreen> {
  final _search = TextEditingController();
  bool _showSearch = false;
  String _query = '';

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final genres = ref.watch(libraryGenresProvider);
    final colors = Theme.of(context).colorScheme;
    final inset = wideListInset(context);
    final needle = LibraryDatabase.normalizeLookupText(_query);
    final rows = [
      for (final genre in genres.value ?? const <LibraryGenreEntry>[])
        if (needle.isEmpty || genre.key.contains(needle)) genre,
    ];
    return Scaffold(
      body: CustomScrollView(
        slivers: [
          AppSliverHeader.page(
            title: context.l10n.libraryGenres,
            actions: [
              IconButton(
                tooltip: context.l10n.librarySearchHint,
                icon: const Icon(Icons.search),
                onPressed: () => setState(() => _showSearch = !_showSearch),
              ),
            ],
          ),
          if (_showSearch)
            SliverToBoxAdapter(
              child: Padding(
                padding: EdgeInsets.fromLTRB(24 + inset, 8, 24 + inset, 16),
                child: AppSearchField(
                  controller: _search,
                  autofocus: true,
                  hintText: context.l10n.librarySearchHint,
                  clearTooltip: context.l10n.dialogClear,
                  samplesBackdrop: false,
                  onChanged: (value) => setState(() => _query = value.trim()),
                  onClear: () {
                    _search.clear();
                    setState(() => _query = '');
                  },
                ),
              ),
            ),
          if (genres.hasError)
            SliverToBoxAdapter(
              child: Center(
                child: TextButton.icon(
                  onPressed: () => ref.invalidate(libraryGenresProvider),
                  icon: const Icon(Icons.refresh),
                  label: Text(context.l10n.dialogRetry),
                ),
              ),
            )
          else if (!genres.hasValue)
            const SliverToBoxAdapter(
              child: Padding(
                padding: EdgeInsets.all(24),
                child: Center(child: CircularProgressIndicator.adaptive()),
              ),
            )
          else if (rows.isEmpty)
            SliverFillRemaining(
              hasScrollBody: false,
              child: Center(child: Text(context.l10n.libraryEmptyCollection)),
            )
          else
            SliverList.builder(
              itemCount: rows.length,
              itemBuilder: (context, index) {
                final genre = rows[index];
                return ListTile(
                  contentPadding: EdgeInsets.symmetric(
                    horizontal: 24 + inset,
                    vertical: 8,
                  ),
                  leading: SizedBox.square(
                    dimension: 56,
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(8),
                      child: _GenreCover(cover: genre.cover, colors: colors),
                    ),
                  ),
                  title: Text(genre.name),
                  subtitle: Text(
                    context.l10n.queueTrackCount(genre.trackCount),
                  ),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: () => pushPage<void>(
                    context,
                    (_) => LibraryGenreScreen(
                      genreKey: genre.key,
                      name: genre.name,
                    ),
                  ),
                );
              },
            ),
          const NavBarSliverSpacer(),
        ],
      ),
    );
  }
}

/// A library cover is a file path or a URL; a genre without one shows a tag.
class _GenreCover extends StatelessWidget {
  const _GenreCover({required this.cover, required this.colors});

  final String? cover;
  final ColorScheme colors;

  @override
  Widget build(BuildContext context) {
    Widget fallback() => ColoredBox(
      color: colors.surfaceContainerHighest,
      child: Center(
        child: Icon(Icons.category_outlined, color: colors.onSurfaceVariant),
      ),
    );
    if (cover == null) return fallback();
    return CachedCoverImage(
      imageUrl: cover!,
      fit: BoxFit.cover,
      errorWidget: (context, url, error) => fallback(),
    );
  }
}
