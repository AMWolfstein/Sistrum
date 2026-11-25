import 'dart:io';
import 'dart:typed_data';

import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/providers/runtime_profile_provider.dart';
import 'package:sistrum/providers/user_profile_provider.dart';
import 'package:sistrum/services/shell_navigation_service.dart';
import 'package:sistrum/widgets/album_detail_header.dart'
    show kHeaderButtonSize;
import 'package:sistrum/widgets/mornye_chrome.dart';
import 'package:sistrum/widgets/mornye_glass_press.dart';

class ProfileAvatar extends StatelessWidget {
  const ProfileAvatar({
    super.key,
    this.name = '',
    this.photoPath,
    this.photo,
    this.size = 40,
    this.plain = false,
  });

  final String name;
  final String? photoPath;
  final Uint8List? photo;
  final double size;

  /// Without a photo, shows only the accent-coloured glyph, no filled circle,
  /// for use inside a glass button.
  final bool plain;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        // ListTile can constrain height more than width. Keep the clipped
        // image square even when the surrounding slot is rectangular.
        final diameter = constraints.biggest.shortestSide.clamp(0.0, size);
        return Align(
          widthFactor: 1,
          heightFactor: 1,
          child: _buildAvatar(context, diameter),
        );
      },
    );
  }

  Widget _buildAvatar(BuildContext context, double diameter) {
    final scheme = Theme.of(context).colorScheme;
    final fallback = ColoredBox(
      color: plain ? Colors.transparent : scheme.primaryContainer,
      child: Center(
        child: name.trim().isEmpty
            ? Icon(
                Icons.person,
                size: diameter * 0.55,
                color: plain ? scheme.primary : scheme.onPrimaryContainer,
              )
            : Text(
                name.trim().characters.first.toUpperCase(),
                style: TextStyle(
                  fontSize: diameter * 0.42,
                  fontWeight: FontWeight.w600,
                  color: plain ? scheme.primary : scheme.onPrimaryContainer,
                ),
              ),
      ),
    );
    return ExcludeSemantics(
      child: ClipOval(
        child: SizedBox.square(
          dimension: diameter,
          child: photo != null
              ? Image.memory(
                  photo!,
                  fit: BoxFit.cover,
                  errorBuilder: (_, _, _) => fallback,
                )
              : photoPath != null
              ? Image.file(
                  File(photoPath!),
                  fit: BoxFit.cover,
                  cacheWidth:
                      (diameter * MediaQuery.devicePixelRatioOf(context))
                          .ceil(),
                  errorBuilder: (_, _, _) => fallback,
                )
              : fallback,
        ),
      ),
    );
  }
}

class HomeProfileButton extends ConsumerWidget {
  const HomeProfileButton({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final profile = ref.watch(userProfileProvider).value;
    final dark = Theme.of(context).brightness == Brightness.dark;
    // A glass circle like the other header buttons, holding the avatar.
    return Center(
      widthFactor: 1,
      child: MornyeGlassPress(
        radius: kHeaderButtonSize / 2,
        child: MornyeGlass.navigation(
          radius: kHeaderButtonSize / 2,
          // The header behind it is an opaque app bar, so there is nothing to
          // sample: the control keeps its rim and tint and reads no backdrop.
          kind: MornyeGlassKind.control,
          samplesBackdrop: false,
          tintColor: dark ? Colors.black : Colors.white,
          tintOpacity: dark ? 0.45 : 0.55,
          blurEnabled: ref.watch(mornyeBlurEnabledProvider),
          child: IconButton(
            tooltip: context.l10n.settingsTitle,
            padding: EdgeInsets.zero,
            constraints: const BoxConstraints.tightFor(
              width: kHeaderButtonSize,
              height: kHeaderButtonSize,
            ),
            onPressed: () =>
                ShellNavigationService.requestTab(ShellTab.settings),
            icon: ProfileAvatar(
              name: profile?.name ?? '',
              photoPath: profile?.photoPath,
              size: kHeaderButtonSize - 8,
              plain: true,
            ),
          ),
        ),
      ),
    );
  }
}
