import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/utils/nav_bar_inset.dart';
import 'package:sistrum/widgets/settings_group.dart';
import 'package:sistrum/widgets/app_sliver_header.dart';

class AppSettingsPage extends ConsumerWidget {
  const AppSettingsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final settings = ref.watch(settingsProvider);

    return PopScope(
      canPop: true,
      child: Scaffold(
        body: CustomScrollView(
          slivers: [
            AppSliverHeader.page(title: context.l10n.settingsApp),

            ...settingsSectionSlivers(
              title: context.l10n.sectionDebug,
              children: [
                SettingsSwitchItem(
                  icon: Icons.bug_report,
                  title: context.l10n.optionsDetailedLogging,
                  subtitle: settings.enableLogging
                      ? context.l10n.optionsDetailedLoggingOn
                      : context.l10n.optionsDetailedLoggingOff,
                  value: settings.enableLogging,
                  onChanged: (v) =>
                      ref.read(settingsProvider.notifier).setEnableLogging(v),
                  showDivider: false,
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
