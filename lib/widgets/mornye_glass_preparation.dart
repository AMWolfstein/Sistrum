import 'dart:async';

import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/controllers/mornye_glass_warmup.dart';
import 'package:sistrum/providers/runtime_profile_provider.dart';

/// Starts the one-time preparation when Liquid Glass is rendered, including
/// selecting it or leaving a downgraded device after the app has started.
class MornyeGlassPreparation extends ConsumerWidget {
  const MornyeGlassPreparation({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    if (ref.watch(mornyeGlassLevelProvider) == MornyeGlassLevel.liquid &&
        !ref.watch(
          mornyeGlassContrastBlockedProvider(
            MediaQuery.highContrastOf(context),
          ),
        )) {
      unawaited(MornyeGlassWarmup.prepare(View.of(context)));
    }
    return child;
  }
}
