import 'package:sistrum/models/settings.dart';
import 'package:sistrum/providers/settings_provider.dart';

class ReEnrichTestSettings extends SettingsNotifier {
  @override
  AppSettings build() => const AppSettings();

  @override
  Future<void> syncLyricsSettingsToBackend({AppSettings? settings}) async {}
}
