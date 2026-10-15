# Artwork, portraits and motion design

**VERIFIED IN SOURCE:** [source map](../01-reference-source-map.md) S18/S19, X02–X04, R02–R04 and E04–E06. Apple source `getAlbum/getArtist/motionArtworkVideoURL` at X `0290b263802d3418e45583612574b749289bf369` exposes album/artist HLS motion and preview fields. No universal availability is implied. Rhythm R `6f26f963b82ed592ec28f05824bb7f12b65b82ea` uses a separate muted ExoPlayer with focus handling false; its token retrieval scrapes web bundles and has a fallback. Reuse design concepts, not credentials or assumptions of authorization.

## PROPOSED retrieval and matching

`ArtworkRepository` observes local entity + provider-match record. Static resolution: manual override first (reuse existing artist staging), preferred enabled approved provider cache, alternate approved provider, existing local album/embedded image, placeholder. Default artist provider order is YouTube Music, Deezer, local; users may reorder/disable, refresh an entity, pick a correct remote match or set/remove a manual image. Never replace an existing manual override by automatic refresh. Portrait source and entity kind remain visible in provenance.

Match artist IDs from an approved provider lookup using normalized name plus album/track collaborators and external IDs where available; tolerate transliteration as a candidate, not proof. Album match also uses artist, edition, year, track identifiers/duration when available. Homonyms, tribute artists, compilations and featured artists get explicit ambiguity states. Persist confirmed matches separately from file tags; changing tags invalidates automatic matches. Failures never attach a different artist's photo silently.

Motion descriptor: provider/origin/hash, matched entity ID/kind, rendition URL, static preview, expiry/validators, aspect ratio and permitted retention. Validate URLs at fetch and each HLS playlist/variant/key/segment redirect, not just initial m3u8. Deny DRM/protected/unknown encryption or credentials outside approved scope; no bypass. Select one bounded video rendition, validate codec/container/dimensions/duration, and never pass arbitrary media URLs into music playback. If caching HLS is not feasible/legal, static fallback is truthful; remote stream availability is not offline availability.

## PROPOSED rendering contract

One independent UI-owned motion player, outside music service/MediaSession/Media3PlayerFactory. Disable audio track selection/rendering, volume 0, no audio focus, repeat-one, no noisy receiver/session publication, no shared music AudioSink/session/DSP. Muting alone is insufficient assurance. UI starts only when asset is ready, surface substantially visible, activity STARTED/resumed as appropriate, screen on, Reduce Motion off and power/data policy allows. Pause when covered/scrolled away/background/paused by policy; release decoder/surface on disposal and low-memory. Re-entry may resume only while current entity/request generation matches.

Prefer one visible motion surface; maintain static preview until first frame, crossfade visual layers without triggering audio commands, bound buffers and retries. Cache and decoded image quotas follow architecture doc. Initial policy: no metered motion prefetch, no battery-saver motion, no background decode; reduced-motion and device-effect fallback use static art. Users can disable motion globally. Accessibility labels describe album/artist, not a decorative video.

Instrument no interference: music position/queue/gain/session/focus/listening counters unchanged on motion create/error/close, even when video contains an audio track. Test background audio continuing while motion releases; memory returns near baseline, no active decoder in background, corrupt video/expired HLS/static cache/offline cases. Performance budget proposals: at most one video decoder and 60 Hz p95 UI frame ≤16.7 ms on target class; compare to baseline on low-end devices and report actual deltas. Do not assert these measured.

## UNKNOWN / NEEDS VALIDATION / BLOCKED

Apple terms restrict MusicKit content use/download; offline motion caching and independent local-library display require explicit authorization, not a scraped token. Apple access/cache, YouTube Music undocumented endpoint access and Deezer rights remain BLOCKED for shipping pending review. Synthetic/local licensed motion assets allow isolated renderer tests without provider clearance. Runtime package execution has its own gate and is not replaced by copying Rhythm's backend.
