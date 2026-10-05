#!/usr/bin/env python3
"""Test-corpus generator and verifier for the Media3 migration (task T006).

Spec: specs/001-media3-migration/spec.md FR-071 (and US9, Principle 10). The committed
manifest `corpus.tsv` is the specification of every corpus file; this script makes the
manifest real and checks it again.

    generate_corpus.py --out DIR     generate every `source=generated` row into DIR,
                                     copy `owner-supplied` rows from $SISTRUM_REAL_SAMPLES
                                     when present, and write DIR/corpus.generated.tsv
    generate_corpus.py --verify DIR  ffprobe every present file against its row, read the
                                     tags back (mutagen; the reused Opus header reader),
                                     and check the measured gains against corpus.tsv

Rules implemented here (see README.md for the full text):
  * Never fake a format: each file is encoded to the container/codec its row claims and
    `--verify` re-checks it with ffprobe.
  * Every generated track is 35 s (the app's scan ignores files under 30 s) unless the row
    is part of a gapless album (3 x 35 s cut sample-exactly from one 105 s signal).
  * Gains are measured, not assumed: integrated loudness L (LUFS, ffmpeg ebur128) and the
    sample peak (volumedetect) are measured on the encoded audio, then the track gain is
    written so that its -18 LUFS-relative effective value is -18 - L.
  * Expected-gain columns in the committed corpus.tsv are targets rounded to 0.1 dB;
    --verify accepts the generated value within +/-0.2 dB.

Requires Python 3 (stdlib) plus mutagen; the pinned venv is created by generate-corpus.sh.
"""

from __future__ import annotations

import argparse
import importlib.util
import json
import math
import os
import re
import shutil
import struct
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO_ROOT = HERE.parents[1]
DEFAULT_MANIFEST = HERE / "corpus.tsv"
OPUS_SPIKE = REPO_ROOT / "scripts" / "spikes" / "opus-header-gain.py"

COLUMNS = [
    "file", "format", "codec", "rate_hz", "bits", "channels", "loudness_target_lufs",
    "tag_forms", "source", "expected_track_gain_db", "expected_album_gain_db",
    "expected_registry_001", "notes",
]

TARGET_DB_TOLERANCE = 0.2
TRACK_SECONDS = 35
ALBUM_SECONDS = 3 * TRACK_SECONDS

# ffprobe's container name for each scanner-style format label.
CONTAINER_NAME = {
    "mp3": "mp3", "flac": "flac", "ogg": "ogg", "opus": "ogg", "m4a": "mov,mp4,m4a,3gp,3g2,mj2",
    "wav": "wav", "aiff": "aiff", "aifc": "aiff", "wv": "wv", "wma": "asf",
    "ape": "ape", "dsf": "dsf", "dff": "dff",
}
AUDIO_SUFFIXES = {
    ".mp3", ".flac", ".ogg", ".oga", ".opus", ".m4a", ".mp4", ".wav", ".aiff", ".aif",
    ".aifc", ".wv", ".wma", ".ape", ".dsf", ".dff",
}

REPLAYGAIN_KEYS = {
    "REPLAYGAIN_TRACK_GAIN", "REPLAYGAIN_ALBUM_GAIN", "REPLAYGAIN_TRACK_PEAK",
    "REPLAYGAIN_ALBUM_PEAK", "REPLAYGAIN_REFERENCE_LOUDNESS",
}

# ---- base signal ---------------------------------------------------------------
# A stereo test-music substitute: four sine partials with a slow amplitude modulation
# plus low-level pink noise. Stationary enough that integrated loudness is stable.

PARTIALS = "0.28*sin(2*PI*220*t)+0.20*sin(2*PI*330*t)+0.16*sin(2*PI*440*t)+0.10*sin(2*PI*660*t)"
MODULATION = "0.65+0.35*sin(2*PI*0.13*t)"
BASE_EXPR = f"({PARTIALS})*({MODULATION})"
PINK_SEED = 7


def run(command: list[str], **kwargs) -> subprocess.CompletedProcess:
    return subprocess.run(command, capture_output=True, text=True, **kwargs)


def fail(message: str) -> None:
    raise SystemExit(f"error: {message}")


