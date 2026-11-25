import 'package:flutter/foundation.dart';

class AppInfo {
  static const String version = '5.4.0';
  static const String buildNumber = '169';

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

  static const String mobileAuthor = 'Zarz Eleutherius';
}
