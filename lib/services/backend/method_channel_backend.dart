import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:sistrum/services/backend/backend_channel.dart';

class MethodChannelBackend extends BackendChannel {
  const MethodChannelBackend(this.channel);

  static const shared = MethodChannelBackend(
    MethodChannel('com.amwolfstein.sistrum/backend'),
  );
  static const _downloadProgressChannel = EventChannel(
    'com.amwolfstein.sistrum/download_progress_stream',
  );
  static const _libraryScanProgressChannel = EventChannel(
    'com.amwolfstein.sistrum/library_scan_progress_stream',
  );

  final MethodChannel channel;

  @override
  @optionalTypeArgs
  Future<T?> invokeMethod<T>(String method, [dynamic arguments]) =>
      channel.invokeMethod<T>(method, arguments);

  @override
  Future<Map<K, V>?> invokeMapMethod<K, V>(
    String method, [
    dynamic arguments,
  ]) => channel.invokeMapMethod<K, V>(method, arguments);

  @override
  void setMethodCallHandler(BackendCallHandler? handler) =>
      channel.setMethodCallHandler(handler);

  @override
  Stream<dynamic> downloadProgressEvents() =>
      _downloadProgressChannel.receiveBroadcastStream();

  @override
  Stream<dynamic> libraryScanProgressEvents() =>
      _libraryScanProgressChannel.receiveBroadcastStream();
}
