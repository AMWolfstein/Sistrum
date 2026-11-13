import tempfile
from pathlib import Path
import unittest
import toolchains


class ToolchainTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        self.root=Path(self.tmp.name)

    def test_rust_channel_comes_from_the_repository_pin(self):
        self.assertIsNone(toolchains.rust_channel(self.root))
        path=self.root/'rust_backend/rust-toolchain.toml';path.parent.mkdir()
        path.write_text('[toolchain]\nchannel = "1.99.2"\nprofile = "minimal"\n')
        self.assertEqual(toolchains.rust_channel(self.root),'1.99.2')

    def test_jdk_version_comes_from_the_gradle_build(self):
        self.assertIsNone(toolchains.jdk_version(self.root))
        path=self.root/'android/app/build.gradle.kts';path.parent.mkdir(parents=True)
        path.write_text('android {\n  compileOptions {\n    sourceCompatibility = JavaVersion.VERSION_26\n  }\n}\n')
        self.assertEqual(toolchains.jdk_version(self.root),26)
