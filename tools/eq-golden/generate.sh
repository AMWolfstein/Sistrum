#!/usr/bin/env bash
# eq-golden: host-side generator for the native EQ / preamp / stereo-width DSP.
#
# Extracts the EQ code by marker from the native C++ source, compiles it with the
# host compiler into a temporary directory and emits a reproducible golden JSON at
# androidApp/src/test/resources/golden/eq_golden.json.
#
# Usage: tools/eq-golden/generate.sh [--check]
#   (no args)  write the golden JSON
#   --check    regenerate to a temp file and diff against the committed JSON
#
# Environment:
#   CXX                host C++ compiler (default: c++)
#   EQ_GOLDEN_SOURCE   override the native source path (defaults to the repo file)
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(git -C "$script_dir" rev-parse --show-toplevel)"

rel_source="androidApp/src/main/cpp/ffmpeg_player.cpp"
source_path="${EQ_GOLDEN_SOURCE:-$repo_root/$rel_source}"
out_path="$repo_root/androidApp/src/test/resources/golden/eq_golden.json"
harness_in="$script_dir/harness.cpp.in"

check=0
for arg in "$@"; do
    case "$arg" in
        --check) check=1 ;;
        -h|--help)
            echo "usage: tools/eq-golden/generate.sh [--check]"
            exit 0
            ;;
        *)
            echo "eq-golden: unknown argument: $arg" >&2
            exit 2
            ;;
    esac
done

if [ ! -f "$source_path" ]; then
    echo "eq-golden: source not found: $source_path" >&2
    exit 1
fi

commit="$(git -C "$repo_root" log -1 --format=%H -- "$rel_source" || true)"

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

EQ_GOLDEN_COMMIT="$commit" python3 - "$source_path" "$harness_in" "$tmp_dir" <<'PY'
import hashlib
import os
import re
import sys


def die(message):
    sys.stderr.write("eq-golden: " + message + "\n")
    sys.exit(1)


