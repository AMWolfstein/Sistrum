import 'dart:typed_data';
import 'dart:ui' show FramePhase;

import 'package:flutter/scheduler.dart';

/// Frames kept per recording (about a minute at 60 Hz). Older frames are
/// overwritten, so memory stays fixed however long the owner records.
const kFrameRecorderCapacity = 3600;

/// Kept frames of one recording, oldest first. Each list has the same length.
class FrameSamples {
  const FrameSamples({
    required this.buildUs,
    required this.rasterUs,
    required this.intervalUs,
    required this.frameCount,
  });

  final List<int> buildUs;
  final List<int> rasterUs;

  /// Gap to the previous frame's vsync start; zero for the first frame.
  final List<int> intervalUs;

  /// Every frame of the recording, including ones the ring overwrote.
  final int frameCount;
}

/// Records UI (build) and raster durations from [SchedulerBinding]. The
/// timings callback exists only between [start] and [stop].
class FrameTimingRecorder {
  FrameTimingRecorder({this.capacity = kFrameRecorderCapacity})
    : _buildUs = Uint32List(capacity),
      _rasterUs = Uint32List(capacity),
      _intervalUs = Uint32List(capacity);

  final int capacity;
  final Uint32List _buildUs;
  final Uint32List _rasterUs;
  final Uint32List _intervalUs;
  int _frameCount = 0;
  int _lastVsyncUs = -1;
  int _lastBuildUs = 0;
  int _lastRasterUs = 0;
  bool _recording = false;

  bool get isRecording => _recording;
  int get frameCount => _frameCount;
  int get lastBuildUs => _lastBuildUs;
  int get lastRasterUs => _lastRasterUs;

  void start() {
    if (_recording) return;
    _frameCount = 0;
    _lastVsyncUs = -1;
    _recording = true;
    SchedulerBinding.instance.addTimingsCallback(_onTimings);
  }

  /// Removes the callback and returns the kept frames.
  FrameSamples stop() {
    if (_recording) {
      SchedulerBinding.instance.removeTimingsCallback(_onTimings);
      _recording = false;
    }
    return samples();
  }

  FrameSamples samples() {
    final kept = _frameCount < capacity ? _frameCount : capacity;
    // Once the ring wraps, the next write slot holds the oldest frame.
    final oldest = _frameCount <= capacity ? 0 : _frameCount % capacity;
    List<int> ordered(Uint32List ring) => [
      for (var i = 0; i < kept; i++) ring[(oldest + i) % capacity],
    ];
    return FrameSamples(
      buildUs: ordered(_buildUs),
      rasterUs: ordered(_rasterUs),
      intervalUs: ordered(_intervalUs),
      frameCount: _frameCount,
    );
  }

  void _onTimings(List<FrameTiming> timings) {
    for (final timing in timings) {
      addFrame(
        timing.buildDuration.inMicroseconds,
        timing.rasterDuration.inMicroseconds,
        timing.timestampInMicroseconds(FramePhase.vsyncStart),
      );
    }
  }

  /// One frame; the callback feeds it, and tests call it directly.
  void addFrame(int buildUs, int rasterUs, int vsyncStartUs) {
    final slot = _frameCount % capacity;
    _buildUs[slot] = buildUs.clamp(0, 0xFFFFFFFF);
    _rasterUs[slot] = rasterUs.clamp(0, 0xFFFFFFFF);
    _intervalUs[slot] = _lastVsyncUs < 0
        ? 0
        : (vsyncStartUs - _lastVsyncUs).clamp(0, 0xFFFFFFFF);
    _lastVsyncUs = vsyncStartUs;
    _lastBuildUs = buildUs;
    _lastRasterUs = rasterUs;
    _frameCount++;
  }
}
