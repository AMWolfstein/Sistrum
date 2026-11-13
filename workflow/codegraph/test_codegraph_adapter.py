"""Real-engine regressions and staging/normalization boundaries; not mocked parser claims."""
import json
import os
import shutil
import tempfile
import unittest
from pathlib import Path
from unittest import mock
import codegraph_adapter as adapter


class StagingTests(unittest.TestCase):
    def test_untrusted_paths_and_symlinks_cannot_escape_snapshot(self):
        for value in ('../outside', '/absolute', '.', '.git/config', '.codegraph/db', 'C:/drive', 'a/../b'):
            with self.subTest(value=value), self.assertRaises(ValueError):
                adapter.safe_relative(value)
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary);source = root / 'project';source.mkdir();cache = root / 'cache';cache.mkdir()
            (source / 'bad.dart').symlink_to('/etc/passwd')
            with self.assertRaises(ValueError): adapter.stage(source, cache, ['bad.dart'])

    def test_incremental_snapshot_handles_directory_to_file_and_deletion(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary);source = root / 'project';source.mkdir();cache = root / 'cache';cache.mkdir()
            directory = source / 'module.dart';directory.mkdir();(directory / 'child.dart').write_text('class A {}')
            mirror = adapter.stage(source, cache, ['module.dart/child.dart'])
            (directory / 'child.dart').unlink();directory.rmdir();directory.write_text('class B {}')
            adapter.stage(source, cache, ['module.dart'])
            self.assertEqual((mirror / 'module.dart').read_text(), 'class B {}')
            adapter.stage(source, cache, [])
            self.assertFalse((mirror / 'module.dart').exists())

    def test_unchanged_source_mtime_is_preserved_for_incremental_sync(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary);source = root / 'project';source.mkdir();cache = root / 'cache';cache.mkdir()
            (source / 'a.dart').write_text('class A {}')
            mirror = adapter.stage(source, cache, ['a.dart']);before = (mirror / 'a.dart').stat().st_mtime_ns
            adapter.stage(source, cache, ['a.dart'])
            self.assertEqual((mirror / 'a.dart').stat().st_mtime_ns, before)






CLI = shutil.which(adapter.command()[0])


