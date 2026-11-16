# Feature Specification: Offline library detail screens

**Feature Branch**: `wip`

**Created**: 2026-11-15

**Status**: Draft

**Input**: Owner request (Arabic, summarized): the library's artist, album, playlist and
Loved songs pages take the design of the pages that open from an Apple Music search,
read the local library, and keep only what works offline. The old offline versions of
the artist and album pages are deleted. The library loses its Recently Added shelf; the
Songs page gains Play and Shuffle; library track rows look like the album rows. Now
Playing opens the library artist, shows a heart for Love, drops online options and adds
Remove from device. Two lyric changes from Mornye are ported: right-to-left lyrics
(mornyeapp/Mornye PR #844, commit `a45631a`) and the Now Playing lyric preview
(mornyeapp/Mornye PR #847). The app will become offline-only; removing the online
features themselves is the next mission, not this one.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Artist page from the library (Priority: P1)

From the library's Artists list (or Go to artist anywhere in the library or player) the
person opens an artist page with the Apple Music artist design, filled only from
their own files.

**Independent Test**: With a library holding several albums, singles and featured
tracks of one artist, open the artist from the library and check each section.

**Acceptance Scenarios**:

1. **Given** an artist with albums in the library, **When** the page opens, **Then** the
   header image is the cover of the artist's newest album by release date, and the
   header button plays the artist (no download button).
2. **Given** that artist, **Then** Latest Release shows the newest album by release date,
   Popular shows the artist's most-played tracks from listening statistics, filled up
   with random tracks of the artist when there are too few plays (or statistics are
   off), Albums and Singles & EPs list the artist's albums and singles, and
   Compilations lists tracks where the artist appears but is not the album artist.
3. **Given** the artist page, **Then** there are no Releases, monthly listeners, share or
   open-in-other-services, discography download, download selection or concerts.

### User Story 2 - Album page from the library (Priority: P1)

**Acceptance Scenarios**:

1. **Given** any album in the library (downloaded or scanned), **When** it opens,
   **Then** it shows the Apple Music album design with the local tracks; where the
   download button was there are smaller Play and Shuffle buttons beside the existing
   Love and Add to playlist buttons.
2. **Given** any place that opened the old offline album pages, **When** used, **Then**
   it opens this page.

### User Story 3 - Playlists and Loved songs (Priority: P2)

**Acceptance Scenarios**:

1. **Given** a library playlist or Loved songs, **When** opened, **Then** it uses the
   Apple Music playlist design (header, rows) with the library tracks, and keeps every
   offline action it has today: Play, Shuffle, rename, change or remove the cover,
   reorder, remove from playlist or from Loved, export M3U, selection actions and
   delete files.
2. **Given** the Wishlist, **Then** it is unchanged (out of scope).

### User Story 4 - Library and track rows (Priority: P2)

**Acceptance Scenarios**:

1. **Given** the library overview, **Then** the Recently Added shelf is gone; the
   Recently Added sort option stays.
2. **Given** the Songs page, **Then** the Create playlist button and dragging a track
   onto a playlist are gone, and Play and Shuffle buttons sit at the top like in the
   Folders screen.
3. **Given** the Songs page, a folder, a library playlist or Loved songs, **Then** each
   track row shows a small cover, the title, and the artist under it, and nothing else.

### User Story 5 - Offline-only menus and Now Playing (Priority: P2)

**Acceptance Scenarios**:

1. **Given** the three-dot menu of a track on any page in this feature, **Then** it has
   no option that needs the internet (download, quality variant, copy or share link,
   open in other services, Wishlist, re-enrich, network server upload or delete);
   offline options stay (Play, Play next, Add to queue, Add to playlist, Love, Go to
   album, Go to artist, Details, Convert, Delete or Remove from device).
2. **Given** Now Playing, **When** Go to artist is used, **Then** the library artist page
   opens. Love is a heart (filled when loved), everywhere a song is loved; favourite
   artists keep the star.
3. **Given** the Now Playing "..." sheet, **Then** Save offline and Share are gone and
   Remove from device is there, behaving as on the track metadata page.

### User Story 6 - Lyrics (Priority: P3)

**Acceptance Scenarios**:

1. **Given** lyrics in a right-to-left script (Arabic, Hebrew, Persian, Urdu, Syriac,
   Thaana, N'Ko), **Then** every lyric surface (synced, word-by-word, plain, editor,
   share card) aligns and wraps right to left, and the word-by-word highlight sweeps
   from right to left, including wrapped lines, translations and pronunciation lines.
2. **Given** synced lyrics and the new setting on, **Then** Now Playing shows the
   current lyric line (or its translation when translations are shown) under the track
   title, following playback and seeking, honouring right-to-left text (the word-by-word
   highlight and the line transition run right to left) and reduced motion; tapping it opens the lyrics. The setting is in Lyrics settings, translated.

### User Story 7 - Split artist and genre credits (Priority: P1)

**Acceptance Scenarios**:

1. **Given** a song tagged `A, B feat. C`, **Then** A, B and C each appear in Artists
   with that song, and Go to artist offers A, B and C.
2. **Given** `Tyler, The Creator` in Keep together, **Then** it stays one artist.
3. **Given** changed separators in settings, **Then** the library follows without a
   rescan; the Genres page follows the Genre separators.
4. **Given** Edit metadata saves a file, **Then** its row, cover and credits refresh.

### Edge Cases

- Albums without a release date never become Latest Release or the artist image; an
  artist whose albums all lack a date shows no Latest Release and the app's default
  artist placeholder.
- An artist with only featured tracks still opens, showing Popular and Compilations.
- The same track as album artist and featured artist appears only under the artist's
  albums, not in Compilations.
- Network-storage (Subsonic) tracks that are in the library keep working; only the
  server upload/delete menu options go.
- Lyrics that mix right-to-left and left-to-right words keep each line's direction
  from its first strong character.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The library artist page MUST use the Apple Music artist design with
  library data: header image from the newest album by release date, a play button in
  place of download, Latest Release, Popular (most played, filled up at random),
  Albums, Singles & EPs and Compilations (featured tracks). Every other section and
  online action MUST be absent.
- **FR-002**: The old offline artist page (the artist-filtered library album grid) MUST
  be deleted, and every place that opened it MUST open the new page.
- **FR-003**: The library album page MUST use the Apple Music album design with library
  data and Play and Shuffle buttons in place of download, beside Love and Add to
  playlist. `DownloadedAlbumScreen` and `LocalAlbumScreen` MUST be deleted and every
  place that opened them MUST open the new page.
- **FR-004**: Library playlists and Loved songs MUST use the Apple Music playlist design
  with library data and keep all their current offline actions. The Wishlist is
  unchanged.
- **FR-005**: The library overview MUST NOT show the Recently Added shelf.
- **FR-006**: The Songs page MUST NOT offer Create playlist or drag-to-playlist and MUST
  show Play and Shuffle at the top.
- **FR-007**: Track rows on the Songs page, in folders, library playlists and Loved
  songs MUST show a small cover, the title and the artist only.
- **FR-008**: Track menus on the pages of this feature and the Now Playing "..." sheet
  MUST offer only options that work offline; Now Playing MUST add Remove from device.
- **FR-009**: Go to artist from Now Playing and from library track menus MUST open the
  library artist page.
- **FR-010**: Loving a song MUST use a heart everywhere; favourite artists keep a star.
- **FR-011**: Right-to-left lyrics MUST render and animate right to left on every lyric
  surface (port of Mornye #844, adapted to this code base).
- **FR-012**: Now Playing MUST offer the synced lyric preview line with a translated
  on/off setting (port of Mornye #847, adapted), without its unrelated changes (the
  `.gitignore` entry, disabling Android lint, the deleted Material layout).
- **FR-013**: The online pages opened from extension (Apple Music) searches are removed by
  this mission at the owner's request: the artist, album and playlist pages by TOR014, and the
  Home and Search entries that opened them by TOR015.
- **FR-015**: Artist and genre tags MUST be split into credits with the Tag separators
  rules (port of AMWolfstein/Private `new-blur`): configurable artist and genre
  delimiters with chips, All, custom separators and Keep together, applied by one
  splitter; split names live in a derived table, so changing the rules needs no
  rescan. Composer and an Album Artists list are out of scope.
- **FR-016**: The library Artists list MUST include every credited track artist,
  featured artists included; Go to artist MUST open the single credited artist or
  offer a choice when there are several.
- **FR-017**: The save-time "Save with" separators (`metadataSeparators`) MUST be
  removed; saved tags keep names as typed.
- **FR-018**: The library MUST have a Genres page built from the genre credits, each
  genre opening its songs.
- **FR-019**: After Edit metadata saves, the library row and its cover MUST be
  refreshed from the saved file, and a failed index update MUST be reported.
- **FR-020**: The online versions of the converted screens (artist, album, playlist),
  their extension data path, discography download and concerts screens MUST be
  deleted; every link opens the library page or reports that the item is not in the
  library.
- **FR-021**: Home MUST show only library content: Top Picks (library mixes), Recently
  Played, New Releases, On Repeat, Your Top Artists, Recently Added, Your Playlists and
  Genres, in that order; the online feed, online Top Picks, Recently visited and the
  feed setting MUST be deleted.
- **FR-022**: Search MUST search the library only; an artist result MUST show the same
  image as the library artist page.
- **FR-023**: A release MUST be Album, Single or EP by its track total tag (1-3 Single,
  4-6 EP, 7+ Album), not by the tracks owned.
- **FR-014**: Code, strings, tests and assets left without a reader by these changes
  MUST be deleted.

### Key Entities

- **Library artist**: name, albums and singles where they are the album artist,
  featured tracks, newest album by release date, most-played tracks.
- **Library album**: the album key, its tracks, cover, release date.

## Success Criteria *(mandatory)*

- **SC-001**: Opening any artist, album, playlist or Loved songs from the library makes
  no network request.
- **SC-002**: 0 references remain to `DownloadedAlbumScreen`, `LocalAlbumScreen` and the
  artist-filtered library album page.
- **SC-003**: The analyzer reports no issues and the full test suite has no failures.
- **SC-004**: Right-to-left word-by-word lyric tests show the highlight moving from the
  right edge to the left edge of each word.

## Assumptions

- "Online" means anything that needs the internet or a remote service; local files and
  the local network library (Subsonic) tracks already in the library count as offline,
  except server upload/delete.
- Removing the online features, extension pages and Wishlist is the next mission.
- The artist favourite (star) stays as it is.
- No APK build or device test is part of this mission unless the owner asks.
