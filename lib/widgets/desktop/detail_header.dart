import 'package:cupertino_ui/cupertino_ui.dart';
import 'package:material_ui/material_ui.dart';
import 'package:sistrum/theme/app_motion.dart';
import 'package:sistrum/theme/app_tokens.dart';
import 'package:sistrum/utils/adaptive_layout.dart';
import 'package:sistrum/widgets/album_detail_header.dart';
import 'package:sistrum/widgets/desktop/context_menu.dart';
import 'package:sistrum/widgets/sliver_layout_info_builder.dart';

/// Space below a desktop detail page, so its last rows clear the floating
/// player.
const double desktopDetailBottomInset = 90;

const double _toolbarHeight = 52;

/// The header artwork side for a content column: about a third of it, from
/// 200 to 300.
double desktopDetailArtworkSize(double contentWidth) =>
    (contentWidth * 0.32).clamp(200.0, 300.0);

/// The artist line under a desktop detail title, large and in the accent color.
TextStyle desktopDetailSubtitleStyle(BuildContext context) => TextStyle(
  color: Theme.of(context).colorScheme.primary,
  fontSize: 24,
  fontWeight: FontWeight.w400,
  height: 1.2,
);

/// The desktop header of album, playlist, folder and artist pages.
///
/// The artwork sits on the left edge of the list below, with the title,
/// [subtitle] and [meta] centered beside it and [actions] along its bottom
/// edge. The page background stays plain. A glass back button and a capsule
/// of [appBarActions] float over the pane. [moreMenu] adds a "More" button
/// that opens those groups as a menu.
List<Widget> desktopDetailHeaderSlivers(
  BuildContext context, {
  required String title,
  required Widget Function(BuildContext context, double size) artwork,
  String? paletteSource,
  bool circularArtwork = false,
  Widget? subtitle,
  Widget? meta,
  Widget? actions,
  List<Widget>? appBarActions,
  List<List<ContextMenuAction>> Function()? moreMenu,
  String? appBarTitle,
  Widget? leading,
  Color? backgroundColor,
}) {
  final theme = Theme.of(context);
  // Lines the artwork up with the description and track table below.
  final inset = wideListInset(context) + 20;
  final more = moreMenu == null ? null : HeaderMoreButton(groups: moreMenu);
  final trailing = [...?appBarActions, ?more];

  Widget details(BuildContext context, BoxConstraints constraints) {
    final side = desktopDetailArtworkSize(constraints.maxWidth);
    const gap = 28.0;
    final textWidth = (constraints.maxWidth - side - gap).clamp(
      0.0,
      double.infinity,
    );
    final radius = circularArtwork ? side / 2 : 12.0;
    final corners = BorderRadius.circular(radius);
    final shadows = [
      BoxShadow(
        color: Colors.black.withValues(alpha: 0.18),
        blurRadius: 18,
        offset: const Offset(0, 6),
      ),
    ];
    final text = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(
          title,
          maxLines: 3,
          overflow: TextOverflow.ellipsis,
          style: const TextStyle(
            fontSize: 28,
            fontWeight: FontWeight.w700,
            height: 1.15,
          ),
        ),
        if (subtitle != null) ...[const SizedBox(height: 2), subtitle],
        if (meta != null) ...[const SizedBox(height: 8), meta],
      ],
    );
    return SizedBox(
      height: side,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Container(
            width: side,
            height: side,
            decoration: ShapeDecoration(
              shape: RoundedSuperellipseBorder(borderRadius: corners),
              shadows: shadows,
            ),
            child: ClipRSuperellipse(
              borderRadius: corners,
              child: artwork(context, side),
            ),
          ),
          const SizedBox(width: gap),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: Align(
                    alignment: AlignmentDirectional.centerStart,
                    // Large text scales down instead of overflowing the
                    // artwork's height.
                    child: FittedBox(
                      fit: BoxFit.scaleDown,
                      alignment: AlignmentDirectional.centerStart,
                      child: SizedBox(width: textWidth, child: text),
                    ),
                  ),
                ),
                if (actions != null)
                  Align(
                    alignment: AlignmentDirectional.centerStart,
                    child: actions,
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  final back =
      leading ??
      (Navigator.canPop(context)
          ? HeaderCircleButton(
              icon: CupertinoIcons.back,
              tooltip: MaterialLocalizations.of(context).backButtonTooltip,
              onPressed: () => Navigator.maybePop(context),
            )
          : null);
  // The bar's title fades on the fast effects spring of the style.
  final titleFade = AppMotion.effectsFast(
    context,
    AppMotionSpec(context.tokens.motionFast, Curves.linear),
  );

  return [
    SliverLayoutInfoBuilder(
      select: (constraints) => appBarTitle != null,
      builder: (context, showTitle) {
        return SliverAppBar(
          pinned: true,
          automaticallyImplyLeading: false,
          toolbarHeight: _toolbarHeight,
          // The controls float over the pane.
          backgroundColor: Colors.transparent,
          surfaceTintColor: Colors.transparent,
          scrolledUnderElevation: 0,
          titleSpacing: 0,
          title: AnimatedOpacity(
            opacity: showTitle ? 1 : 0,
            duration: titleFade.duration,
            curve: titleFade.curve,
            child: Text(
              appBarTitle ?? title,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: theme.textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
          leadingWidth: _toolbarHeight + 12,
          leading: back == null
              ? const SizedBox.shrink()
              : Padding(
                  padding: const EdgeInsetsDirectional.only(start: 16),
                  child: Center(child: back),
                ),
          actionsPadding: const EdgeInsetsDirectional.only(end: 16),
          actions: trailing.isEmpty
              ? null
              : [MornyeActionCapsule(children: trailing)],
        );
      },
    ),
    SliverToBoxAdapter(
      child: Padding(
        padding: EdgeInsets.fromLTRB(inset, 4, inset, 28),
        child: LayoutBuilder(builder: details),
      ),
    ),
  ];
}
