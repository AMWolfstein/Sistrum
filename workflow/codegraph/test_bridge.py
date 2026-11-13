import json
from pathlib import Path
import shutil
import tempfile
import unittest

import bridge
import quality


FIXTURE = Path(__file__).with_name('fixtures') / 'bridge'


class BridgeTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        shutil.copytree(FIXTURE, self.root, dirs_exist_ok=True)

    def test_discovery_separates_missing_symbol_and_signature_mismatch(self):
        data = bridge.discover(self.root)
        by_name = {contract['name']: contract for contract in data['contracts']}
        self.assertEqual(by_name['Java_com_example_NativeBridge_missingSymbol']['status'], 'FAILED')
        self.assertEqual(by_name['Java_com_example_NativeBridge_missingSymbol']['checks']['symbol_resolution']['status'], 'FAIL')
        mismatch = by_name['Java_com_example_NativeBridge_signatureMismatch']
        self.assertEqual(mismatch['status'], 'FAILED')
        self.assertIn('2', mismatch['checks']['signature_compatibility']['detail'])
        self.assertIn('1', mismatch['checks']['signature_compatibility']['detail'])
        open_file = by_name['Java_com_example_NativeBridge_openFile']
        self.assertNotEqual(open_file['status'], 'VERIFIED')
        self.assertEqual(open_file['checks']['abi_compatibility']['status'], 'NOT_VERIFIED')

    def test_dynamic_and_unresolved_mechanisms_stay_explicit(self):
        data = bridge.discover(self.root)
        dynamic = [c for c in data['contracts'] if c['mechanism'] == 'jni-dynamic-registration']
        self.assertTrue(dynamic)
        self.assertEqual(dynamic[0]['status'], 'UNVERIFIED')
        unresolved = [c for c in data['contracts'] if c['unresolved_references']]
        self.assertTrue(unresolved)
        self.assertGreaterEqual(data['inventory']['unresolved_channels'], 1)

    def test_generated_channel_is_reported_but_not_claimed_verified(self):
        data = bridge.discover(self.root)
        api = next(c for c in data['contracts'] if c['mechanism'] == 'platform-channel' and c['name'] == 'dev.example/api')
        self.assertTrue(api['generated_binding'])
        self.assertEqual(api['status'], 'PARTIAL')

    def test_abi_mismatch_is_statically_detectable(self):
        data = bridge.discover(self.root)
        exported = next(c for c in data['contracts'] if c['name'] == 'sistrum_rust_export')
        self.assertEqual(exported['checks']['abi_compatibility']['status'], 'FAIL')
        self.assertEqual(exported['status'], 'FAILED')

    def test_guard_blocks_removal_even_for_verified_contract(self):
        data = bridge.discover(self.root)
        for contract in data['contracts']:
            self.assertFalse(contract['optimization']['eligible'])
            if contract['status'] != 'VERIFIED':
                self.assertEqual(contract['optimization']['status'], 'BLOCKED')
        index = bridge.bridge_index(self.root)
        self.assertIn('Java_com_example_NativeBridge_openFile', index['names'])
        self.assertIn('app/src/main/cpp/native_bridge.cpp', index['files'])

    def test_report_and_scorecard_never_claim_100_from_analyzer_self_coverage(self):
        payload = bridge.verify(self.root)
        self.assertEqual(payload['status'], 'FAILED')
        self.assertLess(payload['scorecard']['discovery']['coverage_pct'], 100.0)
        self.assertEqual(payload['scorecard']['runtime_verification']['coverage_pct'], 0.0)
        self.assertGreater(payload['scorecard']['failed_contracts']['count'], 0)
        self.assertTrue(Path(payload['report']).is_file())
        self.assertTrue(Path(payload['report_markdown']).is_file())

    def test_runtime_report_is_the_only_runtime_evidence(self):
        data = bridge.discover(self.root)
        target = next(c for c in data['contracts'] if c['name'] == 'Java_com_example_NativeBridge_openFile')
        report = self.root / 'runtime.json'
        report.write_text(json.dumps({'contracts': {target['id']: {'passed': True, 'tests': ['JniOpenFileTest']}}}))
        payload = bridge.verify(self.root, runtime_report=report)
        updated = next(c for c in payload['contracts'] if c['id'] == target['id'])
        self.assertEqual(updated['checks']['runtime_execution']['status'], 'PASS')
        self.assertGreater(payload['scorecard']['runtime_verification']['verified_contracts'], 0)
        self.assertEqual(payload['scorecard']['runtime_verification']['required_contracts'],
                         len(payload['contracts']))

    def test_empty_inventory_reports_unknown_discovery(self):
        empty = Path(self.tmp.name) / 'empty'
        empty.mkdir()
        score = bridge.scorecard([], {})
        self.assertEqual(score['discovery']['coverage_pct'], 'UNKNOWN')


if __name__ == '__main__':
    unittest.main()
