import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/l10n/app_localizations.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/audio_output_button.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('com.amwolfstein.sistrum/audio_output');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  Widget app({ValueChanged<bool>? onChanged}) => ProviderScope(
    child: MaterialApp(
      theme: MornyeTheme.build(Brightness.light),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: Scaffold(
        body: Center(child: AudioOutputButton(onPickerChanged: onChanged)),
      ),
    ),
  );

  tearDown(() {
    messenger.setMockMethodCallHandler(channel, null);
    messenger.setMockMethodCallHandler(SystemChannels.platform_views, null);
  });

  testWidgets(
    'opens the system output picker once and reports failure visibly',
    (tester) async {
      final completion = Completer<bool>();
      var requests = 0;
      final visibility = <bool>[];
      messenger.setMockMethodCallHandler(channel, (call) {
        expect(call.method, 'show');
        requests++;
        return completion.future;
      });
      await tester.pumpWidget(app(onChanged: visibility.add));
      await tester.tap(find.byTooltip('Audio Output'));
      await tester.pumpAndSettle();
      expect(requests, 1);
      expect(visibility, [true]);
      completion.complete(false);
      await tester.pumpAndSettle();
      expect(visibility, [true, false]);
      expect(
        find.text('Unable to open audio output settings.'),
        findsOneWidget,
      );
    },
    variant: TargetPlatformVariant.only(TargetPlatform.android),
  );

  testWidgets('a successful request shows no error and disposal is safe', (
    tester,
  ) async {
    messenger.setMockMethodCallHandler(channel, (_) async => true);
    await tester.pumpWidget(app());
    await tester.tap(find.byTooltip('Audio Output'));
    await tester.pumpAndSettle();
    expect(find.byType(SnackBar), findsNothing);

    final completion = Completer<bool>();
    messenger.setMockMethodCallHandler(channel, (_) => completion.future);
    await tester.tap(find.byTooltip('Audio Output'));
    await tester.pumpAndSettle();
    await tester.pumpWidget(const SizedBox());
    completion.complete(false);
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  }, variant: TargetPlatformVariant.only(TargetPlatform.android));
}
