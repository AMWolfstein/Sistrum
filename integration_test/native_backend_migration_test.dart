import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:sistrum/services/native_file_hash.dart';
import 'package:sistrum/services/platform_bridge.dart';
import 'package:path_provider/path_provider.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('file hashing reaches the native worker', (tester) async {
    final root = await (await getTemporaryDirectory()).createTemp(
      'native-hash-',
    );
    try {
      final bytes = List<int>.generate(200000, (index) => index % 251);
      final file = await File('${root.path}/input.bin').writeAsBytes(bytes);
      final native = await PlatformBridge.runNativeDataJob({
        'operation': 'hash_file',
        'path': file.path,
      });
      expect(native['sha256'], sha256.convert(bytes).toString());
      expect(await hashFileSha256(file.path), native['sha256']);
    } finally {
      await root.delete(recursive: true);
    }
  });
}
