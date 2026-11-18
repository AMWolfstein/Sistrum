import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/services/backend/ffi_backend_transport.dart';
import 'package:sistrum/services/backend/rust_ffi_backend.dart';
import 'package:sistrum/services/native_file_hash.dart';
import 'package:sistrum/services/platform_bridge.dart';
import 'package:sistrum/utils/desktop_platform.dart';

/// Run against a freshly built desktop library with SISTRUM_DESKTOP_LIB set.
/// Unlike mock channel tests, these cases cross the actual Dart C ABI worker.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final enabled =
      Platform.environment[desktopBackendLibraryEnvironmentKey]?.isNotEmpty ??
      false;

  group('actual Rust backend migration', () {
    late Directory root;

    setUpAll(() async {
      root = await Directory.systemTemp.createTemp('sistrum-migration-ffi-');
      debugDesktopPlatformOverride = true;
      RustFfiBackend.debugShared = RustFfiBackend(
        openTransport: FfiBackendTransport.open,
        loadConfig: () async => DesktopBackendConfig(
          appSupportDir: root.path,
          documentsDir: root.path,
          tempDir: root.path,
        ),
      );
    });

    tearDownAll(() async {
      RustFfiBackend.debugShared = null;
      debugDesktopPlatformOverride = null;
      await root.delete(recursive: true);
    });

    test('native file hashes preserve streamed digest', () async {
      final bytes = List<int>.generate(200000, (index) => index % 251);
      final file = await File('${root.path}/hash.bin').writeAsBytes(bytes);
      final native = await PlatformBridge.runNativeDataJob({
        'operation': 'hash_file',
        'path': file.path,
      });
      expect(native['sha256'], sha256.convert(bytes).toString());
      expect(await hashFileSha256(file.path), native['sha256']);
    });
  }, skip: !enabled);
}
