# Owner plan, 2026-10-14 (verbatim instructions, queued behind 001 M8)

Received during 001 M8 (T062). Not started while M8 is in progress. After M8 completes: Part 1, then Part 2
immediately, without waiting for the owner. Branch: `feature/media3-migration` (no new branch).

Delegation: as CLAUDE.md says, feature code, fixes and tests go to Codex through /delegate-task (dispatch.sh → codex
exec). The orchestrator only makes trivial fixes itself (imports, typos, package renames, conflict markers, graph
files), and says so in HANDOFF.

## PART 1 — Normalization "Auto" mode (spec 001, final task)

Owner decision (spec change): add a third volume-normalization mode, "Auto", and make it the default for new installs.
Existing users keep their current setting. Append it as a new task at the end of spec 001.

Behaviour:
- Decide per queue item at track start (never mid-track):
  - ALBUM gain when every item in the current queue belongs to the same album (same album id), regardless of shuffle
    or order.
  - Otherwise TRACK gain.
- If the queue changes, re-evaluate only at the next track start; the playing track keeps its gain.
- Same precedence as today for each gain: measured → tags → unity + untagged pre-amp. If album loudness isn't
  available yet for that album, use track gain for that item.
- Crossfade: each player keeps the gain decided at its own track start.
- Track and Album modes keep their current behaviour exactly.

Docs: update spec.md (new FR), tasks.md (new task id), the plan/ADR-005 if it lists the modes, and HANDOFF.

Settings UI: Mode = Auto / Track / Album. Add only the three labels, in English and Arabic (no description line). Keep
ArabicTranslationCompletenessTest passing. Persist with the existing settings mechanism. Migrate safely: an absent
value means Auto for new installs only; existing installs keep their stored mode.

Tests first, as their own commit; the implementation must pass them unmodified (unit tests on gain resolution and
PlaybackCoordinator):
- one album in order → album gain
- one album shuffled → album gain
- a mixed playlist → track gain, including two adjacent tracks that happen to be from the same album
- adding a track from another album to a single-album queue → the next track uses track gain; the playing track is
  unchanged
- removing the other album's tracks → album gain again from the next track
- missing album loudness → track gain for that item
- Track and Album modes unchanged (regression)
- crossfade: the outgoing and incoming players keep their own gains

Afterwards, check whether any M8 measurement could be affected. If so, re-run only that measurement on
com.amwolfstein.sistrum.dev.qa and record it in HANDOFF.

## PART 2 — Spec 002: Kotlin decoders, DSP, scanner, Mood and CUE

Continue on feature/media3-migration right after Part 1 is pushed. origin/port/waxflow and origin/port/analysis are
read-only sources; never push to them.

### Phase A — Import Codex's commits one by one

Source: every commit on origin/port/analysis that is not on feature/media3-migration, oldest first:

    git log --reverse --format=%H \
      $(git merge-base HEAD origin/port/analysis)..origin/port/analysis

Expected today: 46 commits, in this order: codecs (1–28, ending with the CUE parser), :metadata / WaxLabel (29–36),
:analysis / Mood (37–41), :dsp (42–46). Keep exactly this order. Fetch again right before starting and again at the
end of Phase A. If new commits landed on port/analysis in the meantime (for example a Kotlin Opus decoder), import them
the same way, after the others.

Before the first cherry-pick:
- Extend the Graphify scope to every source set of the new modules (codecs/src, metadata/src, analysis/src, dsp/src),
  main AND test, exactly like androidApp and sharedLogic today. Apply the same non-code exclusions (resources,
  including test resources and fixtures, plus build outputs and generated code). Change .graphifyignore and
  .claude/hooks/graph_refresh.py together. Commit this as its own commit and push it.
- Rebuild the external corpora (`bash scripts/waxflow-oracle.sh --fetch-generate` and the other modules' rebuild
  commands). If an oracle toolchain (Go, Rust, native) is missing, install it if that's trivial; otherwise mark those
  parity tests NOT RUN in the import log. Never fake results.

