import 'package:flutter/material.dart';
import 'package:sistrum/widgets/library_directory_view.dart';

/// A Library folder as a page of its own. Without [folderKey] it opens the
/// Folders view at the top.
class LibraryDirectoryScreen extends StatelessWidget {
  const LibraryDirectoryScreen({super.key, this.folderKey});

  final String? folderKey;

  @override
  Widget build(BuildContext context) =>
      Scaffold(body: LibraryDirectoryView(folderKey: folderKey));
}
