#!/usr/bin/env python3
"""Spike S5 fixtures for specs/001-media3-migration (ADR-005), not app code.

Purpose
-------
ADR-005 ("Tag-based normalization and the gain source") depends on one device fact: does the
platform Opus decoder apply the OpusHead *output gain* field, and exactly once? To answer that,
Spike S5 decodes three Ogg/Opus files that are bit-identical except for that field, measures the
decoded level of each, and compares.

This script builds those fixtures. It encodes a 10 s, 48 kHz, stereo, 1 kHz sine at -20 dBFS peak
with ffmpeg/libopus, then writes two copies whose OpusHead "output gain" (signed Q7.8 dB at packet
byte offset 16) is patched to +6.0 dB (1536) and -6.0 dB (-1536). Because the gain field lives in
the first Ogg page, that page's CRC (Ogg CRC-32, poly 0x04C11DB7, init 0, no reflection, no final
XOR) is recomputed. Everything else is untouched, so the only difference between the files is the
header gain.

Usage
-----
    python3 scripts/spikes/opus-header-gain.py --out DIR
        Generate DIR/s5_gain0.opus, DIR/s5_gain_plus6.opus and DIR/s5_gain_minus6.opus.

    python3 scripts/spikes/opus-header-gain.py --self-check DIR
        Parse the three files back, print each output gain and validate every Ogg page CRC.
        Exit 1 if any gain differs from the expected 0.0 / +6.0 / -6.0 dB or any CRC is invalid.

Requires Python 3 (stdlib only) and an ffmpeg on PATH built with the libopus encoder. Files are
generated into a scratch directory; never commit the resulting audio.
"""

from __future__ import annotations

import argparse
import re
import shutil
import subprocess
import sys
from pathlib import Path

SINE_INPUT = "sine=frequency=1000:sample_rate=48000:duration=10"
TARGET_PEAK_DBFS = -20.0
FILES = {
    "s5_gain0.opus": 0,
    "s5_gain_plus6.opus": 1536,
    "s5_gain_minus6.opus": -1536,
}
OPUS_HEAD_MAGIC = b"OpusHead"


# --------------------------------------------------------------------------------------------
# Ogg page helpers
# --------------------------------------------------------------------------------------------

def _build_crc_table() -> list[int]:
    table = []
    for index in range(256):
        remainder = index << 24
        for _ in range(8):
            if remainder & 0x80000000:
                remainder = ((remainder << 1) ^ 0x04C11DB7) & 0xFFFFFFFF
            else:
                remainder = (remainder << 1) & 0xFFFFFFFF
        table.append(remainder)
    return table


_CRC_TABLE = _build_crc_table()


def crc32_ogg(data: bytes) -> int:
    """Ogg CRC-32: poly 0x04C11DB7, init 0, no reflection, no final XOR."""
    crc = 0
    for byte in data:
        crc = ((crc << 8) & 0xFFFFFFFF) ^ _CRC_TABLE[((crc >> 24) ^ byte) & 0xFF]
    return crc


def iter_pages(data: bytes):
    """Yield (page_index, page_offset, page_length) for every Ogg page in `data`."""
    offset = 0
    index = 0
    while offset < len(data):
        if data[offset:offset + 4] != b"OggS":
            raise ValueError(f"no OggS capture pattern at byte {offset}")
        segment_count = data[offset + 26]
        segment_table = data[offset + 27:offset + 27 + segment_count]
        if len(segment_table) != segment_count:
            raise ValueError(f"truncated segment table on page {index}")
        page_length = 27 + segment_count + sum(segment_table)
        if offset + page_length > len(data):
            raise ValueError(f"truncated page {index} payload at byte {offset}")
        yield index, offset, page_length
        offset += page_length
        index += 1


def find_opus_head(data: bytes) -> tuple[int, int, int, int]:
    """Return (page_index, packet_offset, page_offset, page_length) of the OpusHead packet."""
    for index, page_offset, page_length in iter_pages(data):
        segment_count = data[page_offset + 26]
        packet_offset = page_offset + 27 + segment_count
        if data[packet_offset:packet_offset + 8] == OPUS_HEAD_MAGIC:
            return index, packet_offset, page_offset, page_length
    raise ValueError("OpusHead packet not found")


def read_output_gain(data: bytes) -> int:
    """Signed Q7.8 output gain from the OpusHead packet (byte offset 16, little-endian)."""
    _, packet_offset, _, _ = find_opus_head(data)
    if packet_offset + 19 > len(data):
        raise ValueError("OpusHead packet is shorter than 19 bytes")
    return int.from_bytes(data[packet_offset + 16:packet_offset + 18], "little", signed=True)


def read_channels(data: bytes) -> int:
    _, packet_offset, _, _ = find_opus_head(data)
    return data[packet_offset + 9]


def page_crc_matches(data: bytes, page_offset: int, page_length: int) -> bool:
    stored = int.from_bytes(data[page_offset + 22:page_offset + 26], "little")
    page = bytearray(data[page_offset:page_offset + page_length])
    page[22:26] = b"\x00\x00\x00\x00"
    return crc32_ogg(bytes(page)) == stored


def patch_output_gain(data: bytes, gain_raw: int) -> bytes:
    """Return a copy of `data` with the OpusHead output gain set and the page CRC fixed up."""
    _, packet_offset, page_offset, page_length = find_opus_head(data)
    patched = bytearray(data)
    patched[packet_offset + 16:packet_offset + 18] = gain_raw.to_bytes(2, "little", signed=True)
    patched[page_offset + 22:page_offset + 26] = b"\x00\x00\x00\x00"
    checksum = crc32_ogg(bytes(patched[page_offset:page_offset + page_length]))
    patched[page_offset + 22:page_offset + 26] = checksum.to_bytes(4, "little")
    return bytes(patched)


