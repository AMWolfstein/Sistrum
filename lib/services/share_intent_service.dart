import 'dart:async';
import 'dart:io';
import 'package:receive_sharing_intent/receive_sharing_intent.dart';
import 'package:sistrum/utils/logger.dart';
import 'package:sistrum/services/audio_file_open_service.dart';

final _log = AppLogger('ShareIntent');

/// Hands local audio files shared into the app to [AudioFileOpenService].
class ShareIntentService {
  static final ShareIntentService _instance = ShareIntentService._internal();
  factory ShareIntentService() => _instance;
  ShareIntentService._internal();

  StreamSubscription<List<SharedMediaFile>>? _mediaSubscription;
  bool _initialized = false;

  Future<void> initialize() async {
    if (_initialized) return;
    _initialized = true;

    if (!Platform.isAndroid && !Platform.isIOS) {
      _log.i('Share intent is not supported on this platform');
      return;
    }

    _mediaSubscription = ReceiveSharingIntent.instance.getMediaStream().listen(
      _handleSharedMedia,
      onError: (Object err) => _log.e('Error: $err'),
    );

    final initialMedia = await ReceiveSharingIntent.instance.getInitialMedia();
    if (initialMedia.isNotEmpty) {
      _handleSharedMedia(initialMedia);
      ReceiveSharingIntent.instance.reset();
    }
  }

  void _handleSharedMedia(List<SharedMediaFile> files) {
    final audio = [
      for (final file in files)
        if (file.type == SharedMediaType.file &&
            OpenAudioFile.parse(file.path)?.isAudio == true)
          file.path,
    ];
    if (audio.isNotEmpty) {
      AudioFileOpenService.instance.receive(audio);
    }
  }

  void dispose() {
    _mediaSubscription?.cancel();
  }
}
