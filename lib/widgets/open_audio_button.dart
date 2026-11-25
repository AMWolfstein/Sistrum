import 'dart:async';

import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/controllers/open_audio_controller.dart';
import 'package:sistrum/widgets/app_snack_bar.dart';

Future<void> pickOpenAudio(
  BuildContext context,
  WidgetRef ref, {
  required bool folder,
}) async {
  try {
    final controller = ref.read(openAudioControllerProvider.notifier);
    if (folder) {
      await controller.pickFolder();
    } else {
      await controller.pickFiles();
    }
  } catch (error) {
    if (context.mounted) showCannotOpenFileSnackBar(context, error);
  }
}
