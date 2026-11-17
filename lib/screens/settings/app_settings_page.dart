import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart' show CupertinoActivityIndicator;
import 'package:sistrum/theme/mornye_icons.dart';
import 'package:sistrum/widgets/app_alert_dialog.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/utils/nav_bar_inset.dart';
import 'package:sistrum/widgets/settings_group.dart';
import 'package:sistrum/widgets/app_icon.dart';
import 'package:sistrum/widgets/app_sliver_header.dart';
import 'package:sistrum/providers/download_history_provider.dart';

class AppSettingsPage extends ConsumerWidget {
  const AppSettingsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final settings = ref.watch(settingsProvider);
    final colorScheme = Theme.of(context).colorScheme;

    return PopScope(
      canPop: true,
      child: Scaffold(
        body: CustomScrollView(
          slivers: [
            AppSliverHeader.page(title: context.l10n.settingsApp),

            ...settingsSectionSlivers(
              title: context.l10n.sectionApp,
              children: [
                SettingsSwitchItem(
                  title: context.l10n.optionsExtensionStore,
                  subtitle: context.l10n.optionsExtensionStoreSubtitle,
                  value: settings.showExtensionStore,
                  onChanged: (v) => ref
                      .read(settingsProvider.notifier)
                      .setShowExtensionStore(v),
                ),
                _VerificationBrowserModeSelector(
                  currentMode: settings.extensionVerificationBrowserMode,
                  onChanged: (mode) => ref
                      .read(settingsProvider.notifier)
                      .setExtensionVerificationBrowserMode(mode),
                ),
                SettingsSwitchItem(
                  title: context.l10n.optionsCheckUpdates,
                  subtitle: context.l10n.optionsCheckUpdatesSubtitle,
                  value: settings.checkForUpdates,
                  onChanged: (v) =>
                      ref.read(settingsProvider.notifier).setCheckForUpdates(v),
                  showDivider: settings.checkForUpdates,
                ),
                if (settings.checkForUpdates)
                  _UpdateChannelSelector(
                    currentChannel: settings.updateChannel,
                    onChanged: (v) =>
                        ref.read(settingsProvider.notifier).setUpdateChannel(v),
                  ),
              ],
            ),

            ...settingsSectionSlivers(
              title: context.l10n.sectionData,
              children: [
                SettingsItem(
                  title: context.l10n.cleanupOrphanedDownloads,
                  subtitle: context.l10n.cleanupOrphanedDownloadsSubtitle,
                  onTap: () => _cleanupOrphanedDownloads(context, ref),
                ),
                SettingsItem(
                  title: context.l10n.optionsClearHistory,
                  subtitle: context.l10n.optionsClearHistorySubtitle,
                  onTap: () =>
                      _showClearHistoryDialog(context, ref, colorScheme),
                ),
              ],
            ),

            ...settingsSectionSlivers(
              title: context.l10n.sectionDebug,
              children: [
                SettingsSwitchItem(
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

  void _showClearHistoryDialog(
    BuildContext context,
    WidgetRef ref,
    ColorScheme colorScheme,
  ) {
    showAppDialog<void>(
      context: context,
      builder: (context) => AppAlertDialog(
        title: Text(context.l10n.dialogClearHistoryTitle),
        content: Text(context.l10n.dialogClearHistoryMessage),
        actions: [
          AppDialogAction(
            isDefault: true,
            onPressed: () => Navigator.pop(context),
            child: Text(context.l10n.dialogCancel),
          ),
          AppDialogAction(
            isDestructive: true,
            onPressed: () {
              ref.read(downloadHistoryProvider.notifier).clearHistory();
              Navigator.pop(context);
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(content: Text(context.l10n.snackbarHistoryCleared)),
              );
            },
            style: TextButton.styleFrom(foregroundColor: colorScheme.error),
            child: Text(context.l10n.dialogClear),
          ),
        ],
      ),
    );
  }

  Future<void> _cleanupOrphanedDownloads(
    BuildContext context,
    WidgetRef ref,
  ) async {
    // The dialog sits on the root navigator, while this page may live in a
    // tab or settings detail navigator: popping through the page context
    // closed the page and left the blocking dialog up.
    final dialogNavigator = Navigator.of(context, rootNavigator: true);
    showAppDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (context) => AppAlertDialog(
        content: Row(
          children: [
            const CupertinoActivityIndicator(),
            const SizedBox(width: 16),
            Expanded(child: Text(context.l10n.cleanupOrphanedDownloads)),
          ],
        ),
      ),
    );
    try {
      final removed = await ref
          .read(downloadHistoryProvider.notifier)
          .cleanupOrphanedDownloads();
      dialogNavigator.pop();
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              removed > 0
                  ? context.l10n.cleanupOrphanedDownloadsResult(removed)
                  : context.l10n.cleanupOrphanedDownloadsNone,
            ),
          ),
        );
      }
    } catch (e) {
      dialogNavigator.pop();
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(context.l10n.snackbarError(context.friendlyError(e))),
          ),
        );
      }
    }
  }
}

