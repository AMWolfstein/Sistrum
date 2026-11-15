import 'package:flutter/material.dart';
import 'package:sistrum/widgets/app_bottom_sheet.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/constants/language_choices.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/providers/theme_provider.dart';
import 'package:sistrum/models/theme_settings.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/mornye_chrome.dart';
import 'package:sistrum/utils/adaptive_layout.dart';
import 'package:sistrum/utils/nav_bar_inset.dart';
import 'package:sistrum/widgets/settings_group.dart';
import 'package:sistrum/widgets/app_icon.dart';
import 'package:sistrum/widgets/app_sliver_header.dart';
import 'package:sistrum/widgets/custom_font_setting.dart';

part 'appearance_settings_page_widgets.dart';

class AppearanceSettingsPage extends ConsumerWidget {
  const AppearanceSettingsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final themeSettings = ref.watch(themeProvider);
    final settings = ref.watch(settingsProvider);

    return PopScope(
      canPop: true,
      child: Scaffold(
        body: CustomScrollView(
          slivers: [
            AppSliverHeader.page(title: context.l10n.appearanceTitle),

            SliverToBoxAdapter(
              child: Padding(
                padding: EdgeInsets.symmetric(
                  horizontal: 16 + wideListInset(context),
                  vertical: 8,
                ),
                child: _ThemePreviewCard(),
              ),
            ),

            ...settingsSectionSlivers(
              title: context.l10n.sectionColor,
              children: [
                _MornyeAccentPicker(
                  selected: themeSettings.mornyeAccent,
                  onChanged: ref.read(themeProvider.notifier).setMornyeAccent,
                ),
                SettingsSwitchItem(
                  key: const ValueKey('mornye-glass-outlines'),
                  title: context.l10n.appearanceGlassOutlines,
                  subtitle: context.l10n.appearanceGlassOutlinesSubtitle,
                  value: themeSettings.mornyeGlassOutlines,
                  onChanged: ref
                      .read(themeProvider.notifier)
                      .setMornyeGlassOutlines,
                  showDivider: false,
                ),
              ],
            ),

            ...settingsSectionSlivers(
              title: context.l10n.sectionTheme,
              children: [
                _ThemeModeSelector(
                  currentMode: themeSettings.themeMode,
                  onChanged: (mode) =>
                      ref.read(themeProvider.notifier).setThemeMode(mode),
                ),
              ],
            ),

            SliverToBoxAdapter(
              child: SettingsGroup(
                children: [
                  SettingsSwitchItem(
                    title: context.l10n.appearanceUseSystemFont,
                    subtitle: context.l10n.appearanceUseSystemFontSubtitle,
                    value: themeSettings.useSystemFont,
                    onChanged: ref
                        .read(themeProvider.notifier)
                        .setUseSystemFont,
                  ),
                  SettingsSwitchItem(
                    title: context.l10n.appearanceHeroAnimations,
                    subtitle: context.l10n.appearanceHeroAnimationsSubtitle,
                    value: settings.heroAnimationsEnabled,
                    onChanged: (value) => ref
                        .read(settingsProvider.notifier)
                        .setHeroAnimationsEnabled(value),
                  ),
                  const CustomFontSetting(),
                  SettingsSwitchItem(
                    title: context.l10n.appearanceForceBlur,
                    subtitle: context.l10n.appearanceForceBlurSubtitle,
                    value: settings.forceBackdropBlur,
                    onChanged: (value) => ref
                        .read(settingsProvider.notifier)
                        .setForceBackdropBlur(value),
                    showDivider: false,
                  ),
                ],
              ),
            ),

            ...settingsSectionSlivers(
              title: context.l10n.sectionLanguage,
              children: [
                _LanguageSelector(
                  currentLocale: settings.locale,
                  onChanged: (locale) =>
                      ref.read(settingsProvider.notifier).setLocale(locale),
                ),
              ],
            ),

            ...settingsSectionSlivers(
              title: context.l10n.sectionLayout,
              children: [
                _HistoryViewSelector(
                  currentMode: settings.historyViewMode,
                  onChanged: (mode) => ref
                      .read(settingsProvider.notifier)
                      .setHistoryViewMode(mode),
                ),
              ],
            ),

            const SliverToBoxAdapter(child: SizedBox(height: 32)),
            const NavBarSliverSpacer(),
          ],
        ),
      ),
    );
  }
}
