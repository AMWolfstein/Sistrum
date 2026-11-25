import 'package:material_ui/material_ui.dart';
import 'package:sistrum/widgets/app_bottom_sheet.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/constants/language_choices.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/providers/runtime_profile_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/providers/theme_provider.dart';
import 'package:sistrum/screens/settings/mornye_glass_mode_preview.dart';
import 'package:sistrum/models/theme_settings.dart';
import 'package:sistrum/theme/app_tokens.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/app_slider.dart';
import 'package:sistrum/widgets/mornye_chrome.dart';
import 'package:sistrum/widgets/slider_value_header.dart';
import 'package:sistrum/utils/desktop_platform.dart';
import 'package:sistrum/utils/nav_bar_inset.dart';
import 'package:sistrum/widgets/settings_group.dart';
import 'package:sistrum/widgets/app_icon.dart';
import 'package:sistrum/widgets/app_sliver_header.dart';
import 'package:sistrum/widgets/desktop/desktop_window_controls.dart';

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

            if (isDesktopPlatform)
              const SliverToBoxAdapter(
                child: SettingsGroup(children: [DesktopWindowModeSetting()]),
              ),

            ...settingsSectionSlivers(
              title: context.l10n.sectionColor,
              children: [
                _MornyeAccentPicker(
                  selected: themeSettings.mornyeAccent,
                  onChanged: ref.read(themeProvider.notifier).setMornyeAccent,
                ),
                const _MornyeGlassModeSelector(),
                SettingsSwitchItem(
                  key: const ValueKey('mornye-glass-outlines'),
                  icon: Icons.rounded_corner,
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
              connected: true,
              children: [
                _ThemeModeSelector(
                  currentMode: themeSettings.themeMode,
                  onChanged: (mode) =>
                      ref.read(themeProvider.notifier).setThemeMode(mode),
                ),
              ],
            ),

            ...settingsSectionSlivers(
              title: context.l10n.sectionLanguage,
              connected: true,
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
              connected: true,
              children: [const _InterfaceSizeSlider()],
            ),

            const SliverToBoxAdapter(child: SizedBox(height: 32)),
            const NavBarSliverSpacer(),
          ],
        ),
      ),
    );
  }
}
