"""Check the arm64 APK audit against synthetic release archives."""

import io
import struct
import tempfile
import unittest
import zipfile
from pathlib import Path

import check_backend_apk as checker


def elf(machine=183):
    header = bytearray(64)
    header[:4] = b"\x7fELF"
    header[4] = 2  # 64-bit
    header[5] = 1  # little-endian
    struct.pack_into("<H", header, 18, machine)
    return bytes(header)


class CheckBackendApkTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.apk = Path(directory.name) / "app-release.apk"
        self.entries = {
            "lib/arm64-v8a/libapp.so": elf(),
            "lib/arm64-v8a/libflutter.so": elf(),
            "lib/arm64-v8a/libmornye_mobile.so": elf(),
            "lib/arm64-v8a/libjnidispatch.so": elf(),
            "assets/flutter_assets/AssetManifest.json": b"{}",
        }

    def write(self):
        with zipfile.ZipFile(self.apk, "w") as zf:
            for name, data in self.entries.items():
                zf.writestr(name, data)

    def audit(self, abis=("arm64-v8a",)):
        self.write()
        return checker.audit(self.apk, "rust", abis)

    def test_accepts_arm64_rust_apk(self):
        self.assertEqual(len(self.audit()), 64)

    def test_rejects_missing_backend_library(self):
        del self.entries["lib/arm64-v8a/libmornye_mobile.so"]
        with self.assertRaisesRegex(checker.AuditError, "missing APK entry"):
            self.audit()

    def test_rejects_wrong_machine(self):
        self.entries["lib/arm64-v8a/libapp.so"] = elf(machine=40)
        with self.assertRaisesRegex(checker.AuditError, "does not match"):
            self.audit()

    def test_rejects_other_abi_directories(self):
        self.entries["lib/x86_64/libapp.so"] = elf(machine=62)
        with self.assertRaisesRegex(checker.AuditError, "unexpected lib ABI"):
            self.audit()

    def test_rejects_unsupported_abi_argument(self):
        with self.assertRaisesRegex(checker.AuditError, "unsupported ABI"):
            checker.parse_abis("x86_64")


if __name__ == "__main__":
    unittest.main()
