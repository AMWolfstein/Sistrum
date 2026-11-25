import 'dart:async';
import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:cupertino_ui/cupertino_ui.dart';
import 'package:material_ui/material_ui.dart';
import 'package:flutter/services.dart';
import 'package:path/path.dart' as p;
import 'package:sistrum/controllers/metadata_editor_controller.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/services/metadata_cover_resources.dart';
import 'package:sistrum/services/metadata_persistence_service.dart';
import 'package:sistrum/widgets/app_action_button.dart';
import 'package:sistrum/widgets/app_bottom_sheet.dart';
import 'package:sistrum/widgets/app_progress_indicator.dart';
import 'package:sistrum/widgets/app_icon_button.dart';
import 'package:sistrum/widgets/app_loading_indicator.dart';
import 'package:sistrum/widgets/app_snack_bar.dart';
import 'package:sistrum/widgets/metadata_editor_fields.dart';
import 'package:sistrum/widgets/metadata_text_field.dart';

part 'track_metadata_edit_cover.dart';

class EditMetadataSheet extends StatefulWidget {
  static const _onlineCoverSentinel = '__online_cover__';
  final ColorScheme colorScheme;
  final Map<String, String> initialValues;
  final String filePath;
  final String? sourceTrackId;
  final int durationMs;

  /// Saves the tags somewhere other than [filePath], such as the copy on a
  /// linked computer: null once saved, else why it failed. The cover editor
  /// and the online autofill, which work on the local file, are left out.
  final Future<String?> Function(Map<String, String> metadata)? saveTags;

  const EditMetadataSheet({
    super.key,
    required this.colorScheme,
    required this.initialValues,
    required this.filePath,
    this.sourceTrackId,
    required this.durationMs,
    this.saveTags,
  });

  @override
  State<EditMetadataSheet> createState() => _EditMetadataSheetState();
}

class _EditMetadataSheetState extends State<EditMetadataSheet> {
  bool _saving = false;
  final _covers = MetadataCoverResources();
  late final _persistence = MetadataPersistenceService(covers: _covers);
  bool _showAdvanced = false;
  String? _selectedCoverPath;
  String? _selectedCoverTempDir;
  String? _selectedCoverName;
  String? _currentCoverPath;
  String? _currentCoverTempDir;
  bool _loadingCurrentCover = false;
  final Map<String, ({int width, int height, int bytes})> _coverDetails = {};
  final Set<String> _coverDetailLoads = {};
  final GlobalKey<ScaffoldMessengerState> _sheetMessengerKey =
      GlobalKey<ScaffoldMessengerState>();

  /// setState is @protected, so the extension part files route rebuilds
  /// through this forwarder.
  void _setState(VoidCallback fn) => setState(fn);

  void _showSheetSnackBar(String message) {
    final snackBar = SnackBar(content: Text(message));
    final messenger = _sheetMessengerKey.currentState;
    if (messenger != null) {
      messenger
        ..hideCurrentSnackBar()
        ..showSnackBar(snackBar);
      return;
    }
    ScaffoldMessenger.maybeOf(context)?.showSnackBar(snackBar);
  }

  late final MetadataEditorController _form;

  bool _hasValue(String? value) => value != null && value.trim().isNotEmpty;

  @override
  void initState() {
    super.initState();
    _form = MetadataEditorController(widget.initialValues);
    if (widget.saveTags == null) unawaited(_loadCurrentCoverPreview());
  }