def main():
    source_path, harness_in, tmp_dir = sys.argv[1], sys.argv[2], sys.argv[3]

    with open(source_path, "rb") as handle:
        raw = handle.read()
    lines = raw.decode("utf-8").split("\n")

    def find_one(predicate, marker):
        hits = [i for i, line in enumerate(lines) if predicate(line)]
        if not hits:
            die("marker not found: " + marker)
        if len(hits) > 1:
            die("marker duplicated: " + marker)
        return hits[0]

    def block_range(start):
        depth = 0
        started = False
        for i in range(start, len(lines)):
            for ch in lines[i]:
                if ch == "{":
                    depth += 1
                    started = True
                elif ch == "}":
                    depth -= 1
                    if started and depth == 0:
                        return start, i
        die("unbalanced braces for block starting at line %d" % (start + 1))

    k_eq_bands = find_one(lambda l: re.search(r"constexpr\s+int\s+kEqBands\s*=", l),
                          "constexpr int kEqBands =")
    gdc_span = block_range(find_one(lambda l: "struct GlobalDspConfig {" in l,
                                    "struct GlobalDspConfig {"))
    biquad_span = block_range(find_one(lambda l: "struct Biquad {" in l,
                                       "struct Biquad {"))
    freq_line = find_one(lambda l: "constexpr float kEqFrequenciesHz[kEqBands] =" in l,
                         "constexpr float kEqFrequenciesHz[kEqBands] =")
    configure_span = block_range(find_one(
        lambda l: "void configure_eq(PlaybackEngine &engine, const GlobalDspConfig *config) {" in l,
        "void configure_eq(PlaybackEngine &engine, const GlobalDspConfig *config) {"))
    filter_span = block_range(find_one(
        lambda l: "float filter_sample(float input, float &z1, float &z2, const Biquad &filter) {" in l,
        "float filter_sample(float input, float &z1, float &z2, const Biquad &filter) {"))

    pred_line = find_one(lambda l: re.search(r"^\s*const float preamp =", l),
                         "const float preamp =")
    width_line = find_one(lambda l: re.search(r"^\s*const float width =", l),
                          "const float width =")
    mid_line = find_one(lambda l: re.search(r"^\s*const float mid =", l),
                        "const float mid =")
    side_line = find_one(lambda l: re.search(r"^\s*const float side =", l),
                         "const float side =")

    out_l = "output[i * kOutputChannels] = (mid + side) * preamp * engine.applied_focus_gain;"
    out_r = "output[i * kOutputChannels + 1] = (mid - side) * preamp * engine.applied_focus_gain;"
    find_one(lambda l: out_l in l, out_l)
    find_one(lambda l: out_r in l, out_r)

    structs_code = "\n".join(
        [lines[k_eq_bands]]
        + lines[gdc_span[0]:gdc_span[1] + 1]
        + lines[biquad_span[0]:biquad_span[1] + 1]
    )
    funcs_code = "\n".join(
        [lines[freq_line]]
        + lines[configure_span[0]:configure_span[1] + 1]
        + lines[filter_span[0]:filter_span[1] + 1]
    )
    width_code = "\n".join(lines[i] for i in sorted([pred_line, width_line, mid_line, side_line]))

    with open(harness_in, "r", encoding="utf-8") as handle:
        harness = handle.read()
    for placeholder, code in (
        ("@EXTRACTED_STRUCTS@", structs_code),
        ("@EXTRACTED_CODE@", funcs_code),
        ("@WIDTH_PREAMP_CODE@", width_code),
    ):
        if harness.count(placeholder) != 1:
            die("harness placeholder not found exactly once: " + placeholder)
        harness = harness.replace(placeholder, code)
    with open(os.path.join(tmp_dir, "harness.cpp"), "w", encoding="utf-8") as handle:
        handle.write(harness)

    width_lo = min(pred_line, width_line, mid_line, side_line)
    width_hi = max(pred_line, width_line, mid_line, side_line)
    ranges = {
        "kEqBands": "%d-%d" % (k_eq_bands + 1, k_eq_bands + 1),
        "GlobalDspConfig": "%d-%d" % (gdc_span[0] + 1, gdc_span[1] + 1),
        "Biquad": "%d-%d" % (biquad_span[0] + 1, biquad_span[1] + 1),
        "kEqFrequenciesHz": "%d-%d" % (freq_line + 1, freq_line + 1),
        "configure_eq": "%d-%d" % (configure_span[0] + 1, configure_span[1] + 1),
        "filter_sample": "%d-%d" % (filter_span[0] + 1, filter_span[1] + 1),
        "width_preamp": "%d-%d" % (width_lo + 1, width_hi + 1),
    }
    commit = os.environ.get("EQ_GOLDEN_COMMIT", "")
    sha256 = hashlib.sha256(raw).hexdigest()

    meta = [
        "#pragma once",
        '#define EQ_GOLDEN_SOURCE_FILE "androidApp/src/main/cpp/ffmpeg_player.cpp"',
        '#define EQ_GOLDEN_COMMIT "%s"' % commit,
        '#define EQ_GOLDEN_SHA256 "%s"' % sha256,
        '#define EQ_GOLDEN_RANGE_KEQBANDS "%s"' % ranges["kEqBands"],
        '#define EQ_GOLDEN_RANGE_GLOBALDSPCONFIG "%s"' % ranges["GlobalDspConfig"],
        '#define EQ_GOLDEN_RANGE_BIQUAD "%s"' % ranges["Biquad"],
        '#define EQ_GOLDEN_RANGE_KEQFREQUENCIESHZ "%s"' % ranges["kEqFrequenciesHz"],
        '#define EQ_GOLDEN_RANGE_CONFIGURE_EQ "%s"' % ranges["configure_eq"],
        '#define EQ_GOLDEN_RANGE_FILTER_SAMPLE "%s"' % ranges["filter_sample"],
        '#define EQ_GOLDEN_RANGE_WIDTH_PREAMP "%s"' % ranges["width_preamp"],
        "",
    ]
    with open(os.path.join(tmp_dir, "meta.h"), "w", encoding="utf-8") as handle:
        handle.write("\n".join(meta))


try:
    main()
except SystemExit:
    raise
except Exception as error:  # noqa: BLE001
    die(str(error))
PY

cxx="${CXX:-c++}"
"$cxx" -std=c++17 -O2 -ffp-contract=off -fno-fast-math -I "$tmp_dir" \
    -o "$tmp_dir/eq_golden" "$tmp_dir/harness.cpp"

if [ "$check" -eq 1 ]; then
    "$tmp_dir/eq_golden" > "$tmp_dir/eq_golden.json"
    if diff -q "$out_path" "$tmp_dir/eq_golden.json" > /dev/null 2>&1; then
        echo "eq-golden: golden JSON is up to date"
    else
        echo "eq-golden: committed golden JSON is out of date (run tools/eq-golden/generate.sh)" >&2
        diff -u "$out_path" "$tmp_dir/eq_golden.json" | head -n 40 >&2 || true
        exit 1
    fi
else
    mkdir -p "$(dirname "$out_path")"
    "$tmp_dir/eq_golden" > "$out_path"
    echo "eq-golden: wrote $out_path"
fi
