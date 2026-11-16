import 'dart:async';
import 'dart:io';
import 'dart:math' as math;
import 'package:flutter/cupertino.dart' show CupertinoIcons;
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:sistrum/widgets/app_snack_bar.dart';
import 'package:sistrum/widgets/app_switch.dart';
import 'package:sistrum/widgets/app_loading_indicator.dart';
import 'package:sistrum/theme/app_motion.dart';
import 'package:sistrum/widgets/mornye_mix_banner.dart';
import 'package:sistrum/widgets/app_icon.dart';
import 'package:sistrum/widgets/app_alert_dialog.dart';
import 'package:sistrum/widgets/app_sliver_header.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/artist_album.dart';
import 'package:sistrum/providers/library_collections_provider.dart';
import 'package:sistrum/providers/library_detail_provider.dart';
import 'package:sistrum/providers/library_tag_credits_provider.dart';
import 'package:sistrum/screens/library_genre_screen.dart';
import 'package:sistrum/screens/library_genres_screen.dart';
import 'package:sistrum/screens/playlist_screen.dart';
import 'package:sistrum/services/library_detail_data.dart';
import 'package:sistrum/providers/download_queue_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/providers/extension_provider.dart';
import 'package:sistrum/providers/library_counts_provider.dart';
import 'package:sistrum/providers/library_mixes_provider.dart';
import 'package:sistrum/providers/local_library_provider.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/models/unified_library_item.dart';
import 'package:sistrum/screens/library_mix_screen.dart';
import 'package:sistrum/screens/listening_statistics_screen.dart';
import 'package:sistrum/screens/music_recognition_screen.dart';
import 'package:sistrum/screens/now_playing_screen.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/services/listening_statistics.dart';
import 'package:sistrum/services/music_player_service.dart' show PlayableMedia;
import 'package:sistrum/screens/album_screen.dart';
import 'package:sistrum/controllers/clickable_metadata.dart';
import 'package:sistrum/services/csv_import_service.dart';
import 'package:sistrum/services/downloaded_embedded_cover_resolver.dart';
import 'package:sistrum/services/shell_navigation_service.dart';
import 'package:sistrum/utils/adaptive_layout.dart';
import 'package:sistrum/utils/nav_bar_inset.dart';
import 'package:sistrum/utils/file_access.dart';
import 'package:sistrum/widgets/download_service_picker.dart';
import 'package:sistrum/widgets/cached_cover_image.dart';
import 'package:sistrum/widgets/library_search_results.dart';
import 'package:sistrum/widgets/open_audio_button.dart';
import 'package:sistrum/widgets/player_artwork.dart';
import 'package:sistrum/widgets/app_search_field.dart';
import 'package:sistrum/widgets/view_queue_snackbar_action.dart';
import 'package:sistrum/widgets/profile_avatar.dart';
import 'package:sistrum/widgets/app_crash_banner.dart';

part 'home_tab_helpers.dart';
part 'home_tab_import.dart';
part 'home_tab_intro.dart';
part 'home_tab_widgets.dart';

enum HomeTabMode { browse, search }

/// The Search tab's text. The shell clears it on back, as the search field does.
class HomeSearchTextNotifier extends Notifier<String> {
  @override
  String build() => '';

  void set(String text) => state = text;
}

final homeSearchTextProvider = NotifierProvider<HomeSearchTextNotifier, String>(
  HomeSearchTextNotifier.new,
);

/// The offline Home (browse) and the library Search (search).
class HomeTab extends ConsumerStatefulWidget {
  const HomeTab({super.key, required this.mode});

  final HomeTabMode mode;

  @override
  ConsumerState<HomeTab> createState() => _HomeTabState();
}

