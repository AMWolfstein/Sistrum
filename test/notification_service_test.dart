import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/l10n/app_localizations.dart';
import 'package:sistrum/services/notification_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  AndroidFlutterLocalNotificationsPlugin.registerWith();

  const channel = MethodChannel('dexterous.com/flutter/local_notifications');
  final methodCalls = <MethodCall>[];

  setUp(() async {
    debugDefaultTargetPlatformOverride = TargetPlatform.android;
    NotificationService().updateStrings(
      await AppLocalizations.delegate.load(const Locale('en')),
    );
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          methodCalls.add(call);
          if (call.method == 'initialize') return true;
          return null;
        });
  });

  tearDown(() {
    debugDefaultTargetPlatformOverride = null;
    methodCalls.clear();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  test(
    'scan with unknown total shows count and indeterminate progress',
    () async {
      final service = NotificationService();
      for (final count in [0, 1566]) {
        await service.showLibraryScanProgress(
          progress: 0,
          scannedFiles: count,
          totalFiles: 0,
          currentFile: 'track.flac',
        );

        final arguments =
            methodCalls.lastWhere((call) => call.method == 'show').arguments
                as Map<Object?, Object?>;
        final details = arguments['platformSpecifics'] as Map<Object?, Object?>;

        expect(arguments['id'], NotificationService.libraryScanId);
        expect(arguments['body'], '$count files scanned\ntrack.flac');
        expect(details['showProgress'], isTrue);
        expect(details['indeterminate'], isTrue);
        expect(details['progress'], 0);
        expect(details['ongoing'], isTrue);
        expect(details['autoCancel'], isFalse);
        expect(details['playSound'], isFalse);
      }
    },
  );

  test('scan switches to percentage when total becomes known', () async {
    final service = NotificationService();
    await service.showLibraryScanProgress(
      progress: 0,
      scannedFiles: 1566,
      totalFiles: 0,
    );
    await service.showLibraryScanProgress(
      progress: 78.3,
      scannedFiles: 1566,
      totalFiles: 2000,
      currentFile: 'track.flac',
    );

    final progressArguments =
        methodCalls.lastWhere((call) => call.method == 'show').arguments
            as Map<Object?, Object?>;
    final progressDetails =
        progressArguments['platformSpecifics'] as Map<Object?, Object?>;
    expect(progressArguments['body'], '1566/2000 files • 78%\ntrack.flac');
    expect(progressDetails['showProgress'], isTrue);
    expect(progressDetails['indeterminate'], isFalse);
    expect(progressDetails['progress'], 78);

    await service.showLibraryScanComplete(totalTracks: 2000);
    final completeArguments =
        methodCalls.lastWhere((call) => call.method == 'show').arguments
            as Map<Object?, Object?>;
    final completeDetails =
        completeArguments['platformSpecifics'] as Map<Object?, Object?>;
    expect(completeArguments['body'], '2000 tracks indexed');
    expect(completeDetails['showProgress'], isFalse);
    expect(completeDetails['ongoing'], isFalse);
    expect(completeDetails['autoCancel'], isTrue);
  });

  test('unknown-total scan never displays percentage in any locale', () async {
    final service = NotificationService();
    for (final locale in AppLocalizations.supportedLocales) {
      service.updateStrings(await AppLocalizations.delegate.load(locale));
      await service.showLibraryScanProgress(
        progress: 0,
        scannedFiles: 1566,
        totalFiles: 0,
      );
      final arguments =
          methodCalls.lastWhere((call) => call.method == 'show').arguments
              as Map<Object?, Object?>;
      final body = arguments['body'] as String;
      expect(body, contains('1566'), reason: '$locale');
      expect(body, isNot(contains('%')), reason: '$locale');
    }
  });
}
