import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/providers/listening_statistics_provider.dart';
import 'package:sistrum/screens/listening_rewind_screen.dart';
import 'package:sistrum/services/listening_statistics.dart';
import 'package:sistrum/utils/adaptive_layout.dart';
import 'package:sistrum/utils/nav_bar_inset.dart';
import 'package:sistrum/widgets/animation_utils.dart' show SkeletonBox;
import 'package:sistrum/widgets/app_alert_dialog.dart';
import 'package:sistrum/widgets/app_choice_chip.dart';
import 'package:sistrum/widgets/app_content_card.dart';
import 'package:sistrum/widgets/app_icon_button.dart';
import 'package:sistrum/widgets/app_refresh_indicator.dart';
import 'package:sistrum/widgets/app_shape.dart';
import 'package:sistrum/widgets/app_sliver_header.dart';
import 'package:sistrum/widgets/settings_group.dart';
import 'package:sistrum/widgets/player_artwork.dart';

export 'package:sistrum/providers/listening_statistics_provider.dart';

class ListeningStatisticsScreen extends ConsumerStatefulWidget {
  const ListeningStatisticsScreen({super.key});

  @override
  ConsumerState<ListeningStatisticsScreen> createState() =>
      _ListeningStatisticsScreenState();
}

class _ListeningStatisticsScreenState
    extends ConsumerState<ListeningStatisticsScreen> {
  int _days = 7;
  int _year = DateTime.now().year;
  bool _clearing = false;

  Future<void> _clear() async {
    final confirmed = await showAppConfirmDialog(
      context,
      title: context.l10n.listeningStatsClear,
      message: context.l10n.listeningStatsClearDescription,
      confirmLabel: context.l10n.listeningStatsClear,
      destructive: true,
      filled: false,
    );
    if (!confirmed || !mounted) return;
    setState(() => _clearing = true);
    try {
      await listeningRecorder.clear(ListeningStatisticsStore.instance.clear);
      ref.invalidate(listeningSummaryProvider);
      ref.invalidate(listeningRewindProvider);
      ref.invalidate(listeningRewindSpotlightProvider);
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(context.l10n.listeningStatsClearError)),
        );
      }
    } finally {
      if (mounted) setState(() => _clearing = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final period = (days: _days, year: _year);
    final summary = ref.watch(listeningSummaryProvider(period));
    final colors = Theme.of(context).colorScheme;
    return Scaffold(
      body: AppRefreshIndicator(
        onRefresh: () => ref.refresh(listeningSummaryProvider(period).future),
        child: CustomScrollView(
          slivers: [
            AppSliverHeader.page(title: context.l10n.navInsights),
            AppRefreshIndicator.sliver(),
            // Centered at the reading width on tablets, under the title.
            SliverPadding(
              padding: EdgeInsets.symmetric(horizontal: wideListInset(context)),
              sliver: SliverMainAxisGroup(
                slivers: [
                  const SliverToBoxAdapter(child: ListeningRewindEntry()),
                  SliverPadding(
                    padding: const EdgeInsets.all(16),
                    sliver: SliverToBoxAdapter(
                      child: Wrap(
                        spacing: 8,
                        runSpacing: 8,
                        children: [
                          for (final days in [7, 30, 0])
                            AppChoiceChip(
                              label: Text(
                                days == 0
                                    ? '$_year'
                                    : context.l10n.listeningStatsDays(days),
                              ),
                              selected: _days == days,
                              onSelected: (_) => setState(() => _days = days),
                            ),
                          if (_days == 0)
                            AppIconButton(
                              tooltip: context.l10n.listeningStatsPreviousYear,
                              onPressed: () => setState(() => _year--),
                              icon: const Icon(Icons.chevron_left),
                            ),
                          if (_days == 0)
                            AppIconButton(
                              tooltip: context.l10n.listeningStatsNextYear,
                              onPressed: _year >= DateTime.now().year
                                  ? null
                                  : () => setState(() => _year++),
                              icon: const Icon(Icons.chevron_right),
                            ),
                        ],
                      ),
                    ),
                  ),
                  ...summary.when(
                    loading: () => [
                      SliverToBoxAdapter(child: _summarySkeleton(context)),
                    ],
                    error: (error, _) => [
                      SliverToBoxAdapter(
                        child: ListTile(
                          title: Text(context.l10n.listeningStatsLoadError),
                          trailing: AppIconButton(
                            icon: const Icon(Icons.refresh),
                            onPressed: () => ref.invalidate(
                              listeningSummaryProvider(period),
                            ),
                          ),
                        ),
                      ),
                    ],
                    data: (data) => _summarySlivers(context, data),
                  ),
                  SliverToBoxAdapter(
                    child: Padding(
                      padding: const EdgeInsets.fromLTRB(16, 24, 16, 48),
                      child: TextButton.icon(
                        onPressed: _clearing ? null : _clear,
                        icon: Icon(Icons.delete_outline, color: colors.error),
                        label: Text(
                          context.l10n.listeningStatsClear,
                          style: TextStyle(color: colors.error),
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
            const NavBarSliverSpacer(),
          ],
        ),
      ),
    );
  }

  Widget _summarySkeleton(BuildContext context) {
    return ExcludeSemantics(
      child: Column(
        children: [
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: AppContentCard(
              mornyeSurface: false,
              child: Padding(
                padding: const EdgeInsets.all(20),
                child: const Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    SkeletonBox(width: 180, height: 44),
                    SizedBox(height: 8),
                    SkeletonBox(width: double.infinity, height: 20),
                  ],
                ),
              ),
            ),
          ),
          SettingsSectionHeader(title: context.l10n.listeningStatsActivity),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 24),
            child: Column(
              children: [
                for (var index = 0; index < 7; index++)
                  const Padding(
                    padding: EdgeInsets.symmetric(vertical: 6),
                    child: Row(
                      children: [
                        SkeletonBox(width: 68, height: 20),
                        SizedBox(width: 8),
                        Expanded(
                          child: SkeletonBox(
                            width: double.infinity,
                            height: 12,
                          ),
                        ),
                        SizedBox(width: 12),
                        SkeletonBox(width: 24, height: 16),
                      ],
                    ),
                  ),
              ],
            ),
          ),
          for (final artists in [true, false]) ...[
            SettingsSectionHeader(
              title: artists
                  ? context.l10n.listeningStatsTopArtists
                  : context.l10n.listeningStatsTopTracks,
            ),
            for (var index = 0; index < 3; index++)
              ListTile(
                leading: artists
                    ? const AppShapeClip(
                        child: SkeletonBox(
                          width: 48,
                          height: 48,
                          borderRadius: 0,
                        ),
                      )
                    : const SkeletonBox(width: 48, height: 48),
                title: const Align(
                  alignment: AlignmentDirectional.centerStart,
                  child: SkeletonBox(width: 160, height: 16),
                ),
                subtitle: const Padding(
                  padding: EdgeInsets.only(top: 8),
                  child: Align(
                    alignment: AlignmentDirectional.centerStart,
                    child: SkeletonBox(width: 88, height: 12),
                  ),
                ),
                trailing: artists
                    ? null
                    : const SkeletonBox(width: 40, height: 12),
              ),
          ],
        ],
      ),
    );
  }

  List<Widget> _summarySlivers(BuildContext context, ListeningSummary data) {
    final colors = Theme.of(context).colorScheme;
    final artists = data.artists.entries.toList()
      ..sort((a, b) => b.value.compareTo(a.value));
    final topArtists = artists.take(10).toList();
    final topTracks = data.tracks.take(20).toList();
    final artistArtwork = data.artistArtwork;
    final days = data.days.entries.toList()
      ..sort((a, b) => a.key.compareTo(b.key));
    final recentDays = days
        .skip((days.length - 7).clamp(0, days.length))
        .toList();
    final peakDay = days.isEmpty
        ? 0
        : days.map((e) => e.value).reduce((a, b) => a > b ? a : b);
    final textTheme = Theme.of(context).textTheme;
    return [
      SliverToBoxAdapter(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: AppContentCard(
            mornyeSurface: false,
            child: Padding(
              padding: const EdgeInsets.all(20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    context.l10n.listeningStatsMinutes(
                      (data.milliseconds / 60000).round(),
                    ),
                    style: textTheme.headlineMedium,
                  ),
                  const SizedBox(height: 8),
                  Text(
                    context.l10n.listeningStatsTotals(
                      data.plays,
                      data.tracks.length,
                      data.days.length,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
      if (data.tracks.isEmpty)
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Text(context.l10n.listeningStatsEmpty),
          ),
        ),
      if (days.isNotEmpty) ...[
        SliverToBoxAdapter(
          child: SettingsSectionHeader(
            title: context.l10n.listeningStatsActivity,
          ),
        ),
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 24),
            child: Column(
              children: [
                for (final day in recentDays)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 6),
                    child: Row(
                      children: [
                        SizedBox(
                          width: 68,
                          child: Text(
                            DateFormat.MMMd(
                              Localizations.localeOf(context).toLanguageTag(),
                            ).format(DateTime.parse(day.key)),
                          ),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: LinearProgressIndicator(
                            value: day.value / peakDay,
                            minHeight: 8,
                            borderRadius: BorderRadius.circular(8),
                          ),
                        ),
                        const SizedBox(width: 12),
                        Text('${(day.value / 60000).round()}'),
                      ],
                    ),
                  ),
              ],
            ),
          ),
        ),
      ],
      SliverToBoxAdapter(
        child: SettingsSectionHeader(
          title: context.l10n.listeningStatsTopArtists,
        ),
      ),
      SliverList.list(
        children: [
          for (final artist in topArtists)
            ListTile(
              leading: ExcludeSemantics(
                child: AppShapeClip(
                  child: SizedBox.square(
                    dimension: 48,
                    child: artistArtwork[artist.key] == null
                        ? ColoredBox(
                            color: colors.surfaceContainerHighest,
                            child: const Icon(Icons.person_outline),
                          )
                        : PlayerArtwork(
                            artUri: artistArtwork[artist.key],
                            colorScheme: colors,
                            cacheWidth:
                                (48 * MediaQuery.devicePixelRatioOf(context))
                                    .ceil(),
                            iconSize: 24,
                          ),
                  ),
                ),
              ),
              title: Text(artist.key),
              subtitle: Text(
                context.l10n.listeningStatsMinutes(
                  (artist.value / 60000).round(),
                ),
              ),
            ),
        ],
      ),
      SliverToBoxAdapter(
        child: SettingsSectionHeader(
          title: context.l10n.listeningStatsTopTracks,
        ),
      ),
      SliverList.list(
        children: [
          for (final total in topTracks)
            ListTile(
              leading: ExcludeSemantics(
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(8),
                  child: SizedBox.square(
                    dimension: 48,
                    child: PlayerArtwork(
                      artUri: total.track.artwork,
                      colorScheme: Theme.of(context).colorScheme,
                      cacheWidth: (48 * MediaQuery.devicePixelRatioOf(context))
                          .ceil(),
                      iconSize: 24,
                    ),
                  ),
                ),
              ),
              title: Text(
                total.track.title,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),
              subtitle: Text(
                total.track.artist.trim().isEmpty
                    ? context.l10n.unknownArtist
                    : total.track.artist,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),
              trailing: Text(
                context.l10n.listeningStatsMinutes(
                  (total.milliseconds / 60000).round(),
                ),
              ),
            ),
        ],
      ),
    ];
  }
}
