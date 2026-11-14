part of 'download_queue_provider.dart';

// Rate-limit backoff for queued downloads.
extension _DownloadQueueRateLimitGate on DownloadQueueNotifier {
  Duration _rateLimitBackoffDelay(String errorMsg) {
    final lower = errorMsg.toLowerCase();
    final retryAfterMatch = RegExp(
      r'retry[- ]?after(?: seconds)?[:= ]+(\d+)',
      caseSensitive: false,
    ).firstMatch(lower);
    final parsedSeconds = retryAfterMatch == null
        ? null
        : int.tryParse(retryAfterMatch.group(1) ?? '');
    final seconds = (parsedSeconds ?? 30).clamp(5, 300).toInt();
    return Duration(seconds: seconds);
  }

  Future<bool> _handleRateLimitedDownload(
    DownloadItem item,
    String errorMsg,
  ) async {
    if (_rateLimitRetriedItemIds.contains(item.id)) {
      return false;
    }
    _rateLimitRetriedItemIds.add(item.id);

    final delay = _rateLimitBackoffDelay(errorMsg);
    _log.i(
      'Rate limited while downloading ${item.track.name}; retrying after ${delay.inSeconds}s',
    );
    updateItemStatus(
      item.id,
      DownloadStatus.downloading,
      error: 'Rate limited, retrying after ${delay.inSeconds}s',
      errorType: DownloadErrorType.rateLimit,
    );

    // A synchronous state listener may already have cancelled or paused when
    // the backoff status was published. Do not register a new wait afterward.
    if (!_isProviderMounted) return true;
    var current = _findItemById(item.id);
    if (current == null || _isLocallyCancelled(item.id, item: current)) {
      return true;
    }
    if (!_isPausePending(item.id)) {
      await _rateLimitBackoff.wait(item.id, delay);
    }
    if (!_isProviderMounted) return true;
    current = _findItemById(item.id);
    if (current == null || _isLocallyCancelled(item.id, item: current)) {
      return true;
    }
    if (_isPausePending(item.id)) {
      _requeueItemForPause(item.id);
      await _finishPausedDownload(item.id);
      return true;
    }
    updateItemStatus(
      item.id,
      DownloadStatus.queued,
      progress: 0,
      speedMBps: 0,
      error: 'Retrying after rate limit',
      errorType: DownloadErrorType.rateLimit,
    );
    _saveQueueToStorage();
    return true;
  }
}
