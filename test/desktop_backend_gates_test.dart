
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/services/backend/rust_ffi_backend.dart';
import 'package:sistrum/services/platform_bridge.dart';
import 'package:sistrum/utils/desktop_platform.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  setUpAll(
    () => messenger.setMockMethodCallHandler(secure, (call) async => null),
  );
  tearDownAll(() => messenger.setMockMethodCallHandler(secure, null));

  tearDown(() {
    debugDesktopPlatformOverride = null;
    RustFfiBackend.debugShared = null;
  });

  test('desktop builds use the core backend', () {
    debugDesktopPlatformOverride = true;
    expect(PlatformBridge.supportsCoreBackend, isTrue);

    // The test host is neither Android nor iOS.
    debugDesktopPlatformOverride = false;
    expect(PlatformBridge.supportsCoreBackend, isFalse);
  });
}
