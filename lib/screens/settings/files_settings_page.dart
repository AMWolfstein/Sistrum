import 'dart:io';
import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:device_info_plus/device_info_plus.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/utils/adaptive_layout.dart';
import 'package:sistrum/utils/nav_bar_inset.dart';
import 'package:sistrum/widgets/app_alert_dialog.dart';
import 'package:sistrum/widgets/app_icon.dart';
import 'package:sistrum/widgets/app_sliver_header.dart';
import 'package:sistrum/widgets/settings_group.dart';

class FilesSettingsPage extends ConsumerStatefulWidget {
  const FilesSettingsPage({super.key});

  @override
  ConsumerState<FilesSettingsPage> createState() => _FilesSettingsPageState();
}

class _FilesSettingsPageState extends ConsumerState<FilesSettingsPage> {
  int _androidSdkVersion = 0;
  bool _hasAllFilesAccess = false;

  @override
  void initState() {
    super.initState();
    _initDeviceInfo();
  }

  Future<void> _initDeviceInfo() async {
    if (Platform.isAndroid) {
      final deviceInfo = DeviceInfoPlugin();
      final androidInfo = await deviceInfo.androidInfo;
      final sdkVersion = androidInfo.version.sdkInt;
      final hasAccess = await Permission.manageExternalStorage.isGranted;
      if (mounted) {
        setState(() {
          _androidSdkVersion = sdkVersion;
          _hasAllFilesAccess = hasAccess;
        });
      }
    }
  }

  Future<void> _requestAllFilesAccess() async {
    final status = await Permission.manageExternalStorage.request();
    if (status.isGranted) {
      ref.read(settingsProvider.notifier).setUseAllFilesAccess(true);
      if (mounted) setState(() => _hasAllFilesAccess = true);
    } else if (status.isPermanentlyDenied && mounted) {
      final shouldOpen = await showAppConfirmDialog(
        context,
        title: context.l10n.setupStorageAccessRequired,
        message: context.l10n.allFilesAccessDeniedMessage,
        confirmLabel: context.l10n.setupOpenSettings,
        confirmIsDefault: true,
      );
      if (shouldOpen) await openAppSettings();
    }
  }

  Future<void> _disableAllFilesAccess() async {
    ref.read(settingsProvider.notifier).setUseAllFilesAccess(false);
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(context.l10n.allFilesAccessDisabledMessage)),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final settings = ref.watch(settingsProvider);
    final colorScheme = Theme.of(context).colorScheme;
    return PopScope(
      canPop: true,
      child: Scaffold(
        body: CustomScrollView(
          slivers: [
            AppSliverHeader.page(title: context.l10n.settingsFiles),
            ...settingsSectionSlivers(
              title: context.l10n.sectionExistingDownloads,
              children: [
                SettingsSwitchItem(
                  key: const ValueKey('remove-empty-folders'),
                  icon: Icons.folder_delete_outlined,
                  title: context.l10n.removeEmptyFoldersTitle,
                  subtitle: context.l10n.removeEmptyFoldersSubtitle,
                  value: settings.removeEmptyFolders,
                  onChanged: ref
                      .read(settingsProvider.notifier)
                      .setRemoveEmptyFolders,
                  showDivider: false,
                ),
              ],
            ),
            if (Platform.isAndroid && _androidSdkVersion >= 33) ...[
              ...settingsSectionSlivers(
                title: context.l10n.sectionStorageAccess,
                children: [
                  SettingsSwitchItem(
                    icon: Icons.folder_special_outlined,
                    title: context.l10n.allFilesAccess,
                    subtitle: _hasAllFilesAccess
                        ? context.l10n.allFilesAccessEnabledSubtitle
                        : context.l10n.allFilesAccessDisabledSubtitle,
                    value: _hasAllFilesAccess && settings.useAllFilesAccess,
                    onChanged: (value) {
                      if (value) {
                        _requestAllFilesAccess();
                      } else {
                        _disableAllFilesAccess();
                      }
                    },
                    showDivider: false,
                  ),
                ],
              ),
              SliverToBoxAdapter(
                child: Padding(
                  padding: EdgeInsets.fromLTRB(
                    16 + wideListInset(context),
                    8,
                    16 + wideListInset(context),
                    0,
                  ),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      AppIcon(
                        Icons.info_outline,
                        size: 16,
                        color: colorScheme.onSurfaceVariant,
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          context.l10n.allFilesAccessDescription,
                          style: Theme.of(context).textTheme.bodySmall
                              ?.copyWith(color: colorScheme.onSurfaceVariant),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
            const SliverToBoxAdapter(child: SizedBox(height: 32)),
            const NavBarSliverSpacer(),
          ],
        ),
      ),
    );
  }
}
