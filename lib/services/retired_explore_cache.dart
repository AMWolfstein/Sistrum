import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/services/preference_blob_store.dart';

/// The Home explore feed cache: a blob file and its preference keys. Nothing
/// reads or writes it since the feed was removed, so startup clears it.
const retiredExploreCache = PreferenceBlobStore('explore_feed', {
  'explore_home_feed_cache': String,
  'explore_home_feed_ts': int,
});

Future<void> clearRetiredExploreCache(
  SharedPreferences prefs, [
  PreferenceBlobStore store = retiredExploreCache,
]) => store.clear(prefs);
