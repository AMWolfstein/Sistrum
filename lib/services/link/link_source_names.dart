import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/services/link/client/link_client_controller.dart';
import 'package:sistrum/services/link/desktop/link_desktop_host.dart';
import 'package:sistrum/services/link/link_protocol.dart';
import 'package:sistrum/utils/desktop_platform.dart';

/// The Sistrum Link devices behind network sources, by source id (the host
/// of a `network://` song): on a phone, its paired computers' connections;
/// on the desktop, the song sources of its paired phones. Such a source is
/// a whole Sistrum on another device, not a plain file server.
final linkSourceNamesProvider = Provider<Map<String, String>>((ref) {
  if (isDesktopPlatform) {
    final devices = ref.watch(linkDesktopHostProvider.select((s) => s.devices));
    return {
      for (final device in devices)
        linkPhoneSourceId(device.deviceId): device.name,
    };
  }
  if (!ref.watch(linkClientSupportedProvider)) return const {};
  final servers = ref.watch(
    linkClientControllerProvider.select((s) => s.servers),
  );
  return {for (final server in servers) server.connectionId: server.name};
});

/// The Sistrum Link device that serves the `network://` [source], if any.
String? linkSourceName(Map<String, String> names, String? source) {
  if (source == null || !source.startsWith('network://')) return null;
  return names[Uri.tryParse(source)?.host];
}
