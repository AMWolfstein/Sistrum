import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/providers/library_collections_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/controllers/clickable_metadata.dart';
import 'package:sistrum/controllers/local_playback.dart';
import 'package:sistrum/utils/service_track_link.dart';
import 'package:sistrum/widgets/track_lists/context_menu.dart';
import 'package:sistrum/widgets/playlist_picker_sheet.dart';
import 'package:sistrum/widgets/mornye_context_menu.dart';
import 'package:share_plus/share_plus.dart';
import 'package:sistrum/widgets/app_icon.dart';
import 'package:sistrum/widgets/app_icon_button.dart';
import 'package:sistrum/widgets/track_collection_action_policy.dart';
import 'package:sistrum/widgets/track_detail_actions.dart';

class TrackCollectionQuickActions extends ConsumerWidget {
  final Track track;
  final bool hasLocalPlaybackCandidate;

  const TrackCollectionQuickActions({
    super.key,
    required this.track,
    this.hasLocalPlaybackCandidate = false,
  });

  /// Track options: a popover on phones
  /// and tablets, a context menu at [position] or under [context] on
  static void showTrackOptionsSheet(
    BuildContext context,
    WidgetRef ref,
    Track track, {
    bool hasLocalPlaybackCandidate = false,
    Offset? position,
  }) {
    showMornyeContextMenu<void>(
      context: context,
      builder: (_) => _TrackOptionsSheet(
        track: track,
        hasLocalPlaybackCandidate: hasLocalPlaybackCandidate,
      ),
    );
    return;
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final colorScheme = Theme.of(context).colorScheme;

    return AppIconButton(
      tooltip: MaterialLocalizations.of(context).showMenuTooltip,
      icon: AppIcon(
        CupertinoIcons.ellipsis,
        color: colorScheme.onSurfaceVariant,
        size: 20,
      ),
      onPressed: () => showTrackOptionsSheet(
        context,
        ref,
        track,
        hasLocalPlaybackCandidate: hasLocalPlaybackCandidate,
        position: contextMenuPositionBelow(context),
      ),
      padding: EdgeInsets.zero,
      constraints: const BoxConstraints(minWidth: 48, minHeight: 48),
    );
  }
}

class _TrackOptionsSheet extends ConsumerWidget {
  final Track track;
  final bool hasLocalPlaybackCandidate;