  @override
  void dispose() {
    unawaited(_covers.dispose());
    _form.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (_saving) return;
    if (widget.saveTags case final saveTags?) {
      setState(() => _saving = true);
      final error = await saveTags(_form.saveMetadata());
      if (!mounted) return;
      if (error == null) {
        Navigator.pop(context, true);
        return;
      }
      setState(() => _saving = false);
      if (error.isNotEmpty) _showSheetSnackBar(error);
      return;
    }
    setState(() => _saving = true);
    try {
      final result = await _persistence.save(
        MetadataSaveRequest(
          filePath: widget.filePath,
          metadata: _form.saveMetadata(),
          selectedCoverPath: _selectedCoverPath,
          currentCoverPath: _currentCoverPath,
        ),
      );
      if (result.failure == null) {
        if (!mounted) return;
        Navigator.pop(context, true);
        return;
      }
      if (!mounted) return;
      final l10n = context.l10n;
      _showSheetSnackBar(switch (result.failure!) {
        MetadataSaveFailure.backend => context.friendlyError(
          result.error,
          fallback: l10n.metadataSaveFailedFfmpeg,
        ),
        MetadataSaveFailure.ffmpeg => l10n.metadataSaveFailedFfmpeg,
        MetadataSaveFailure.storage => l10n.metadataSaveFailedStorage,
        MetadataSaveFailure.unexpected => l10n.snackbarError(
          context.friendlyError(result.error),
        ),
      });
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final cs = widget.colorScheme;

    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(context).bottom),
      child: DraggableScrollableSheet(
        initialChildSize: 0.85,
        minChildSize: 0.5,
        maxChildSize: 0.95,
        expand: false,
        builder: (context, scrollController) => AppScaffoldMessenger(
          key: _sheetMessengerKey,
          child: Scaffold(
            backgroundColor: Colors.transparent,
            resizeToAvoidBottomInset: false,
            body: Column(
              children: [
                Padding(
                  padding: const EdgeInsets.only(top: 12, bottom: 8),
                  child: const AppSheetHandle(),
                ),
                Padding(
                  padding: const EdgeInsets.fromLTRB(20, 4, 20, 12),
                  child: Row(
                    children: [
                      Expanded(
                        child: Text(
                          context.l10n.trackEditMetadata,
                          style: Theme.of(context).textTheme.titleLarge
                              ?.copyWith(fontWeight: FontWeight.bold),
                        ),
                      ),
                      if (_saving)
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 12),
                          child: SizedBox(
                            width: 22,
                            height: 22,
                            child: const CupertinoActivityIndicator(),
                          ),
                        )
                      else
                        AppActionButton(
                          onPressed: _save,
                          icon: const Icon(Icons.check, size: 18),
                          label: Text(context.l10n.dialogSave),
                        ),
                    ],
                  ),
                ),
                Divider(
                  height: 1,
                  color: cs.outlineVariant.withValues(alpha: 0.5),
                ),
                Expanded(
                  child: ListView(
                    controller: scrollController,
                    padding: const EdgeInsets.fromLTRB(20, 6, 20, 24),
                    children: [
                      if (widget.saveTags == null) ...[_buildCoverEditor(cs)],
                      MetadataEditorSectionCard(
                        colorScheme: widget.colorScheme,
                        icon: Icons.info_outline,
                        title: context.l10n.trackMetadata,
                        children: [
                          MetadataTextField(
                            cs,
                            context.l10n.editMetadataFieldTitle,
                            _form['title']!,
                          ),
                          MetadataTextField(
                            cs,
                            context.l10n.editMetadataFieldArtist,
                            _form['artist']!,
                          ),
                          MetadataTextField(
                            cs,
                            context.l10n.editMetadataFieldAlbum,
                            _form['album']!,
                          ),
                          MetadataTextField(
                            cs,
                            context.l10n.editMetadataFieldAlbumArtist,
                            _form['album_artist']!,
                          ),
                          MetadataTextField(
                            cs,
                            context.l10n.editMetadataFieldDate,
                            _form['date']!,
                            hint: context.l10n.editMetadataFieldDateHint,
                          ),
                          Row(
                            children: [
                              Expanded(
                                child: MetadataTextField(
                                  cs,
                                  context.l10n.editMetadataFieldTrackNum,
                                  _form['track_number']!,
                                  keyboard: TextInputType.number,
                                ),
                              ),
                              const SizedBox(width: 12),
                              Expanded(
                                child: MetadataTextField(
                                  cs,
                                  context.l10n.editMetadataFieldTrackTotal,
                                  _form['total_tracks']!,
                                  keyboard: TextInputType.number,
                                ),
                              ),
                            ],
                          ),
                          Row(
                            children: [
                              Expanded(
                                child: MetadataTextField(
                                  cs,
                                  context.l10n.editMetadataFieldDiscNum,
                                  _form['disc_number']!,
                                  keyboard: TextInputType.number,
                                ),
                              ),
                              const SizedBox(width: 12),
                              Expanded(
                                child: MetadataTextField(
                                  cs,
                                  context.l10n.editMetadataFieldDiscTotal,
                                  _form['total_discs']!,
                                  keyboard: TextInputType.number,
                                ),
                              ),
                            ],
                          ),
                          MetadataTextField(
                            cs,
                            context.l10n.editMetadataFieldGenre,
                            _form['genre']!,
                          ),
                          MetadataTextField(
                            cs,
                            context.l10n.editMetadataFieldIsrc,
                            _form['isrc']!,
                          ),
                        ],
                      ),
                      MetadataEditorSectionCard(
                        colorScheme: widget.colorScheme,
                        icon: Icons.lyrics_outlined,
                        title: context.l10n.trackLyrics,
                        children: [
                          MetadataTextField(
                            cs,
                            context.l10n.trackLyrics,
                            _form['lyrics']!,
                            maxLines: 8,
                            keyboard: TextInputType.multiline,
                          ),
                        ],
                      ),
                      MetadataEditorSectionCard(
                        colorScheme: widget.colorScheme,
                        icon: Icons.tune,
                        title: context.l10n.editMetadataAdvanced,
                        onHeaderTap: () =>
                            setState(() => _showAdvanced = !_showAdvanced),
                        expanded: _showAdvanced,
                        children: [
                          if (_showAdvanced) ...[
                            MetadataTextField(
                              cs,
                              context.l10n.editMetadataFieldLabel,
                              _form['label']!,
                            ),
                            MetadataTextField(
                              cs,
                              context.l10n.editMetadataFieldCopyright,
                              _form['copyright']!,
                            ),
                            MetadataTextField(
                              cs,
                              context.l10n.editMetadataFieldComposer,
                              _form['composer']!,
                            ),
                            MetadataTextField(
                              cs,
                              context.l10n.trackAlbumType,
                              _form['album_type']!,
                              hint: context.l10n.editMetadataFieldAlbumTypeHint,
                            ),
                            MetadataEditorSwitchField(
                              colorScheme: widget.colorScheme,
                              label: context.l10n.editMetadataFieldExplicit,
                              subtitle:
                                  context.l10n.editMetadataFieldExplicitHint,
                              value: _form.explicit,
                              onChanged: (value) =>
                                  setState(() => _form.explicit = value),
                            ),
                            MetadataTextField(
                              cs,
                              context.l10n.editMetadataFieldUpc,
                              _form['upc']!,
                              hint: context.l10n.editMetadataFieldUpcHint,
                              keyboard: TextInputType.number,
                              inputFormatters: [
                                FilteringTextInputFormatter.digitsOnly,
                                LengthLimitingTextInputFormatter(18),
                              ],
                            ),
                            MetadataTextField(
                              cs,
                              context.l10n.editMetadataFieldComment,
                              _form['comment']!,
                              maxLines: 3,
                            ),
                          ],
                        ],
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