# --------------------------------------------------------------------------------------------
# ffmpeg
# --------------------------------------------------------------------------------------------

def require_ffmpeg_with_libopus() -> str:
    ffmpeg = shutil.which("ffmpeg")
    if not ffmpeg:
        raise SystemExit(
            "error: ffmpeg not found on PATH; install ffmpeg with the libopus encoder "
            "(e.g. 'apt install ffmpeg' or a build configured with --enable-libopus)"
        )
    result = subprocess.run(
        [ffmpeg, "-hide_banner", "-encoders"],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0 or "libopus" not in result.stdout:
        raise SystemExit(
            f"error: {ffmpeg} has no 'libopus' encoder; rebuild ffmpeg with --enable-libopus"
        )
    return ffmpeg


def measure_sine_peak_db(ffmpeg: str) -> float:
    result = subprocess.run(
        [
            ffmpeg, "-hide_banner",
            "-f", "lavfi", "-i",
            "sine=frequency=1000:sample_rate=48000:duration=1",
            "-af", "volumedetect",
            "-f", "null", "-",
        ],
        capture_output=True,
        text=True,
    )
    match = re.search(r"max_volume:\s*(-?\d+(?:\.\d+)?)\s*dB", result.stdout + result.stderr)
    if not match:
        raise SystemExit("error: could not measure the sine peak with ffmpeg volumedetect")
    return float(match.group(1))


def encode_base(ffmpeg: str, volume_db: float, output: Path) -> None:
    command = [
        ffmpeg, "-hide_banner", "-loglevel", "error", "-y",
        "-f", "lavfi", "-i", SINE_INPUT,
        "-af", f"volume={volume_db:+.2f}dB",
        "-ac", "2",
        "-c:a", "libopus", "-b:a", "192k",
        "-map_metadata", "-1",
        str(output),
    ]
    result = subprocess.run(command, capture_output=True, text=True)
    if result.returncode != 0:
        raise SystemExit(
            f"error: ffmpeg failed to encode {output.name}:\n{result.stderr.strip()}"
        )


# --------------------------------------------------------------------------------------------
# Commands
# --------------------------------------------------------------------------------------------

def generate(out_dir: Path) -> int:
    ffmpeg = require_ffmpeg_with_libopus()
    out_dir.mkdir(parents=True, exist_ok=True)

    peak_db = measure_sine_peak_db(ffmpeg)
    volume_db = TARGET_PEAK_DBFS - peak_db
    print(
        f"ffmpeg sine peak {peak_db:+.2f} dBFS -> applying volume {volume_db:+.2f} dB "
        f"for a {TARGET_PEAK_DBFS:+.0f} dBFS peak"
    )

    base_path = out_dir / "s5_gain0.opus"
    encode_base(ffmpeg, volume_db, base_path)
    base_data = base_path.read_bytes()
    base_head = find_opus_head(base_data)
    print(
        f"wrote {base_path.name}: channels={read_channels(base_data)} "
        f"output_gain={read_output_gain(base_data) / 256.0:+.1f} dB"
    )

    for name, gain_raw in FILES.items():
        if gain_raw == 0:
            continue
        patched = patch_output_gain(base_data, gain_raw)
        path = out_dir / name
        path.write_bytes(patched)

        parsed = read_output_gain(patched)
        if parsed != gain_raw:
            raise SystemExit(
                f"error: {name} verified {parsed} but expected {gain_raw}"
            )
        for index, page_offset, page_length in iter_pages(patched):
            if not page_crc_matches(patched, page_offset, page_length):
                raise SystemExit(f"error: {name} page {index} CRC invalid after patching")
        print(f"wrote {name}: output_gain={parsed / 256.0:+.1f} dB, all page CRCs valid")

    print(f"ok: generated 3 fixtures in {out_dir}")
    return 0


def self_check(directory: Path) -> int:
    ok = True
    for name, expected_raw in FILES.items():
        path = directory / name
        if not path.is_file():
            print(f"FAIL {name}: not found in {directory}")
            ok = False
            continue
        data = path.read_bytes()
        try:
            gain_raw = read_output_gain(data)
        except ValueError as error:
            print(f"FAIL {name}: {error}")
            ok = False
            continue
        gain_db = gain_raw / 256.0
        expected_db = expected_raw / 256.0

        pages = list(iter_pages(data))
        bad_pages = [
            index for index, page_offset, page_length in pages
            if not page_crc_matches(data, page_offset, page_length)
        ]
        crc_text = "all page CRCs valid" if not bad_pages else f"invalid CRCs on pages {bad_pages}"
        gain_text = "ok" if gain_raw == expected_raw else f"MISMATCH (expected {expected_db:+.1f} dB)"

        print(
            f"{name}: output_gain={gain_db:+.1f} dB (raw={gain_raw}) channels={read_channels(data)} "
            f"pages={len(pages)} {crc_text} [{gain_text}]"
        )
        if gain_raw != expected_raw or bad_pages:
            ok = False

    if not ok:
        print("self-check FAILED")
        return 1
    print("self-check PASSED: gains 0.0, +6.0, -6.0 dB; all page CRCs valid")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Spike S5 OpusHead output-gain fixtures (specs/001-media3-migration, ADR-005)."
    )
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--out", metavar="DIR", type=Path, help="generate fixtures into DIR")
    group.add_argument(
        "--self-check", metavar="DIR", type=Path,
        help="parse fixtures in DIR, print gains and validate every page CRC",
    )
    args = parser.parse_args()

    if args.out is not None:
        return generate(args.out)
    return self_check(args.self_check)


if __name__ == "__main__":
    sys.exit(main())
