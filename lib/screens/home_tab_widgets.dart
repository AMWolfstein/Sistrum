part of 'home_tab.dart';

class _DownloadedOrRemoteCover extends StatefulWidget {
  final String? downloadedFilePath;
  final String? imageUrl;
  final double width;
  final double height;
  final BorderRadius borderRadius;
  final IconData fallbackIcon;
  final double fallbackIconSize;
  final ColorScheme colorScheme;

  const _DownloadedOrRemoteCover({
    required this.downloadedFilePath,
    required this.imageUrl,
    required this.width,
    required this.height,
    required this.borderRadius,
    required this.fallbackIcon,
    required this.colorScheme,
    this.fallbackIconSize = 24,
  });

  @override
  State<_DownloadedOrRemoteCover> createState() =>
      _DownloadedOrRemoteCoverState();
}

class _DownloadedOrRemoteCoverState extends State<_DownloadedOrRemoteCover> {
  String? _embeddedCoverPath;
  bool _refreshScheduled = false;

  @override
  void initState() {
    super.initState();
    _embeddedCoverPath = _resolveEmbeddedCoverPath();
  }

  @override
  void didUpdateWidget(covariant _DownloadedOrRemoteCover oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.downloadedFilePath != widget.downloadedFilePath ||
        oldWidget.imageUrl != widget.imageUrl) {
      final nextPath = _resolveEmbeddedCoverPath();
      if (nextPath != _embeddedCoverPath) {
        setState(() => _embeddedCoverPath = nextPath);
      }
    }
  }

  String? _resolveEmbeddedCoverPath() {
    final filePath = widget.downloadedFilePath;
    if (filePath == null || filePath.isEmpty) return null;
    return DownloadedEmbeddedCoverResolver.resolve(
      filePath,
      onChanged: _onEmbeddedCoverChanged,
    );
  }

  void _onEmbeddedCoverChanged() {
    if (!mounted || _refreshScheduled) return;
    _refreshScheduled = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _refreshScheduled = false;
      if (!mounted) return;
      final nextPath = _resolveEmbeddedCoverPath();
      if (nextPath != _embeddedCoverPath) {
        setState(() => _embeddedCoverPath = nextPath);
      }
    });
  }

  Widget _fallback() {
    return Container(
      width: widget.width,
      height: widget.height,
      color: widget.colorScheme.surfaceContainerHighest,
      child: Icon(
        widget.fallbackIcon,
        color: widget.colorScheme.onSurfaceVariant,
        size: widget.fallbackIconSize,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final cacheWidth = (widget.width * 2).round();
    final cacheHeight = (widget.height * 2).round();

    Widget child;
    if (_embeddedCoverPath != null) {
      child = Image.file(
        File(_embeddedCoverPath!),
        width: widget.width,
        height: widget.height,
        fit: BoxFit.cover,
        cacheWidth: cacheWidth,
        cacheHeight: cacheHeight,
        gaplessPlayback: true,
        filterQuality: FilterQuality.low,
        errorBuilder: (_, _, _) => _fallback(),
      );
    } else if (widget.imageUrl != null && widget.imageUrl!.isNotEmpty) {
      child = CachedCoverImage(
        imageUrl: widget.imageUrl!,
        width: widget.width,
        height: widget.height,
        fit: BoxFit.cover,
        memCacheWidth: cacheWidth,
        memCacheHeight: cacheHeight,
        errorWidget: (_, _, _) => _fallback(),
      );
    } else {
      child = _fallback();
    }

    return ClipRRect(borderRadius: widget.borderRadius, child: child);
  }
}
