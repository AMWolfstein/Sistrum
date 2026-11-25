import 'package:material_ui/material_ui.dart';
import 'package:sistrum/models/library_database_models.dart';
import 'package:sistrum/models/unified_library_item.dart';
import 'package:sistrum/widgets/song_menu.dart';

/// The three-dots button a song row shows, the way tests open a song's menu.
Widget songMenuButton(LocalLibraryItem item) => SongMenuButton(
  track: UnifiedLibraryItem.fromLocalLibrary(item).toTrack(),
  filePath: item.filePath,
  localItem: item,
);

/// A page holding only [item]'s three-dots button.
Widget songMenuPage(LocalLibraryItem item) =>
    Scaffold(body: Center(child: songMenuButton(item)));
