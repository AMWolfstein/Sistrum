"""Check the arm64 Android build script without building an APK."""

import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


FLUTTER_STUB = """import json, os, sys
from pathlib import Path
args = sys.argv[1:]
if Path(sys.argv[0]).name == 'fvm':
    assert args.pop(0) in ('flutter', 'dart')
with open(os.environ['BUILD_TEST_LOG'], 'a') as log:
    log.write(json.dumps({'tool': Path(sys.argv[0]).name, 'args': args}) + '\\n')
if args == ['scripts/prepare_flutter_sdk.dart']:
    sys.exit(int(os.environ.get('BUILD_TEST_PATCH_FAIL', '0')))
elif args == ['--version', '--machine']:
    print(json.dumps({'frameworkVersion': os.environ.get('BUILD_TEST_FLUTTER_VERSION', '3.47.8')}))
else:
    assert args[:2] == ['build', 'apk']
    output = Path('build/app/outputs/flutter-apk')
    output.mkdir(parents=True, exist_ok=True)
    (output / 'app-release.apk').write_bytes(b'fixture apk')
"""

PYTHON_STUB = """import json, os, sys
if sys.argv[1] == 'scripts/check_backend_apk.py':
    with open(os.environ['BUILD_TEST_LOG'], 'a') as log:
        log.write(json.dumps({'tool': 'audit', 'args': sys.argv[2:]}) + '\\n')
else:
    os.execv(sys.executable, [sys.executable, *sys.argv[1:]])
"""


class AndroidBuildScriptTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.project = Path(directory.name)
        (self.project / "scripts").mkdir()
        shutil.copyfile(Path(__file__).with_name("build_android.sh"), self.project / "scripts/build_android.sh")
        (self.project / ".fvmrc").write_text('{"flutter":"3.47.8"}')
        self.tools = self.project / "tools"
        self.tools.mkdir()
        self.log = self.project / "calls.jsonl"
        self.env = os.environ.copy()
        # The fixture project has no key.properties; the build is a debug-signed test build.
        self.env["MORNYE_ALLOW_DEBUG_SIGNING"] = "1"
        self.env.pop("MORNYE_RUST_ANDROID_ABIS", None)
        self.env.update(PATH=str(self.tools) + os.pathsep + os.defpath, BUILD_TEST_LOG=str(self.log))
        self.tool(self.tools / "python3", PYTHON_STUB)
        git = self.tools / "git"
        git.write_text("#!/bin/sh\necho 1234abcd\n")
        git.chmod(0o755)
        self.pinned = self.project / ".fvm/flutter_sdk/bin/flutter"
        self.tool(self.pinned, FLUTTER_STUB)
        self.tool(self.pinned.with_name("dart"), FLUTTER_STUB)

    def tool(self, path, source):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("#!" + sys.executable + "\n" + source)
        path.chmod(0o755)

    def run_build(self, *args):
        return subprocess.run(
            ["/bin/bash", str(self.project / "scripts/build_android.sh"), *args],
            cwd=self.project, env=self.env, text=True, capture_output=True,
        )

    def calls(self):
        if not self.log.exists():
            return []
        return [json.loads(line) for line in self.log.read_text().splitlines()]

    def test_builds_one_arm64_apk_and_audits_it(self):
        result = self.run_build()
        self.assertEqual(result.returncode, 0, result.stderr)
        calls = self.calls()
        build = next(call for call in calls if call["args"][:2] == ["build", "apk"])
        self.assertIn("android-arm64", build["args"])
        self.assertNotIn("--split-per-abi", build["args"])
        audit = next(call for call in calls if call["tool"] == "audit")
        self.assertEqual(audit["args"][audit["args"].index("--abis") + 1], "arm64-v8a")

    def test_rejects_other_architectures(self):
        for value in ("x86_64", "arm64-v8a,x86_64"):
            with self.subTest(value=value):
                self.env["MORNYE_RUST_ANDROID_ABIS"] = value
                result = self.run_build()
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("must be arm64-v8a", result.stderr)
        self.assertFalse(self.calls())

    def test_refuses_debug_signing_unless_allowed(self):
        self.env.pop("MORNYE_ALLOW_DEBUG_SIGNING", None)
        result = self.run_build()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("no android/key.properties", result.stderr)

    def test_rejects_target_platform_arguments(self):
        result = self.run_build("--target-platform", "android-arm")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("always targets android-arm64", result.stderr)

    def test_rejects_wrong_flutter_version(self):
        self.env["BUILD_TEST_FLUTTER_VERSION"] = "3.47.5"
        result = self.run_build()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Flutter 3.47.8 is required", result.stderr)
        self.assertFalse(any(call["args"][:2] == ["build", "apk"] for call in self.calls()))

    def test_patch_failure_stops_before_build(self):
        self.env["BUILD_TEST_PATCH_FAIL"] = "1"
        result = self.run_build()
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(any(call["args"][:2] == ["build", "apk"] for call in self.calls()))


if __name__ == "__main__":
    unittest.main()
