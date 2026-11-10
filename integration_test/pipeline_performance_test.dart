import 'library_pipeline_performance_test.dart' as library_pipeline;
import 'mornye_lyrics_performance_test.dart' as lyrics;
import 'performance_probe.dart';

void main() {
  PerformanceTestBinding.ensureInitialized();
  library_pipeline.main();
  lyrics.runLyricsPerformanceTests(mornye: false);
  lyrics.runLyricsPerformanceTests(mornye: true);
}