def load_opus_spike():
    """Import scripts/spikes/opus-header-gain.py (read-only) for its gain helpers."""
    spec = importlib.util.spec_from_file_location("opus_header_gain", OPUS_SPIKE)
    if spec is None or spec.loader is None:
        fail(f"cannot import {OPUS_SPIKE}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


# ---- manifest -----------------------------------------------------------------

def read_manifest(path: Path) -> list[dict]:
    if not path.is_file():
        fail(f"manifest not found: {path}")
    rows: list[dict] = []
    with path.open(encoding="utf-8") as handle:
        header = handle.readline().rstrip("\n").split("\t")
        if header != COLUMNS:
            fail(f"{path}: unexpected header {header!r}")
        for line_number, line in enumerate(handle, start=2):
            line = line.rstrip("\n")
            if not line or line.startswith("#"):
                continue
            values = line.split("\t")
            if len(values) != len(COLUMNS):
                fail(f"{path}:{line_number}: expected {len(COLUMNS)} columns, got {len(values)}")
            rows.append(dict(zip(COLUMNS, values)))
    return rows


def write_manifest(path: Path, rows: list[dict]) -> None:
    with path.open("w", encoding="utf-8") as handle:
        handle.write("\t".join(COLUMNS) + "\n")
        for row in rows:
            handle.write("\t".join(row.get(column, "") for column in COLUMNS) + "\n")


def as_int(value: str) -> int | None:
    value = value.strip()
    return int(value) if value else None


# ---- ffmpeg helpers -----------------------------------------------------------

def require_tools() -> None:
    for tool in ("ffmpeg", "ffprobe"):
        if not shutil.which(tool):
            fail(f"{tool} not found on PATH")


def base_signal(rate: int, scratch: Path, duration: int = TRACK_SECONDS) -> tuple[Path, float]:
    """Generate a `duration`-second base at `rate` (32-bit float WAV); return (path, loudness)."""
    path = scratch / f"base_{rate}_{duration}.wav"
    if not path.is_file():
        command = [
            "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
            "-f", "lavfi", "-i", f"aevalsrc={BASE_EXPR}:s={rate}:d={duration}:c=stereo",
            "-f", "lavfi", "-i",
            f"anoisesrc=color=pink:seed={PINK_SEED}:sample_rate={rate}:duration={duration}:amplitude=0.008",
            "-filter_complex", "[0:a][1:a]amix=inputs=2:normalize=0:duration=longest[out]",
            "-map", "[out]", "-c:a", "pcm_f32le", str(path),
        ]
        result = run(command)
        if result.returncode != 0:
            fail(f"base signal {rate} Hz failed:\n{result.stderr.strip()}")
    loudness, _ = measure(path)
    return path, loudness


def measure(path: Path) -> tuple[float, float]:
    """Return (integrated loudness LUFS, sample peak dBFS) for `path`."""
    ebur = run(["ffmpeg", "-hide_banner", "-nostats", "-i", str(path), "-af", "ebur128=peak=true", "-f", "null", "-"])
    text = ebur.stdout + ebur.stderr
    loudness_match = re.findall(r"\bI:\s*(-?\d+(?:\.\d+)?)\s*LUFS", text)
    peak_match = re.search(r"Peak:\s*(-?\d+(?:\.\d+)?)\s*dBFS", text)
    if not loudness_match:
        fail(f"could not measure loudness of {path.name}")
    vol = run(["ffmpeg", "-hide_banner", "-nostats", "-i", str(path), "-af", "volumedetect", "-f", "null", "-"])
    sample_match = re.search(r"max_volume:\s*(-?\d+(?:\.\d+)?)\s*dB", vol.stdout + vol.stderr)
    if not sample_match:
        fail(f"could not measure sample peak of {path.name}")
    loudness = float(loudness_match[-1])
    sample_peak = float(sample_match.group(1))
    _ = peak_match
    return loudness, sample_peak


def encode(row: dict, base: Path, volume_db: float, out: Path, scratch: Path) -> None:
    """Encode `base` (scaled by `volume_db`) into `out` in the row's real format/codec."""
    fmt, codec = row["format"], row["codec"]
    rate = as_int(row["rate_hz"])
    channels = as_int(row["channels"])
    bits = as_int(row["bits"])

    command = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(base),
               "-af", f"volume={volume_db:+.2f}dB", "-map_metadata", "-1"]
    if rate:
        command += ["-ar", str(rate)]
    if channels:
        command += ["-ac", str(channels)]

    if fmt == "mp3":
        command += ["-c:a", "libmp3lame", "-b:a", "192k", "-f", "mp3"]
    elif fmt == "flac":
        command += ["-c:a", "flac", "-sample_fmt", "s32" if (bits or 16) > 16 else "s16"]
    elif fmt == "ogg":
        command += ["-c:a", "libvorbis", "-q:a", "5"]
    elif fmt == "opus":
        command += ["-c:a", "libopus", "-b:a", "128k"]
    elif fmt == "m4a" and codec == "aac":
        command += ["-c:a", "aac", "-b:a", "192k"]
    elif fmt == "m4a" and codec == "alac":
        command += ["-c:a", "alac", "-sample_fmt", "s16p"]
    elif fmt == "wav":
        pcm = {
            "pcm_s16": "pcm_s16le", "pcm_s24": "pcm_s24le", "pcm_f32": "pcm_f32le",
            "adpcm_ms": "adpcm_ms", "adpcm_ima": "adpcm_ima_wav",
        }
        command += ["-c:a", pcm[codec]]
    elif fmt == "aiff":
        pcm = {"pcm_s8": "pcm_s8", "pcm_s16be": "pcm_s16be",
               "pcm_s24be": "pcm_s24be", "pcm_s32be": "pcm_s32be"}
        command += ["-c:a", pcm[codec], "-f", "aiff"]
    elif fmt == "aifc" and codec == "sowt":
        command += ["-c:a", "pcm_s16le", "-f", "aiff"]
    elif fmt == "aifc" and codec == "fl32":
        command += ["-c:a", "pcm_f32be", "-f", "aiff"]
    elif fmt == "aifc" and codec == "ima4":
        command += ["-c:a", "adpcm_ima_qt", "-f", "aiff"]
    elif fmt == "aifc" and codec == "twos":
        command += ["-c:a", "pcm_s16be", "-f", "aiff"]
    elif fmt == "wv":
        command += ["-c:a", "wavpack", "-sample_fmt", "s16p"]
    elif fmt == "wma":
        command += ["-c:a", "wmav2", "-b:a", "192k"]
    else:
        fail(f"no encoder route for {fmt}/{codec}")
    command.append(str(out))

    result = run(command)
    if result.returncode != 0:
        fail(f"ffmpeg failed for {out.name}:\n{result.stderr.strip()}")
    if fmt == "aifc" and codec == "twos":
        rewrite_aifc_twos(out)


