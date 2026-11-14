import 'package:flutter/foundation.dart';

class AppInfo {
  static const String version = '0.1.0';
  static const String buildNumber = '1';

  static const String gitCommit = String.fromEnvironment('GIT_COMMIT');
  static String get shortGitCommit =>
      gitCommit.length > 8 ? gitCommit.substring(0, 8) : gitCommit;

  /// The version shown to people; debug builds stay internal.
  static String get displayVersion => versionLabel();

  @visibleForTesting
  static String versionLabel({bool debug = kDebugMode}) =>
      debug ? 'Internal' : version;

  static const String appName = 'Sistrum';
  static const String license = 'LGPL-3.0-only';
  static const String copyright = '© 2026 AMWolfstein';
  static const String originalCredit = 'Based on Mornye © 2026 Zarz Eleutherius';

  static const String githubRepo = 'AMWolfstein/Sistrum';
  static const String githubUrl = 'https://github.com/$githubRepo';
}