For each Codex commit, in order. Each one stays its own commit; never squash them:
1. `git cherry-pick <sha>`, then bring that commit up to the rebrand inside the same commit:
   - Packages: move every file the commit adds or changes from me/misa198/airmedy/... to com/amwolfstein/sistrum/...
     and rewrite its package and import lines to com.amwolfstein.sistrum.*.
   - Build variant: replace devDebug references (assembleDevDebug, testDevDebugUnitTest, lintDevDebug) in the commit's
     files, scripts and docs with the devQa equivalents.
   - Application IDs: replace me.misa198.airmedy.dev(.qa) with com.amwolfstein.sistrum.dev(.qa) in the commit's
     files. Keep the FfmpegDecoder JNI exception.
   - Conflicts: resolve them properly, keeping both sides' intent (both the migration's change and Codex's change).
     Later commits will touch files that earlier commits already moved. Let git's rename detection apply them to the
     new paths, and check by hand that nothing landed back under me/misa198/airmedy. Never hand-merge graph files;
     regenerate them.
   - Before moving on: `git grep -n "me.misa198.airmedy\|DevDebug"` in the imported modules, scripts and docs must
     return nothing (except the documented FfmpegDecoder exception).
2. Run `python3 .claude/hooks/graph_refresh.py auto`. If the graph changed, stage the two graph files into the same
   commit.
