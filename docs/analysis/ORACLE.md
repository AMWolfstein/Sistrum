# Analysis source pins and desktop oracle

The production `:analysis` module is pure JVM. The desktop cgo harness is only
an oracle and is never a dependency of the application or the module.

| Source | Pin | Archive SHA-256 |
| --- | --- | --- |
| [Airmedy](https://github.com/misa198/airmedy/tree/7d3a08d0eaa2c88d3518f19665eaffc4762dd6a1) | `7d3a08d0eaa2c88d3518f19665eaffc4762dd6a1` | `5b89ef1c11d60163f8f372fdb53a0753cf4986b6881d46655083867621c5f467` |
| [FFmpeg](https://ffmpeg.org/releases/ffmpeg-8.1.2.tar.gz) | 8.1.2, as selected by pinned Airmedy | `32faba5ef67340d54724941eae1425580791195312a4fd13bf6f820a2818bf22` |
| [aubio](https://files.pythonhosted.org/packages/cd/80/302d89240603e5347c7f8026c8b02c59f8dfaec66c91a743d82de7c86006/aubio-0.4.9.tar.gz) | 0.4.9, as selected by pinned Airmedy | `df1244f6c4cf5bea382c8c2d35aa43bc31f4cf631fe325ae3992c219546a4202` |
| [FFTW](https://www.fftw.org/fftw-3.3.10.tar.gz) | 3.3.10, single precision, as selected by pinned Airmedy | `56c932549852cddcfafdab3820b0200c7742675be92179e59e6215b340e26467` |

## Licence decision (owner, 2026-10-12)

aubio 0.4.9 is **GPL-3.0-or-later**, per its source headers (including
`src/tempo/tempo.c`) and GPLv3 `COPYING`. The checksum above was verified.
[Airmedy's NOTICES](https://github.com/misa198/airmedy/blob/7d3a08d0eaa2c88d3518f19665eaffc4762dd6a1/NOTICES#L48-L56)
incorrectly labels aubio GPL-2.0-or-later. This is an upstream labelling error;
aubio's own licence governs. The owner authorized continuing under GPL-3.0.
FFmpeg's ported sources are LGPL-2.1-or-later. FFTW is GPL-2.0-or-later and
is linked only into the external oracle; its implementation is not ported.

## Reproduce

```
python3 scripts/analysis-oracle/build.py
python3 scripts/analysis-oracle/corpus.py --generate
python3 scripts/analysis-oracle/corpus.py --verify
```

`SISTRUM_ANALYSIS_ORACLE_DIR` defaults to `~/.cache/sistrum-analysis-oracle`.
All source archives, extracted source, native builds, harness binaries and audio
live there. Owned signals and decoded PCM are in `owned/`; no audio goes in git.
The codec input cache is `SISTRUM_ORACLE_DIR`, defaulting to
`~/.cache/sistrum-waxflow-oracle`. Its existing manifests govern input ownership
and licences, including the local-only WavPack suite. Decoding does not grant a
new licence to those inputs. The analysis manifest records source and PCM hashes.
Missing or modified inputs fail verification; tests never generate replacements.

The harness copies the pinned analyzer header and shared mutex unchanged into
an external cgo package. It runs all feature components and emits the twelve
`FFAnalysisResult` fields as one JSON object per file. Non-finite values use
`"NaN"`, `"+Infinity"` and `"-Infinity"`, because JSON has no numeric equivalents.
The harness uses the exact native header; the Go transport omits app/domain
integration, timestamps and schema version. FFmpeg is built with extra decoders
and WAV output for corpus preparation; the filter graph and aubio algorithms
are unchanged. aubio uses FFTW3F, matching the Airmedy build configuration.
The only aubio build patch replaces Python's removed `imp` and `rU` interfaces
in Waf; it changes no DSP source.

## Definitions and scope

The chain is `ebur128=metadata=1:peak=true,aspectralstats=measure=centroid+rolloff+flatness+flux,astats=metadata=1:reset=0`.
Crest, ZCR and spectral descriptors use channel 1; overall RMS combines channels.
Spectral analysis uses 2048-point Hann windows with 1024-sample hops, starting
with a zero-filled previous half-window, and averages every emitted frame.
The descriptor input is FFT magnitude, scaled by 1/2048, excluding Nyquist.
aubio tempo uses `default`, onset uses `hfc`, both 1024/512 at 44100 Hz. Resampling
and default channel downmix are libswresample's; tempo is the mean of positive
per-beat BPM estimates. Onset variance is Welford sample variance of intervals
in samples, ignoring warm-up hits before 1024 samples. No resampler drain or
partial aubio hop is processed at EOF. Octave estimates are preserved.

This follows `analyzer-future.md`'s independent Kotlin Mood feature intent. The
owner's explicit aubio parity requirement supersedes that document's proposed
BeatRoot implementation. There is no app, scanner, player, database or worker
integration. Spec 001 M6's separate loudness port overlaps; see
`analysis/SISTRUM-PATCHES.md` for future unification.

## Initial corpus inventory

444 inventoried inputs: 41 generated signals and 403 existing codec-cache files,
including separate owned DSD and WavPack vectors. 368 return all twelve features.
72 inputs are refused by the pinned FFmpeg decode command; four initialization-only
HLS MP4 fragments decode to empty PCM and the analyzer returns `FFA_ERR_PROCESS`.
Each refusal and its diagnostic is recorded in `corpus-manifest.json`; there is
no fabricated feature result. One source is an auxiliary JSON metadata file and
several are WavPack correction streams rather than standalone audio.

All decoded WAVs use a canonical 44-byte float header without a channel mask.
PCM samples, rates and channel counts are retained; FFmpeg therefore selects its
default layout for the count, matching the PCM API. Source-specific alternative
speaker layouts cannot be represented by this API. Input/source hashes are
preserved so this choice can be audited.
