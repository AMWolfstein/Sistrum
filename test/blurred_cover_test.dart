import 'dart:convert';

import 'package:flutter/painting.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/widgets/blurred_cover.dart';

final _png = base64Decode(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=',
);

void main() {
  testWidgets(
    'one blurred copy per cover and size; evicted copies are disposed',
    (tester) async {
      final cache = BlurredCoverCache.instance;
      cache.clear();
      final before = cache.debugGenerations;
      final provider = MemoryImage(_png);

      final first = await tester.runAsync(
        () => cache.blurred(provider, const Size(390, 480)),
      );
      final again = await tester.runAsync(
        () => cache.blurred(provider, const Size(390, 480)),
      );
      expect(
        identical(first, again),
        isTrue,
        reason: 'the same entry is reused',
      );
      expect(cache.debugGenerations - before, 1, reason: 'one blurred copy');

      // More sizes than the cache holds: it stays bounded and disposes the rest.
      final disposedBefore = cache.debugDisposed;
      await tester.runAsync(() async {
        for (var i = 0; i < BlurredCoverCache.maxEntries + 2; i++) {
          await cache.blurred(provider, Size(200 + i * 10.0, 200));
        }
      });
      expect(cache.length, lessThanOrEqualTo(BlurredCoverCache.maxEntries));
      await tester.runAsync(() => Future<void>.delayed(Duration.zero));
      expect(cache.debugDisposed - disposedBefore, greaterThanOrEqualTo(2));
      cache.clear();
      expect(cache.length, 0);
    },
  );
}
