import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/theme/app_tokens.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/mornye_chrome.dart';

/// Dialog presentation follows the selected design, including on Android.
Future<T?> showAppDialog<T>({
  required BuildContext context,
  required WidgetBuilder builder,
  bool barrierDismissible = true,
  bool useRootNavigator = true,
}) {
  final navigator = Navigator.of(context, rootNavigator: useRootNavigator);
  final themes = InheritedTheme.capture(from: context, to: navigator.context);
  return navigator.push<T>(
    _MornyeDialogRoute<T>(
      context: context,
      builder: (context) => themes.wrap(Builder(builder: builder)),
      barrierDismissible: barrierDismissible,
    ),
  );
}

/// Asks a yes/no question: the cancel action pops false, the confirm action
/// pops true, and dismissing the dialog counts as cancel. [content] replaces
/// the plain [message] text; the flags map onto the two [AppDialogAction]s.
/// [errorConfirm] paints the confirm action in the dialog theme's error
/// color: the fill when [filled], the label otherwise.
Future<bool> showAppConfirmDialog(
  BuildContext context, {
  required String title,
  String? message,
  Widget? content,
  required String confirmLabel,
  String? cancelLabel,
  bool destructive = false,
  bool filled = true,
  bool cancelIsDefault = false,
  bool confirmIsDefault = false,
  ButtonStyle? confirmStyle,
  bool errorConfirm = false,
  bool barrierDismissible = true,
}) async {
  assert(message == null || content == null, 'Pass message or content.');
  assert(!errorConfirm || confirmStyle == null, 'Pass one confirm style.');
  final result = await showAppDialog<bool>(
    context: context,
    barrierDismissible: barrierDismissible,
    builder: (context) => AppAlertDialog(
      title: Text(title),
      content: content ?? (message == null ? null : Text(message)),
      actions: [
        AppDialogAction(
          isDefault: cancelIsDefault,
          onPressed: () => Navigator.pop(context, false),
          child: Text(cancelLabel ?? context.l10n.dialogCancel),
        ),
        AppDialogAction(
          filled: filled,
          isDefault: confirmIsDefault,
          isDestructive: destructive,
          style: !errorConfirm
              ? confirmStyle
              : filled
              ? FilledButton.styleFrom(
                  backgroundColor: Theme.of(context).colorScheme.error,
                )
              : TextButton.styleFrom(
                  foregroundColor: Theme.of(context).colorScheme.error,
                ),
          onPressed: () => Navigator.pop(context, true),
          child: Text(confirmLabel),
        ),
      ],
    ),
  );
  return result ?? false;
}

/// The confirmation glass also hosts larger dialogs such as release notes.
class AppDialogSurface extends StatelessWidget {
  const AppDialogSurface({
    super.key,
    required this.child,
    this.maxWidth = 480,
    this.backgroundColor,
    this.shape,
  });

  final Widget child;
  final double maxWidth;
  final Color? backgroundColor;
  final ShapeBorder? shape;

  @override
  Widget build(BuildContext context) {
    return Dialog(
      backgroundColor: Colors.transparent,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      shape: shape,
      insetPadding: EdgeInsets.symmetric(
        horizontal: context.tokens.dialogInsetH,
        vertical: 24,
      ),
      child: ConstrainedBox(
        constraints: BoxConstraints(maxWidth: maxWidth),
        child: MornyeGlassPanel.overlay(
          child: SingleChildScrollView(child: child),
        ),
      ),
    );
  }
}

class _MornyeDialogRoute<T> extends RawDialogRoute<T> {
  _MornyeDialogRoute({
    required BuildContext context,
    required WidgetBuilder builder,
    required super.barrierDismissible,
  }) : super(
         pageBuilder: (context, animation, secondaryAnimation) =>
             builder(context),
         barrierColor: CupertinoDynamicColor.resolve(
           kCupertinoModalBarrierColor,
           context,
         ),
         barrierLabel: CupertinoLocalizations.of(
           context,
         ).modalBarrierDismissLabel,
         transitionDuration: MediaQuery.disableAnimationsOf(context)
             ? Duration.zero
             : const Duration(milliseconds: 220),
       );

