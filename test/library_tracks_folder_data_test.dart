import 'dart:collection';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/providers/library_collections_provider.dart';
import 'package:sistrum/controllers/library_tracks_folder_data.dart';
import 'package:sistrum/services/history_database.dart';
import 'package:flutter_test/flutter_test.dart';

class _ReadCountingList<T> extends ListBase<T> {
  _ReadCountingList(this.values);
  final List<T> values;
  int reads = 0;
  @override
  int get length => values.length;
  @override
  set length(int value) => throw UnsupportedError('immutable');
  @override
  T operator [](int index) {
    reads++;
    return values[index];
  }

  @override
  void operator []=(int index, T value) => throw UnsupportedError('immutable');
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  test('unchanged collection snapshot does no additional full-list reads', () {
    final entries = _ReadCountingList(
      List.generate(
        4000,
        (index) => CollectionTrackEntry(
          key: '$index',
          addedAt: DateTime.utc(2026),
          track: Track(
            id: '$index',
            name: 'Song $index',
            artistName: 'Artist',
            albumName: 'Album',
            duration: 1,
          ),
        ),
      ),
    );
    final data = LibraryTracksFolderData()..update(entries);
    final request = data.historyRequest;
    final existingKeys = {data.historyLookupKeys.first};
    final inHistory = data.inHistory(existingKeys);
    final reads = entries.reads;
    final hash = request.hashCode;
    for (var i = 0; i < 100; i++) {
      data.update(entries);
      expect(identical(data.historyRequest, request), isTrue);
      expect(data.historyRequest.hashCode, hash);
      expect(identical(data.inHistory(existingKeys), inHistory), isTrue);
    }
    expect(entries.reads, reads);
    expect(data.keys.length, 4000);
    expect(() => data.tracks.clear(), throwsUnsupportedError);
    expect(() => request.tracks.clear(), throwsUnsupportedError);
    expect(request, HistoryBatchLookupRequest(request.tracks));
    expect(hash, HistoryBatchLookupRequest(request.tracks).hashCode);
    data.update(entries.take(2).toList());
    expect(data.keys, {'0', '1'});
    expect(data.tracks.length, 2);
    expect(identical(data.historyRequest, request), isFalse);
  });

  test('history flags follow membership and changed lookup metadata', () {
    const track = Track(
      id: 'one',
      name: 'Song',
      artistName: 'Artist',
      albumName: 'Album',
      duration: 1,
    );
    CollectionTrackEntry entry(Track value) => CollectionTrackEntry(
      key: value.id,
      track: value,
      addedAt: DateTime.utc(2026),
    );
    final data = LibraryTracksFolderData()..update([entry(track)]);
    final keys = {data.historyLookupKeys.single};
    expect(data.inHistory(keys), [true]);
    expect(data.inHistory(const {}), [false]);
    expect(data.inHistory({...keys}), [true]);
    final mutableKeys = {...keys};
    final previousFlags = data.inHistory(mutableKeys);
    mutableKeys.clear();
    expect(data.inHistory(mutableKeys), [false]);
    expect(identical(data.inHistory(mutableKeys), previousFlags), isFalse);

    data.update([entry(track.copyWith(name: 'Edited'))]);
    expect(data.inHistory(keys), [false]);
    final editedKeys = {data.historyLookupKeys.single};
    expect(data.inHistory(editedKeys), [true]);
    data.update([]);
    expect(data.inHistory(editedKeys), isEmpty);
  });
}