def rewrite_aifc_twos(path: Path) -> None:
    """Rewrite ffmpeg's big-endian AIFF as an AIFF-C whose compression type is `twos`."""
    data = path.read_bytes()
    if data[:4] != b"FORM" or data[8:12] != b"AIFF":
        fail(f"{path.name}: expected a FORM/AIFF file to convert to AIFC twos")

    def chunks(blob: bytes):
        offset = 12
        while offset + 8 <= len(blob):
            cid = blob[offset:offset + 4]
            size = struct.unpack(">I", blob[offset + 4:offset + 8])[0]
            yield cid, blob[offset + 8:offset + 8 + size]
            offset += 8 + size + (size & 1)

    def chunk(cid: bytes, body: bytes) -> bytes:
        out = cid + struct.pack(">I", len(body)) + body
        return out + (b"\x00" if len(body) % 2 else b"")

    comm = next(payload for cid, payload in chunks(data) if cid == b"COMM")
    if len(comm) != 18:
        fail(f"{path.name}: unexpected AIFF COMM size {len(comm)}")
    name = bytes([14]) + b"not compressed"
    if len(name) % 2:
        name += b"\x00"
    body = (b"AIFC" + chunk(b"FVER", struct.pack(">I", 0xA2805140))
            + chunk(b"COMM", comm + b"twos" + name))
    for cid, payload in chunks(data):
        if cid in (b"COMM", b"FVER"):
            continue
        body += chunk(cid, payload)
    path.write_bytes(b"FORM" + struct.pack(">I", len(body)) + body)


def trim_segment(source: Path, start: int, out: Path, row: dict) -> None:
    """Cut [start, start+35) sample-exactly from `source` and encode it as `row`."""
    fmt, codec, bits = row["format"], row["codec"], as_int(row["bits"])
    command = [
        "ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(source),
        "-af", f"atrim=start={start}:end={start + TRACK_SECONDS},asetpts=PTS-STARTPTS",
        "-map_metadata", "-1", "-ar", row["rate_hz"], "-ac", row["channels"],
    ]
    if fmt == "flac":
        command += ["-c:a", "flac", "-sample_fmt", "s32" if (bits or 16) > 16 else "s16"]
    elif fmt == "mp3":
        command += ["-c:a", "libmp3lame", "-b:a", "192k", "-f", "mp3"]
    else:
        fail(f"gapless albums support flac/mp3, not {fmt}")
    command.append(str(out))
    result = run(command)
    if result.returncode != 0:
        fail(f"gapless cut {out.name} failed:\n{result.stderr.strip()}")


# ---- gain math ----------------------------------------------------------------

def parse_number(text: str) -> float | None:
    match = re.search(r"[-+]?\d*\.?\d+(?:[eE][-+]?\d+)?", text)
    if not match:
        return None
    value = float(match.group())
    return value if math.isfinite(value) else None


