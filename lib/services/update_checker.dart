import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

import 'package:mornye/constants/app_info.dart';
import 'package:mornye/services/preference_blob_store.dart';
import 'package:mornye/utils/logger.dart';

final _log = AppLogger('UpdateChecker');

/// The release APK built for 64-bit ARM, the only build this app ships.
const _arm64ApkMarker = 'arm64';

/// A downloadable file of a GitHub release.
class ReleaseAsset {
  final String name;
  final String url;

  /// GitHub's `digest` for the asset, when it reports one.
  final String? sha256;

  const ReleaseAsset({required this.name, required this.url, this.sha256});
}

class UpdateInfo {
  final String version;
  final String changelog;
  final String downloadUrl;

  /// The arm64 APK to download; null when the device needs a build this
  /// release does not offer (see [noCompatibleApk]).
  final String? apkDownloadUrl;
  final String? apkSha256;

  /// Set when the release is newer but has no arm64 APK, which is the only
  /// build this device can install.
  final bool noCompatibleApk;

  /// How many stable (non-prerelease) releases are newer than the installed
  /// version. Drives the forced-update flow.
  final int releasesBehind;

  const UpdateInfo({
    required this.version,
    required this.changelog,
    required this.downloadUrl,
    this.apkDownloadUrl,
    this.apkSha256,
    this.noCompatibleApk = false,
    this.releasesBehind = 0,
  });
}

class UpdateChecker {
  final http.Client? _client;
  final String _installedVersion;

  UpdateChecker({
    http.Client? client,
    String installedVersion = AppInfo.version,
  }) : _client = client,
       _installedVersion = installedVersion;

  /// Installed versions this many stable releases (or more) behind must
  /// update before continuing to use the app.
  static const int forceUpdateThreshold = 3;

  // Revalidate on every check. A local TTL can hide a newly published release;
  // ETag still avoids downloading the release list when it has not changed.
  static const String _cachedBodyKey = 'update_checker_releases_json';
  static const String _cachedAtKey = 'update_checker_releases_fetched_at';
  static const String _cachedEtagKey = 'update_checker_releases_etag';
  static const _cache = PreferenceBlobStore('update_releases', {
    _cachedBodyKey: String,
    _cachedAtKey: int,
    _cachedEtagKey: String,
  });

  Future<({String body, bool verified})?> _fetchReleasesBody() async {
    final prefs = await SharedPreferences.getInstance();
    final cached = (await _cache.read(prefs)).values;
    var cachedBody = cached[_cachedBodyKey] as String?;
    if (cachedBody != null) {
      try {
        _parseReleases(cachedBody);
      } on FormatException {
        cachedBody = null;
        _log.w('Ignoring invalid cached release list');
      }
    }

    final cachedEtag = cached[_cachedEtagKey] as String?;
    try {
      final response = await (_client?.get ?? http.get)(
        Uri.parse(
          'https://api.github.com/repos/${AppInfo.githubRepo}/releases?per_page=30',
        ),
        headers: {
          'Accept': 'application/vnd.github.v3+json',
          'Cache-Control': 'no-cache',
          if (cachedBody != null && cachedEtag != null)
            'If-None-Match': cachedEtag,
        },
      ).timeout(const Duration(seconds: 10));

      if (response.statusCode == 304 && cachedBody != null) {
        _log.i('Release list revalidated by GitHub (304)');
        await _cache.update(
          prefs,
          (current) => {
            ...current,
            _cachedAtKey: DateTime.now().millisecondsSinceEpoch,
          },
        );
        return (body: cachedBody, verified: true);
      }
      if (response.statusCode != 200) {
        throw http.ClientException(
          'GitHub API returned ${response.statusCode}',
        );
      }

      // Invalid responses must not overwrite a usable offline snapshot.
      _parseReleases(response.body);
      final etag = response.headers['etag'];
      await _cache.write(prefs, {
        _cachedBodyKey: response.body,
        _cachedEtagKey: ?etag,
        _cachedAtKey: DateTime.now().millisecondsSinceEpoch,
      });
      _log.i('Fetched current release list from GitHub (200)');
      return (body: response.body, verified: true);
    } catch (error) {
      _log.w('Could not refresh releases: $error');
      return cachedBody == null ? null : (body: cachedBody, verified: false);
    }
  }

  static List<Map<String, dynamic>> _parseReleases(String body) {
    final decoded = jsonDecode(body);
    if (decoded is! List<dynamic> ||
        decoded.any((release) => release is! Map<String, dynamic>)) {
      throw const FormatException('Invalid GitHub release list');
    }
    return decoded.cast<Map<String, dynamic>>();
  }