class _HomeTabState extends ConsumerState<HomeTab>
    with AutomaticKeepAliveClientMixin {
  bool get _showsSearch => widget.mode == HomeTabMode.search;
  bool get _showsHome => widget.mode == HomeTabMode.browse;
  final _searchController = TextEditingController();
  final FocusNode _searchFocusNode = FocusNode();
  final _homeScrollController = ScrollController();
  bool _isCsvImporting = false;

  @override
  bool get wantKeepAlive => true;

  @override
  void initState() {
    super.initState();
    _searchController.addListener(_onSearchChanged);
    ShellNavigationService.searchRequests.addListener(_focusSearchFromShell);
  }

  @override
  void dispose() {
    ShellNavigationService.searchRequests.removeListener(_focusSearchFromShell);
    _homeScrollController.dispose();
    _searchController.removeListener(_onSearchChanged);
    _searchController.dispose();
    _searchFocusNode.dispose();
    super.dispose();
  }

  void _setCsvImporting(bool value) {
    if (_isCsvImporting == value) return;
    if (!mounted) {
      _isCsvImporting = value;
      return;
    }
    setState(() {
      _isCsvImporting = value;
    });
  }

  void _focusSearchFromShell() {
    if (!_showsSearch) return;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted ||
          !_showsSearch ||
          !TickerMode.getValuesNotifier(context).value.enabled) {
        return;
      }
      if (_homeScrollController.hasClients) _homeScrollController.jumpTo(0);
      _searchFocusNode.requestFocus();
    });
  }

  void _onSearchChanged() {
    final text = _searchController.text.trim();
    ref.read(homeSearchTextProvider.notifier).set(text);
    if (mounted) setState(() {});
  }

  Future<void> _pasteFromClipboard() async {
    final data = await Clipboard.getData(Clipboard.kTextPlain);
    if (data?.text != null) _searchController.text = data!.text!;
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    // The shell clears the Search text on back.
    ref.listen<String>(homeSearchTextProvider, (previous, next) {
      if (_showsSearch && next.isEmpty && _searchController.text.isNotEmpty) {
        _searchController.clear();
      }
    });
    final searchText = _showsSearch ? _searchController.text.trim() : '';
    final colorScheme = Theme.of(context).colorScheme;
    final sectionMotion = const AppMotionSpec(
      Duration(milliseconds: 250),
      Curves.easeOut,
    );
    return GestureDetector(
      onTap: () {
        if (_searchFocusNode.hasFocus) _searchFocusNode.unfocus();
      },
      behavior: HitTestBehavior.translucent,
      child: Scaffold(
        body: CustomScrollView(
          controller: _homeScrollController,
          keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
          slivers: [
            _buildHeaderSliver(),
            if (_showsHome) const SliverToBoxAdapter(child: AppCrashBanner()),
            if (_showsSearch)
              SliverToBoxAdapter(
                child: Padding(
                  // Centered with the search results on tablets.
                  padding: EdgeInsets.fromLTRB(
                    16 + homeContentInset(context),
                    searchText.isEmpty ? 32 : 8,
                    16 + homeContentInset(context),
                    searchText.isEmpty ? 16 : 8,
                  ),
                  child: _buildSearchBar(),
                ),
              ),
            if (_showsSearch && searchText.isNotEmpty)
              LibrarySearchResults(
                query: searchText,
                shelves: true,
                onOpenArtist: (artist) => openLibraryArtist(context, artist),
              ),
            if (_showsHome)
              SliverToBoxAdapter(
                child: AnimatedSize(
                  alignment: AlignmentDirectional.topStart,
                  duration: sectionMotion.duration,
                  curve: sectionMotion.curve,
                  child: _HomeSections(inset: 16 + homeContentInset(context)),
                ),
              ),
            if (_showsHome)
              SliverToBoxAdapter(
                child: _HomeEmptyState(colorScheme: colorScheme),
              ),
            const NavBarSliverSpacer(),
          ],
        ),
      ),
    );
  }

  Widget _buildHeaderSliver() {
    return AppSliverHeader.tabRoot(
      alignToContent: true,
      contentInset: homeContentInset(context),
      title: widget.mode == HomeTabMode.search
          ? context.l10n.sistrumSearch
          : context.l10n.homeTitle,
      actions: _showsHome ? const [HomeProfileButton()] : null,
    );
  }

  Widget _buildSearchBar() {
    final hasText = _searchController.text.isNotEmpty;
    return AppSearchField(
      controller: _searchController,
      samplesBackdrop: false,
      focusNode: _searchFocusNode,
      hintText: context.l10n.librarySearchHint,
      clearTooltip: context.l10n.dialogClear,
      onClear: () => _searchController.clear(),
      suffixIcon: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (hasText)
            IconButton(
              icon: const Icon(Icons.clear),
              onPressed: () => _searchController.clear(),
              tooltip: context.l10n.dialogClear,
            )
          else ...[
            IconButton(
              icon: const Icon(Icons.file_upload_outlined),
              onPressed: _isCsvImporting
                  ? null
                  : () => _importCsv(context, ref),
              tooltip: context.l10n.homeImportCsvTooltip,
            ),
            IconButton(
              icon: const Icon(Icons.paste),
              onPressed: _pasteFromClipboard,
              tooltip: context.l10n.actionPaste,
            ),
            IconButton(
              icon: const AppIcon.adaptive(Icons.mic),
              onPressed: _openMusicRecognition,
              tooltip: context.l10n.recognizeSearchTooltip,
            ),
          ],
        ],
      ),
      onSubmitted: (_) => _searchFocusNode.unfocus(),
    );
  }

  /// Identifies a song nearby and searches the library for it.
  Future<void> _openMusicRecognition() async {
    _searchFocusNode.unfocus();
    final query = await pushPage<String>(
      context,
      (_) => const MusicRecognitionScreen(),
    );
    if (!mounted || query == null || query.trim().isEmpty) return;
    _searchController.text = query.trim();
  }
}
