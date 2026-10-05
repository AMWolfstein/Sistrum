#!/usr/bin/env bash
# WaxFlow test oracle for feature 002 (Kotlin decoder ports). See docs/waxflow/ORACLE.md.
#
# Clones the owner's WaxFlow fork at the pinned commit OUTSIDE this repository, builds its CLI and a
# small loudness helper, decodes every file of a corpus directory, and writes one small fixtures file:
# per file, the SHA-256 of the decoded PCM and the BS.1770 / EBU Tech 3342 loudness numbers.
# No audio is written into the repository.
#
# Usage: scripts/waxflow-oracle.sh <corpus-dir> [fixtures-file]
#   corpus-dir     local copy of the test corpus (e.g. pulled from /sdcard/Music/SistrumTestCorpus)
#   fixtures-file  default: androidApp/src/test/resources/waxflow/oracle-fixtures.tsv
# Environment:
#   SISTRUM_WAXFLOW_DIR  where the fork is cloned (default: ${XDG_CACHE_HOME:-~/.cache}/sistrum/waxflow)
#   GO                   go binary to use (default: go on PATH); needs the Go version WaxFlow's go.mod asks for
set -euo pipefail

# The pin. Bump it only through the procedure in docs/waxflow/ORACLE.md.
WAXFLOW_REPO="https://github.com/AMWolfstein/WaxFlow.git"
WAXFLOW_COMMIT="446ca3124d890fd08caecdcbf8931a493d4ebd04"

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
corpus="${1:?usage: scripts/waxflow-oracle.sh <corpus-dir> [fixtures-file]}"
fixtures="${2:-$repo_root/androidApp/src/test/resources/waxflow/oracle-fixtures.tsv}"
work="${SISTRUM_WAXFLOW_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/sistrum/waxflow}"
go_bin="${GO:-go}"

