import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/utils/logger.dart';

/// Opens the system's own output picker (the phone's speaker, Bluetooth and
/// wired routes).
class AudioOutputButton extends StatefulWidget {
  const AudioOutputButton({super.key, this.color, this.onPickerChanged});

  final Color? color;

  final ValueChanged<bool>? onPickerChanged;

  @override
  State<AudioOutputButton> createState() => _AudioOutputButtonState();
}

class _AudioOutputButtonState extends State<AudioOutputButton> {
  static const _systemChannel = MethodChannel(
    'com.amwolfstein.sistrum/audio_output',
  );
  bool _opening = false;

  Future<void> _showPicker() async {
    if (_opening) return;
    _opening = true;
    widget.onPickerChanged?.call(true);
    var opened = false;
    try {
      opened = await _systemChannel.invokeMethod<bool>('show') ?? false;
    } on PlatformException {
      // Show a visible failure instead of leaving an unresponsive control.
    } on MissingPluginException {
      // The native bridge is not available on unsupported builds.
    } on Object catch (error) {
      AppLogger('AudioOutput').w('Could not open output picker: $error');
    } finally {
      _opening = false;
      if (mounted) widget.onPickerChanged?.call(false);
    }
    if (mounted && !opened) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(context.l10n.nowPlayingAudioOutputUnavailable)),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    if (kIsWeb) return const SizedBox.shrink();
    return switch (defaultTargetPlatform) {
      TargetPlatform.android => IconButton(
        tooltip: context.l10n.nowPlayingAudioOutput,
        color: widget.color,
        icon: const Icon(Icons.speaker_group_outlined),
        onPressed: _showPicker,
      ),
      _ => const SizedBox.shrink(),
    };
  }
}
