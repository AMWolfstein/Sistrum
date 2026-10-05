#!/usr/bin/env bash
# Test-corpus generator/verifier for the Media3 migration (task T006).
#
# Creates a pinned virtualenv with mutagen if needed, then runs generate_corpus.py with it:
#
#   scripts/test-corpus/generate-corpus.sh --out DIR
#   scripts/test-corpus/generate-corpus.sh --verify DIR
#
# The corpus and its manifest are described in scripts/test-corpus/README.md. No audio is
# written into the repository; DIR is a scratch directory (e.g. /tmp/sistrum-corpus).
#
# Environment:
#   XDG_CACHE_HOME          venv parent (default: ~/.cache)
#   SISTRUM_REAL_SAMPLES    directory holding owner-supplied real_*.ape/.dsf/.dff samples
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
venv="${XDG_CACHE_HOME:-$HOME/.cache}/sistrum-corpus-venv"
mutagen_pin="mutagen==1.47.0"

command -v python3 >/dev/null 2>&1 || { echo "python3 not found on PATH" >&2; exit 2; }

# Only (re)create the venv when missing or missing its one dependency.
if [[ ! -x "$venv/bin/python" ]] || ! "$venv/bin/python" -c 'import mutagen' >/dev/null 2>&1; then
  echo "setting up $venv ($mutagen_pin) ..." >&2
  python3 -m venv "$venv"
  "$venv/bin/python" -m pip install --quiet --disable-pip-version-check "$mutagen_pin"
fi

exec "$venv/bin/python" "$script_dir/generate_corpus.py" "$@"