  const _TrackOptionsSheet({
    required this.track,
    required this.hasLocalPlaybackCandidate,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final link = serviceTrackLinkFor(track);
    final isLoved = ref.watch(
      libraryCollectionsProvider.select((state) => state.isLoved(track)),
    );
    final isInWishlist = ref.watch(
      libraryCollectionsProvider.select((state) => state.isInWishlist(track)),
    );
    final allowQualityVariants = ref.watch(
      settingsProvider.select((settings) => settings.allowQualityVariants),
    );
    final qualityVariantAction = resolveQualityVariantMenuAction(
      allowQualityVariants: allowQualityVariants,
      hasLocalPlaybackCandidate: hasLocalPlaybackCandidate,
    );
    final l10n = context.l10n;
    return MornyeContextMenu(
      quickActions: [
        MornyeMenuAction(
          icon: CupertinoIcons.arrow_down_circle_fill,
          label: l10n.dialogDownload,
          onPressed: () {
            final root = Navigator.of(context, rootNavigator: true).context;
            Navigator.pop(context);
            downloadSingleTrack(root, ref, track);
          },
        ),
        MornyeMenuAction(
          icon: CupertinoIcons.star_fill,
          label: isLoved ? l10n.sistrumFavorited : l10n.sistrumFavorite,
          selected: isLoved,
          onPressed: () => _toggleCollection(context, ref, wishlist: false),
        ),
        MornyeMenuAction(
          icon: CupertinoIcons.share_solid,
          label: l10n.trackMetadataShare,
          onPressed: () {
            final origin =
                mornyeMenuAnchor(context) ??
                Rect.fromCenter(
                  center: MediaQuery.sizeOf(context).center(Offset.zero),
                  width: 1,
                  height: 1,
                );
            Navigator.pop(context);
            SharePlus.instance.share(
              ShareParams(
                text: [
                  '${track.name} - ${track.artistName}',
                  ?link?.webUrl,
                ].join('\n'),
                sharePositionOrigin: origin,
              ),
            );
          },
        ),
      ],
      groups: [
        [
          if (qualityVariantAction == QualityVariantMenuAction.playLocal)
            MornyeMenuAction(
              icon: CupertinoIcons.play,
              label: l10n.trackMetadataPlay,
              onPressed: () => _playLocal(context, ref),
            ),
          if (qualityVariantAction ==
              QualityVariantMenuAction.downloadAnotherQuality)
            MornyeMenuAction(
              icon: CupertinoIcons.arrow_down_circle,
              label: l10n.trackOptionDownloadQualityVariant,
              onPressed: () => _downloadQualityVariant(context, ref),
            ),
          MornyeMenuAction(
            icon: CupertinoIcons.square_stack,
            label: l10n.homeGoToAlbum,
            onPressed: () => _goToAlbum(context),
          ),
        ],
        [
          MornyeMenuAction(
            icon: CupertinoIcons.text_badge_plus,
            label: l10n.collectionAddToPlaylist,
            onPressed: () {
              final root = Navigator.of(context, rootNavigator: true).context;
              Navigator.pop(context);
              showAddTrackToPlaylistSheet(root, ref, track);
            },
          ),
          MornyeMenuAction(
            icon: isInWishlist
                ? CupertinoIcons.checkmark_circle
                : CupertinoIcons.add_circled,
            label: isInWishlist
                ? l10n.trackOptionRemoveFromWishlist
                : l10n.trackOptionAddToWishlist,
            onPressed: () => _toggleCollection(context, ref, wishlist: true),
          ),
        ],
        [
          MornyeMenuAction(
            icon: CupertinoIcons.doc_on_doc,
            label: l10n.trackOptionCopyTrackName,
            onPressed: () => _copyText(context, track.name),
          ),
          MornyeMenuAction(
            icon: CupertinoIcons.person,
            label: l10n.trackOptionCopyArtist,
            onPressed: () => _copyText(context, track.artistName),
          ),
          MornyeMenuAction(
            icon: CupertinoIcons.doc_on_doc,
            label: l10n.trackOptionCopyTrackAndArtist,
            onPressed: () =>
                _copyText(context, '${track.name} - ${track.artistName}'),
          ),
          if (link != null)
            MornyeMenuAction(
              icon: CupertinoIcons.link,
              label: l10n.trackOptionCopyLink(link.serviceName),
              onPressed: () => _copyLink(context, link),
            ),
        ],
      ],
    );
  }

  void _downloadQualityVariant(BuildContext context, WidgetRef ref) {
    final rootContext = Navigator.of(context, rootNavigator: true).context;
    Navigator.pop(context);
    downloadSingleTrack(rootContext, ref, track, forceQualityPicker: true);
  }

  Future<void> _toggleCollection(
    BuildContext context,
    WidgetRef ref, {
    required bool wishlist,
  }) async {
    final root = Navigator.of(context, rootNavigator: true).context;
    final notifier = ref.read(libraryCollectionsProvider.notifier);
    Navigator.pop(context);
    final added = wishlist
        ? await notifier.toggleWishlist(track)
        : await notifier.toggleLoved(track);
    if (!root.mounted) return;
    final l10n = root.l10n;
    final message = wishlist
        ? (added
              ? l10n.collectionAddedToWishlist(track.name)
              : l10n.collectionRemovedFromWishlist(track.name))
        : (added
              ? l10n.collectionAddedToLoved(track.name)
              : l10n.collectionRemovedFromLoved(track.name));
    ScaffoldMessenger.of(root).showSnackBar(SnackBar(content: Text(message)));
  }

  void _copyText(BuildContext context, String text) {
    final rootContext = Navigator.of(context, rootNavigator: true).context;
    Navigator.pop(context);
    Clipboard.setData(ClipboardData(text: text));
    ScaffoldMessenger.of(rootContext).showSnackBar(
      SnackBar(content: Text(rootContext.l10n.trackCopiedToClipboard)),
    );
  }

  void _copyLink(BuildContext context, ServiceTrackLink link) {
    final rootContext = Navigator.of(context, rootNavigator: true).context;
    Navigator.pop(context);
    Clipboard.setData(ClipboardData(text: link.webUrl));
    ScaffoldMessenger.of(rootContext).showSnackBar(
      SnackBar(
        content: Text(rootContext.l10n.snackbarUrlCopied(link.serviceName)),
      ),
    );
  }

  Future<void> _goToAlbum(BuildContext context) async {
    final navigationContext = Navigator.of(
      context,
      rootNavigator: true,
    ).context;
    Navigator.pop(context);
    await navigateToTrackAlbum(navigationContext, track);
  }

  Future<void> _playLocal(BuildContext context, WidgetRef ref) async {
    final rootContext = Navigator.of(context, rootNavigator: true).context;
    Navigator.pop(context);
    final played = await playLocalIfAvailable(rootContext, ref, track);
    if (!played && rootContext.mounted) {
      ScaffoldMessenger.of(rootContext).showSnackBar(
        SnackBar(content: Text(rootContext.l10n.snackbarFileNotFound)),
      );
    }
  }
}
