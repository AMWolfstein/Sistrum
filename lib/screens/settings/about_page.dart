import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:sistrum/constants/app_info.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/theme/mornye_icons.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/utils/adaptive_layout.dart';
import 'package:sistrum/utils/nav_bar_inset.dart';
import 'package:sistrum/widgets/settings_group.dart';
import 'package:sistrum/widgets/app_icon.dart';
import 'package:sistrum/widgets/app_sliver_header.dart';

class AboutPage extends StatelessWidget {
  const AboutPage({super.key});

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return PopScope(
      canPop: true,
      child: Scaffold(
        body: CustomScrollView(
          slivers: [
            AppSliverHeader.page(title: context.l10n.aboutTitle),

            SliverToBoxAdapter(
              child: Padding(
                padding: EdgeInsets.fromLTRB(
                  16 + wideListInset(context),
                  16,
                  16 + wideListInset(context),
                  8,
                ),
                child: _AppHeaderCard(),
              ),
            ),

            ...settingsSectionSlivers(
              title: context.l10n.aboutSpecialThanks,
              connected: true,
              children: [
                _AboutSettingsItem(
                  icon: Icons.swap_horiz_rounded,
                  title: 'UniTune',
                  subtitle: context.l10n.aboutUnituneDesc,
                  onTap: () =>
                      _launchUrl('https://github.com/FlazeIGuess/unitune-api'),
                  showDivider: true,
                ),
                _AboutSettingsItem(
                  icon: Icons.library_music_outlined,
                  title: 'MusicBrainz',
                  subtitle: context.l10n.aboutMusicBrainzDesc,
                  onTap: () => _launchUrl('https://musicbrainz.org'),
                  showDivider: true,
                ),
                _AboutSettingsItem(
                  icon: Icons.hub_outlined,
                  title: 'Squigly',
                  subtitle: context.l10n.aboutSquiglyDesc,
                  onTap: () => _launchUrl('https://squigly.link'),
                  showDivider: true,
                ),
                _AboutSettingsItem(
                  icon: Icons.lyrics_outlined,
                  title: 'Zarz-LRC',
                  subtitle: context.l10n.lyricsProviderZarzLrcDesc,
                  onTap: () =>
                      _launchUrl('https://api.zarz.moe/apple-music/docs'),
                  showDivider: true,
                ),
                _AboutSettingsItem(
                  icon: Icons.lyrics_outlined,
                  title: 'Paxsenix',
                  subtitle: context.l10n.aboutPaxsenixSubtitle,
                  onTap: () => _launchUrl('https://lyrics.paxsenix.org'),
                  showDivider: false,
                ),
              ],
            ),

            ...settingsSectionSlivers(
              title: context.l10n.aboutLinks,
              connected: true,
              children: [
                _AboutSettingsItem(
                  icon: Icons.phone_android,
                  title: context.l10n.aboutMobileSource,
                  subtitle: 'github.com/${AppInfo.githubRepo}',
                  onTap: () => _launchUrl(AppInfo.githubUrl),
                  showDivider: true,
                ),
                _AboutSettingsItem(
                  icon: Icons.lyrics_outlined,
                  title: context.l10n.aboutLyricsWebsite,
                  subtitle: 'lyrics.zarz.moe',
                  onTap: () => _launchUrl('https://lyrics.zarz.moe'),
                  showDivider: true,
                ),
                _AboutSettingsItem(
                  icon: Icons.monitor_heart_outlined,
                  title: context.l10n.aboutServiceStatus,
                  subtitle: 'status.zarz.moe',
                  onTap: () => _launchUrl('https://status.zarz.moe'),
                  showDivider: true,
                ),
                _AboutSettingsItem(
                  icon: Icons.bug_report_outlined,
                  title: context.l10n.aboutReportIssue,
                  subtitle: context.l10n.aboutReportIssueSubtitle,
                  onTap: () => _launchUrl('${AppInfo.githubUrl}/issues/new'),
                  showDivider: true,
                ),
                _AboutSettingsItem(
                  icon: Icons.lightbulb_outline,
                  title: context.l10n.aboutFeatureRequest,
                  subtitle: context.l10n.aboutFeatureRequestSubtitle,
                  onTap: () => _launchUrl('${AppInfo.githubUrl}/issues/new'),
                  showDivider: false,
                ),
              ],
            ),

            ...settingsSectionSlivers(
              title: context.l10n.aboutApp,
              connected: true,
              children: [
                _AboutSettingsItem(
                  icon: Icons.info_outline,
                  title: context.l10n.aboutVersion,
                  subtitle:
                      'v${AppInfo.displayVersion} (build ${AppInfo.buildNumber})',
                  showDivider: true,
                ),
                _AboutSettingsItem(
                  icon: Icons.policy_outlined,
                  title: MaterialLocalizations.of(context).licensesPageTitle,
                  subtitle: AppInfo.license,
                  onTap: () => showLicensePage(
                    context: context,
                    applicationName: AppInfo.appName,
                    applicationVersion: AppInfo.displayVersion,
                    applicationLegalese: '${AppInfo.copyright}\n${AppInfo.originalCredit}',
                  ),
                  showDivider: false,
                ),
              ],
            ),

            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Center(
                  child: Text(
                    [
                      AppInfo.shortGitCommit.isEmpty
                          ? AppInfo.copyright
                          : '${AppInfo.copyright} · ${AppInfo.shortGitCommit}',
                      AppInfo.originalCredit,
                    ].join('\n'),
                    textAlign: TextAlign.center,
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: colorScheme.onSurfaceVariant,
                    ),
                  ),
                ),
              ),
            ),

            const SliverToBoxAdapter(child: SizedBox(height: 16)),
            const NavBarSliverSpacer(),
          ],
        ),
      ),
    );
  }
}

