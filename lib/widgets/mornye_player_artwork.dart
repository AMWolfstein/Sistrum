import 'package:audio_service/audio_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/widgets/player_artwork.dart';

/// The player's cover, filling its box.
class MornyePlayerArtwork extends ConsumerWidget {
  const MornyePlayerArtwork({super.key, required this.mediaItem});

  final MediaItem mediaItem;

  @override
  Widget build(BuildContext context, WidgetRef ref) => SizedBox.expand(
    child: PlayerArtwork(
      artUri: mediaItem.artUri?.toString(),
      colorScheme: Theme.of(context).colorScheme,
      cacheWidth: PlayerArtwork.transitionCacheWidth(context),
    ),
  );
}
