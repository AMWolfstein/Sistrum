import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/providers/library_collections_provider.dart';
import 'package:sistrum/controllers/clickable_metadata.dart';
import 'package:sistrum/widgets/cached_cover_image.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/utils/adaptive_layout.dart';
import 'package:sistrum/utils/nav_bar_inset.dart';
import 'package:sistrum/widgets/app_icon_button.dart';
import 'package:sistrum/widgets/app_shape.dart';
import 'package:sistrum/widgets/app_sliver_header.dart';

class FavoriteArtistsScreen extends ConsumerWidget {
  const FavoriteArtistsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final artists = ref.watch(
      libraryCollectionsProvider.select((state) => state.favoriteArtists),
    );
    final colorScheme = Theme.of(context).colorScheme;
    final bottomInset = context.navBarBottomInset;

    return Scaffold(
      body: CustomScrollView(
        slivers: [
          AppSliverHeader.page(title: context.l10n.collectionFavoriteArtists),
          if (artists.isEmpty)
            SliverFillRemaining(
              hasScrollBody: false,
              child: Center(
                child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        Icons.favorite,
                        size: 60,
                        color: colorScheme.onSurfaceVariant,
                      ),
                      const SizedBox(height: 12),
                      Text(
                        context.l10n.collectionFavoriteArtistsEmptyTitle,
                        style: Theme.of(context).textTheme.titleMedium,
                        textAlign: TextAlign.center,
                      ),
                      const SizedBox(height: 8),
                      Text(
                        context.l10n.collectionFavoriteArtistsEmptySubtitle,
                        textAlign: TextAlign.center,
                        style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                          color: colorScheme.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            )
          else
            SliverPadding(
              padding: EdgeInsets.symmetric(horizontal: wideListInset(context)),
              sliver: SliverList.separated(
                itemCount: artists.length,
                // Material lists separate rows by space; dividers are Mornye's.
                separatorBuilder: (_, _) => context.isMornye
                    ? const Divider(height: 1)
                    : const SizedBox.shrink(),
                itemBuilder: (context, index) {
                  final artist = artists[index];
                  return ListTile(
                    contentPadding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 4,
                    ),
                    leading: _ArtistThumbnail(artist: artist),
                    title: Text(
                      artist.name,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    subtitle:
                        artist.providerId == null || artist.providerId!.isEmpty
                        ? null
                        : Text(
                            artist.providerId!,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                    trailing: AppIconButton(
                      tooltip: context.l10n.artistOptionRemoveFromFavorites,
                      icon: Icon(Icons.favorite, color: colorScheme.error),
                      onPressed: () async {
                        await ref
                            .read(libraryCollectionsProvider.notifier)
                            .removeFavoriteArtist(artist.key);
                        if (!context.mounted) return;
                        ScaffoldMessenger.of(context).showSnackBar(
                          SnackBar(
                            content: Text(
                              context.l10n.collectionRemovedFromFavoriteArtists(
                                artist.name,
                              ),
                            ),
                          ),
                        );
                      },
                    ),
                    onTap: () {
                      navigateToArtist(context, artistName: artist.name);
                    },
                  );
                },
              ),
            ),
          SliverToBoxAdapter(child: SizedBox(height: bottomInset)),
        ],
      ),
    );
  }
}

class _ArtistThumbnail extends StatelessWidget {
  final CollectionArtistEntry artist;

  const _ArtistThumbnail({required this.artist});

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final imageUrl = artist.imageUrl;
    return AppShapeClip(
      kind: AppShapes.artist,
      child: LocalCoverImage(
        url: imageUrl ?? '',
        width: 56,
        height: 56,
        fit: BoxFit.cover,
        localCacheWidth: 112,
        placeholder: (_) => _placeholder(colorScheme),
      ),
    );
  }

  Widget _placeholder(ColorScheme colorScheme) {
    return Container(
      width: 56,
      height: 56,
      color: colorScheme.surfaceContainerHighest,
      child: Icon(Icons.person, color: colorScheme.onSurfaceVariant),
    );
  }
}