Future<void> _launchUrl(String url) async {
  final uri = Uri.parse(url);
  await launchUrl(uri, mode: LaunchMode.inAppBrowserView);
}

class _AppHeaderCard extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final isDark = Theme.of(context).brightness == Brightness.dark;

    // Material sits on the settings group tier beside the groups below.
    final cardColor = !context.isMornye
        ? settingsGroupColor(context)
        : isDark
        ? Color.alphaBlend(
            Colors.white.withValues(alpha: 0.08),
            colorScheme.surface,
          )
        : colorScheme.surfaceContainerHighest;

    return LayoutBuilder(
      builder: (context, constraints) {
        final cardWidth = constraints.maxWidth;
        final shortestSide = MediaQuery.sizeOf(context).shortestSide;
        final textScale = MediaQuery.textScalerOf(
          context,
        ).scale(1.0).clamp(1.0, 1.4);
        final logoSize = (shortestSide * 0.22).clamp(72.0, 88.0);
        final contentPadding = (cardWidth * 0.06).clamp(16.0, 24.0);
        final titleGap = (16 * (1 + ((textScale - 1) * 0.2))).clamp(12.0, 20.0);

        return Container(
          decoration: BoxDecoration(
            color: cardColor,
            borderRadius: BorderRadius.circular(20),
          ),
          padding: EdgeInsets.all(contentPadding),
          child: Column(
            children: [
              Container(
                width: logoSize,
                height: logoSize,
                decoration: BoxDecoration(
                  color: colorScheme.primary,
                  shape: BoxShape.circle,
                ),
                // The asset is the bare mark, so inset it within the disc.
                padding: EdgeInsets.all(logoSize * 0.24),
                child: Image.asset(
                  'assets/images/logo-transparent.png',
                  color: colorScheme.onPrimary,
                  fit: BoxFit.contain,
                  errorBuilder: (_, _, _) => ClipRRect(
                    borderRadius: BorderRadius.circular(24),
                    child: Image.asset(
                      'assets/images/logo.png',
                      width: logoSize,
                      height: logoSize,
                      fit: BoxFit.cover,
                    ),
                  ),
                ),
              ),
              SizedBox(height: titleGap),
              Text(
                AppInfo.appName,
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                  fontWeight: FontWeight.bold,
                ),
              ),
              const SizedBox(height: 4),
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: 4,
                ),
                decoration: BoxDecoration(
                  color: colorScheme.secondaryContainer,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Text(
                  'v${AppInfo.displayVersion}',
                  style: Theme.of(context).textTheme.labelMedium?.copyWith(
                    color: colorScheme.onSecondaryContainer,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
              SizedBox(height: titleGap),
              Text(
                context.l10n.aboutAppDescription,
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: colorScheme.onSurfaceVariant,
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}

class _AboutSettingsItem extends StatelessWidget {
  final IconData icon;
  final String title;
  final String? subtitle;
  final VoidCallback? onTap;
  final bool showDivider;

  const _AboutSettingsItem({
    required this.icon,
    required this.title,
    this.subtitle,
    this.onTap,
    this.showDivider = true,
  });

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    final content = Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        InkWell(
          onTap: onTap,
          splashColor: colorScheme.primary.withValues(alpha: 0.12),
          highlightColor: colorScheme.primary.withValues(alpha: 0.08),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
            child: Row(
              children: [
                SizedBox(
                  width: 40,
                  height: 40,
                  child: AppIcon(
                    context.adaptiveIcon(icon),
                    color: context.isMornye
                        ? colorScheme.primary
                        : colorScheme.onSurfaceVariant,
                    size: 24,
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(title, style: Theme.of(context).textTheme.bodyLarge),
                      if (subtitle != null) ...[
                        const SizedBox(height: 2),
                        Text(
                          subtitle!,
                          style: Theme.of(context).textTheme.bodyMedium
                              ?.copyWith(color: colorScheme.onSurfaceVariant),
                        ),
                      ],
                    ],
                  ),
                ),
                if (onTap != null)
                  AppIcon(
                    Icons.chevron_right,
                    color: colorScheme.onSurfaceVariant,
                  ),
              ],
            ),
          ),
        ),
        if (showDivider && !settingsRowsConnected(context))
          Divider(
            height: 1,
            thickness: 1,
            indent: 76, // 20 + 40 + 16 = 76 (same as contributor item)
            endIndent: 20,
            color: colorScheme.outlineVariant.withValues(alpha: 0.3),
          ),
      ],
    );
    return SettingsSearchTarget(label: title, child: content);
  }
}
