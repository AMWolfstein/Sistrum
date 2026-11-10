import 'dart:io' show Platform;
import 'dart:math' as math;

import 'package:flutter/cupertino.dart' show CupertinoIcons;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart' show HapticFeedback;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_3_expressive/material_3_expressive.dart'
    show
        M3EShapeKind,
        Morph,
        MorphToPathExtension,
        RoundedPolygonToPathExtension;
import 'package:mornye/constants/app_info.dart';
import 'package:mornye/l10n/l10n.dart';
import 'package:mornye/providers/settings_provider.dart';
import 'package:mornye/services/local_network_access.dart';
import 'package:mornye/theme/mornye_icons.dart';
import 'package:mornye/theme/mornye_theme.dart';
import 'package:mornye/widgets/app_icon_button.dart';
import 'package:mornye/widgets/app_shape.dart';
import 'package:mornye/widgets/expressive_button.dart';

/// The highlights of [AppInfo.whatsNewRelease] on phones and tablets, shown
/// once after an update and again from About.
class WhatsNewScreen extends ConsumerStatefulWidget {
  const WhatsNewScreen({super.key, this.localNetworkAccess});

  /// Replaces [LocalNetworkAccess.instance] in tests.
  final LocalNetworkAccess? localNetworkAccess;

  @override
  ConsumerState<WhatsNewScreen> createState() => _WhatsNewScreenState();
}

class _WhatsNewScreenState extends ConsumerState<WhatsNewScreen> {
  final _pages = PageController();
  int _page = 0;

  /// Whether the tour asks for Nearby devices: Android 17 without it.
  /// Decided once, so the page stays after it is answered.
  bool _asksNearby = false;

  /// Null until Android answers the request; true when allowed.
  bool? _nearbyAllowed;
  bool _askingNearby = false;

  LocalNetworkAccess get _localNetwork =>
      widget.localNetworkAccess ?? LocalNetworkAccess.instance;

  @override
  void initState() {
    super.initState();
    _localNetwork.isGranted().then((granted) {
      if (mounted && !granted) setState(() => _asksNearby = true);
    }, onError: (_) {});
  }

  @override
  void dispose() {
    _pages.dispose();
    super.dispose();
  }

  void _goTo(int page) {
    if (MediaQuery.disableAnimationsOf(context)) {
      _pages.jumpToPage(page);
    } else {
      _pages.animateToPage(
        page,
        duration: const Duration(milliseconds: 420),
        curve: Curves.easeInOutCubicEmphasized,
      );
    }
  }

  void _next(int count) => _page < count - 1 ? _goTo(_page + 1) : _finish();

  void _previous() {
    if (_page > 0) _goTo(_page - 1);
  }

  /// Skip never passes an unanswered Nearby devices page: network storage
  /// cannot find servers on Android 17 without it.
  void _skip(int nearbyPage) {
    if (nearbyPage > _page && _nearbyAllowed == null) {
      _goTo(nearbyPage);
    } else {
      _finish();
    }
  }

  Future<void> _allowNearby() async {
    setState(() => _askingNearby = true);
    try {
      await _localNetwork.ensureGranted();
      // Discovery that found nothing without it starts over.
      if (mounted) setState(() => _nearbyAllowed = true);
    } on LocalNetworkPermissionException {
      if (mounted) setState(() => _nearbyAllowed = false);
    } finally {
      if (mounted) setState(() => _askingNearby = false);
    }
  }

