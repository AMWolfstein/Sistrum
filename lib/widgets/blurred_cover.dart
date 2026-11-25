import 'dart:async';
import 'dart:collection';
import 'dart:io';
import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:flutter/widgets.dart';
import 'package:sistrum/widgets/cached_cover_image.dart';

/// Blur radius of collapsing header artwork at full collapse, full size.
const double kCollapsingArtworkBlurSigma = 18;

/// The blurred copy is decoded and drawn at 1/[kBlurredCoverDivisor] of the
/// header size, so the blur runs over few pixels, once per cover.
const int kBlurredCoverDivisor = 8;

/// Keeps the blurred copies of covers, one per provider and size. Each entry is
/// a `ui.Image` the cache owns; a widget draws a clone, so evicting an entry
/// disposes only the cache's own handle.
class BlurredCoverCache {
  BlurredCoverCache._();

  static final BlurredCoverCache instance = BlurredCoverCache._();

  /// Entries kept at once. The least recently used is evicted first.
  static const int maxEntries = 8;

  final LinkedHashMap<_CoverKey, Future<ui.Image>> _entries = LinkedHashMap();

  /// Blurred copies generated since the start (tests read this).
  int debugGenerations = 0;

  /// Cache-owned images disposed on eviction (tests read this).
  int debugDisposed = 0;

  /// Entries currently held.
  int get length => _entries.length;

  /// The blurred copy of [provider] at [size], generated once and then reused.
  Future<ui.Image> blurred(ImageProvider provider, Size size) {
    final w = size.width.round().clamp(1, 1 << 20);
    final h = size.height.round().clamp(1, 1 << 20);
    final key = _CoverKey(provider, w, h);
    final existing = _entries.remove(key);
    if (existing != null) {
      _entries[key] = existing; // most recently used goes last
      return existing;
    }
    final future = _generate(provider, w, h);
    _entries[key] = future;
    while (_entries.length > maxEntries) {
      final oldest = _entries.keys.first;
      final evicted = _entries.remove(oldest)!;
      unawaited(
        evicted.then((image) {
          debugDisposed++;
          image.dispose();
        }, onError: (_) {}),
      );
    }
    return future;
  }

  /// Empties the cache. Entries are disposed as if evicted.
  void clear() {
    final all = _entries.values.toList();
    _entries.clear();
    for (final future in all) {
      unawaited(
        future.then((image) {
          debugDisposed++;
          image.dispose();
        }, onError: (_) {}),
      );
    }
  }

  Future<ui.Image> _generate(ImageProvider provider, int w, int h) async {
    debugGenerations++;
    final tw = (w / kBlurredCoverDivisor).round().clamp(1, 1 << 20);
    final th = (h / kBlurredCoverDivisor).round().clamp(1, 1 << 20);
    // The stream gives each listener its own clone of the decoded image, so
    // this method owns [source] and disposes it, also when drawing fails.
    final source = await _decode(
      ResizeImage(
        provider,
        width: tw,
        height: th,
        policy: ResizeImagePolicy.fit,
      ),
    );
    try {
      return await _draw(source, tw, th);
    } finally {
      source.dispose();
    }
  }

  Future<ui.Image> _draw(ui.Image source, int tw, int th) async {
    final recorder = ui.PictureRecorder();
    final canvas = ui.Canvas(recorder);
    final target = ui.Rect.fromLTWH(0, 0, tw.toDouble(), th.toDouble());
    // BoxFit.cover: the largest centred crop of the source with the target's
    // aspect, so the blurred copy has the framing of the sharp cover.
    final scale = math.max(tw / source.width, th / source.height);
    final crop = ui.Rect.fromCenter(
      center: ui.Offset(source.width / 2, source.height / 2),
      width: tw / scale,
      height: th / scale,
    );
    final sigma = kCollapsingArtworkBlurSigma / kBlurredCoverDivisor;
    canvas.saveLayer(
      target,
      ui.Paint()
        ..imageFilter = ui.ImageFilter.blur(
          sigmaX: sigma,
          sigmaY: sigma,
          tileMode: ui.TileMode.clamp,
        ),
    );
    canvas.drawImageRect(
      source,
      crop,
      target,
      ui.Paint()..filterQuality = ui.FilterQuality.medium,
    );
    canvas.restore();
    final picture = recorder.endRecording();
    try {
      return await picture.toImage(tw, th);
    } finally {
      picture.dispose();
    }
  }

  /// The decoded image for [provider]. The listener receives its own clone
  /// (ImageStream clones per listener), so the caller owns the result and
  /// disposes it.
  static Future<ui.Image> _decode(ImageProvider provider) {
    final completer = Completer<ui.Image>();
    final stream = provider.resolve(ImageConfiguration.empty);
    late final ImageStreamListener listener;
    listener = ImageStreamListener(
      (info, _) {
        stream.removeListener(listener);
        completer.complete(info.image);
      },
      onError: (error, stack) {
        stream.removeListener(listener);
        completer.completeError(error, stack);
      },
    );
    stream.addListener(listener);
    return completer.future;
  }
}

/// The file provider of a single local cover, the same file a
/// [LocalCoverImage] of [url] shows. Null when there is no such file.
ImageProvider? localCoverBlurSource(String? url) {
  final path = coverFilePath(url);
  return path == null ? null : FileImage(File(path));
}

class _CoverKey {
  const _CoverKey(this.provider, this.width, this.height);

  final ImageProvider provider;
  final int width;
  final int height;

  @override
  bool operator ==(Object other) =>
      other is _CoverKey &&
      other.provider == provider &&
      other.width == width &&
      other.height == height;

  @override
  int get hashCode => Object.hash(provider, width, height);
}

/// The blurred copy of a cover, drawn over the sharp artwork in the same box.
/// It is an opaque image painted at [opacity]: a cross-fade from the sharp
/// artwork to its blur. No filter runs per frame; the blur is in the cache.
class BlurredCoverLayer extends StatefulWidget {
  const BlurredCoverLayer({
    super.key,
    required this.provider,
    required this.opacity,
  });

  final ImageProvider provider;

  /// 0 shows nothing, 1 shows the blurred copy over the sharp artwork.
  final double opacity;

  @override
  State<BlurredCoverLayer> createState() => _BlurredCoverLayerState();
}

class _BlurredCoverLayerState extends State<BlurredCoverLayer> {
  ui.Image? _image;
  Size? _size;
  ImageProvider? _provider;
  int _generation = 0;

  @override
  void dispose() {
    _image?.dispose();
    _generation++;
    super.dispose();
  }

  void _request(ImageProvider provider, Size size) {
    if (_provider == provider && _size == size) return;
    _provider = provider;
    _size = size;
    final generation = ++_generation;
    _image?.dispose();
    _image = null;
    BlurredCoverCache.instance.blurred(provider, size).then((image) {
      if (!mounted || generation != _generation) return;
      setState(() => _image = image.clone());
    }, onError: (_) {});
  }

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        if (!constraints.hasBoundedWidth || !constraints.hasBoundedHeight) {
          return const SizedBox.shrink();
        }
        _request(widget.provider, constraints.biggest);
        final image = _image;
        if (image == null) return const SizedBox.expand();
        return Opacity(
          opacity: widget.opacity.clamp(0.0, 1.0),
          child: RawImage(
            image: image,
            width: constraints.maxWidth,
            height: constraints.maxHeight,
            fit: BoxFit.fill,
            filterQuality: FilterQuality.medium,
          ),
        );
      },
    );
  }
}
