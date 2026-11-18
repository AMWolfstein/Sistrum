import 'dart:collection';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/providers/library_collections_provider.dart';
import 'package:sistrum/controllers/library_tracks_folder_data.dart';
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
    final newestFirst = data.newestFirstTracks;
    final reads = entries.reads;
    for (var i = 0; i < 100; i++) {
      data.update(entries);
      expect(identical(data.newestFirstTracks, newestFirst), isTrue);
    }
    expect(entries.reads, reads);
    expect(data.keys.length, 4000);
    expect(() => data.tracks.clear(), throwsUnsupportedError);
    data.update(entries.take(2).toList());
    expect(data.keys, {'0', '1'});
    expect(data.tracks.length, 2);
  });

  test('newest-first seeds track additions, edits, order and empty lists', () {
    CollectionTrackEntry entry(String id, int day) => CollectionTrackEntry(
      key: id,
      track: Track(
        id: id,
        name: 'Song $id',
        artistName: 'Artist',
        albumName: 'Album',
        duration: 1,
      ),
      addedAt: DateTime.utc(2026, 1, day),
    );
    final first = entry('first', 1);
    final second = entry('second', 3);
    final third = entry('third', 2);
    final entries = [first, second];
    final data = LibraryTracksFolderData()..update(entries);
    final original = data.newestFirstTracks;
    expect(original, [second.track, first.track]);
    expect(entries, [first, second]);

    data.update([first, second, third]);
    expect(data.newestFirstTracks, [second.track, third.track, first.track]);
    final edited = CollectionTrackEntry(
      key: first.key,
      track: first.track.copyWith(name: 'Edited', albumName: 'Edited album'),
      addedAt: DateTime.utc(2026, 1, 4),
    );
    data.update([edited, second, third]);
    expect(data.newestFirstTracks, [edited.track, second.track, third.track]);

    final tied = entry('tied', 3);
    data.update([tied, second]);
    final reference = [tied, second]
      ..sort((a, b) => b.addedAt.compareTo(a.addedAt));
    expect(data.newestFirstTracks, reference.map((entry) => entry.track));
    data.update([second, tied]);
    final reorderedReference = [second, tied]
      ..sort((a, b) => b.addedAt.compareTo(a.addedAt));
    expect(
      data.newestFirstTracks,
      reorderedReference.map((entry) => entry.track),
    );

    data.update([third]);
    expect(data.newestFirstTracks, [third.track]);
    data.update([]);
    expect(data.newestFirstTracks, isEmpty);
  });
}
