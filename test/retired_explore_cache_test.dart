import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/services/preference_blob_store.dart';
import 'package:sistrum/services/retired_explore_cache.dart';

void main() {
  test('startup removes the retired explore cache file and its keys', () async {
    final root = await Directory.systemTemp.createTemp('retired-explore-');
    addTearDown(() => root.delete(recursive: true));
    SharedPreferences.setMockInitialValues({
      'explore_home_feed_cache': '{"sections":[]}',
      'explore_home_feed_ts': 42,
      'unrelated': true,
    });
    final prefs = await SharedPreferences.getInstance();
    final store = PreferenceBlobStore('explore_feed', {
      'explore_home_feed_cache': String,
      'explore_home_feed_ts': int,
    }, directory: () async => root);
    final file = File('${root.path}/preference_blobs/explore_feed.json');
    await file.parent.create(recursive: true);
    await file.writeAsString('{"explore_home_feed_cache":"old"}');

    await clearRetiredExploreCache(prefs, store);

    expect(await file.exists(), isFalse);
    expect(prefs.containsKey('explore_home_feed_cache'), isFalse);
    expect(prefs.containsKey('explore_home_feed_ts'), isFalse);
    expect(prefs.getBool('unrelated'), isTrue);
  });
}
