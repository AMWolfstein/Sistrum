#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Test-only packet extraction through the pinned MIT WaxFlow MP4 demuxer.
# No PCM expectations are generated here; parity uses the committed oracle TSV.
set -euo pipefail
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
corpus="$(realpath "${1:?usage: waxflow-alac-packets.sh <corpus> [output-dir]}")"
work="${SISTRUM_WAXFLOW_DIR:-$HOME/.cache/sistrum/waxflow}"
output="${2:-/tmp/sistrum-waxflow-oracle/alac-packets}"
mkdir -p "$output"
output="$(realpath "$output")"
case "$output/" in "$repo_root/"*) echo 'Packet data must stay outside the repo' >&2; exit 1;; esac
pin="$(sed -n 's/^WAXFLOW_COMMIT="\([^"]*\)"/\1/p' "$repo_root/scripts/waxflow-oracle.sh")"
test "$(git -C "$work/src" rev-parse HEAD)" = "$pin"
helper="$work/alac-packet-helper"
mkdir -p "$helper"
cat > "$helper/go.mod" <<GO
module sistrum-alac-packets

go 1.26

require github.com/colespringer/waxflow v0.0.0
replace github.com/colespringer/waxflow => $work/src
GO
cat > "$helper/main.go" <<'GO'
package main

import (
    "crypto/sha256"
    "encoding/binary"
    "fmt"
    "io"
    "os"
    "path/filepath"
    "strings"
    "github.com/colespringer/waxflow/codec"
    "github.com/colespringer/waxflow/container"
    "github.com/colespringer/waxflow/container/mp4"
)
func main() {
    err := filepath.WalkDir(os.Args[1],func(path string,entry os.DirEntry,walkErr error) error {
        if walkErr != nil { return walkErr }
        if entry.IsDir() || !(strings.HasSuffix(path,".m4a") || strings.HasSuffix(path,".m4b")) { return nil }
        raw,err:=os.ReadFile(path); if err!=nil { return err }
        demux,err:=mp4.NewDemuxer(container.BytesSource(raw),nil); if err!=nil { return err }
        track:=demux.Tracks()[0]; if track.Codec!=codec.ALAC { return nil }
        rel,err:=filepath.Rel(os.Args[1],path); if err!=nil { return err }
        dest:=filepath.Join(os.Args[2],rel+".packets")
        if err=os.MkdirAll(filepath.Dir(dest),0755); err!=nil { return err }
        file,err:=os.Create(dest); if err!=nil { return err }; defer file.Close()
        // Header: source hash, cookie length/cookie, total samples, then records
        // of PTS(int64), duration(int64), size(uint32), immutable packet bytes.
        hash:=sha256.Sum256(raw); file.Write(hash[:])
        binary.Write(file,binary.LittleEndian,uint32(len(track.CodecConfig))); file.Write(track.CodecConfig)
        binary.Write(file,binary.LittleEndian,track.Samples)
        var packet container.Packet; count:=0
        for {
            err=demux.ReadPacket(&packet)
            if err==io.EOF { break }; if err!=nil { return err }
            binary.Write(file,binary.LittleEndian,packet.PTS)
            binary.Write(file,binary.LittleEndian,packet.Dur)
            binary.Write(file,binary.LittleEndian,uint32(len(packet.Data)))
            if _,err=file.Write(packet.Data); err!=nil { return err }; count++
        }
        fmt.Printf("%s: %d ALAC packets\n",rel,count); return file.Close()
    })
    if err!=nil { panic(err) }
}
GO
(cd "$helper" && "${GO:-go}" run -mod=mod . "$corpus" "$output")
