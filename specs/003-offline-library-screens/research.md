# Research: Offline library detail screens

## R1. One page, two sources

- **Decision**: The library opens the existing `ArtistScreen`, `AlbumScreen` and
  `PlaylistScreen` with data from the library (a library source/flag), instead of
  copying them; online-only widgets are hidden in that mode.
- **Rationale**: the owner wants the Apple Music design; the extension pages must keep
  working until the next mission (FR-013), which then deletes the online mode.

## R2. Artist data from the library

- Albums and Singles: albums whose album artist (fallback track artist) is the artist;
  single/EP by the existing release-type rules (track count when no type is stored).
- Latest Release and header image: newest album by release date only; undated albums
  never qualify (owner).
- Popular: the artist's tracks ordered by plays in listening statistics, then random
  tracks of the artist to fill the section (owner: most played plus random).
- Compilations: tracks where the artist is one of the split track artists
  (`splitArtistNames`) but not the album artist (owner: this is the Compilations
  section, holding those songs).
- Favourite artist keeps its star (owner).

## R3. Album page in library mode

- Play and Shuffle reuse `AlbumPlayActions` in a compact size next to Love and Add to
  playlist; download goes. Downloaded and scanned albums both load by album key.

## R4. Playlists and Loved songs

- **Decision**: the `PlaylistScreen` look with every offline action of
  `LibraryTracksFolderScreen` moved into it (owner accepted the proposal). Wishlist
  stays on `LibraryTracksFolderScreen` untouched (owner: out of scope).

## R5. Offline menu rule

- Removed: download, quality variant, copy/share link, open in other services,
  Wishlist, re-enrich, network server upload/delete, Save offline, Share (link).
- Kept: play, play next, add to queue, add to playlist, love, go to album/artist,
  details, convert, delete/remove from device, lyrics share image, equalizer, sleep
  timer, volume, external player.

## R6. Mornye ports

- #844: hand port; skip `desktop_mini_player.dart`; keep its tests and extend them to
  wrapped RTL lines, translations and pronunciation lines.
- #847: port the preview widget, setting and switch with l10n keys in all 21 ARB
  files; use #844's direction and right-to-left sweep in the preview; honour reduced
  motion; drop the `.gitignore` line, the lint switch-off and the Material layout edit.

## R7. Tag separators from AMWolfstein/Private (`new-blur`)

- **Source**: `docs/TAG_CREDITS_PLAN.md`; final state after `6a8c363a` (every artist
  chip selected by default, `&` included; Save with removed) and `49397af5` (genres
  from credits). Artist chips `;` `,` `،` `feat.` `ft.` `featuring` `/` `&` `x`
  `with`; genre chips `;` `،` `,` `/` `&`, defaults `;` `،`.
- **SQL**: no change to the `library` table; a TEMP table `library_tag_credits`
  (field, name, name_key, item_id; indexes on (field, name_key) and item_id) rebuilt
  when the library version or the rules change, keys normalised like the existing
  lookups.
- **Owner decisions**: port all of Tag separators and its settings; add a library
  Genres page; remove Save with; port the metadata index reconciler (`148e103b`);
  no composer; no Album Artists list.
- **Adaptation**: Private has a different layout (`lib/core`, `lib/services/library/
  browse`); the port is by hand onto this tree's `library_database`/`library_queue_store`.
