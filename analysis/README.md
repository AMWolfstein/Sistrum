# Mood analysis

Independent JVM module with no Android, decoder, database, scanner or player
references. Pinned algorithms and corpus reconstruction are documented in
[ORACLE.md](../docs/analysis/ORACLE.md); licence and port changes are in
THIRD-PARTY-NOTICES and SISTRUM-PATCHES.md.

`Mood` computes the locked Airmedy energy, danceability and brightness formulas
from `RawFeatures` and corpus-relative `Percentile` values. Missing percentile
entries use the upstream neutral 0.5 normalization. `computeCorpusPercentiles`
returns the pure sorting/interpolation result and sample count; the caller owns
persistence. It copies columns rather than mutating caller-owned arrays, with
Go's NaN-first ordering preserved.

```
python3 scripts/analysis-oracle/build.py
python3 scripts/analysis-oracle/corpus.py --generate
./gradlew :analysis:testMood
```

The Kotlin suite ports all 17 upstream mood test cases, separating the pure
percentile calculation from repository persistence. Another test compares 1,024
vectors (6,144 scalar comparisons) emitted by the pinned Go code at 1e-12. The
additional edge case checks Go's NaN-first sorting behavior. Reproduce Go vectors:

```
cp scripts/analysis-oracle/mood-parity_test.go "$HOME/.cache/sistrum-analysis-oracle/src-airmedy/internal/app/analysis/mood/sistrum_parity_test.go"
cd "$HOME/.cache/sistrum-analysis-oracle/src-airmedy"
SISTRUM_MOOD_FIXTURES=/absolute/path/to/Sistrum/docs/analysis/mood-oracle.json go test -v ./internal/app/analysis/mood
```
