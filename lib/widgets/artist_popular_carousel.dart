import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/theme/app_tokens.dart';
import 'package:sistrum/widgets/track_card.dart';

/// The artist page's Popular songs: pages of [pageSize] rows (cover, title,
/// album, three dots), swiped sideways, with a dot for each page.
class ArtistPopularCarousel extends StatefulWidget {
  const ArtistPopularCarousel({
    super.key,
    required this.tracks,
    required this.coverBuilder,
    required this.onPlay,
    required this.onMenu,
    this.pageSize = 5,
  });

  final List<Track> tracks;
  final Widget Function(Track track) coverBuilder;

  /// A row was tapped: plays the songs from its index.
  final void Function(int index) onPlay;

  /// The three dots of the row at [index] were tapped; [buttonContext]
  /// anchors the menu.
  final void Function(BuildContext buttonContext, int index) onMenu;
  final int pageSize;

  @override
  State<ArtistPopularCarousel> createState() => _ArtistPopularCarouselState();
}

class _ArtistPopularCarouselState extends State<ArtistPopularCarousel> {
  final _controller = PageController();
  int _page = 0;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final tokens = context.tokens;
    final tracks = widget.tracks;
    final pageCount = (tracks.length / widget.pageSize).ceil();
    if (pageCount == 0) return const SizedBox.shrink();
    final textHeight = MediaQuery.textScalerOf(context).scale(52);
    // A flat row: its padding and margin around a cover or two text lines.
    final rowHeight =
        (textHeight > 48 ? textHeight : 48) + tokens.gapMd * 2 + 4;
    final scheme = Theme.of(context).colorScheme;
    return Column(
      children: [
        SizedBox(
          // One short page is as tall as its rows; with more pages every page
          // keeps the height of a full one, so swiping does not jump.
          height:
              rowHeight *
              (tracks.length < widget.pageSize
                  ? tracks.length
                  : widget.pageSize),
          child: PageView.builder(
            controller: _controller,
            itemCount: pageCount,
            onPageChanged: (page) => setState(() => _page = page),
            itemBuilder: (context, page) {
              final start = page * widget.pageSize;
              final end = (start + widget.pageSize).clamp(0, tracks.length);
              return Column(
                children: [
                  for (var index = start; index < end; index++)
                    SizedBox(height: rowHeight, child: _row(context, index)),
                ],
              );
            },
          ),
        ),
        if (pageCount > 1)
          Padding(
            padding: const EdgeInsets.only(top: 4),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                for (var page = 0; page < pageCount; page++)
                  Container(
                    width: 8,
                    height: 8,
                    margin: const EdgeInsets.symmetric(horizontal: 4),
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: page == _page
                          ? scheme.primary
                          : scheme.onSurfaceVariant.withValues(alpha: 0.4),
                    ),
                  ),
              ],
            ),
          ),
      ],
    );
  }

  Widget _row(BuildContext context, int index) {
    final track = widget.tracks[index];
    final album = track.albumName.trim();
    return TrackCard(
      style: TrackCardStyle.flat,
      leading: SizedBox.square(
        dimension: 48,
        child: ClipRRect(
          borderRadius: BorderRadius.circular(4),
          child: widget.coverBuilder(track),
        ),
      ),
      title: track.name,
      explicit: track.isExplicit,
      subtitle: Text(
        album.isNotEmpty ? album : track.artistName,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
      trailing: Builder(
        builder: (buttonContext) => IconButton(
          tooltip: MaterialLocalizations.of(context).showMenuTooltip,
          onPressed: () => widget.onMenu(buttonContext, index),
          icon: Icon(
            CupertinoIcons.ellipsis,
            color: Theme.of(context).colorScheme.onSurfaceVariant,
          ),
        ),
      ),
      onTap: () => widget.onPlay(index),
    );
  }
}
