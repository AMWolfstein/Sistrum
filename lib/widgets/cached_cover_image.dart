import 'dart:async';
import 'dart:io';
import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:material_ui/material_ui.dart';
import 'package:path/path.dart' as p;
import 'package:sistrum/theme/cover_palette.dart';
import 'package:sistrum/utils/image_cache_utils.dart';

/// Renders a stored cover string from disk: an absolute path or `file://` URI.
/// Other forms, and a missing file, show the [placeholder], which also serves
/// the local error state and the not-ready frame.
class LocalCoverImage extends StatelessWidget {
  final String url;
  final double? width;
  final double? height;
  final BoxFit fit;
  final BorderRadius? borderRadius;
  final int? localCacheWidth;
  final Duration? fadeInDuration;
  final Duration fadeOutDuration;
  final Widget Function(BuildContext) placeholder;

  const LocalCoverImage({
    super.key,
    required this.url,
    this.width,
    this.height,
    this.fit = BoxFit.cover,
    this.borderRadius,
    this.localCacheWidth,
    this.fadeInDuration,
    this.fadeOutDuration = Duration.zero,
    required this.placeholder,
  });

  @override
  Widget build(BuildContext context) {
    final path = coverFilePath(url);
    if (path == null) return _clip(placeholder(context));
    final file = File(path);
    if (localCacheWidth != null) {
      return _buildLocalImage(context, file, const BoxConstraints());
    }
    return LayoutBuilder(
      builder: (context, constraints) =>
          _buildLocalImage(context, file, constraints),
    );
  }

  Widget _clip(Widget child) => borderRadius == null
      ? child
      : ClipRRect(borderRadius: borderRadius!, child: child);

  Widget _buildLocalImage(
    BuildContext context,
    File file,
    BoxConstraints constraints,
  ) {
    final ImageProvider provider;
    if (localCacheWidth != null) {
      provider = ResizeImage(FileImage(file), width: localCacheWidth);
    } else {
      final decodeWidth = _localDecodeExtent(
        context,
        constraints.constrainWidth(width ?? double.infinity),
      );
      final decodeHeight = _localDecodeExtent(
        context,
        constraints.constrainHeight(height ?? double.infinity),
      );
      provider = decodeWidth == null && decodeHeight == null
          ? FileImage(file)
          : _FittedLocalFileImage(
              file,
              width: decodeWidth,
              height: decodeHeight,
              fit: fit,
            );
    }
    final image = Image(
      image: provider,
      width: width,
      height: height,
      fit: fit,
      gaplessPlayback: true,
      filterQuality: FilterQuality.low,
      frameBuilder: fadeInDuration == null
          ? null
          : (context, child, frame, wasSynchronouslyLoaded) {
              final ready = wasSynchronouslyLoaded || frame != null;
              if (fadeInDuration == Duration.zero) {
                return ready ? child : placeholder(context);
              }
              // The image defines the cover's size even when a track row
              // gives its leading widget unbounded constraints.
              return Stack(
                fit: StackFit.passthrough,
                children: [
                  Positioned.fill(child: placeholder(context)),
                  AnimatedOpacity(
                    opacity: ready ? 1.0 : 0.0,
                    duration: fadeInDuration!,
                    curve: Curves.easeOutCubic,
                    child: child,
                  ),
                ],
              );
            },
      errorBuilder: (_, _, _) => placeholder(context),
    );
    return _clip(image);
  }

  static int? _localDecodeExtent(BuildContext context, double? size) {
    if (size == null || !size.isFinite || size <= 0) return null;
    return (size * MediaQuery.devicePixelRatioOf(context)).ceil();
  }
}

/// Lets Flutter read intrinsic dimensions before choosing the decode size.
/// FileImage still owns file loading, codec creation and image lifecycle.
class _FittedLocalFileImage extends FileImage {
  final int? width;
  final int? height;
  final BoxFit fit;

  const _FittedLocalFileImage(
    super.file, {
    required this.width,
    required this.height,
    required this.fit,
  });

  @override
  ImageStreamCompleter loadImage(FileImage key, ImageDecoderCallback decode) {
    return super.loadImage(key, (buffer, {getTargetSize}) {
      return decode(
        buffer,
        getTargetSize: (intrinsicWidth, intrinsicHeight) {
          final double ratio;
          if (width != null && height != null) {
            final fitted = applyBoxFit(
              fit,
              Size(intrinsicWidth.toDouble(), intrinsicHeight.toDouble()),
              Size(width!.toDouble(), height!.toDouble()),
            );
            ratio = math.max(
              fitted.destination.width / fitted.source.width,
              fitted.destination.height / fitted.source.height,
            );
          } else if (fit == BoxFit.none) {
            ratio = 1;
          } else {
            ratio = width != null
                ? width! / intrinsicWidth
                : height! / intrinsicHeight;
          }
          // Never enlarge the decoded source. Cover uses the cropped source
          // dimensions above, retaining enough pixels along both display axes.
          final scale = ratio.clamp(0.0, 1.0);
          return ui.TargetImageSize(
            width: (intrinsicWidth * scale).ceil().clamp(1, intrinsicWidth),
            height: (intrinsicHeight * scale).ceil().clamp(1, intrinsicHeight),
          );
        },
      );
    });
  }

