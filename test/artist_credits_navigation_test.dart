import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/controllers/clickable_metadata.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/models/tag_credit_settings.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/theme/mornye_theme.dart';

import 'support/fixed_settings.dart';

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));

  testWidgets('artist picker offers each featured credit', (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.dark),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Builder(
            builder: (context) => Scaffold(
              body: TextButton(
                onPressed: () => navigateToArtistCredits(
                  context,
                  artistNames: 'First Artist feat. Second Artist',
                ),
                child: const Text('Artists'),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Artists'));
    await tester.pumpAndSettle();
    expect(find.text('First Artist'), findsOneWidget);
    expect(find.text('Second Artist'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets('a kept-together name stays one artist in the picker', (
    tester,
  ) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          settingsProvider.overrideWith(
            () => FixedSettingsNotifier(
              const AppSettings(
                tagCredits: TagCreditSettings(
                  keepTogether: ['Tyler, The Creator'],
                ),
              ),
            ),
          ),
        ],
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.dark),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Builder(
            builder: (context) => Scaffold(
              body: TextButton(
                onPressed: () => navigateToArtistCredits(
                  context,
                  artistNames: 'Tyler, The Creator & Kali Uchis',
                ),
                child: const Text('Artists'),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Artists'));
    await tester.pumpAndSettle();
    expect(find.text('Tyler, The Creator'), findsOneWidget);
    expect(find.text('Kali Uchis'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox());
  });
}
