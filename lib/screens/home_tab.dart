import 'dart:async';
import 'dart:io';
import 'dart:math' as math;
import 'package:cupertino_ui/cupertino_ui.dart' show CupertinoIcons;
import 'package:flutter/foundation.dart';
import 'package:material_ui/material_ui.dart';
import 'package:sistrum/theme/app_tokens.dart';
import 'package:sistrum/widgets/app_loading_indicator.dart';
import 'package:sistrum/theme/app_motion.dart';
import 'package:sistrum/widgets/app_icon_button.dart';
import 'package:sistrum/widgets/app_icon.dart';
import 'package:sistrum/widgets/library_search_results.dart';
import 'package:sistrum/controllers/clickable_metadata.dart' show pushPage;
import 'package:sistrum/screens/library_artist_screen.dart';
import 'package:sistrum/widgets/app_sliver_header.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/providers/recent_access_provider.dart';
import 'package:sistrum/widgets/desktop/desktop_scroll_arrows.dart';
import 'package:sistrum/providers/library_counts_provider.dart';
import 'package:sistrum/providers/library_mixes_provider.dart';
import 'package:sistrum/providers/local_library_provider.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/models/unified_library_item.dart';
import 'package:sistrum/screens/library_mix_screen.dart';
import 'package:sistrum/screens/listening_statistics_screen.dart';
import 'package:sistrum/screens/now_playing_screen.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/services/downloaded_embedded_cover_resolver.dart';
import 'package:sistrum/services/listening_statistics.dart';
import 'package:sistrum/services/music_player_service.dart' show PlayableMedia;
import 'package:sistrum/screens/local_album_screen.dart';
import 'package:sistrum/services/shell_navigation_service.dart';
import 'package:sistrum/utils/adaptive_layout.dart';
import 'package:sistrum/utils/desktop_platform.dart';
import 'package:sistrum/utils/nav_bar_inset.dart';
import 'package:sistrum/widgets/desktop/desktop_commands.dart'
    show openDesktopNowPlaying;
import 'package:sistrum/utils/file_access.dart';
import 'package:sistrum/widgets/cached_cover_image.dart';
import 'package:sistrum/widgets/mornye_mix_banner.dart';
import 'package:sistrum/widgets/player_artwork.dart';
import 'package:sistrum/widgets/error_card.dart';
import 'package:sistrum/widgets/app_search_field.dart';
import 'package:sistrum/widgets/profile_avatar.dart';
import 'package:sistrum/widgets/app_crash_banner.dart';

part 'home_tab_helpers.dart';
part 'home_tab_recent.dart';
part 'home_tab_intro.dart';
part 'home_tab_widgets.dart';

enum HomeTabMode { combined, browse, search }

class HomeTab extends ConsumerStatefulWidget {
  const HomeTab({super.key, this.mode = HomeTabMode.combined});

  final HomeTabMode mode;

  @override
  ConsumerState<HomeTab> createState() => _HomeTabState();
}