[[ -d "$corpus" ]] || { echo "corpus directory not found: $corpus" >&2; exit 2; }
corpus="$(cd "$corpus" && pwd)"
work="$(realpath -m "$work")"
case "$work/" in
  "$repo_root"/*) echo "SISTRUM_WAXFLOW_DIR must be outside the repository: $work" >&2; exit 2 ;;
esac
mkdir -p "$work"
command -v "$go_bin" >/dev/null || { echo "Go not found (set GO=/path/to/go); WaxFlow needs the version in its go.mod" >&2; exit 2; }
command -v python3 >/dev/null || { echo "python3 not found" >&2; exit 2; }

src="$work/src"
if [[ ! -d "$src/.git" ]]; then
  git clone --quiet "$WAXFLOW_REPO" "$src"
fi
git -C "$src" fetch --quiet origin
git -C "$src" checkout --quiet --detach "$WAXFLOW_COMMIT"
if [[ -n "$(git -C "$src" status --porcelain --untracked-files=no)" ]]; then
  echo "the WaxFlow clone at $src has local changes; refusing to build an unpinned oracle" >&2; exit 2
fi
upstream_note="$(git -C "$src" log -1 --format='%H %cs %s')"

# The CLI decodes; the helper measures loudness through the same engine (waxflow.Engine.Analyze), which the
# CLI only exposes as a gain inside transcode. The helper lives in the work dir, never in Sistrum.
bin="$work/bin"
mkdir -p "$bin" "$work/helper"
(cd "$src/cli" && CGO_ENABLED=0 "$go_bin" build -trimpath -o "$bin/waxflow" ./cmd/waxflow)
cat > "$work/helper/go.mod" <<EOF
module sistrum.local/waxflow-loudness

go 1.26

require github.com/colespringer/waxflow v0.0.0
replace github.com/colespringer/waxflow => $src
EOF
cat > "$work/helper/main.go" <<'EOF'
// Prints one tab-separated line: frames rate channels integrated_lufs lra_lu true_peak_dbtp sample_peak_dbfs
package main

import (
	"context"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/colespringer/waxflow"
	"github.com/colespringer/waxflow/container"
)

func main() {
	path := os.Args[1]
	f, err := os.Open(path)
	if err != nil {
		fail(err)
	}
	defer f.Close()
	src, err := container.FileSource(f)
	if err != nil {
		fail(err)
	}
	hint := strings.TrimPrefix(strings.ToLower(filepath.Ext(path)), ".")
	res, err := waxflow.New().Analyze(context.Background(), src, hint, waxflow.AnalyzeOptions{})
	if err != nil {
		fail(err)
	}
	fmt.Printf("%d\t%d\t%d\t%.4f\t%.4f\t%.4f\t%.4f\n", res.Samples, res.Format.Rate, res.Format.Channels,
		res.IntegratedLUFS, res.LoudnessRange, res.TruePeakDB, res.SamplePeakDB)
}

func fail(err error) {
	fmt.Fprintln(os.Stderr, err)
	os.Exit(1)
}
EOF
(cd "$work/helper" && GOFLAGS=-mod=mod "$go_bin" mod tidy >/dev/null 2>&1 || true
 cd "$work/helper" && CGO_ENABLED=0 GOFLAGS=-mod=mod "$go_bin" build -trimpath -o "$bin/waxflow-loudness" .)

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$(dirname "$fixtures")"

python3 - "$corpus" "$fixtures" "$bin" "$tmp" "$WAXFLOW_COMMIT" "$upstream_note" <<'EOF'
import hashlib, os, struct, subprocess, sys

corpus, fixtures, bindir, tmp, commit, note = sys.argv[1:7]
waxflow = os.path.join(bindir, "waxflow")
loudness = os.path.join(bindir, "waxflow-loudness")

def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()

def wav_data(path):
    """Returns (bits, format_tag, sha256 of the data chunk). RIFF/WAVE or RF64 as WaxFlow writes it."""
    with open(path, "rb") as f:
        head = f.read(12)
        if head[8:12] != b"WAVE":
            raise ValueError("decoded output is not WAVE")
        bits = tag = None
        big_data = None
        while True:
            chunk = f.read(8)
            if len(chunk) < 8:
                raise ValueError("no data chunk")
            cid, size = chunk[:4], struct.unpack("<I", chunk[4:])[0]
            if cid == b"ds64":
                body = f.read(size)
                big_data = struct.unpack("<Q", body[8:16])[0]
                size = 0
            elif cid == b"fmt ":
                body = f.read(size)
                tag, bits = struct.unpack("<H", body[:2])[0], struct.unpack("<H", body[14:16])[0]
                size = 0
            elif cid == b"data":
                if size == 0xFFFFFFFF and big_data is not None:
                    size = big_data
                h = hashlib.sha256()
                left = size
                while left:
                    block = f.read(min(left, 1 << 20))
                    if not block:
                        raise ValueError("truncated data chunk")
                    h.update(block)
                    left -= len(block)
                return bits, tag, h.hexdigest()
            f.seek(size + (size & 1), 1)

rows = []
for root, dirs, files in os.walk(corpus):
    dirs.sort()
    for name in sorted(files):
        if name.startswith("."):
            continue
        path = os.path.join(root, name)
        rel = os.path.relpath(path, corpus)
        row = {"file": rel, "file_sha256": sha256_file(path), "status": "ok"}
        out = os.path.join(tmp, "out.wav")
        dec = subprocess.run([waxflow, "transcode", "--force", "--no-tags", "--format", "wav", path, out],
                             capture_output=True, text=True)
        if dec.returncode != 0:
            row["status"] = "refused: " + " ".join(dec.stderr.strip().splitlines()[-1:]).replace("\t", " ")
            rows.append(row)
            continue
        bits, tag, pcm = wav_data(out)
        os.remove(out)
        meas = subprocess.run([loudness, path], capture_output=True, text=True)
        if meas.returncode != 0:
            row["status"] = "loudness failed: " + meas.stderr.strip().replace("\t", " ")
            rows.append(row)
            continue
        frames, rate, ch, lufs, lra, tp, sp = meas.stdout.split()
        row.update(frames=frames, rate=rate, channels=ch, bits=str(bits),
                   sample_format="float" if tag == 3 else "int", pcm_sha256=pcm,
                   integrated_lufs=lufs, lra_lu=lra, true_peak_dbtp=tp, sample_peak_dbfs=sp)
        rows.append(row)

cols = ["file", "file_sha256", "status", "frames", "rate", "channels", "bits", "sample_format",
        "pcm_sha256", "integrated_lufs", "lra_lu", "true_peak_dbtp", "sample_peak_dbfs"]
with open(fixtures, "w") as out:
    out.write("# WaxFlow oracle fixtures. Generated by scripts/waxflow-oracle.sh; do not edit by hand.\n")
    out.write(f"# waxflow_fork_commit\t{commit}\n")
    out.write(f"# waxflow_commit_info\t{note}\n")
    out.write("# pcm_sha256: SHA-256 of the WAV data chunk WaxFlow writes at the source rate, channels and depth\n")
    out.write("#   (interleaved little-endian; 8-bit unsigned, 16/24/32-bit signed packed, float as IEEE).\n")
    out.write("\t".join(cols) + "\n")
    for row in rows:
        out.write("\t".join(row.get(c, "") for c in cols) + "\n")
ok = sum(1 for r in rows if r["status"] == "ok")
print(f"{len(rows)} files, {ok} decoded, {len(rows) - ok} refused or failed -> {fixtures}")
EOF