class _UpdateChannelSelector extends StatelessWidget {
  final String currentChannel;
  final ValueChanged<String> onChanged;
  const _UpdateChannelSelector({
    required this.currentChannel,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    final content = Padding(
      padding: const EdgeInsets.all(20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              AppIcon(
                context.adaptiveIcon(Icons.new_releases),
                color: colorScheme.primary,
                size: 24,
              ),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      context.l10n.optionsUpdateChannel,
                      style: Theme.of(context).textTheme.bodyLarge,
                    ),
                    const SizedBox(height: 2),
                    Text(
                      currentChannel == 'preview'
                          ? context.l10n.optionsUpdateChannelPreview
                          : context.l10n.optionsUpdateChannelStable,
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        color: colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          SettingsSegmentedChoice<String>(
            selected: currentChannel,
            onChanged: onChanged,
            options: [
              SettingsSegmentOption(
                value: 'stable',
                label: context.l10n.channelStable,
              ),
              SettingsSegmentOption(
                value: 'preview',
                label: context.l10n.channelPreview,
              ),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              AppIcon(
                Icons.info_outline,
                size: 16,
                color: colorScheme.onSurfaceVariant,
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  context.l10n.optionsUpdateChannelWarning,
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: colorScheme.onSurfaceVariant,
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
    return SettingsSearchTarget(
      label: context.l10n.optionsUpdateChannel,
      child: content,
    );
  }
}

class _VerificationBrowserModeSelector extends StatelessWidget {
  final String currentMode;
  final ValueChanged<String> onChanged;

  const _VerificationBrowserModeSelector({
    required this.currentMode,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final normalizedMode = currentMode == 'in_app_first'
        ? 'in_app_first'
        : currentMode == 'external_first'
        ? 'external_first'
        : 'in_app_first';

    final content = Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  AppIcon(
                    context.adaptiveIcon(Icons.open_in_browser),
                    color: colorScheme.primary,
                    size: 24,
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          context.l10n.extensionVerificationBrowserTitle,
                          style: Theme.of(context).textTheme.bodyLarge,
                        ),
                        const SizedBox(height: 2),
                        Text(
                          normalizedMode == 'external_first'
                              ? context
                                    .l10n
                                    .extensionVerificationBrowserSubtitleExternal
                              : context
                                    .l10n
                                    .extensionVerificationBrowserSubtitleInApp,
                          style: Theme.of(context).textTheme.bodyMedium
                              ?.copyWith(color: colorScheme.onSurfaceVariant),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              SettingsSegmentedChoice<String>(
                selected: normalizedMode,
                onChanged: onChanged,
                options: [
                  SettingsSegmentOption(
                    value: 'external_first',
                    label: context.l10n.extensionVerificationBrowserExternal,
                  ),
                  SettingsSegmentOption(
                    value: 'in_app_first',
                    label: context.l10n.extensionVerificationBrowserInApp,
                  ),
                ],
              ),
            ],
          ),
        ),
        Divider(
          height: 1,
          thickness: 1,
          indent: 56,
          endIndent: 20,
          color: colorScheme.outlineVariant.withValues(alpha: 0.3),
        ),
      ],
    );
    return SettingsSearchTarget(
      label: context.l10n.extensionVerificationBrowserTitle,
      child: content,
    );
  }
}