3. Run the memory check, then the full tests of every module imported so far (not only this commit's module, so a
   rename can't silently break an earlier one), plus `./gradlew :androidApp:assembleDevQa`.
4. If everything passes: amend to include the rebrand changes and graph files, and add the trailer
   "Co-authored-by: Claude <noreply@anthropic.com>" next to the existing Codex trailer. Keep the original message and
   author.
5. If something fails: get it fixed within that commit's scope (via /delegate-task unless it is trivial), amend, and
   add the same trailer. Record what failed and how it was fixed.
6. Push after each commit.

Write specs/002-kotlin-decoders/IMPORT-LOG.md: one row per original sha → new sha, with rebrand changes, conflicts
resolved, graph changed (yes/no), tests run with counts, fixes made, and anything NOT RUN with the reason. At the end
of Phase A, also run testDevQaUnitTest and lintDevQa once.

### Phase B — Plan and build 002 (Spec Kit)

Run speckit specify → clarify → plan → tasks → analyze for specs/002-kotlin-decoders, starting from its NOTES.md. Then
implement task by task through /delegate-task → /verify-task → commit → push.

Scope (verify each item against the code before planning). Organise 002 into these milestones, in this order; each one
ends green before the next starts:

M1 — Decoders in playback
- Decoder Registry wiring: Media3 Extractors and audio Renderers for APE, WavPack (including hybrid with a sibling
  .wvc, float, multichannel, DSD), Musepack, ASF/WMA (v1/v2, Lossless, Pro, Voice), AIFF/AIFF-C, DSF/DFF (to PCM; DoP
  stays unwired), MS ADPCM, IMA4, multichannel G.711, and ALAC as a decoder behind Media3's MP4 extractor. Platform
  decoders stay first wherever the device has them.
- Channel layout via :dsp's Media3 layout mapping: fill missing speakers with silence, never play LFE as centre,
  single channel = mono.
- .wvc discovery through MediaStore/SAF, with lossy fallback and a visible flag.
- ReplayGain/R128 for the new formats exposed from their containers (via :metadata) so TagGainSource works for them.
- Offline loudness analysis decodes through the same registry (covers ALAC on devices without a platform decoder). If a
  Kotlin Opus decoder was imported, benchmark it against MediaCodec for analysis and use the faster one.
- Update the skipped-files summary to the new coverage.

M2 — DSP features
- Night mode: wire :dsp's compressor into the playback DSP chain, before the limiter. Setting Off / Light / Medium /
  Strong (default Off), English and Arabic labels. Off must be bit-identical to today.
- Silence-aware crossfade: compute silence spans with :dsp in the offline analysis pass and store them with the
  loudness document. Automatic crossfades start where the outgoing track's trailing silence begins; without data, keep
  the current behaviour.

M3 — Scanner on :metadata
- Replace EmbeddedTagReader with :metadata (pictures METADATA_ONLY during scans, FULL on demand). Keep the "one file
  open per read" rule.
- File tags win over MediaStore; MediaStore becomes the fallback. Keep the folder-name album rule's intent.
- Add the new fields to LocalTrack and the track JSON document: multi-value artists and genres, sort tags (TITLESORT,
  ARTISTSORT, ALBUMSORT, ALBUMARTISTSORT, COMPOSERSORT), TRACKTOTAL, DISCTOTAL, COMPILATION, DISCSUBTITLE,
  ORIGINALDATE, RELEASETYPE, MusicBrainz IDs, ReplayGain/R128, channels, exact duration, codec and profile, picture
  roles (front cover, artist), and synced lyrics (SYLT/LRC). Use them in the UI where an existing screen already shows
  that kind of data (sorting, track "3 of 12", compilations grouped as Various Artists, the artist header from an
  embedded artist picture, synced lyrics in the lyrics screen).
- Bump CurrentMetadataSchemaVersion. Change the Room schema only if SQL sorting or filtering needs a real column, and
  then only with a tested migration.
- Library visibility: verify on com.amwolfstein.sistrum.dev.qa whether MediaStore indexes .ape .wv .mpc .wma .dsf .dff
  .aif and .mka. If it doesn't, add a folder-based discovery path (SAF folder picker) without the all-files permission.
- Scan safety: if a storage root is unavailable (SD card removed), never mark its tracks as deleted (survival gate).
- Parity: on the test corpus and a sample of the owner's library, every field the old reader produced is identical or
  better. Report differences in DECISIONS.md.

M4 — Mood Radio revival
- Run :analysis as a second consumer of the offline analysis decode (same worker, scheduling, checkpoints and charging
  rules), with its own version key so it can run on tracks that already have loudness.
- Store the 12 raw features and compute the corpus percentiles and energy / danceability / brightness / tempo with the
  ported mood maths, written into the existing analysis documents in the shape the read side expects. Recompute
  percentiles in batches when the corpus changes.
- Turn on library_analysis_enabled and verify Mood Radio starts and refills on real data. Extend the analysis progress
  line to cover Mood.

M5 — CUE sheets
- Single-file albums with a .cue (from the :codecs CUE parser) appear as separate virtual tracks in the library, with
  correct start/end, gapless joins between them, per-track tags from the CUE, and correct seeking, queueing,
  scrobbling and play counts.

M6 — Phone performance gate on com.amwolfstein.sistrum.dev.qa (time-boxed: short samples, 3 runs, median, every run
under 10 minutes): DSD256 stereo, APE extra high/insane, WMA Pro, two-player crossfade with Kotlin decoders and night
mode on, a full library scan with :metadata, and the Mood analysis rate. Record CPU, RTF and scan time.

Out of scope for 002: removing FFmpeg and the native engine (spec 003), and writing tags (a future tag editor).

### Decisions without the owner

Whenever there is a choice (in clarify, plan, review or a fix), pick the option you recommend and keep going. Log every
decision in specs/002-kotlin-decoders/DECISIONS.md:

    ## D-<n>: <title>
    Case: what came up and why it matters
    Options: each one with its pros and cons
    Chosen: which one, and why
    Revisit if: what would make me change it

Write it in plain language so the owner can review and override later.

### Reporting and stops

Keep a "Current status" section at the top of specs/002-kotlin-decoders/HANDOFF.md, rewritten (not appended) after
every task: current milestone and task, what's done, what's blocked, the next step, and links to new DECISIONS.md
entries since the last milestone report. Put the detailed per-task log below it, newest first.

For spec 002, don't stop at milestone ends: write the milestone report into HANDOFF and continue. Still stop and wait
for the owner only on:
- anything touching the daily apps com.amwolfstein.sistrum.dev or me.misa198.airmedy.dev (device work only on
  com.amwolfstein.sistrum.dev.qa)
- anything touching main
- a task failing 3 review rounds
- the model quota running out
- the memory rule's 3 failed waits
- a change that would alter spec 001's shipped behaviour or its ADRs (except the silence-aware crossfade above, which
  is approved)

Keep every other CLAUDE.md rule: one commit per task, push after each, memory checks, HANDOFF updated at the end of
each session.
