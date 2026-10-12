#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
src="${SISTRUM_WAXFLOW_DIR:-$HOME/.cache/sistrum-waxflow-oracle/oracle-work/src}"
pin=b7857aff88820ad37936026421d1641e64611dbe
[[ "$(git -C "$src" rev-parse HEAD)" == "$pin" ]] || { echo 'Wrong WaxFlow pin' >&2; exit 1; }
[[ -z "$(git -C "$src" status --porcelain)" ]] || { echo 'Dirty WaxFlow source' >&2; exit 1; }
work="$(mktemp -d /tmp/sistrum-dsp-oracle.XXXXXX)"
cp "$root/scripts/dsp-oracle/main.go" "$work/main.go"
cat > "$work/go.mod" <<MOD
module sistrum.local/dsp-oracle
go 1.26
require github.com/colespringer/waxflow v0.0.0
replace github.com/colespringer/waxflow => $src
MOD
cd "$work"
GOWORK=off GOFLAGS=-mod=mod go run . > "$root/docs/dsp/resample-oracle.json"
