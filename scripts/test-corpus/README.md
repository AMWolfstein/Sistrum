# Test corpus generator (T006)

Generates the playback migration's test corpus: one file for every format, codec and gain-tag
form in scope, checked against a committed manifest. Spec: spec FR-071 and US9; constitution
Principle 10 (the corpus is built for every listener's library, not the owner's). No audio is
committed to the repository.

## Files

| File | Role |
|---|---|
| `corpus.tsv` | The specification: one row per corpus file. Committed. |
| `generate_corpus.py` | Generator and verifier (Python 3 + mutagen; ffmpeg/ffprobe on PATH). |
| `generate-corpus.sh` | Creates the pinned venv (`mutagen==1.47.0`) if needed, then runs the Python. |
| `push-corpus.sh` | Pushes a generated corpus to `/sdcard/Music/SistrumTestCorpus/` and rescans MediaStore. |
| `corpus.generated.tsv` | Written into the output directory by `--out`; the generated values. Not committed. |

## Usage

```sh
# Generate into a scratch directory (never inside the repository):
bash scripts/test-corpus/generate-corpus.sh --out /tmp/sistrum-corpus

# Verify the generated files against corpus.tsv (exit 1 on any mismatch):
bash scripts/test-corpus/generate-corpus.sh --verify /tmp/sistrum-corpus

# Push to a connected device (exactly one adb device), then rescan MediaStore:
bash scripts/test-corpus/push-corpus.sh /tmp/sistrum-corpus
```

`--out` prints a summary (generated / owner-supplied present / owner-supplied missing / total
size). `--verify` ffprobes every present file against its row, reads the tags back, and checks the
gap between the measured value and `corpus.tsv` is at most ±0.2 dB. Rows with
`source=owner-supplied` that are absent are reported as MISSING and do **not** fail verification.

Defaults: `${XDG_CACHE_HOME:-$HOME/.cache}/sistrum-corpus-venv`; owner-supplied samples come from
`$SISTRUM_REAL_SAMPLES`.

## The manifest

`corpus.tsv` columns (tab-separated):

```
file  format  codec  rate_hz  bits  channels  loudness_target_lufs  tag_forms  source
expected_track_gain_db  expected_album_gain_db  expected_registry_001  notes
```

* `format` / `codec` are the real container and codec (scanner-style labels).
* `tag_forms` is a comma list: `rg_txxx`, `rg_vorbis`, `rg_lowercase`, `rg_reference_loudness`,
  `r128`, `opus_header_gain`, `itunnorm`, `itunes_rg_freeform`, `malformed`, `out_of_range`,
  `non_finite`, `missing_unit`, `none`.
* `source` is `generated` or `owner-supplied`.
* `expected_track_gain_db` / `expected_album_gain_db` are the −18 LUFS-relative effective gains
  the app should use; empty when the file is untagged or the tag is invalid.
* `expected_registry_001` is the 001 Decoder Registry outcome (ADR-002): `platform`,
  `kotlin-aiff`, `skipped:<reason>`, `device-dependent:alac`, `decode-failure-path`.

Expected-gain columns are targets rounded to 0.1 dB. `--out` rewrites them from measurement in
`corpus.generated.tsv`; `--verify` accepts the generated value within ±0.2 dB.

## Never fake a format

Every generated file's container and codec must be what its file name and row claim, verified by
`ffprobe`. If an encoder cannot produce a claimed format, the format is not generated:
APE and DSF/DFF cannot be produced on the host, so those rows are `source=owner-supplied` and
`generate_corpus.py` copies them from `$SISTRUM_REAL_SAMPLES/<file>`; when that directory is
missing the rows are listed as MISSING.

## Gain math

The reference is −18 LUFS. For each tagged track the generator measures integrated loudness `L`
(LUFS) and the sample peak on the *generated audio*, then writes the track gain so that the
effective −18-relative gain is `E = −18 − L`. Each tag form stores `E` in its own reference:

* **ReplayGain** (`REPLAYGAIN_TRACK_GAIN`, `_ALBUM_GAIN`, `_TRACK_PEAK`, `_ALBUM_PEAK`): taken as
  written against −18 LUFS. With `REPLAYGAIN_REFERENCE_LOUDNESS` (here `-14.0 LUFS`), the stored
  tag is `E + (−18 − reference)` so the effective value stays `E`. Peaks are linear (1.0 = full
  scale) from the measured sample peak.
* **Opus R128** (`R128_TRACK_GAIN`, `R128_ALBUM_GAIN`): signed Q7.8 integers relative to −23 LUFS.
  The effective −18-relative gain is `value / 256 + 5`, so the generator stores
  `round((E − 5) × 256)`.
* **Opus header output gain**: the signed Q7.8 `OpusHead` field, patched after tagging with the
  reused `scripts/spikes/opus-header-gain.py` helpers. It is applied by the platform decoder, not
  counted in `expected_track_gain_db`; the header rows carry no `TagGainSource` gain.
* **iTunes Sound Check** (`iTunNORM`, ten 8-hex-digit fields): fields 1 and 2 store
  `round(1000 × 10^(−E/10))` and fields 3 and 4 store `round(2500 × 10^(−E/10))`, written as
  `%08X`; the expected gain is `−10·log10(field1 / 1000)`. `iTunNORM` has no album gain.
* **iTunes freeform ReplayGain** (`----:com.apple.iTunes:replaygain_*`): same rule as ReplayGain.

Precedence when several forms are present is ReplayGain → R128 → Sound Check (FR-047). Malformed
(`abc`) and non-finite (`nan dB`) tags are treated as untagged; an out-of-range value (`+50.00 dB`)
is clamped to the +20 dB sane-range bound; a bare number without a unit is accepted.

## Corpus rules and coverage

* Every generated track is 35 s (the scanner ignores files under 30 s) at one of four loudness
  targets (−10, −14, −20, −24 LUFS) so normalization is measurable. The signal is a stationary
  stereo test-music substitute (four sine partials with slow amplitude modulation plus low-level
  pink noise) held as 32-bit float WAV and encoded per row.
* Gapless albums (`notes` starts with `gapless-album:`) are 3 × 35 s cut sample-exactly from one
  105 s signal — as FLAC (album ReplayGain) and as MP3 (LAME gapless headers).
* WAV MS-ADPCM and IMA-ADPCM reach the registry's decode-failure path: the scan cannot tell the
  inner codec (ADR-002). AIFF-C `fl32`/`ima4` are refused by name by the `kotlin-aiff` provider.

## Push and the owner's device

`push-corpus.sh` copies only audio files to `/sdcard/Music/SistrumTestCorpus/`, deletes only files
inside that folder that are no longer in the corpus, writes no `.nomedia`, and triggers a
MediaStore rescan. It never touches app data, installs or uninstalls anything, or stops an app.

**The folder must be blocklisted in the owner's daily app** (`me.misa198.airmedy.dev`): without it
the corpus simply appears as music in the daily library. The `.qa` test build
(`me.misa198.airmedy.dev.qa`) is the build the corpus is meant to be scanned by.