  @override
  bool operator ==(Object other) =>
      other is _FittedLocalFileImage &&
      super == other &&
      width == other.width &&
      height == other.height &&
      fit == other.fit;

  @override
  int get hashCode => Object.hash(super.hashCode, width, height, fit);
}

/// The local file a stored cover string names: an absolute path or a
/// `file://` URI. Null for URLs, `content://` URIs and other forms.
String? coverFilePath(String? source) {
  if (source == null || source.isEmpty) return null;
  if (source.startsWith('file://')) {
    try {
      return Uri.parse(source).toFilePath();
    } on FormatException {
      return null;
    } on UnsupportedError {
      return null;
    }
  }
  return p.isAbsolute(source) ? source : null;
}

/// The file a stored cover string decodes from, so every form of one cover
/// (path or `file://` URI) shares its cache entries. Null when it has no file.
String? coverImageSource(String? source) => coverFilePath(source);

/// The image of a stored cover string in any form, keyed like the cover
/// widgets' own decode and resized as `Image.file` does with [cacheWidth]
/// and [cacheHeight]. Null when the form has no image.
ImageProvider<Object>? coverImageProvider(
  String? source, {
  int? cacheWidth,
  int? cacheHeight,
}) {
  final path = coverFilePath(source);
  if (path == null) return null;
  return ResizeImage.resizeIfNeeded(
    cacheWidth,
    cacheHeight,
    FileImage(File(path)),
  );
}

/// Chooses one artwork source for both the Metadata foreground cover and its
/// blurred backdrop. Embedded file artwork is authoritative for downloaded
/// tracks; local scan artwork is next.
String? resolveMetadataArtworkSource({
  String? embeddedCoverPath,
  String? localCoverPath,
}) {
  for (final candidate in [embeddedCoverPath, localCoverPath]) {
    final normalized = candidate?.trim();
    if (normalized != null && normalized.isNotEmpty) return normalized;
  }
  return null;
}

/// Decode size shared by Track Metadata's blurred backdrop and its prewarm.
/// The backdrop is heavily blurred, so a modest square bitmap is sufficient
/// and avoids allocating a second full-viewport image beside the Hero cover.
int metadataBackdropCacheExtent(BuildContext context) {
  final dpr = MediaQuery.devicePixelRatioOf(context).clamp(1.0, 3.0);
  final logicalWidth = MediaQuery.sizeOf(context).width;
  return (logicalWidth * dpr * 0.35).round().clamp(192, 384).toInt();
}

/// Warms the exact resized image used by Track Metadata's blurred backdrop.
/// Navigation waits for the common memory/disk-cache path, but a bounded budget
/// prevents a cold network request from delaying the route for too long.
Future<void> precacheMetadataBackdrop(
  BuildContext context,
  String? source,
) async {
  final normalized = source?.trim();
  if (normalized == null || normalized.isEmpty) return;

  final extent = metadataBackdropCacheExtent(context);
  final provider = coverImageProvider(
    normalized,
    cacheWidth: extent,
    cacheHeight: extent,
  );
  if (provider == null) return;
  // The header takes its colours from the cover as well. Worked out before
  // the page shows, it opens in them; left to the page, it opens in the
  // app's colours and changes to the cover's a moment later, the first time
  // a cover is seen.
  final paletteSource = coverFilePath(normalized);
  final brightness = Theme.of(context).brightness;
  try {
    await Future.wait([
      precacheImage(
        provider,
        context,
        onError: (_, _) {},
      ).timeout(const Duration(milliseconds: 200), onTimeout: () {}),
      if (paletteSource != null)
        CoverPalette.resolve(
          paletteSource,
          brightness,
        ).timeout(const Duration(milliseconds: 300), onTimeout: () => null),
    ]);
  } catch (_) {}
}

/// Pre-warms the cover cache at the metadata-screen display size so the hero
/// transition doesn't pop in a low-res frame. Grid callers can supply their
/// own decode extent to share the cell's cache entry and error handling.
void precacheCoverImage(
  BuildContext context,
  String? url, {
  int? cacheWidth,
  int? cacheHeight,
}) {
  if (url == null || url.isEmpty) return;
  final provider = coverImageProvider(
    url,
    cacheWidth:
        cacheWidth ??
        (cacheHeight == null ? coverCacheWidthForViewport(context) : null),
    cacheHeight: cacheHeight,
  );
  if (provider == null) return;
  // A stale local path is common and its placeholder already covers it; only
  // a failed download still reaches the app's error handler.
  precacheImage(provider, context, onError: (_, _) {});
}
