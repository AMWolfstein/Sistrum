#!/usr/bin/env python3
"""Build ID_WVX_NEW synthetic coverage and verify every PCM sample with pinned WaxFlow.

Usage: python3 codecs/tools/generate-max-width.py [pinned-waxflow-binary]
An optional binary verifies the originally recorded PCM independently; normal
generation is checked against the committed owned SHA-256 manifest.
No production encoder is added to the Kotlin module.
"""
import os
import hashlib
import json
import pathlib
import struct
import subprocess
import sys
import tempfile
import wave

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = pathlib.Path(os.environ.get("SISTRUM_ORACLE_DIR", pathlib.Path.home()/".cache/sistrum-waxflow-oracle")).expanduser().resolve()/"owned/wavpack"
if OUT.is_relative_to(ROOT.parent):raise SystemExit("Oracle storage must remain outside the repository")
PIN = "b7857aff88820ad37936026421d1641e64611dbe"
BASE = [0, 1, 3, 7, 15, 31, 63, 127, -1, -2, -4, -8, -16, -32, -64, -128]
# Constructed completed samples, checked independently by Go before recording them.
PCM = [5, 14, 31, 60, 124, 248, 504, 1016, -5, -12, -26, -62, -128, -256, -512, -1024]
SENT, WIDTH = 3, 5


class Bits:
    def __init__(self):
        self.bits = []

    def put(self, value, n):
        self.bits.extend((value >> i) & 1 for i in range(n))

    def code(self, value, maximum):
        if maximum < 2:
            if maximum:
                self.put(value, 1)
            return
        n = maximum.bit_length()
        extras = (1 << n) - maximum - 1
        if value < extras:
            self.put(value, n - 1)
        else:
            v = value + extras
            self.put(v >> 1, n - 1)
            self.put(v & 1, 1)

    def finish(self):
        self.bits.extend([0] * ((-len(self.bits)) % 16))
        return bytes(sum(self.bits[i + k] << k for k in range(8))
                     for i in range(0, len(self.bits), 8))


def metadata(identifier, payload):
    n = len(payload)
    return bytes([identifier | (0x40 if n & 1 else 0), (n + 1) // 2]) + payload + (b"\0" if n & 1 else b"")


words, extension = Bits(), Bits()
median = 4096  # exp2s(0x0d00), nominal median = 257; every residual is in zone zero.
extension.put(WIDTH, 5)
crc, crc_x = 0xffffffff, 0xffffffff
branches = {"full": 0, "partial": 0, "none": 0}
for i, (base, sample) in enumerate(zip(BASE, PCM)):
    if i % 2 == 0:
        words.put(0, 1)  # zero unary count holds a zero for the next sample.
    words.code(base if base >= 0 else ~base, (median >> 4))
    median -= ((median + 126) // 128) * 2
    words.put(int(base < 0), 1)
    crc = (crc * 3 + base) & 0xffffffff
    pos = base if base >= 0 else ~base
    excess = max(0, pos.bit_length() + SENT - WIDTH)
    read = max(0, SENT - excess)
    branches["full" if read == SENT else "partial" if read else "none"] += 1
    if read:
        extension.put(sample >> (SENT - read), read)
    crc_x = (crc_x * 9 + (sample & 65535) * 3 + ((sample >> 16) & 65535)) & 0xffffffff

body = (metadata(5, struct.pack("<HHH", 0x0d00, 0, 0)) +
        metadata(9, bytes([SENT, 0, 0, 0])) +
        metadata(10, words.finish()) +
        metadata(44, struct.pack("<I", crc_x) + extension.finish()))
header = struct.pack("<4sIHBBIIIII", b"wvpk", 24 + len(body), 0x410, 0, 0,
                     len(BASE), 0, len(BASE), (9 << 23) | (10 << 18) | 0x1905, crc)
raw = header + body
OUT.mkdir(parents=True, exist_ok=True)
path = OUT / "extended-max-width.wv"
path.write_bytes(raw)
packed = struct.pack("<" + "h" * len(PCM), *PCM)
decoded = PCM
if len(sys.argv)>1:
    with tempfile.TemporaryDirectory() as temporary:
        wav = pathlib.Path(temporary) / "reference.wav"
        subprocess.run([sys.argv[1], "transcode", "--force", "--no-tags", "--format", "wav", str(path), str(wav)], check=True)
        with wave.open(str(wav)) as reader:
            assert (reader.getnchannels(), reader.getsampwidth(), reader.getframerate(), reader.getnframes()) == (1, 2, 44100, len(PCM))
            packed = reader.readframes(reader.getnframes())
        decoded = list(struct.unpack("<" + "h" * len(PCM), packed))
        assert decoded == PCM, (decoded, PCM)
record = {"waxflow_commit": PIN, "file_sha256": hashlib.sha256(raw).hexdigest(),
          "pcm_sha256": hashlib.sha256(packed).hexdigest(), "samples": decoded,
          "sent_bits": SENT, "maximum_width": WIDTH, "branches": branches}
(OUT / "extended-max-width.json").write_text(json.dumps(record, indent=2) + "\n")
print(json.dumps(record, indent=2))
