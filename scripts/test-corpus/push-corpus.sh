#!/usr/bin/env bash
# Push a generated test corpus to an Android device (task T006).
#
# Usage: scripts/test-corpus/push-corpus.sh DIR
#
# Requires adb with exactly one device. Copies DIR's audio files (never the TSV manifests)
# to /sdcard/Music/SistrumTestCorpus/, deletes only files inside that folder that are not in
# DIR, writes no .nomedia, and asks MediaStore to rescan the external volume. It never touches
# an app's data directory, never installs or uninstalls anything, and never stops an app.
set -euo pipefail

dir="${1:?usage: push-corpus.sh DIR}"
[[ -d "$dir" ]] || { echo "not a directory: $dir" >&2; exit 2; }
command -v adb >/dev/null 2>&1 || { echo "adb not found on PATH" >&2; exit 2; }

mapfile -t devices < <(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
if [[ ${#devices[@]} -ne 1 ]]; then
  echo "expected exactly one adb device, found ${#devices[@]}" >&2
  exit 1
fi
device="${devices[0]}"

dest="/sdcard/Music/SistrumTestCorpus"
adb -s "$device" shell mkdir -p "$dest"

# Local audio files, flat (the generator writes a flat directory).
local_files=()
while IFS= read -r -d '' file; do
  case "${file,,}" in
    *.tsv) continue ;;
    *.mp3|*.flac|*.ogg|*.oga|*.opus|*.m4a|*.mp4|*.wav|*.aiff|*.aif|*.aifc|*.wv|*.wma|*.ape|*.dsf|*.dff)
      local_files+=("$(basename "$file")") ;;
  esac
done < <(find "$dir" -maxdepth 1 -type f -print0)

# Delete remote files that are not in DIR (and only files, never directories or .nomedia).
declare -A wanted=()
for name in "${local_files[@]+"${local_files[@]}"}"; do
  wanted["$name"]=1
done
while IFS= read -r remote; do
  [[ -n "$remote" ]] || continue
  if [[ ! -v wanted["$remote"] ]]; then
    adb -s "$device" shell rm -f "$dest/$remote"
  fi
done < <(adb -s "$device" shell ls -1 "$dest" 2>/dev/null | tr -d '\r')

for name in "${local_files[@]+"${local_files[@]}"}"; do
  adb -s "$device" push "$dir/$name" "$dest/$name" >/dev/null
  echo "pushed $name"
done

# Ask MediaStore to rescan the external volume so the files appear without a reboot.
if ! adb -s "$device" shell content call --uri content://media/ --method scan_volume --arg external_primary >/dev/null 2>&1; then
  echo "warning: MediaStore scan_volume call failed; trigger a rescan manually (the file manager scan or a reboot)" >&2
fi

echo "pushed ${#local_files[@]} file(s) to $dest on $device"
