import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/services/platform_bridge.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('com.amwolfstein.sistrum/backend');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  const uri = 'content://test.provider/document/song.flac';

  tearDown(() => messenger.setMockMethodCallHandler(channel, null));

  test('a provider that cannot tell reports unknown, not missing', () async {
    messenger.setMockMethodCallHandler(channel, (call) async {
      expect(call.method, 'safExists');
      expect(call.arguments, {'uri': uri});
      return null;
    });
    expect(await PlatformBridge.safExistsState(uri), isNull);
    expect(await PlatformBridge.safExists(uri), isFalse);
  });

  for (final found in [true, false]) {
    test('a definite answer of $found is kept as is', () async {
      messenger.setMockMethodCallHandler(channel, (call) async => found);
      expect(await PlatformBridge.safExistsState(uri), found);
      expect(await PlatformBridge.safExists(uri), found);
    });
  }
}
