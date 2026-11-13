import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

import optimize
import quality

FIXTURE = Path(__file__).with_name('fixtures') / 'optimize'


class OptimizeTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        shutil.copytree(FIXTURE, self.root, dirs_exist_ok=True)
        subprocess.run(['git', 'init', '-q', str(self.root)], check=True)
        subprocess.run(['git', '-C', str(self.root), 'add', '.'], check=True)
        subprocess.run(['git', '-C', str(self.root), '-c', 'user.email=a@b', '-c', 'user.name=t',
                        'commit', '-qm', 'fixture'], check=True)
        self.finding = quality.finding('test-quality', 'test-quality', 'test/duplicate_test.dart', 9,
                                       "Test 'adds numbers again' matches body at test/duplicate_test.dart:3",
                                       code='same_body', symbols=['adds numbers again'])

    def test_plan_attaches_a_deterministic_operation_only_to_exact_same_file_duplicates(self):
        payload = optimize.plan(self.root, findings=[self.finding], sources=['probe'], bridge_info={'names': []})
        item = payload['items'][0]
        self.assertEqual(item['action'], 'MERGE')
        self.assertEqual(item['blocking_conditions'], [])
        self.assertEqual(item['operation']['kind'], 'remove_test_block')
        self.assertEqual(item['operation']['source']['same_file'], True)

    def test_apply_requires_explicit_approval(self):
        payload = optimize.plan(self.root, findings=[self.finding], bridge_info={'names': []})
        with self.assertRaises(ValueError):
            optimize.apply(self.root, payload['report'], [])

    def test_dry_run_does_not_modify_source(self):
        payload = optimize.plan(self.root, findings=[self.finding], bridge_info={'names': []})
        item = payload['items'][0]
        before = (self.root / 'test/duplicate_test.dart').read_text()
        result = optimize.apply(self.root, payload['report'], [item['id']], dry_run=True)
        self.assertEqual(result['status'], 'DRY_RUN')
        self.assertEqual((self.root / 'test/duplicate_test.dart').read_text(), before)

    def test_apply_removes_the_approved_duplicate_and_keeps_a_backup(self):
        payload = optimize.plan(self.root, findings=[self.finding], bridge_info={'names': []})
        item = payload['items'][0]
        result = optimize.apply(self.root, payload['report'], [item['id']])
        self.assertEqual(result['status'], 'APPLIED_NOT_VERIFIED')
        text = (self.root / 'test/duplicate_test.dart').read_text()
        self.assertNotIn('adds numbers again', text)
        self.assertIn('adds numbers', text)
        self.assertTrue((Path(result['backup']) / 'test/duplicate_test.dart').is_file())

    def test_failed_verification_rolls_back(self):
        payload = optimize.plan(self.root, findings=[self.finding], bridge_info={'names': []})
        item = payload['items'][0]
        before = (self.root / 'test/duplicate_test.dart').read_text()
        result = optimize.apply(self.root, payload['report'], [item['id']],
                                verify_commands=[[sys.executable, '-c', 'import sys; sys.exit(1)']])
        self.assertEqual(result['status'], 'ROLLED_BACK')
        self.assertEqual((self.root / 'test/duplicate_test.dart').read_text(), before)

    def test_production_removal_is_blocked_even_with_approval(self):
        finding = quality.finding('audit:dart', 'dead-code', 'lib/a.dart', 2, 'Unused import', code='unused_import',
                                  confidence='CONFIRMED')
        payload = optimize.plan(self.root, findings=[finding], bridge_info={'names': []})
        item = payload['items'][0]
        self.assertEqual(item['action'], 'REMOVE')
        self.assertTrue(item['blocking_conditions'])
        self.assertIsNone(item['operation'])
        result = optimize.apply(self.root, payload['report'], [item['id']])
        self.assertEqual(result['status'], 'BLOCKED')

    def test_bridge_reachable_symbol_is_never_a_removal_candidate(self):
        finding = quality.finding('audit:kotlin', 'dead-code', 'app/Native.kt', 3, 'Unused native declaration',
                                  code='unused_element', confidence='CONFIRMED', symbols=['openFile'])
        payload = optimize.plan(self.root, findings=[finding], bridge_info={'names': ['openFile']})
        item = payload['items'][0]
        self.assertEqual(item['action'], 'KEEP')
        self.assertEqual(item['cross_language_impact'], 'BRIDGE_REACHABLE')

    def test_compare_never_turns_missing_evidence_into_improvement(self):
        before = self.root / 'before.json'
        after = self.root / 'after.json'
        before.write_text(json.dumps({'findings': [], 'tests': [{'id': 'a'}]}))
        after.write_text(json.dumps({'findings': []}))
        result = optimize.compare(self.root, before, after)
        self.assertEqual(result['status'], 'INCOMPLETE')
        self.assertIn('coverage', result['missing_evidence'])


if __name__ == '__main__':
    unittest.main()
