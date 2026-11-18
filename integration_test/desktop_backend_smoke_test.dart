import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:sistrum/services/backend/backend_channel.dart';
import 'package:sistrum/services/native_log_source.dart';
import 'package:sistrum/utils/desktop_platform.dart';

/// Runs the desktop app against the bundled Rust library (set
/// `SISTRUM_DESKTOP_LIB` to test another build):
/// `fvm flutter test integration_test/desktop_backend_smoke_test.dart -d macos`
void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  final desktopHost =
      Platform.isMacOS || Platform.isWindows || Platform.isLinux;

  setUpAll(() => debugDesktopPlatformOverride = true);
  tearDownAll(() => debugDesktopPlatformOverride = null);

  testWidgets('backend calls reach the Rust library', (tester) async {
    await const NativeLogSource().clear();
  }, skip: !desktopHost);

  testWidgets('mobile-only methods report a missing plugin', (tester) async {
    await expectLater(
      backendChannel.invokeMethod('pickSafTree'),
      throwsA(isA<MissingPluginException>()),
    );
  }, skip: !desktopHost);
}
