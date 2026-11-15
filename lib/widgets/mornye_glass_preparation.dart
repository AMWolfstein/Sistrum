import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/controllers/mornye_glass_warmup.dart';
import 'package:sistrum/providers/runtime_profile_provider.dart';

/// Starts the one-time preparation when the full glass theme is enabled,
/// including switching to Mornye or enabling blur after the app has started.
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
