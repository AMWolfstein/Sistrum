import 'package:flutter/services.dart';
import 'package:sistrum/utils/logger.dart';
import 'package:url_launcher/url_launcher.dart';

final _log = AppLogger('ExtensionAuthLauncher');

/// Opens an extension OAuth page using the browser preference. The page
/// returns through the mornye:// callback, which the app's URL scheme handles
/// on Android.
Future<bool> launchExtensionAuthUrl(Uri uri, {required String browserMode}) async {
  // The deep-link handler hands the callback to the backend.
  final preferInApp = browserMode.trim().toLowerCase() == 'in_app_first';
  final firstMode = preferInApp
      ? LaunchMode.inAppBrowserView
      : LaunchMode.externalApplication;
  final fallbackMode = preferInApp
      ? LaunchMode.externalApplication
      : LaunchMode.inAppBrowserView;

  for (final mode in [firstMode, fallbackMode]) {
    try {
      if (await launchUrl(uri, mode: mode)) return true;
    } on PlatformException catch (error) {
      // Android reports some launch failures as exceptions rather than false.
      // Keep the other mode available in both cases.
      _log.w('Auth browser launch failed (${mode.name}): ${error.code}');
    }
  }
  return false;
}
