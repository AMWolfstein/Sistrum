part of '../platform_bridge.dart';

class ContentUriPlaybackLease {
  final String path;
  final String token;

  final String? displayName;

  const ContentUriPlaybackLease({
    required this.path,
    required this.token,
    this.displayName,
  });
}
