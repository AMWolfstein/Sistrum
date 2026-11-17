import 'package:flutter/widgets.dart';
import 'package:sistrum/utils/string_utils.dart';

/// Editable tags and request generations, independent of sheet navigation.
class MetadataEditorController {
  static const fieldKeys = <String>[
    'title',
    'artist',
    'album',
    'album_artist',
    'date',
    'track_number',
    'total_tracks',
    'disc_number',
    'total_discs',
    'genre',
    'isrc',
    'lyrics',
    'label',
    'copyright',
    'composer',
    'comment',
    'album_type',
    'explicit',
    'upc',
    'cover',
  ];

  final Map<String, TextEditingController> _fields;
  bool explicit;

  MetadataEditorController(Map<String, String> initialValues)
    : _fields = {
        for (final key in fieldKeys)
          if (key != 'explicit' && key != 'cover')
            key: TextEditingController(text: initialValues[key] ?? ''),
      },
      explicit = parseExplicitFlag(initialValues['explicit']) == true;

  TextEditingController? operator [](String key) => _fields[key];

  Map<String, String> get values => {
    for (final entry in _fields.entries) entry.key: entry.value.text,
    'explicit': explicit ? '1' : '0',
  };

  Map<String, String> saveMetadata({required String artistTagMode}) {
    final tags = values;
    tags['track_total'] = tags.remove('total_tracks')!;
    tags['disc_total'] = tags.remove('total_discs')!;
    tags['compilation'] =
        tags['album_type']!.trim().toLowerCase() == 'compilation' ? '1' : '0';
    tags['artist_tag_mode'] = artistTagMode;
    return tags;
  }

  void dispose() {
    for (final field in _fields.values) {
      field.dispose();
    }
  }
}
