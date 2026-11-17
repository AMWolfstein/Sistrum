import 'dart:async';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:sistrum/widgets/app_alert_dialog.dart';
import 'package:sistrum/widgets/app_bottom_sheet.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:path_provider/path_provider.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:device_info_plus/device_info_plus.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/services/platform_bridge.dart';
import 'package:sistrum/utils/adaptive_layout.dart';
import 'package:sistrum/utils/nav_bar_inset.dart';
import 'package:sistrum/utils/saf_download_tree.dart';
import 'package:sistrum/widgets/settings_group.dart';
import 'package:sistrum/widgets/app_icon.dart';
import 'package:sistrum/widgets/app_sliver_header.dart';

class FilesSettingsPage extends ConsumerStatefulWidget {
  const FilesSettingsPage({super.key});

  @override
  ConsumerState<FilesSettingsPage> createState() => _FilesSettingsPageState();
}

class _FilesSettingsPageState extends ConsumerState<FilesSettingsPage> {
  int _androidSdkVersion = 0;
  bool _hasAllFilesAccess = false;
  bool _safAccessLost = false;

  @override
  void initState() {
    super.initState();
    _initDeviceInfo();
    _checkSafAccess();
  }

  Future<void> _checkSafAccess() async {
    if (!Platform.isAndroid) return;
    final settings = ref.read(settingsProvider);
    if (settings.storageMode != 'saf' || settings.downloadTreeUri.isEmpty) {
      if (mounted && _safAccessLost) setState(() => _safAccessLost = false);
      return;
    }
    final accessible = await PlatformBridge.isSafTreeAccessible(
      settings.downloadTreeUri,
    );
    if (mounted) setState(() => _safAccessLost = !accessible);
  }

  Future<void> _pickSafTreeAndSave() async {
    await pickAndStoreSafDownloadTree(ref);
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
              title: context.l10n.setupDownloadLocationTitle,
              children: [
                SettingsItem(
                  title: context.l10n.downloadDirectory,
                  subtitle: settings.downloadDirectory.isEmpty
                      ? ('Download/Sistrum')
                      : settings.downloadDirectory,
                  onTap: () => _pickDirectory(context, ref),
                  showDivider: false,
                ),
              ],
            ),
            if (Platform.isAndroid &&
                _safAccessLost &&
                settings.storageMode == 'saf' &&
                settings.downloadTreeUri.isNotEmpty)
              SliverToBoxAdapter(
                child: SettingsInfoCard(
                  icon: Icons.warning_amber_outlined,
                  tone: SettingsInfoTone.error,
                  title: context.l10n.downloadFolderAccessLostTitle,
                  message: context.l10n.downloadFolderAccessLostSubtitle,
                  margin: const EdgeInsets.fromLTRB(16, 8, 16, 4),
                  action: FilledButton.tonal(
                    onPressed: () async {
                      await _pickSafTreeAndSave();
                      await _checkSafAccess();
                    },
                    child: Text(context.l10n.downloadFolderReselect),
                  ),
                ),
              ),

            if (Platform.isAndroid && _androidSdkVersion >= 33) ...[
              ...settingsSectionSlivers(
                title: context.l10n.sectionStorageAccess,
                children: [
                  SettingsSwitchItem(
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

  Future<void> _pickDirectory(BuildContext context, WidgetRef ref) async {
    if (defaultTargetPlatform == TargetPlatform.android) {
      _showAndroidDirectoryOptions(context, ref);
    }
  }

  Future<String> _getDefaultAndroidDirectory() async {
    const directDownloadPath = '/storage/emulated/0/Download/Sistrum';
    try {
      final downloadDir = Directory(directDownloadPath);
      if (!await downloadDir.exists()) {
        await downloadDir.create(recursive: true);
      }
      return downloadDir.path;
    } catch (_) {}
    try {
      final externalDir = await getExternalStorageDirectory();
      if (externalDir != null) {
        final downloadDir = Directory(
          '${externalDir.parent.parent.parent.parent.path}/Download/Sistrum',
        );
        if (!await downloadDir.exists()) {
          await downloadDir.create(recursive: true);
        }
        return downloadDir.path;
      }
    } catch (_) {}
    final appDir = await getApplicationDocumentsDirectory();
    final fallbackDir = Directory('${appDir.path}/Sistrum');
    if (!await fallbackDir.exists()) await fallbackDir.create(recursive: true);
    return fallbackDir.path;
  }

  void _showAndroidDirectoryOptions(BuildContext context, WidgetRef ref) {
    final colorScheme = Theme.of(context).colorScheme;
    final settings = ref.read(settingsProvider);
    final isSafMode =
        settings.storageMode == 'saf' && settings.downloadTreeUri.isNotEmpty;
    showAppScrollableBottomSheet<void>(
      context: context,
      title: context.l10n.setupDownloadLocationTitle,
      subtitle: context.l10n.downloadLocationSubtitle,
      builder: (ctx, controller) => SingleChildScrollView(
        controller: controller,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            AppSheetOption.choice(
              leading: AppIcon(
                Icons.folder_special,
                color: colorScheme.primary,
              ),
              title: Text(context.l10n.storageAutomaticFolder),
              subtitle: Text(context.l10n.storageAutomaticDownloadFolder),
              selected: !isSafMode,
              onTap: () async {
                Navigator.pop(ctx);
                final defaultDir = await _getDefaultAndroidDirectory();
                final notifier = ref.read(settingsProvider.notifier);
                notifier.setStorageMode('app');
                notifier.setDownloadDirectory(defaultDir);
                notifier.setDownloadTreeUri('');
              },
            ),
            AppSheetOption.choice(
              leading: AppIcon(Icons.folder_open, color: colorScheme.primary),
              title: Text(context.l10n.storageSafRecommended),
              subtitle: Text(context.l10n.storageDownloadFolderHint),
              selected: isSafMode,
              onTap: () async {
                Navigator.pop(ctx);
                await _pickSafTreeAndSave();
                await _checkSafAccess();
              },
            ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
  }
}
