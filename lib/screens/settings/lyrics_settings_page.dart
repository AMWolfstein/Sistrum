import 'dart:io';

import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/providers/runtime_profile_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/widgets/settings_group.dart';
import 'package:sistrum/widgets/app_sliver_header.dart';
import 'package:sistrum/widgets/app_bottom_sheet.dart';
import 'package:sistrum/utils/nav_bar_inset.dart';
import 'package:sistrum/widgets/lyrics_size_setting.dart';

class LyricsSettingsPage extends ConsumerWidget {
  const LyricsSettingsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final settings = ref.watch(settingsProvider);

    return PopScope(
      canPop: true,
      child: Scaffold(
        body: CustomScrollView(
          slivers: [
            AppSliverHeader.page(title: context.l10n.settingsLyrics),

            ...settingsSectionSlivers(
              title: context.l10n.sectionLyrics,
              connected: true,
              children: [
                const LyricsSizeSetting(),
                // Lyrics blur only where the player draws it.
                if (ref.watch(mornyeLiquidGlassProvider))
                  const LyricsBlurSetting(),
                const LyricsOffsetSetting(),
                SettingsSwitchItem(
                  icon: Icons.light_mode_outlined,
                  title: context.l10n.lyricsKeepScreenOn,
                  subtitle: context.l10n.lyricsKeepScreenOnSubtitle,
                  value: settings.keepScreenOnLyrics,
                  onChanged: (value) => ref
                      .read(settingsProvider.notifier)
                      .setKeepScreenOnLyrics(value),
                ),
                if (Platform.isAndroid || Platform.isIOS)
                  SettingsSwitchItem(
                    icon: Icons.screen_lock_portrait_outlined,
                    title: context.l10n.lyricsLockScreen,
                    subtitle: Platform.isIOS
                        ? context.l10n.lyricsLockScreenSubtitleIos
                        : context.l10n.lyricsLockScreenSubtitle,
                    value: settings.lockScreenLyrics,
                    onChanged: (value) => ref
                        .read(settingsProvider.notifier)
                        .setLockScreenLyrics(value),
                  ),
                SettingsSwitchItem(
                  key: const ValueKey('lyric-snippet-preview'),
                  icon: Icons.lyrics_outlined,
                  title: context.l10n.lyricsPlayerPreview,
                  subtitle: context.l10n.lyricsPlayerPreviewSubtitle,
                  value: settings.playerShowLyricSnippet,
                  onChanged: (value) => ref
                      .read(settingsProvider.notifier)
                      .setPlayerShowLyricSnippet(value),
                ),
                SettingsSwitchItem(
                  key: const ValueKey('lyrics-romanize'),
                  icon: Icons.record_voice_over_outlined,
                  title: context.l10n.lyricsRomanize,
                  subtitle: context.l10n.lyricsRomanizeSubtitle,
                  value: settings.lyricsRomanize,
                  onChanged: (value) => ref
                      .read(settingsProvider.notifier)
                      .setLyricsRomanize(value),
                ),
                SettingsSwitchItem(
                  key: const ValueKey('lyrics-romanized-main-line'),
                  icon: Icons.swap_vert_rounded,
                  title: context.l10n.lyricsRomanizedMainLine,
                  subtitle: context.l10n.lyricsRomanizedMainLineSubtitle,
                  value: settings.lyricsRomanizedMainLine,
                  onChanged: (value) => ref
                      .read(settingsProvider.notifier)
                      .setLyricsRomanizedMainLine(value),
                ),
                SettingsSwitchItem(
                  icon: Icons.subtitles_outlined,
                  title: context.l10n.optionsEmbedLyrics,
                  subtitle: settings.embedMetadata
                      ? context.l10n.optionsEmbedLyricsSubtitle
                      : context.l10n.downloadEmbedLyricsDisabled,
                  value: settings.embedLyrics,
                  enabled: settings.embedMetadata,
                  onChanged: (value) =>
                      ref.read(settingsProvider.notifier).setEmbedLyrics(value),
                  showDivider: settings.embedMetadata && settings.embedLyrics,
                ),
                if (settings.embedMetadata && settings.embedLyrics) ...[
                  SettingsItem(
                    icon: Icons.lyrics_outlined,
                    title: context.l10n.lyricsMode,
                    subtitle: _getLyricsModeLabel(context, settings.lyricsMode),
                    onTap: () => _showLyricsModePicker(
                      context,
                      ref,
                      settings.lyricsMode,
                    ),
                  ),
                ],
              ],
            ),

            const SliverToBoxAdapter(child: SizedBox(height: 32)),
            const NavBarSliverSpacer(),
          ],
        ),
      ),
    );
  }

  String _getLyricsModeLabel(BuildContext context, String mode) {
    switch (mode) {
      case 'external':
        return context.l10n.lyricsModeExternal;
      case 'both':
        return context.l10n.lyricsModeBoth;
      default:
        return context.l10n.lyricsModeEmbed;
    }
  }

  void _showLyricsModePicker(
    BuildContext context,
    WidgetRef ref,
    String current,
  ) => showAppChoiceSheet(
    context: context,
    title: context.l10n.lyricsMode,
    subtitle: context.l10n.lyricsModeDescription,
    selected: current,
    choices: [
      AppChoice(
        'embed',
        Icons.audiotrack,
        context.l10n.lyricsModeEmbed,
        subtitle: context.l10n.lyricsModeEmbedSubtitle,
      ),
      AppChoice(
        'external',
        Icons.insert_drive_file_outlined,
        context.l10n.lyricsModeExternal,
        subtitle: context.l10n.lyricsModeExternalSubtitle,
      ),
      AppChoice(
        'both',
        Icons.library_music_outlined,
        context.l10n.lyricsModeBoth,
        subtitle: context.l10n.lyricsModeBothSubtitle,
      ),
    ],
    onSelected: (mode) =>
        ref.read(settingsProvider.notifier).setLyricsMode(mode),
  );
}