def clamp_gain(value: float) -> float:
    return max(-30.0, min(20.0, value))


def replaygain_effective(track: float | None, reference: float | None) -> float | None:
    if track is None:
        return None
    if reference is not None:
        track = track + (-18.0 - reference)
    return clamp_gain(track)


def soundcheck_fields(gain_db: float) -> list[int]:
    def value(base: float) -> int:
        raw = int(round(base * (10.0 ** (-gain_db / 10.0))))
        return max(1, min(raw, 0xFFFFFFFF))
    return [value(1000.0), value(1000.0), value(2500.0), value(2500.0)] + [0] * 6


def soundcheck_text(gain_db: float) -> str:
    return " ".join(f"{value:08X}" for value in soundcheck_fields(gain_db))


def soundcheck_gain(text: str) -> float | None:
    fields = text.split()
    if len(fields) < 2:
        return None
    try:
        value = int(fields[0], 16)
    except ValueError:
        return None
    if value <= 0:
        return None
    return -10.0 * math.log10(value / 1000.0)


def gain_from_tags(tags: dict[str, str]) -> tuple[float | None, float | None]:
    """-18 LUFS-relative (track, album) effective gains from a case-insensitive tag map."""
    upper = {key.upper(): value for key, value in tags.items()}

    track_raw = parse_number(upper.get("REPLAYGAIN_TRACK_GAIN", "")) if "REPLAYGAIN_TRACK_GAIN" in upper else None
    album_raw = parse_number(upper.get("REPLAYGAIN_ALBUM_GAIN", "")) if "REPLAYGAIN_ALBUM_GAIN" in upper else None
    if "REPLAYGAIN_TRACK_GAIN" in upper or "REPLAYGAIN_ALBUM_GAIN" in upper:
        reference = parse_number(upper.get("REPLAYGAIN_REFERENCE_LOUDNESS", "")) if "REPLAYGAIN_REFERENCE_LOUDNESS" in upper else None
        # A value that cannot be parsed (abc, nan) is treated as untagged.
        if ("REPLAYGAIN_TRACK_GAIN" in upper and track_raw is None and "REPLAYGAIN_ALBUM_GAIN" not in upper):
            return None, None
        return replaygain_effective(track_raw, reference), replaygain_effective(album_raw, reference)

    if "R128_TRACK_GAIN" in upper or "R128_ALBUM_GAIN" in upper:
        def r128(key: str) -> float | None:
            if key not in upper:
                return None
            value = parse_number(upper[key])
            return None if value is None else value / 256.0 + 5.0
        return r128("R128_TRACK_GAIN"), r128("R128_ALBUM_GAIN")

    if "ITUNNORM" in upper:
        return soundcheck_gain(upper["ITUNNORM"]), None

    return None, None


def fmt_gain(value: float) -> str:
    return f"{value:.2f} dB"


def fmt_db(value: float) -> str:
    return f"{value:.6f}"


# ---- tag readers/writers ------------------------------------------------------

def import_mutagen():
    try:
        import mutagen  # noqa: F401
    except ImportError:
        fail("mutagen is not installed; run this through scripts/test-corpus/generate-corpus.sh")
    return mutagen


