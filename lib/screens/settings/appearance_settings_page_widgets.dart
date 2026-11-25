part of 'appearance_settings_page.dart';

/// Chooses Liquid Glass or None. A high-contrast request turns
/// glass off, as the renderer does, so the note explains the fallback.
class _MornyeGlassModeSelector extends ConsumerWidget {
  const _MornyeGlassModeSelector();

  static const _modes = [MornyeGlassLevel.liquid, MornyeGlassLevel.flat];

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final selected = ref.watch(mornyeGlassModeProvider);
    final theme = Theme.of(context);
    final l10n = context.l10n;
    final contrastBlocked = ref.watch(
      mornyeGlassContrastBlockedProvider(MediaQuery.highContrastOf(context)),
    );
    final labels = {
      MornyeGlassLevel.liquid: l10n.appearanceGlassLiquid,
      MornyeGlassLevel.flat: l10n.appearanceGlassNone,
    };
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(l10n.appearanceGlassMode, style: theme.textTheme.titleMedium),
          const SizedBox(height: 4),
          Text(
            l10n.appearanceGlassModeSubtitle,
            style: theme.textTheme.bodySmall,
          ),
          if (contrastBlocked) ...[
            const SizedBox(height: 8),
            Text(
              l10n.appearanceGlassHighContrast,
              key: const ValueKey('mornye-glass-high-contrast'),
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ],
          const SizedBox(height: 12),
          MornyeSegmentedControl(
            key: const ValueKey('mornye-glass-mode'),
            labels: [for (final mode in _modes) labels[mode]!],
            selectedIndex: _modes.indexOf(selected),
            onChanged: (index) => ref
                .read(themeProvider.notifier)
                .setMornyeGlassLevel(_modes[index]),
            samplesBackdrop: false,
          ),
          const SizedBox(height: 16),
          MornyeGlassModePreview(modeLabel: labels[selected]!),
        ],
      ),
    );
  }
}

class _MornyeAccentPicker extends StatelessWidget {
  const _MornyeAccentPicker({required this.selected, required this.onChanged});

  final MornyeAccent selected;
  final ValueChanged<MornyeAccent> onChanged;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final labels = {
      MornyeAccent.red: l10n.appearanceAccentRed,
      MornyeAccent.orange: l10n.appearanceAccentOrange,
      MornyeAccent.green: l10n.appearanceAccentGreen,
      MornyeAccent.teal: l10n.appearanceAccentTeal,
      MornyeAccent.blue: l10n.appearanceAccentBlue,
      MornyeAccent.purple: l10n.appearanceAccentPurple,
      MornyeAccent.pink: l10n.appearanceAccentPink,
    };
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Wrap(
        spacing: 8,
        runSpacing: 8,
        children: [
          for (final accent in MornyeAccent.values)
            Builder(
              builder: (context) {
                final color = MornyeTheme.accentColor(
                  accent,
                  Theme.of(context).brightness,
                );
                final foreground =
                    ThemeData.estimateBrightnessForColor(color) ==
                        Brightness.dark
                    ? Colors.white
                    : Colors.black;
                return Semantics(
                  selected: selected == accent,
                  child: IconButton(
                    tooltip: labels[accent],
                    onPressed: () => onChanged(accent),
                    style: IconButton.styleFrom(
                      backgroundColor: color,
                      foregroundColor: foreground,
                      minimumSize: const Size.square(48),
                    ),
                    icon: selected == accent
                        ? const AppIcon(Icons.check)
                        : const SizedBox.square(dimension: 24),
                  ),
                );
              },
            ),
        ],
      ),
    );
  }
}

class _ThemeModeSelector extends StatelessWidget {
  final ThemeMode currentMode;
  final ValueChanged<ThemeMode> onChanged;
  const _ThemeModeSelector({
    required this.currentMode,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    final labels = {
      ThemeMode.system: context.l10n.appearanceThemeSystem,
      ThemeMode.light: context.l10n.appearanceThemeLight,
      ThemeMode.dark: context.l10n.appearanceThemeDark,
    };
    return Padding(
      padding: const EdgeInsets.all(12),
      child: MornyeSegmentedControl(
        labels: labels.values.toList(),
        selectedIndex: labels.keys.toList().indexOf(currentMode),
        onChanged: (index) => onChanged(labels.keys.elementAt(index)),
        samplesBackdrop: false,
      ),
    );
  }
}

/// Scales the whole app. The size applies when the slider is released, so
/// the slider does not move under the pointer while it is dragged.
class _InterfaceSizeSlider extends ConsumerStatefulWidget {
  const _InterfaceSizeSlider();

