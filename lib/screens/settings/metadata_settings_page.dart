import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/controllers/clickable_metadata.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/utils/nav_bar_inset.dart';
import 'package:sistrum/screens/settings/metadata_provider_priority_page.dart';
import 'package:sistrum/widgets/settings_group.dart';
import 'package:sistrum/widgets/app_sliver_header.dart';

class MetadataSettingsPage extends ConsumerWidget {
  const MetadataSettingsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return PopScope(
      canPop: true,
      child: Scaffold(
        body: CustomScrollView(
          slivers: [
            AppSliverHeader.page(title: context.l10n.settingsMetadata),

            ...settingsSectionSlivers(
              title: context.l10n.sectionMetadataProviders,
              children: [
                SettingsItem(
                  title: context.l10n.metadataProvidersTitle,
                  subtitle: context.l10n.metadataProvidersSubtitle,
                  onTap: () => pushPage<void>(
                    context,
                    (_) => const MetadataProviderPriorityPage(),
                  ),
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
