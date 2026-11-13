import json
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest

import quality


class QualityTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)

    def test_fingerprint_is_deterministic_and_line_independent(self):
        first = quality.finding('dart', 'dead-code', 'lib/a.dart', 3, 'Unused import', code='unused_import')
        moved = quality.finding('dart', 'dead-code', 'lib/a.dart', 99, 'Unused import', code='unused_import')
        self.assertEqual(first['fingerprint'], moved['fingerprint'])
        self.assertEqual(first['id'], moved['id'])
        self.assertNotEqual(first['fingerprint'], quality.finding(
            'other', 'dead-code', 'lib/a.dart', 3, 'Unused import', code='unused_import')['fingerprint'])
        self.assertEqual(first['path'], 'lib/a.dart')

    def test_execution_status_aggregation_never_hides_a_gap(self):
        self.assertEqual(quality.combine_statuses(['PASS', 'FINDINGS']), 'FINDINGS')
        self.assertEqual(quality.combine_statuses(['PASS', 'UNAVAILABLE']), 'UNAVAILABLE')
        self.assertEqual(quality.combine_statuses(['FINDINGS', 'INCOMPLETE']), 'INCOMPLETE')
        self.assertEqual(quality.combine_statuses(['INCOMPLETE', 'FAILED']), 'FAILED')
        with self.assertRaises(ValueError):
            quality.combine_statuses(['NOT_A_STATUS'])
        self.assertEqual(quality.result_status({'a': {'status': 'UNAVAILABLE'}}, []), 'UNAVAILABLE')
        self.assertEqual(quality.result_status({'a': {'status': 'UNAVAILABLE'}},
                                                [{'fingerprint': 'x'}]), 'INCOMPLETE')

    def test_missing_tool_is_unavailable_not_clean(self):
        result = quality.run(['definitely-not-a-real-tool-xyz'], self.root, 5)
        self.assertEqual(result['status'], 'UNAVAILABLE')
        self.assertIn('not found', result['reason'])

    def test_timeout_is_failed(self):
        result = quality.run([sys.executable, '-c', 'import time; time.sleep(5)'], self.root, 0.2)
        self.assertEqual(result['status'], 'FAILED')
        self.assertIn('timeout', result['reason'])

    def test_cache_reuses_matching_input_and_invalidates_on_change(self):
        cache = quality.Cache(self.root, 'unit')
        (self.root / 'a.py').write_text('x = 1\n')
        payload = {'files': quality.digest_files(self.root, ['a.py']), 'tool': 'ruff'}
        key = cache.key(payload)
        self.assertIsNone(cache.load(key))
        cache.store(key, {'value': 1})
        self.assertEqual(cache.load(key), {'value': 1})
        changed = cache.key({'files': quality.digest_files(self.root, ['a.py']), 'tool': 'ruff2'})
        self.assertIsNone(cache.load(changed))
        self.assertEqual(cache.clear(), 1)
        self.assertIsNone(cache.load(key))

    def test_report_writes_json_and_markdown_atomically(self):
        payload = {'title': 'Probe', 'status': 'FINDINGS', 'findings': [
            quality.finding('probe', 'code', 'a.py', 1, 'thing', code='X1')],
            'tools': {'probe': {'status': 'PASS'}}, 'limitations': ['a limit']}
        json_path, md_path = quality.write_report(self.root, 'probe', payload)
        data = json.loads(json_path.read_text())
        self.assertEqual(data['schema'], quality.SCHEMA)
        self.assertEqual(data['findings'][0]['id'].startswith('F-'), True)
        self.assertIn('Probe', md_path.read_text())
        self.assertIn('a limit', md_path.read_text())

    def test_baseline_comparison_is_explicit_when_missing(self):
        current = [quality.finding('probe', 'code', 'a.py', 1, 'one', code='X1')]
        self.assertEqual(quality.compare(current, None)['status'], 'NOT_COMPARED')
        old = [quality.finding('probe', 'code', 'a.py', 1, 'two', code='X2')]
        result = quality.compare(current, old)
        self.assertEqual((result['new'], result['fixed'], result['unchanged']), (1, 1, 0))

    def test_with_schema_preserves_legacy_fields(self):
        item = quality.with_schema({'path': 'a.py', 'line': 2, 'code': 'F401', 'message': 'unused',
                                    'categories': ['dead-code'], 'confidence': 'REVIEW_REQUIRED'})
        self.assertEqual(item['path'], 'a.py')
        self.assertEqual(item['category'], 'dead-code')
        self.assertTrue(item['fingerprint'])
        self.assertEqual(item['confidence'], 'REVIEW_REQUIRED')


if __name__ == '__main__':
    unittest.main()
