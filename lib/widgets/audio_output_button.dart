import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:material_ui/material_ui.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/utils/logger.dart';
import 'package:sistrum/widgets/app_icon_button.dart';

/// Opens the system's audio output switcher: Android's output switcher, or
/// AirPlay and Bluetooth routes on iOS.
class AudioOutputButton extends StatefulWidget {
  const AudioOutputButton({
    super.key,
    this.color,
    this.backgroundColor,
    this.width = 48,
    this.onPickerChanged,
  });

  final Color? color;

  /// Fills a pill [width] wide behind the icon, as Material's player footer
  /// does beside its shuffle and repeat group; transparent when null.
  final Color? backgroundColor;
  final double width;
  final ValueChanged<bool>? onPickerChanged;

  @override
  State<AudioOutputButton> createState() => _AudioOutputButtonState();
}

class _AudioOutputButtonState extends State<AudioOutputButton> {
  static const _systemChannel = MethodChannel(
    'com.amwolfstein.sistrum/audio_output',
  );
  bool _opening = false;

  bool get _ios => defaultTargetPlatform == TargetPlatform.iOS;

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
      TargetPlatform.android || TargetPlatform.iOS => _button(context),
      _ => const SizedBox.shrink(),
    };
  }

  Widget _button(BuildContext context) {
    final icon = _ios ? Icons.airplay : Icons.speaker_group_outlined;
    if (widget.backgroundColor case final background?) {
      final scheme = Theme.of(context).colorScheme;
      return IconButton(
        tooltip: context.l10n.nowPlayingAudioOutput,
        icon: Icon(icon),
        onPressed: _showPicker,
        style: ButtonStyle(
          minimumSize: WidgetStatePropertyAll(Size(widget.width, 48)),
          backgroundColor: WidgetStateProperty.resolveWith(
            (states) => states.contains(WidgetState.disabled)
                ? scheme.onSurface.withValues(alpha: 0.12)
                : background,
          ),
        ),
      );
    }
    return AppIconButton(
      tooltip: context.l10n.nowPlayingAudioOutput,
      color: widget.color,
      icon: Icon(icon),
      onPressed: _showPicker,
    );
  }
}
