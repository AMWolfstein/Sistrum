import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/providers/settings_provider.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  setUpAll(() {
    messenger.setMockMethodCallHandler(secure, (call) async => null);
  });
  tearDownAll(() => messenger.setMockMethodCallHandler(secure, null));

  test('the removed rebrand tour leaves no flag behind', () async {
    SharedPreferences.setMockInitialValues({
      'app_settings': jsonEncode(const AppSettings().toJson()),
      'settings_migration_version': 14,
      'upgrade_intro_sistrum_seen': true,
    });
    final container = ProviderContainer();
    addTearDown(container.dispose);
    await container.read(settingsProvider.notifier).ensureLoaded();

    final preferences = await SharedPreferences.getInstance();
    expect(preferences.containsKey('upgrade_intro_sistrum_seen'), isFalse);
    expect(preferences.getInt('settings_migration_version'), 16);
  });

  test('the removed artist album sort is dropped from version 15', () async {
    SharedPreferences.setMockInitialValues({
      'app_settings': jsonEncode(const AppSettings().toJson()),
      'settings_migration_version': 15,
      'artist_release_sorts': '{"artist-1":"year-desc"}',
      'unrelated_preference': 'kept',
    });
    final container = ProviderContainer();
    addTearDown(container.dispose);
    await container.read(settingsProvider.notifier).ensureLoaded();

    final preferences = await SharedPreferences.getInstance();
    expect(preferences.containsKey('artist_release_sorts'), isFalse);
    expect(preferences.getString('unrelated_preference'), 'kept');
    expect(preferences.getInt('settings_migration_version'), 16);
  });
}
