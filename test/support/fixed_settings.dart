import 'package:sistrum/models/settings.dart';
import 'package:sistrum/providers/settings_provider.dart';

/// Skips loading persisted settings and serves [initial] unchanged.
class FixedSettingsNotifier extends SettingsNotifier {
  FixedSettingsNotifier([this.initial = const AppSettings()]);

  final AppSettings initial;

  @override
  AppSettings build() => initial;
}