@unittest.skipUnless(CLI, 'CodeGraph CLI not installed; real parser coverage not verified')
class RealCodeGraphRegressions(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temporary = tempfile.TemporaryDirectory(prefix='codegraph regression ')
        cls.addClassCleanup(cls.temporary.cleanup)
        cls.root = Path(cls.temporary.name);cls.source = cls.root / 'source';cls.source.mkdir();cls.cache = cls.root / 'cache'
        cls.fixture = {
            'lib/calls.dart': "import 'package:external/api.dart';\nclass First {\n void runJob() {\n  processItem();\n  final text = 'ghostCall()';\n  calculateChecksum();\n }\n void processItem() {}\n}\nclass Second {\n void processItem() {}\n}\nvoid ghostCall() {}\n",
            'kotlin/Engine.kt': 'package sample\nclass Engine {\n fun driveKotlin() {\n  stepKotlin()\n }\n fun stepKotlin() {}\n}\n',
            'rust/lib.rs': 'pub fn calculateChecksum() {}\nfn target_rust() {}\nfn drive_rust() {\n target_rust();\n}\n',
            'cpp/engine.cpp': 'void targetCpp() {}\nvoid driveCpp() {\n targetCpp();\n}\n',
            'c/engine.c': 'void targetC(void) {}\nvoid driveC(void) {\n targetC();\n}\n',
            'java/App.java': 'class App {\n int computeJava() { return 1; }\n int driveJava() {\n  return computeJava();\n }\n}\n',
            'py/helper.py': 'def target_python():\n    return 1\n',
            'py/main.py': 'from helper import target_python\ndef drive_python():\n    return target_python()\n',
            'js/main.js': 'function targetJS() { return 1; }\nfunction driveJS() {\n return targetJS();\n}\n',
            'scripts/run.sh': 'build_project() {\n echo hello\n}\n',
            'res/values.xml': '<resources><string name="title">Sistrum</string></resources>\n',
            'pubspec.yaml': 'name: test_fixture\nenvironment:\n  sdk: ">=3.0.0 <4.0.0"\n'}
        for name, text in cls.fixture.items():
            path = cls.source / name;path.parent.mkdir(parents=True, exist_ok=True);path.write_text(text)
        with cls.environment():
            cls.raw = cls.extract()
        cls.nodes = {n['id']: n for n in cls.raw['nodes']}

    @classmethod
    def environment(cls):
        return mock.patch.dict(os.environ, {'CODEGRAPH_CACHE': str(cls.cache), 'CODEGRAPH_COMMAND': json.dumps([CLI])})

    @classmethod
    def extract(cls):
        req, out = cls.root / 'request.json', cls.root / 'out.json'
        req.write_text(json.dumps({'root': str(cls.source), 'files': sorted(cls.fixture)}))
        adapter.extract(req, out)
        return json.loads(out.read_text())

    def call_pairs(self):
        return [(self.nodes[e['source']], self.nodes[e['target']], e)
                for e in self.raw['edges'] if e['relation'] == 'calls']

    def test_core_languages_have_real_declarations_and_calls(self):
        pairs = {(s['label'], t['label']) for s, t, _ in self.call_pairs()}
        for pair in [('driveKotlin', 'stepKotlin'), ('drive_rust', 'target_rust'), ('driveCpp', 'targetCpp'),
                     ('driveC', 'targetC'), ('driveJava', 'computeJava'), ('drive_python', 'target_python'), ('driveJS', 'targetJS')]:
            with self.subTest(pair=pair): self.assertIn(pair, pairs)

    def test_strings_declarations_and_wrong_class_do_not_become_calls(self):
        calls = [(s, t, e) for s, t, e in self.call_pairs() if s['source_file'] == 'lib/calls.dart']
        self.assertFalse([e for s, t, e in calls if t['label'] == 'ghostCall'])
        targets = [t for s, t, e in calls if s['label'] == 'runJob' and t['label'] == 'processItem']
        self.assertTrue(targets)
        self.assertTrue(all('First' in t.get('qualified_name', '') for t in targets))
        self.assertFalse([e for s, t, e in calls if e['source_location'] in ('L8', 'L11', 'L13')])
        self.assertFalse([e for s, t, e in calls if t['source_file'].endswith('.rs')])

    def test_shell_gaps_and_resource_diagnostics_are_explicit(self):
        coverage = {c['file']: c for c in self.raw['coverage']}
        self.assertEqual(coverage['scripts/run.sh']['status'], 'not-indexed')
        self.assertIn(coverage['lib/calls.dart']['status'], ('indexed', 'partial'))
        self.assertIn('unresolved_references', coverage['lib/calls.dart'])
        self.assertIn('res/values.xml', coverage)

    def test_python_import_dependency_survives_normalization(self):
        imports = {(self.nodes[e['source']]['source_file'], self.nodes[e['target']]['source_file'])
                   for e in self.raw['edges'] if e['relation'] == 'imports'}
        self.assertIn(('py/main.py', 'py/helper.py'), imports)


    def test_real_rename_and_delete_leave_no_stale_symbols(self):
        # A separate mirror keeps this mutation independent of the shared parser fixtures.
        with tempfile.TemporaryDirectory() as temporary, mock.patch.dict(os.environ, {
                'CODEGRAPH_CACHE': temporary, 'CODEGRAPH_COMMAND': json.dumps([CLI])}):
            req, out = Path(temporary) / 'r.json', Path(temporary) / 'o.json'
            def run(files):
                req.write_text(json.dumps({'root': str(self.source), 'files': files}))
                adapter.extract(req, out);return json.loads(out.read_text())
            self.assertTrue(run(['lib/calls.dart'])['nodes'])
            after = run(['js/main.js'])
            self.assertFalse(any(n['source_file'] == 'lib/calls.dart' for n in after['nodes']))
            self.assertEqual(run([])['nodes'], [])


if __name__ == '__main__': unittest.main()