def write_tags(path: Path, row: dict, track: float, album: float | None, peak_linear: float,
               reference: float | None) -> None:
    forms = set(row["tag_forms"].split(","))
    import_mutagen()

    if row["format"] == "mp3":
        from mutagen.id3 import ID3, TXXX, COMM
        from mutagen.mp3 import MP3
        audio = MP3(str(path), ID3=ID3)
        if not audio.tags:
            audio.add_tags()
        if any(form in forms for form in ("rg_txxx", "rg_lowercase", "rg_reference_loudness")):
            lower = "rg_lowercase" in forms
            write_track = track
            if "rg_reference_loudness" in forms and reference is not None:
                write_track = track + 18.0 + reference
                audio.tags.add(TXXX(encoding=3, desc="REPLAYGAIN_REFERENCE_LOUDNESS",
                                    text=["-14.0 LUFS"]))
            desc_track = "replaygain_track_gain" if lower else "REPLAYGAIN_TRACK_GAIN"
            desc_album = "replaygain_album_gain" if lower else "REPLAYGAIN_ALBUM_GAIN"
            audio.tags.add(TXXX(encoding=3, desc=desc_track, text=[fmt_gain(write_track)]))
            if album is not None:
                audio.tags.add(TXXX(encoding=3, desc=desc_album, text=[fmt_gain(write_track)]))
            peak_key = "replaygain_track_peak" if lower else "REPLAYGAIN_TRACK_PEAK"
            audio.tags.add(TXXX(encoding=3, desc=peak_key, text=[fmt_db(peak_linear)]))
        if "itunnorm" in forms:
            audio.tags.add(COMM(encoding=3, lang="eng", desc="iTunNORM",
                                text=[soundcheck_text(track)]))
        audio.save()
        return

    if row["format"] in ("flac", "ogg", "opus"):
        if row["format"] == "flac":
            from mutagen.flac import FLAC as Audio
        elif row["format"] == "ogg":
            from mutagen.oggvorbis import OggVorbis as Audio
        else:
            from mutagen.oggopus import OggOpus as Audio
        audio = Audio(str(path))
        if "r128" in forms:
            raw = int(round((track - 5.0) * 256.0))
            audio["R128_TRACK_GAIN"] = str(raw)
            if album is not None:
                audio["R128_ALBUM_GAIN"] = str(raw)
        elif any(form in forms for form in ("rg_vorbis", "rg_lowercase", "rg_reference_loudness")):
            write_track = track
            if "rg_reference_loudness" in forms and reference is not None:
                write_track = track + 18.0 + reference
                audio["REPLAYGAIN_REFERENCE_LOUDNESS"] = "-14.0 LUFS"
            audio["REPLAYGAIN_TRACK_GAIN"] = fmt_gain(write_track)
            audio["REPLAYGAIN_TRACK_PEAK"] = fmt_db(peak_linear)
            if album is not None:
                audio["REPLAYGAIN_ALBUM_GAIN"] = fmt_gain(write_track)
        audio.save()
        return

    if row["format"] == "m4a":
        from mutagen.mp4 import MP4, MP4FreeForm
        audio = MP4(str(path))
        if "itunes_rg_freeform" in forms:
            audio["----:com.apple.iTunes:replaygain_track_gain"] = [MP4FreeForm(fmt_gain(track).encode())]
            if album is not None:
                audio["----:com.apple.iTunes:replaygain_album_gain"] = [MP4FreeForm(fmt_gain(track).encode())]
            audio["----:com.apple.iTunes:replaygain_track_peak"] = [MP4FreeForm(fmt_db(peak_linear).encode())]
        if "itunnorm" in forms:
            audio["----:com.apple.iTunes:iTunNORM"] = [MP4FreeForm(soundcheck_text(track).encode())]
        audio.save()
        return

    # WAV/AIFF/WavPack/WMA rows are untagged in this corpus.


def read_tags(path: Path, fmt: str) -> dict[str, str]:
    import_mutagen()
    tags: dict[str, str] = {}
    if fmt == "mp3":
        from mutagen.id3 import ID3
        try:
            audio = ID3(str(path))
        except Exception:
            return tags
        for frame in audio.values():
            if frame.FrameID == "TXXX":
                tags[frame.desc] = frame.text[0]
            elif frame.FrameID == "COMM":
                tags[frame.desc] = frame.text[0]
        return tags
    if fmt == "flac":
        from mutagen.flac import FLAC
        audio = FLAC(str(path))
    elif fmt == "ogg":
        from mutagen.oggvorbis import OggVorbis
        audio = OggVorbis(str(path))
    elif fmt == "opus":
        from mutagen.oggopus import OggOpus
        audio = OggOpus(str(path))
    elif fmt == "m4a":
        from mutagen.mp4 import MP4
        audio = MP4(str(path))
        for key in audio.keys():
            if key.startswith("----"):
                name = key.split(":")[-1]
                values = audio[key]
                if values:
                    tags[name] = bytes(values[0]).decode("utf-8", "replace")
        return tags
    else:
        return tags
    for key in audio.keys():
        values = audio[key]
        if values:
            tags[key] = values[0]
    return tags


def has_ci(tags: dict[str, str], wanted: str) -> bool:
    return any(key.upper() == wanted for key in tags)


def get_ci(tags: dict[str, str], wanted: str) -> str | None:
    for key, value in tags.items():
        if key.upper() == wanted:
            return value
    return None


# ---- expected values and registry labels --------------------------------------

def registry_label(row: dict) -> str:
    fmt, codec = row["format"], row["codec"]
    if fmt in ("aiff", "aifc"):
        if codec == "fl32":
            return "skipped:refused float AIFF-C"
        if codec in ("ima4",):
            return "skipped:refused compressed AIFF-C"
        return "kotlin-aiff"
    if fmt == "wav" and codec in ("adpcm_ms", "adpcm_ima"):
        return "decode-failure-path"
    if fmt == "m4a" and codec == "alac":
        return "device-dependent:alac"
    if fmt in ("wv", "wma"):
        return "skipped:unsupported-format-002"
    if fmt in ("ape", "dsf", "dff"):
        return "skipped:unsupported-format-002"
    return "platform"