  /// Done and Skip both mark the tour seen. Opened from About, the tour
  /// closes back to it; after an update, the router moves on to the app.
  void _finish() {
    ref.read(settingsProvider.notifier).setWhatsNewSeen();
    final navigator = Navigator.of(context);
    if (navigator.canPop()) {
      navigator.pop();
    } else {
      context.go('/');
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final scheme = Theme.of(context).colorScheme;
    final mornye = context.isMornye;
    final pages = whatsNewHighlights(l10n, nearbyDevices: _asksNearby);
    final last = _page == pages.length - 1;
    final nearbyPage = pages.indexWhere((page) => page.nearbyDevices);
    // Until Android answers, the Nearby devices page is the end of the tour:
    // no Skip, no Continue and no swiping past it.
    final awaitingNearby = nearbyPage >= 0 && _nearbyAllowed == null;
    final gated = awaitingNearby && _page == nearbyPage;
    // Back steps through the pages, then leaves the tour as Skip does.
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (didPop) return;
        _page > 0 ? _previous() : _finish();
      },
      child: Scaffold(
        backgroundColor: scheme.surface,
        body: SafeArea(
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 560),
              child: Column(
                children: [
                  Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 4,
                    ),
                    child: Row(
                      children: [
                        if (_page > 0)
                          AppIconButton(
                            tooltip: MaterialLocalizations.of(
                              context,
                            ).backButtonTooltip,
                            onPressed: _previous,
                            color: scheme.primary,
                            icon: Icon(
                              mornye
                                  ? CupertinoIcons.chevron_back
                                  : Icons.arrow_back,
                            ),
                          )
                        else
                          const SizedBox(width: 48, height: 48),
                        // Large text shrinks the dots instead of pushing
                        // Skip off screen.
                        Expanded(
                          child: FittedBox(
                            fit: BoxFit.scaleDown,
                            child: _PageIndicator(
                              count: pages.length,
                              page: _page,
                            ),
                          ),
                        ),
                        // Capped so large text cannot push it off screen;
                        // the dots keep the rest of the row. Hidden in place
                        // while gated, so the dots do not move.
                        Visibility(
                          visible: !gated,
                          maintainSize: true,
                          maintainAnimation: true,
                          maintainState: true,
                          child: ConstrainedBox(
                            constraints: const BoxConstraints(maxWidth: 160),
                            child: ExpressiveButton(
                              text: true,
                              onPressed: () => _skip(nearbyPage),
                              child: Text(
                                l10n.setupSkip,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                  Expanded(
                    child: PageView.builder(
                      controller: _pages,
                      itemCount: awaitingNearby ? nearbyPage + 1 : pages.length,
                      onPageChanged: (page) => setState(() => _page = page),
                      itemBuilder: (context, index) => _TourPage(
                        index: index,
                        highlight: pages[index],
                        status: pages[index].nearbyDevices
                            ? _NearbyStatus(allowed: _nearbyAllowed)
                            : null,
                      ),
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.fromLTRB(24, 12, 24, 20),
                    child: SizedBox(
                      width: double.infinity,
                      child: ExpressiveButton(
                        // Gated, the main button asks instead of moving on.
                        onPressed: gated
                            ? (_askingNearby ? null : _allowNearby)
                            : () => _next(pages.length),
                        icon: gated
                            ? Icon(context.adaptiveIcon(Icons.wifi))
                            : last
                            ? null
                            : Icon(
                                mornye
                                    ? CupertinoIcons.arrow_right
                                    : Icons.arrow_forward,
                              ),
                        style: FilledButton.styleFrom(
                          minimumSize: const Size.fromHeight(56),
                          shape: mornye
                              ? RoundedRectangleBorder(
                                  borderRadius: BorderRadius.circular(14),
                                )
                              : null,
                          textStyle: Theme.of(context).textTheme.titleMedium,
                        ),
                        child: Text(
                          gated
                              ? l10n.whatsNewNearbyAllow
                              : last
                              ? l10n.whatsNewDone
                              : l10n.upgradeIntroContinue,
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// One page of the tour: a symbol, a title, a line and its feature rows.
@visibleForTesting
class WhatsNewHighlight {
  const WhatsNewHighlight({
    required this.icon,
    required this.title,
    required this.description,
    required this.features,
    this.nearbyDevices = false,
  });

  final IconData icon;
  final String title;
  final String description;
  final List<WhatsNewFeature> features;

  /// The page that asks for Android's Nearby devices permission, which
  /// network storage needs from Android 17.
  final bool nearbyDevices;
}

@visibleForTesting
class WhatsNewFeature {
  const WhatsNewFeature(this.icon, this.title, [this.body]);

  final IconData icon;
  final String title;

  /// Null for the opening page, which lists the sections by title.
  final String? body;
}

/// The tour for [AppInfo.whatsNewRelease]: an overview, then one page per
/// area. Android Auto and exclusive output appear only where they exist;
/// [nearbyDevices] adds the Nearby devices page right after the overview.
@visibleForTesting
List<WhatsNewHighlight> whatsNewHighlights(
  AppLocalizations l10n, {
  bool nearbyDevices = false,
}) {
  final showAndroidAuto = Platform.isAndroid;
  final sections = [
    WhatsNewHighlight(
      icon: Icons.auto_awesome,
      title: l10n.whatsNewHomeTitle,
      description: l10n.whatsNewHomeDesc,
      features: [
        WhatsNewFeature(
          Icons.playlist_play,
          l10n.whatsNewHomeMixesTitle,
          l10n.whatsNewHomeMixesBody,
        ),
        WhatsNewFeature(
          Icons.favorite,
          l10n.whatsNewHomeAutoplayTitle,
          l10n.whatsNewHomeAutoplayBody,
        ),
        WhatsNewFeature(
          Icons.graphic_eq,
          l10n.whatsNewHomeAnalysisTitle,
          l10n.whatsNewHomeAnalysisBody,
        ),
      ],
    ),
    WhatsNewHighlight(
      icon: Icons.lyrics,
      title: l10n.whatsNewPlayerTitle,
      description: l10n.whatsNewPlayerDesc,
      features: [
        WhatsNewFeature(
          Icons.album,
          l10n.whatsNewPlayerGaplessTitle,
          l10n.whatsNewPlayerGaplessBody,
        ),
        WhatsNewFeature(
          Icons.translate,
          l10n.whatsNewPlayerTranslateTitle,
          l10n.whatsNewPlayerTranslateBody,
        ),
        WhatsNewFeature(
          Icons.timer_outlined,
          l10n.whatsNewPlayerTimingTitle,
          l10n.whatsNewPlayerTimingBody,
        ),
        if (showAndroidAuto)
          WhatsNewFeature(
            Icons.directions_car,
            l10n.whatsNewPlayerAutoTitle,
            l10n.whatsNewPlayerAutoBody,
          ),
      ],
    ),
    WhatsNewHighlight(
      icon: Icons.library_music,
      title: l10n.whatsNewLibraryTitle,
      description: l10n.whatsNewLibraryDesc,
      features: [
        WhatsNewFeature(
          Icons.folder_open,
          l10n.whatsNewLibraryFoldersTitle,
          l10n.whatsNewLibraryFoldersBody,
        ),
        WhatsNewFeature(
          Icons.lightbulb_outline,
          l10n.whatsNewLibrarySuggestionsTitle,
          l10n.whatsNewLibrarySuggestionsBody,
        ),
        WhatsNewFeature(
          Icons.mic,
          l10n.whatsNewLibraryRecognizeTitle,
          l10n.whatsNewLibraryRecognizeBody,
        ),
        WhatsNewFeature(
          Icons.playlist_add,
          l10n.whatsNewLibraryPlaylistsTitle,
          l10n.whatsNewLibraryPlaylistsBody,
        ),
      ],
    ),
    WhatsNewHighlight(
      icon: Icons.speed_rounded,
      title: l10n.whatsNewFasterTitle,
      description: l10n.whatsNewFasterDesc,
      features: [
        WhatsNewFeature(
          Icons.library_music_outlined,
          l10n.whatsNewFasterLibraryTitle,
          l10n.whatsNewFasterLibraryBody,
        ),
        WhatsNewFeature(
          Icons.lan_outlined,
          l10n.whatsNewFasterNetworkTitle,
          l10n.whatsNewFasterNetworkBody,
        ),
        WhatsNewFeature(
          Icons.history,
          l10n.whatsNewFasterQueueTitle,
          l10n.whatsNewFasterQueueBody,
        ),
        WhatsNewFeature(
          Icons.timer,
          l10n.whatsNewFasterBackgroundTitle,
          l10n.whatsNewFasterBackgroundBody,
        ),
      ],
    ),
  ];
  return [
    WhatsNewHighlight(
      icon: Icons.new_releases,
      title: l10n.whatsNewIntroTitle(AppInfo.whatsNewRelease),
      description: l10n.whatsNewIntroDesc,
      features: [
        for (final section in sections)
          WhatsNewFeature(section.icon, section.title),
      ],
    ),
    // A setup step rather than a highlight, so the overview leaves it out.
    if (nearbyDevices)
      WhatsNewHighlight(
        icon: Icons.wifi,
        title: l10n.whatsNewNearbyTitle,
        description: l10n.whatsNewNearbyDesc,
        nearbyDevices: true,
        features: [
          WhatsNewFeature(
            Icons.dns,
            l10n.whatsNewNearbyStorageTitle,
            l10n.whatsNewNearbyStorageBody,
          ),
          WhatsNewFeature(
            Icons.lock,
            l10n.whatsNewNearbyPrivateTitle,
            l10n.whatsNewNearbyPrivateBody,
          ),
        ],
      ),
    ...sections,
  ];
}

/// The hero shape and its small accent for each page, by index.
const _heroShapes = [
  (M3EShapeKind.cookie12Sided, M3EShapeKind.sunny),
  (M3EShapeKind.flower, M3EShapeKind.pill),
  (M3EShapeKind.softBurst, M3EShapeKind.clover4Leaf),
  (M3EShapeKind.clover8Leaf, M3EShapeKind.gem),
  (M3EShapeKind.cookie9Sided, M3EShapeKind.sunny),
  (M3EShapeKind.puffy, M3EShapeKind.pill),
];

/// Dots that stretch into a pill for the current page.
class _PageIndicator extends StatelessWidget {
  const _PageIndicator({required this.count, required this.page});

  final int count;
  final int page;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final duration = MediaQuery.disableAnimationsOf(context)
        ? Duration.zero
        : const Duration(milliseconds: 360);
    return Semantics(
      label: '${page + 1} / $count',
      child: ExcludeSemantics(
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            for (var index = 0; index < count; index++)
              AnimatedContainer(
                duration: duration,
                curve: Curves.easeOutBack,
                margin: const EdgeInsets.symmetric(horizontal: 3),
                width: index == page ? 24 : 8,
                height: 8,
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(4),
                  color: index == page
                      ? scheme.primary
                      : index < page
                      ? scheme.primary.withValues(alpha: 0.38)
                      : scheme.outlineVariant,
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _TourPage extends StatelessWidget {
  const _TourPage({required this.index, required this.highlight, this.status});

  final int index;
  final WhatsNewHighlight highlight;

  /// Shown under the description, above the rows, so it needs no scrolling.
  final Widget? status;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final mornye = context.isMornye;
    final (shape, accent) = _heroShapes[index % _heroShapes.length];
    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(24, 8, 24, 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Center(
            child: _TourHero(
              shape: shape,
              accent: accent,
              icon: highlight.icon,
              logo: index == 0,
            ),
          ),
          const SizedBox(height: 24),
          Text(
            highlight.title,
            style: mornye
                ? theme.textTheme.headlineLarge?.copyWith(
                    fontSize: 36,
                    height: 1.08,
                    letterSpacing: -1.2,
                    fontWeight: FontWeight.w700,
                  )
                : theme.textTheme.displaySmall?.copyWith(
                    height: 1.1,
                    letterSpacing: -0.4,
                    fontWeight: FontWeight.w500,
                  ),
          ),
          const SizedBox(height: 12),
          Text(
            highlight.description,
            style: theme.textTheme.bodyLarge?.copyWith(
              fontSize: 17,
              height: 1.4,
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
          ?status,
          const SizedBox(height: 28),
          _FeatureList(features: highlight.features),
        ],
      ),
    );
  }
}

/// Android's answer on the Nearby devices page, under its description;
/// nothing until it answers.
class _NearbyStatus extends StatelessWidget {
  const _NearbyStatus({required this.allowed});

  final bool? allowed;

  @override
  Widget build(BuildContext context) {
    final allowed = this.allowed;
    if (allowed == null) return const SizedBox.shrink();
    final l10n = context.l10n;
    final theme = Theme.of(context);
    final color = allowed
        ? theme.colorScheme.primary
        : theme.colorScheme.onSurfaceVariant;
    return Padding(
      padding: const EdgeInsets.only(top: 20),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(
            context.adaptiveIcon(allowed ? Icons.check_circle : Icons.info),
            color: color,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              allowed ? l10n.whatsNewNearbyGranted : l10n.whatsNewNearbyRefused,
              style: theme.textTheme.titleSmall?.copyWith(color: color),
            ),
          ),
        ],
      ),
    );
  }
}

/// A large expressive shape with two smaller ones beside it. It springs in
/// with its page, morphs once on its own, and morphs into the next shape on
/// each tap.
class _TourHero extends StatefulWidget {
  const _TourHero({
    required this.shape,
    required this.accent,
    required this.icon,
    this.logo = false,
  });

  final M3EShapeKind shape;
  final M3EShapeKind accent;
  final IconData icon;
  final bool logo;

  @override
  State<_TourHero> createState() => _TourHeroState();
}

class _TourHeroState extends State<_TourHero> with TickerProviderStateMixin {
  /// Shapes the hero morphs through, after the page's own shape.
  static const _cycle = [
    M3EShapeKind.cookie12Sided,
    M3EShapeKind.flower,
    M3EShapeKind.softBurst,
    M3EShapeKind.cookie9Sided,
    M3EShapeKind.puffy,
    M3EShapeKind.clover8Leaf,
    M3EShapeKind.sunny,
    M3EShapeKind.cookie6Sided,
  ];

  late final _entrance = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 700),
  );
  late final _morph = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 650),
    value: 1,
  );
  late M3EShapeKind _to = widget.shape;
  Morph? _shapeMorph;
  int _turns = 0;
  bool _started = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_started) return;
    _started = true;
    if (MediaQuery.disableAnimationsOf(context)) {
      _entrance.value = 1;
      return;
    }
    // One unprompted morph shows the shape can change.
    _entrance.forward().whenComplete(() {
      if (mounted) _next();
    });
  }

  @override
  void dispose() {
    _entrance.dispose();
    _morph.dispose();
    super.dispose();
  }

  void _next() {
    if (context.isMornye || MediaQuery.disableAnimationsOf(context)) return;
    final from = _to;
    _to = _cycle[(_cycle.indexOf(from) + 1) % _cycle.length];
    _shapeMorph = Morph(from.polygon, _to.polygon);
    _turns++;
    _morph.forward(from: 0);
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final mornye = context.isMornye;
    // The logo asset is the bare white mark, tinted like the icons.
    final glyph = widget.logo
        ? Image.asset(
            'assets/images/logo-transparent.png',
            height: 64,
            color: scheme.onPrimaryContainer,
            errorBuilder: (_, _, _) => const SizedBox.shrink(),
          )
        : Icon(
            context.adaptiveIcon(widget.icon),
            size: 56,
            color: scheme.onPrimaryContainer,
          );
    // Decorative: tapping to morph is a small extra, not a control.
    return ExcludeSemantics(
      child: AnimatedBuilder(
        animation: Listenable.merge([_entrance, _morph]),
        child: glyph,
        builder: (context, glyph) {
          final t = Curves.elasticOut.transform(_entrance.value);
          final morph = Curves.easeInOutCubicEmphasized.transform(_morph.value);
          // Each morph adds an eighth of a turn, settling with a spring.
          final turn =
              (_turns - 1 + Curves.elasticOut.transform(_morph.value)) *
              math.pi /
              4;
          final shape = GestureDetector(
            onTap: () {
              HapticFeedback.selectionClick();
              _next();
            },
            child: Transform.rotate(
              angle: (1 - t) * -0.35 + turn,
              child: Transform.scale(
                scale: 0.6 + 0.4 * t,
                child: SizedBox.square(
                  dimension: 132,
                  child: CustomPaint(
                    painter: _MorphPainter(
                      morph: mornye ? null : _shapeMorph,
                      kind: mornye ? M3EShapeKind.circle : _to,
                      progress: morph,
                      color: scheme.primaryContainer,
                    ),
                    // Keep the glyph upright while the shape turns.
                    child: Transform.rotate(
                      angle: -turn,
                      child: Center(child: glyph),
                    ),
                  ),
                ),
              ),
            ),
          );
          return _heroStage(scheme, t, shape);
        },
      ),
    );
  }

  Widget _heroStage(ColorScheme scheme, double t, Widget child) => SizedBox(
    width: 220,
    height: 172,
    child: Stack(
      alignment: Alignment.center,
      children: [
        child,
        Positioned(
          right: 18,
          top: 8,
          child: Transform.scale(
            scale: t,
            child: AppShapeBadge(
              kind: widget.accent,
              size: 44,
              color: scheme.tertiaryContainer,
              child: const SizedBox.shrink(),
            ),
          ),
        ),
        Positioned(
          left: 30,
          bottom: 16,
          child: Transform.scale(
            scale: t,
            child: AppShapeBadge(
              kind: M3EShapeKind.circle,
              size: 20,
              color: scheme.secondaryContainer,
              child: const SizedBox.shrink(),
            ),
          ),
        ),
      ],
    ),
  );
}

/// Fills the shape between two expressive polygons; [morph] null draws [kind].
class _MorphPainter extends CustomPainter {
  const _MorphPainter({
    required this.morph,
    required this.kind,
    required this.progress,
    required this.color,
  });

  final Morph? morph;
  final M3EShapeKind kind;
  final double progress;
  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    // Catalog polygons are normalized to the unit square.
    final unit = morph?.toPath(progress: progress) ?? kind.polygon.toPath();
    final scale = Matrix4.diagonal3Values(size.width, size.height, 1);
    canvas.drawPath(
      unit.transform(scale.storage),
      Paint()
        ..color = color
        ..isAntiAlias = true,
    );
  }

  @override
  bool shouldRepaint(_MorphPainter oldDelegate) =>
      morph != oldDelegate.morph ||
      kind != oldDelegate.kind ||
      progress != oldDelegate.progress ||
      color != oldDelegate.color;
}

/// Feature rows as one connected list: large outer corners, small inner
/// ones.
class _FeatureList extends StatelessWidget {
  const _FeatureList({required this.features});

  final List<WhatsNewFeature> features;

  @override
  Widget build(BuildContext context) => Column(
    children: [
      for (var index = 0; index < features.length; index++)
        _FeatureRow(
          feature: features[index],
          first: index == 0,
          last: index == features.length - 1,
        ),
    ],
  );
}

class _FeatureRow extends StatelessWidget {
  const _FeatureRow({
    required this.feature,
    required this.first,
    required this.last,
  });

  final WhatsNewFeature feature;
  final bool first;
  final bool last;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final outer = Radius.circular(context.isMornye ? 18 : 24);
    const inner = Radius.circular(6);
    final body = feature.body;
    return Padding(
      padding: EdgeInsets.only(bottom: last ? 0 : 2),
      child: MergeSemantics(
        child: Material(
          color: scheme.surfaceContainer,
          borderRadius: BorderRadius.vertical(
            top: first ? outer : inner,
            bottom: last ? outer : inner,
          ),
          clipBehavior: Clip.antiAlias,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 20, 16),
            child: Row(
              crossAxisAlignment: body == null
                  ? CrossAxisAlignment.center
                  : CrossAxisAlignment.start,
              children: [
                // Icon containers in one list share the plain circle;
                // abstract shapes are reserved for the decorative hero.
                AppShapeBadge(
                  kind: M3EShapeKind.circle,
                  size: 44,
                  color: scheme.secondaryContainer,
                  child: Icon(
                    context.adaptiveIcon(feature.icon),
                    size: 22,
                    color: scheme.onSecondaryContainer,
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        feature.title,
                        style: theme.textTheme.titleMedium?.copyWith(
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      if (body != null) ...[
                        const SizedBox(height: 4),
                        Text(
                          body,
                          style: theme.textTheme.bodyMedium?.copyWith(
                            height: 1.35,
                            color: scheme.onSurfaceVariant,
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