class _HomeTabState extends ConsumerState<HomeTab>
    with AutomaticKeepAliveClientMixin, SingleTickerProviderStateMixin {
  bool get _showsSearch => widget.mode != HomeTabMode.browse;
  bool get _showsHome => widget.mode != HomeTabMode.search;
  final _urlController = TextEditingController();
  final FocusNode _searchFocusNode = FocusNode();
  final _homeScrollController = ScrollController();
  String _lastSearchText = '';
  String _libraryQuery = '';
  Timer? _libraryQueryDebounce;
  static const Duration _libraryQueryDelay = Duration(milliseconds: 300);

  /// Mornye's desktop field uses the compact toolbar size.
  bool get _compactSearchField => isDesktopPlatform;

  @override
  bool get wantKeepAlive => true;

  @override
  void initState() {
    super.initState();
    _urlController.addListener(_onSearchChanged);
    _searchFocusNode.addListener(_onSearchFocusChanged);
    ShellNavigationService.searchRequests.addListener(_focusSearchFromShell);
  }

  @override
  void dispose() {
    ShellNavigationService.searchRequests.removeListener(_focusSearchFromShell);
    _homeScrollController.dispose();
    _libraryQueryDebounce?.cancel();
    _urlController.removeListener(_onSearchChanged);
    _searchFocusNode.removeListener(_onSearchFocusChanged);
    _urlController.dispose();
    _searchFocusNode.dispose();
    super.dispose();
  }

  void _onSearchFocusChanged() {
    // Only the Recent list depends on focus; TextField handles the cursor.
    if (widget.mode == HomeTabMode.search) return;
    if (mounted) setState(() {});
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
    final text = _urlController.text.trim();
    // Focusing an empty field also changes its selection. Only a text edit
    // should reset results, not that cursor notification.
    if (text == _lastSearchText) return;
    _lastSearchText = text;
    _libraryQueryDebounce?.cancel();
    if (text.isEmpty) {
      if (_libraryQuery.isNotEmpty) setState(() => _libraryQuery = '');
      return;
    }
    _libraryQueryDebounce = Timer(_libraryQueryDelay, () {
      if (mounted && _urlController.text.trim() == text) {
        setState(() => _libraryQuery = text);
      }
    });
  }

  void _clearSearch() {
    _libraryQueryDebounce?.cancel();
    FocusManager.instance.primaryFocus?.unfocus();
    _urlController.clear();
    setState(() => _libraryQuery = '');
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);

    final searchText = _showsSearch ? _urlController.text.trim() : '';
    final hasSearchInput = searchText.isNotEmpty;
    final isSearchFocused = _showsSearch && _searchFocusNode.hasFocus;
    final colorScheme = Theme.of(context).colorScheme;
    // Recents show on the Search page, or while the field is focused.
    final showRecentAccess =
        _showsSearch &&
        !hasSearchInput &&
        (widget.mode == HomeTabMode.search || isSearchFocused);
    final showLibraryResults = _showsSearch && _libraryQuery.isNotEmpty;
    // The Search page waits for input before showing local matches.
    final showLibraryPrompt =
        widget.mode == HomeTabMode.search && !hasSearchInput;
    final showSearchBar = _showsSearch;
    final sectionMotion = AppMotion.surface(context);

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
            if (_showsSearch &&
                widget.mode == HomeTabMode.search &&
                isDesktopPlatform)
              _buildDesktopSearchToolbarSliver()
            else
              _buildHeaderSliver(),
            if (_showsHome) const SliverToBoxAdapter(child: AppCrashBanner()),
            if (showSearchBar &&
                !(widget.mode == HomeTabMode.search && isDesktopPlatform))
              _buildSearchBarSliver(hasResults: hasSearchInput),
            if (showRecentAccess)
              _buildRecentAccessSliver(colorScheme: colorScheme),
            if (showLibraryResults) _buildLibrarySearchSliver(),
            if (showLibraryPrompt) _buildLibraryPromptSliver(),
            _buildHomeSectionsSliver(
              sectionMotion: sectionMotion,
              hasResults: hasSearchInput,
              showRecentAccess: showRecentAccess,
            ),
            const NavBarSliverSpacer(),
          ],
        ),
      ),
    );
  }

  /// Centers the search field in the desktop Search page toolbar.
  Widget _buildDesktopSearchToolbarSliver() {
    return SliverAppBar(
      pinned: true,
      automaticallyImplyLeading: false,
      toolbarHeight: 60,
      backgroundColor: Theme.of(context).colorScheme.surface,
      surfaceTintColor: Colors.transparent,
      titleSpacing: 0,
      title: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 24),
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 480),
            child: _buildSearchBar(),
          ),
        ),
      ),
    );
  }

  Widget _buildLibraryPromptSliver() {
    final colorScheme = Theme.of(context).colorScheme;
    return SliverFillRemaining(
      hasScrollBody: false,
      child: Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              AppIcon(
                Icons.search,
                size: 64,
                color: colorScheme.onSurfaceVariant,
              ),
              const SizedBox(height: 16),
              Text(
                context.l10n.searchLibraryPrompt,
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.titleLarge?.copyWith(
                  fontWeight: FontWeight.w700,
                  color: colorScheme.onSurfaceVariant,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildLibrarySearchSliver() {
    return LibrarySearchResults(
      query: _libraryQuery,
      shelves: true,
      onOpenArtist: (artist) =>
          pushPage<void>(context, (_) => LibraryArtistScreen(artist: artist)),
    );
  }

  Widget _buildHeaderSliver() {
    return AppSliverHeader.tabRoot(
      alignToContent: true,
      contentInset: homeContentInset(context),
      title: widget.mode == HomeTabMode.search
          ? context.l10n.mornyeSearch
          : context.l10n.homeTitle,
      // The desktop sidebar's account row already opens the profile.
      titleTrailing: _showsHome && !isDesktopPlatform
          ? const HomeProfileButton()
          : null,
    );
  }

  Widget _buildSearchBarSliver({required bool hasResults}) {
    return SliverToBoxAdapter(
      child: Padding(
        // Centered with the search results on tablets.
        padding: EdgeInsets.fromLTRB(
          16 + homeContentInset(context),
          hasResults ? 8 : 32,
          16 + homeContentInset(context),
          hasResults ? 8 : 16,
        ),
        child: _buildSearchBar(),
      ),
    );
  }

  Widget _buildSearchBar() {
    final hasText = _urlController.text.isNotEmpty;
    return AppSearchField(
      controller: _urlController,
      compact: _compactSearchField,
      samplesBackdrop: false,
      focusNode: _searchFocusNode,
      hintText: context.l10n.librarySearchHint,
      clearTooltip: context.l10n.dialogClear,
      onClear: _clearSearch,
      suffixIcon: hasText
          ? AppIconButton(
              icon: const Icon(Icons.clear),
              onPressed: _clearSearch,
              tooltip: context.l10n.dialogClear,
            )
          : null,
    );
  }

  Widget _buildRecentAccessSliver({required ColorScheme colorScheme}) {
    return Consumer(
      builder: (context, ref, _) {
        final recentAccessView = ref.watch(recentAccessViewProvider);
        return _buildRecentAccess(recentAccessView, colorScheme);
      },
    );
  }

  Widget _buildHomeSectionsSliver({
    required AppMotionSpec sectionMotion,
    required bool hasResults,
    required bool showRecentAccess,
  }) {
    return SliverToBoxAdapter(
      child: _resizeSection(
        sectionMotion,
        (!_showsHome || hasResults || showRecentAccess)
            ? const SizedBox.shrink()
            : _HomeSections(inset: 16 + homeContentInset(context)),
      ),
    );
  }

  /// Opens or closes the space a Home section takes. Sections stay where
  /// they are while the space below them opens or closes.
  static Widget _resizeSection(AppMotionSpec motion, Widget child, {Key? key}) {
    // A zero-length AnimatedSize re-dirties itself during layout.
    if (motion.duration == Duration.zero) {
      return KeyedSubtree(key: key, child: child);
    }
    return AnimatedSize(
      key: key,
      alignment: AlignmentDirectional.topStart,
      duration: motion.duration,
      curve: motion.curve,
      child: child,
    );
  }
}