def loudness_for(row: dict) -> float | None:
    return parse_number(row["loudness_target_lufs"])


# ---- generation ---------------------------------------------------------------

def generate_row(row: dict, out_dir: Path, scratch: Path, base_cache: dict,
                 album_cache: dict, opus_spike) -> None:
    path = out_dir / row["file"]
    target = loudness_for(row) or -14.0
    notes = row["notes"]
    opus_header_db = 4.0 if "opus_header_gain" in row["tag_forms"] else 0.0

    if notes.startswith("gapless-album:"):
        match = re.match(r"gapless-album:([^:]+):(\d+)", notes)
        if not match:
            fail(f"{row['file']}: malformed gapless notes {notes!r}")
        album_name, index = match.group(1), match.group(2)
        cache_key = (album_name, target)
        if cache_key not in album_cache:
            base, base_loudness = base_signal(as_int(row["rate_hz"]) or 48000, scratch, ALBUM_SECONDS)
            album_path = scratch / f"album_{album_name}_{int(target)}.wav"
            volume = target - base_loudness
            result = run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(base),
                          "-af", f"volume={volume:+.2f}dB", "-t", str(ALBUM_SECONDS),
                          "-c:a", "pcm_f32le", str(album_path)])
            if result.returncode != 0:
                fail(f"gapless album base failed:\n{result.stderr.strip()}")
            album_cache[cache_key] = album_path
        trim_segment(album_cache[cache_key], (int(index) - 1) * TRACK_SECONDS, path, row)
        loudness, sample_peak = measure(path)
        peak_linear = 10.0 ** (sample_peak / 20.0)
        track = -18.0 - loudness
        write_tags(path, row, track, track, peak_linear, None)
        return

    key = as_int(row["rate_hz"]) or 48000
    if key not in base_cache:
        base_cache[key] = base_signal(key, scratch)
    base, base_loudness = base_cache[key]
    encode(row, base, target - base_loudness, path, scratch)

    loudness, sample_peak = measure(path)
    peak_linear = 10.0 ** (sample_peak / 20.0)
    track = -18.0 - loudness

    forms = set(row["tag_forms"].split(","))
    if "out_of_range" in forms:
        write_string_tag(path, row, "REPLAYGAIN_TRACK_GAIN", "+50.00 dB")
    elif "non_finite" in forms:
        write_string_tag(path, row, "REPLAYGAIN_TRACK_GAIN", "nan dB")
    elif "missing_unit" in forms:
        write_string_tag(path, row, "REPLAYGAIN_TRACK_GAIN", f"{track:.2f}")
    elif "malformed" in forms:
        write_string_tag(path, row, "REPLAYGAIN_TRACK_GAIN", "abc")
    elif "none" not in forms:
        reference = -14.0 if "rg_reference_loudness" in forms else None
        album = track if any(form in forms for form in ("rg_txxx", "rg_vorbis", "rg_lowercase", "r128", "itunes_rg_freeform")) else None
        write_tags(path, row, track, album, peak_linear, reference)

    if opus_header_db:
        data = path.read_bytes()
        raw = int(round(opus_header_db * 256.0))
        path.write_bytes(opus_spike.patch_output_gain(data, raw))


def write_string_tag(path: Path, row: dict, key: str, value: str) -> None:
    """Write one intentionally malformed/odd gain tag so --verify can read it back."""
    import_mutagen()
    fmt = row["format"]
    if fmt == "flac":
        from mutagen.flac import FLAC
        audio = FLAC(str(path))
        audio[key] = value
        audio.save()
    elif fmt == "mp3":
        from mutagen.id3 import ID3, TXXX
        from mutagen.mp3 import MP3
        audio = MP3(str(path), ID3=ID3)
        if not audio.tags:
            audio.add_tags()
        audio.tags.add(TXXX(encoding=3, desc=key, text=[value]))
        audio.save()
    else:
        fail(f"malformed tag row for unsupported format {fmt}")


def write_out_of_range(path: Path, row: dict) -> None:
    write_string_tag(path, row, "REPLAYGAIN_TRACK_GAIN", "+50.00 dB")


def copy_owner_supplied(row: dict, out_dir: Path) -> str | None:
    source_dir = os.environ.get("SISTRUM_REAL_SAMPLES")
    if not source_dir:
        return None
    source = Path(source_dir) / row["file"]
    if not source.is_file():
        return None
    shutil.copyfile(source, out_dir / row["file"])
    return str(source)