  Future<UpdateInfo?> checkForUpdate({String channel = 'stable'}) async {
    try {
      final snapshot = await _fetchReleasesBody();
      if (snapshot == null) {
        return null;
      }

      final releases = _parseReleases(snapshot.body);
      if (releases.isEmpty) {
        _log.i('No releases found');
        return null;
      }

      Map<String, dynamic>? releaseData;
      if (channel == 'preview') {
        releaseData = releases.first;
      } else {
        for (final release in releases) {
          if (release['prerelease'] != true) {
            releaseData = release;
            break;
          }
        }
      }
      if (releaseData == null) {
        _log.i('No stable release found');
        return null;
      }

      final tagName = releaseData['tag_name'] as String? ?? '';
      final latestVersion = tagName.replaceFirst('v', '');
      final isPrerelease = releaseData['prerelease'] as bool? ?? false;

      var releasesBehind = 0;
      for (final release in releases) {
        if (release['prerelease'] == true) continue;
        final version = (release['tag_name'] as String? ?? '').replaceFirst(
          'v',
          '',
        );
        if (version.isNotEmpty && _isNewerVersion(version, _installedVersion)) {
          releasesBehind++;
        }
      }

      if (!_isNewerVersion(latestVersion, _installedVersion)) {
        if (snapshot.verified) {
          _log.i(
            'No update available (current: $_installedVersion, latest: $latestVersion, channel: $channel)',
          );
        } else {
          _log.w(
            'Update availability could not be verified '
            '(current: $_installedVersion, cached latest: $latestVersion, channel: $channel)',
          );
        }
        return null;
      }

      final body = releaseData['body'] as String? ?? 'No changelog available';
      final htmlUrl =
          releaseData['html_url'] as String? ??
          'https://github.com/${AppInfo.githubRepo}/releases';

      final apk = _selectApk(
        _collectApkAssets(
          _collectReleaseAssets(
            releaseData['assets'] as List<dynamic>? ?? const [],
          ),
        ),
      );

      _log.i(
        'Update available: $latestVersion (prerelease: $isPrerelease, '
        'releases behind: $releasesBehind), '
        'APK asset: ${apk?.name ?? 'none'}',
      );

      return UpdateInfo(
        version: latestVersion,
        changelog: body,
        downloadUrl: htmlUrl,
        apkDownloadUrl: apk?.url,
        apkSha256: apk?.sha256,
        noCompatibleApk: apk == null,
        releasesBehind: releasesBehind,
      );
    } catch (e) {
      _log.e('Error checking for updates: $e');
      return null;
    }
  }

  static bool _isNewerVersion(String latest, String current) {
    try {
      final latestBase = latest.split('-').first;
      final currentBase = current.split('-').first;

      final latestParts = latestBase.split('.').map(int.parse).toList();
      final currentParts = currentBase.split('.').map(int.parse).toList();

      while (latestParts.length < 3) {
        latestParts.add(0);
      }
      while (currentParts.length < 3) {
        currentParts.add(0);
      }

      for (int i = 0; i < 3; i++) {
        if (latestParts[i] > currentParts[i]) return true;
        if (latestParts[i] < currentParts[i]) return false;
      }

      final latestHasSuffix = latest.contains('-');
      final currentHasSuffix = current.contains('-');

      if (!latestHasSuffix && currentHasSuffix) return true;

      return false;
    } catch (e) {
      _log.e('Error comparing versions: $e');
      return false;
    }
  }

  static List<ReleaseAsset> _collectReleaseAssets(List<dynamic> assets) {
    final releaseAssets = <ReleaseAsset>[];

    for (final asset in assets.whereType<Map<Object?, Object?>>()) {
      final assetMap = Map<String, dynamic>.from(asset);
      final name = (assetMap['name'] as String? ?? '').trim();
      if (name.isEmpty) continue;

      final downloadUrl = assetMap['browser_download_url'] as String?;
      final uri = downloadUrl != null ? Uri.tryParse(downloadUrl) : null;
      if (uri == null || uri.scheme != 'https') {
        _log.w('Skipping non-HTTPS asset URL: $downloadUrl');
        continue;
      }

      releaseAssets.add(
        ReleaseAsset(
          name: name,
          url: uri.toString(),
          sha256: _normalizeAssetDigest(assetMap['digest']?.toString()),
        ),
      );
    }

    return releaseAssets;
  }

  /// The release's APKs. Only the arm64 build is kept: the app ships no
  /// other ABI.
  static List<ReleaseAsset> _collectApkAssets(List<ReleaseAsset> assets) => [
    for (final asset in assets)
      if (asset.name.toLowerCase().endsWith('.apk') &&
          asset.name.toLowerCase().contains(_arm64ApkMarker))
        asset,
  ];

  static String? _normalizeAssetDigest(String? digest) {
    if (digest == null) return null;
    final normalized = digest.trim().toLowerCase().replaceFirst(
      RegExp(r'^sha256:'),
      '',
    );
    return RegExp(r'^[a-f0-9]{64}$').hasMatch(normalized) ? normalized : null;
  }

  /// The release's arm64 APK, or null when it has none. The app runs only on
  /// arm64, so the device ABIs are not read.
  static ReleaseAsset? _selectApk(List<ReleaseAsset> apks) =>
      apks.isEmpty ? null : apks.first;
}