  @override
  ConsumerState<_InterfaceSizeSlider> createState() =>
      _InterfaceSizeSliderState();
}

class _InterfaceSizeSliderState extends ConsumerState<_InterfaceSizeSlider> {
  double? _draft;

  @override
  Widget build(BuildContext context) {
    final saved = ref.watch(
      settingsProvider.select((settings) => settings.interfaceScale),
    );
    final value = interfaceScaleFromJson(_draft ?? saved);
    final l10n = context.l10n;
    final tokens = context.tokens;
    final theme = Theme.of(context);
    final format = NumberFormat.percentPattern(l10n.localeName);
    final label = format.format(value);
    final content = Padding(
      padding: EdgeInsets.symmetric(
        horizontal: tokens.rowPaddingH,
        vertical: tokens.rowPaddingV,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SliderValueHeader(title: l10n.appearanceInterfaceSize, value: label),
          AppSlider(
            key: const ValueKey('interface-size-slider'),
            value: value,
            min: minInterfaceScale,
            max: maxInterfaceScale,
            divisions: 10,
            label: label,
            semanticLabel: l10n.appearanceInterfaceSize,
            semanticFormatterCallback: format.format,
            onChanged: (value) => setState(() => _draft = value),
            onChangeEnd: (value) {
              ref.read(settingsProvider.notifier).setInterfaceScale(value);
              setState(() => _draft = null);
            },
          ),
          DefaultTextStyle(
            style: theme.textTheme.bodySmall!.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(child: Text(l10n.appearanceInterfaceSizeSmaller)),
                SizedBox(width: tokens.gapMd),
                Expanded(
                  child: Text(
                    l10n.appearanceInterfaceSizeLarger,
                    textAlign: TextAlign.end,
                  ),
                ),
              ],
            ),
          ),
          SizedBox(height: tokens.gapSm),
          Text(
            l10n.appearanceInterfaceSizeDescription,
            style: theme.textTheme.bodySmall?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
    return SettingsSearchTarget(
      label: l10n.appearanceInterfaceSize,
      child: content,
    );
  }
}

class _LanguageSelector extends StatelessWidget {
  final String currentLocale;
  final ValueChanged<String> onChanged;
  const _LanguageSelector({
    required this.currentLocale,
    required this.onChanged,
  });

  String _getLanguageName(String code) {
    for (final lang in appLanguageChoices) {
      if (lang.$1 == code) return lang.$2;
    }
    return code;
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final content = ListTile(
      title: Text(context.l10n.appearanceLanguage),
      subtitle: Text(_getLanguageName(currentLocale)),
      trailing: AppIcon(Icons.chevron_right, color: colorScheme.primary),
      onTap: () => _showLanguagePicker(context),
    );
    return SettingsSearchTarget(
      label: context.l10n.appearanceLanguage,
      child: content,
    );
  }

  void _showLanguagePicker(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    showAppScrollableBottomSheet<void>(
      context: context,
      title: context.l10n.appearanceLanguage,
      builder: (context, controller) => ListView.builder(
        controller: controller,
        shrinkWrap: true,
        itemCount: supportedLanguageChoices.length,
        itemBuilder: (context, index) {
          final lang = supportedLanguageChoices[index];
          final isSelected = currentLocale == lang.$1;
          return AppSheetOption(
            leading: AppIcon(
              lang.$3,
              color: isSelected
                  ? colorScheme.primary
                  : colorScheme.onSurfaceVariant,
            ),
            title: Text(
              lang.$2,
              style: TextStyle(
                color: isSelected ? colorScheme.primary : colorScheme.onSurface,
                fontWeight: isSelected ? FontWeight.w600 : FontWeight.normal,
              ),
            ),
            selected: isSelected,
            trailing: isSelected
                ? AppIcon(Icons.check, color: colorScheme.primary)
                : null,
            onTap: () {
              onChanged(lang.$1);
              Navigator.pop(context);
            },
          );
        },
      ),
    );
  }
}