def generated_track_fields(row: dict, path: Path, opus_spike) -> dict[str, str]:
    """Recompute the -18-relative effective gains from the tags actually on disk."""
    tags = read_tags(path, row["format"])
    track, album = gain_from_tags(tags)
    result = {
        "expected_track_gain_db": "" if track is None else f"{track:.2f}",
        "expected_album_gain_db": "" if album is None else f"{album:.2f}",
    }
    if "opus_header_gain" in row["tag_forms"]:
        raw = opus_spike.read_output_gain(path.read_bytes())
        result["notes"] = (row["notes"] + f"; header-gain={raw / 256.0:+.1f} dB").strip("; ")
    return result


def command_generate(out_dir: Path, manifest_path: Path) -> int:
    require_tools()
    rows = read_manifest(manifest_path)
    out_dir.mkdir(parents=True, exist_ok=True)
    scratch = Path(tempfile.mkdtemp(prefix="sistrum-corpus-bases-"))
    opus_spike = load_opus_spike()

    base_cache: dict = {}
    album_cache: dict = {}
    generated: list[dict] = []
    generated_count = 0
    owner_present = 0
    missing: list[str] = []

    try:
        for row in rows:
            output = dict(row)
            if row["source"] == "owner-supplied":
                if copy_owner_supplied(row, out_dir):
                    owner_present += 1
                    generated.append(output)
                else:
                    missing.append(row["file"])
                    generated.append(output)
                continue
            generate_row(row, out_dir, scratch, base_cache, album_cache, opus_spike)
            output.update(generated_track_fields(row, out_dir / row["file"], opus_spike))
            generated.append(output)
            generated_count += 1
    finally:
        shutil.rmtree(scratch, ignore_errors=True)

    write_manifest(out_dir / "corpus.generated.tsv", generated)
    total = sum((out_dir / row["file"]).stat().st_size for row in rows
                if (out_dir / row["file"]).is_file())
    print(f"generated: {generated_count} files")
    print(f"owner-supplied present: {owner_present}")
    print(f"owner-supplied missing: {len(missing)}" + (f" ({', '.join(missing)})" if missing else ""))
    print(f"corpus size: {total / (1024 * 1024):.1f} MiB in {out_dir}")
    print(f"wrote {out_dir / 'corpus.generated.tsv'}")
    return 0


# ---- verification -------------------------------------------------------------

def probe(path: Path) -> dict:
    result = run(["ffprobe", "-v", "error", "-show_format", "-show_streams", "-of", "json", str(path)])
    if result.returncode != 0:
        fail(f"ffprobe failed for {path.name}:\n{result.stderr.strip()}")
    return json.loads(result.stdout)


def check_codec(row: dict, info: dict, problems: list[str]) -> None:
    fmt, codec = row["format"], row["codec"]
    streams = [s for s in info.get("streams", []) if s.get("codec_type") == "audio"]
    if not streams:
        problems.append(f"{row['file']}: no audio stream")
        return
    stream = streams[0]
    actual_format = info.get("format", {}).get("format_name", "")
    expected_format = CONTAINER_NAME.get(fmt)
    if expected_format and actual_format != expected_format:
        problems.append(f"{row['file']}: container is {actual_format!r}, row claims {expected_format!r}")
    actual_codec = stream.get("codec_name", "")
    tag = stream.get("codec_tag_string", "")
    if fmt == "aifc":
        matched = tag == codec
        shown = tag
    elif fmt == "wav" and codec in ("pcm_s16", "pcm_s24", "pcm_f32"):
        matched = actual_codec == f"{codec}le"
        shown = actual_codec
    elif codec == "adpcm_ima":
        matched = actual_codec == "adpcm_ima_wav"
        shown = actual_codec
    else:
        matched = actual_codec == codec
        shown = actual_codec
    if not matched:
        problems.append(f"{row['file']}: codec is {shown!r}, row claims {codec!r}")
    rate = as_int(row["rate_hz"])
    if rate and int(stream.get("sample_rate", 0)) != rate:
        problems.append(f"{row['file']}: sample rate is {stream.get('sample_rate')}, row claims {rate}")
    channels = as_int(row["channels"])
    if channels and int(stream.get("channels", 0)) != channels:
        problems.append(f"{row['file']}: channels is {stream.get('channels')}, row claims {channels}")
    bits = as_int(row["bits"])
    if bits:
        raw = stream.get("bits_per_raw_sample")
        actual_bits = raw if raw not in (None, "", "0", 0) else stream.get("bits_per_sample")
        try:
            matches = int(actual_bits) == bits
        except (TypeError, ValueError):
            matches = False
        if not matches:
            problems.append(f"{row['file']}: bits is {actual_bits!r}, row claims {bits}")