  // Use a timed exit instead of CupertinoDialogRoute's settling spring.
  @override
  Duration get reverseTransitionDuration => transitionDuration == Duration.zero
      ? Duration.zero
      : const Duration(milliseconds: 160);

  CurvedAnimation? _scaleAnimation;

  @override
  Widget buildTransitions(
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
    Widget child,
  ) {
    if (MediaQuery.disableAnimationsOf(context)) return child;
    // Keep the backdrop in the page's compositing context. A fading dialog
    // creates an opacity layer that hides the page from its BackdropFilter.
    final scaleAnimation = _scaleAnimation ??= CurvedAnimation(
      parent: animation,
      curve: Curves.easeOutCubic,
      reverseCurve: Curves.easeInCubic,
    );
    return ScaleTransition(
      scale: scaleAnimation.drive(Tween<double>(begin: 0.96, end: 1)),
      child: child,
    );
  }

  @override
  void dispose() {
    _scaleAnimation?.dispose();
    super.dispose();
  }
}

class AppAlertDialog extends StatelessWidget {
  const AppAlertDialog({
    super.key,
    this.icon,
    this.title,
    this.content,
    this.actions = const [],
    this.backgroundColor,
  });

  final Widget? icon;
  final Widget? title;
  final Widget? content;
  final List<Widget> actions;
  final Color? backgroundColor;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final primaryActions = actions.where(
      (action) =>
          action is AppDialogAction && (action.isDestructive || action.filled),
    );
    final secondaryActions = actions.where(
      (action) =>
          action is! AppDialogAction ||
          (!action.isDestructive && !action.filled),
    );
    final orderedActions = [...primaryActions, ...secondaryActions];
    return Dialog(
      backgroundColor: Colors.transparent,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      insetPadding: const EdgeInsets.symmetric(horizontal: 24, vertical: 24),
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 320),
        child: MornyeGlassPanel.overlay(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (icon != null || title != null || content != null)
                  Flexible(
                    child: SingleChildScrollView(
                      padding: const EdgeInsets.fromLTRB(12, 8, 12, 20),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          if (icon != null) ...[
                            Align(
                              alignment: AlignmentDirectional.centerStart,
                              child: icon!,
                            ),
                            const SizedBox(height: 12),
                          ],
                          if (title != null)
                            DefaultTextStyle(
                              style: theme.textTheme.titleLarge!.copyWith(
                                fontSize: 20,
                                fontWeight: FontWeight.w600,
                                color: theme.colorScheme.onSurface,
                              ),
                              child: title!,
                            ),
                          if (title != null && content != null)
                            const SizedBox(height: 10),
                          if (content != null)
                            DefaultTextStyle(
                              style: theme.textTheme.bodyLarge!.copyWith(
                                fontSize: 17,
                                height: 1.35,
                                color: theme.colorScheme.onSurface,
                              ),
                              child: content!,
                            ),
                        ],
                      ),
                    ),
                  ),
                for (var i = 0; i < orderedActions.length; i++) ...[
                  if (i > 0) const SizedBox(height: 8),
                  orderedActions[i],
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class AppDialogAction extends StatelessWidget {
  const AppDialogAction({
    super.key,
    required this.onPressed,
    required this.child,
    this.isDestructive = false,
    this.isDefault = false,
    this.filled = false,
    this.style,
  });

  final VoidCallback? onPressed;
  final Widget child;
  final bool isDestructive;
  final bool isDefault;
  final bool filled;
  final ButtonStyle? style;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final color = onPressed == null
        ? scheme.onSurfaceVariant
        : isDestructive
        ? scheme.primary
        : scheme.onSurface;
    return CupertinoButton(
      onPressed: onPressed,
      color: MornyeTheme.controlFill(context),
      disabledColor: MornyeTheme.controlFill(context, enabled: false),
      borderRadius: BorderRadius.circular(28),
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      child: DefaultTextStyle(
        textAlign: TextAlign.center,
        style: theme.textTheme.bodyLarge!.copyWith(
          color: color,
          fontSize: 17,
          fontWeight: isDestructive || isDefault || filled
              ? FontWeight.w600
              : FontWeight.w500,
        ),
        child: IconTheme(
          data: IconThemeData(color: color, size: 20),
          child: child,
        ),
      ),
    );
  }
}