def check_tag_forms(row: dict, path: Path, problems: list[str], opus_spike) -> dict[str, str]:
    forms = set(row["tag_forms"].split(","))
    tags = read_tags(path, row["format"])
    if "rg_txxx" in forms:
        if not any(key.upper() in REPLAYGAIN_KEYS for key in tags):
            problems.append(f"{row['file']}: expected ID3 TXXX ReplayGain tags")
    if "rg_lowercase" in forms:
        if not any(key.islower() and key.startswith("replaygain") for key in tags):
            problems.append(f"{row['file']}: expected lower-case ReplayGain tag keys")
    if "rg_vorbis" in forms:
        if not any(key.upper() in REPLAYGAIN_KEYS for key in tags):
            problems.append(f"{row['file']}: expected Vorbis REPLAYGAIN_* comments")
    if "rg_reference_loudness" in forms:
        if not has_ci(tags, "REPLAYGAIN_REFERENCE_LOUDNESS"):
            problems.append(f"{row['file']}: expected REPLAYGAIN_REFERENCE_LOUDNESS")
    if "r128" in forms:
        if not has_ci(tags, "R128_TRACK_GAIN"):
            problems.append(f"{row['file']}: expected R128_TRACK_GAIN")
    if "itunnorm" in forms:
        if not has_ci(tags, "ITUNNORM"):
            problems.append(f"{row['file']}: expected iTunNORM")
    if "itunes_rg_freeform" in forms:
        if not any("replaygain_track_gain" == key.lower() for key in tags):
            problems.append(f"{row['file']}: expected iTunes freeform replaygain_track_gain")
    if "opus_header_gain" in forms:
        expected_raw = int(round(4.0 * 256.0))
        actual_raw = opus_spike.read_output_gain(path.read_bytes())
        if actual_raw != expected_raw:
            problems.append(f"{row['file']}: Opus header gain is {actual_raw}, row claims {expected_raw}")
    if "none" in forms:
        if any(key.upper() in REPLAYGAIN_KEYS or key.upper().startswith("R128") or key.upper() == "ITUNNORM"
               for key in tags):
            problems.append(f"{row['file']}: row says untagged but gain tags are present")
    return tags


def target_db(row: dict, column: str) -> float | None:
    return parse_number(row[column]) if row[column].strip() else None


def command_verify(directory: Path, manifest_path: Path) -> int:
    require_tools()
    rows = read_manifest(manifest_path)
    opus_spike = load_opus_spike()
    problems: list[str] = []
    missing: list[str] = []
    present = 0

    for row in rows:
        path = directory / row["file"]
        if not path.is_file():
            if row["source"] == "owner-supplied":
                missing.append(row["file"])
            else:
                problems.append(f"{row['file']}: generated file is missing")
            continue
        present += 1
        info = probe(path)
        check_codec(row, info, problems)
        tags = check_tag_forms(row, path, problems, opus_spike)
        track, album = gain_from_tags(tags)

        for column, actual in (("expected_track_gain_db", track), ("expected_album_gain_db", album)):
            target = target_db(row, column)
            if target is None:
                if actual is not None:
                    problems.append(f"{row['file']}: {column} target is empty but tags yield {actual:+.2f} dB")
            elif actual is None:
                problems.append(f"{row['file']}: {column} target is {target:+.2f} dB but tags yield none")
            elif abs(actual - target) > TARGET_DB_TOLERANCE:
                problems.append(
                    f"{row['file']}: {column} is {actual:+.2f} dB, corpus.tsv target {target:+.2f} dB "
                    f"(> {TARGET_DB_TOLERANCE} dB)")

    if missing:
        print(f"owner-supplied/missing rows (not failing): {', '.join(missing)}")
    for problem in problems:
        print(f"FAIL {problem}")
    if problems:
        print(f"verify FAILED: {len(problems)} problem(s) over {present} present files")
        return 1
    print(f"verify PASSED: {present} present files match corpus.tsv")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Sistrum test-corpus generator/verifier (T006).")
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--out", metavar="DIR", type=Path, help="generate the corpus into DIR")
    group.add_argument("--verify", metavar="DIR", type=Path, help="verify a generated corpus in DIR")
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST, help=argparse.SUPPRESS)
    args = parser.parse_args()

    if args.out is not None:
        return command_generate(args.out, args.manifest)
    return command_verify(args.verify, args.manifest)


if __name__ == "__main__":
    sys.exit(main())
